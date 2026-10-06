#!/usr/bin/env python3
# Copyright (c) 2026, RTE (http://www.rte-france.com)
# SPDX-License-Identifier: MPL-2.0
#
# Equation-formula generator — ONE residual single-source, SymPy autodiff, Jinja
# render. From codegen/equations/<family>.py it produces the TWO committed targets:
#
#   - the SCALAR CPU API  : src/main/java/.../<Class>.java   (28 static methods,
#                           precomputed-trig params, called by the equation terms
#                           and AcNetworkVector)
#   - the FUSED CUDA kernel: native/cuda/<family>.cuh        (one evalClosedBranch
#                           per element, sin/cos shared via CSE, SoA output — the
#                           GPU assembly kernel body)
#
# Derivatives come from sp.diff (no hand-derived Jacobian). SymPy + Jinja run in
# the render path; the CI git-diff guard is an alert, not a determinism contract —
# a SymPy/Jinja version bump that reformats a body is just re-committed.
#
# Run:  pip install -r codegen/requirements.txt && python3 codegen/generate.py
import json
import os
import re
import sys

import sympy as sp
from jinja2 import Environment, FileSystemLoader

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
sys.path.insert(0, HERE)

from equations import closed_branch  # noqa: E402
from equations import shunt  # noqa: E402
from equations import open_branch  # noqa: E402
from equations import closed_branch_i  # noqa: E402
from equations import open_branch_i  # noqa: E402
from equations import hvdc  # noqa: E402

MODULES = [closed_branch.MODULE, shunt.MODULE, open_branch.MODULE, closed_branch_i.MODULE, open_branch_i.MODULE, hvdc.MODULE]
REGEN = "Regenerate with: python3 codegen/generate.py (see codegen/README.md)."


# --------------------------------------------------------------------------------
# Shared core: residuals + sp.diff  (the only physics, from the single source).
# Family-agnostic: the symbols, branch angles (thetas, optional), differentiation
# variables and constants all come from the per-family MODULE.
# --------------------------------------------------------------------------------

def _symbols(mod):
    syms = {n: sp.Symbol(n, real=True) for n in mod["base_symbols"]}
    syms.update({c: sp.Symbol(c) for c in mod.get("constants", {})})    # kept symbolic
    thetas = {side: sp.sympify(expr, locals=syms)
              for side, expr in mod.get("thetas", {}).items()}
    return syms, thetas


def outputs(mod):
    """Value + every (non-zero) derivative as raw-angle SymPy expressions, tagged with
    the residual's side. A derivative that is identically zero is dropped — the original
    term declares der() only for variables its formula depends on. Shared by both targets.

    EXPLICIT families (HVDC AC emulation) author every output by hand — value AND derivatives —
    because the Jacobian is an intentional approximation (OLF neglects the cable-loss derivative),
    so SymPy autodiff would NOT reproduce it. These bypass sp.diff entirely."""
    syms, thetas = _symbols(mod)
    if "explicit_outputs" in mod:
        return syms, thetas, [(name, None, expr) for name, expr in mod["explicit_outputs"]]
    out = []
    for name, side, expr_str in mod["residuals"]:
        local = {**syms, "sin": sp.sin, "cos": sp.cos}
        if side is not None and side in thetas:
            local["theta"] = thetas[side]
        resid = sp.sympify(expr_str, locals=local)
        out.append((name, side, resid))
        for var in mod["variables"]:
            d = sp.diff(resid, syms[var])
            if d != 0:
                out.append((f"d{name}d{var}", side, d))
    return syms, thetas, out


# --------------------------------------------------------------------------------
# SCALAR target: precomputed-trig methods (trig-substitution + derived signatures)
# --------------------------------------------------------------------------------

# Expand integer powers to repeated multiplication at the string level — robust where
# SymPy's unevaluated-Mul rewrite re-collapses to '**' on print, and (unlike a bare-symbol
# regex) handling a parenthesised base too: (g1 + y*sinKsi)**2 -> (g1 + y*sinKsi)*(...).
def _expand_pows(s):
    while "**" in s:
        i = s.index("**")
        j = i + 2
        while j < len(s) and s[j].isdigit():
            j += 1
        n = int(s[i + 2:j])
        if s[i - 1] == ")":                            # parenthesised base: find its '('
            depth, k = 0, i - 1
            while k >= 0:
                depth += 1 if s[k] == ")" else (-1 if s[k] == "(" else 0)
                if depth == 0:
                    break
                k -= 1
        else:                                          # bare symbol base
            k = i - 1
            while k >= 0 and (s[k].isalnum() or s[k] == "_"):
                k -= 1
            k += 1
        base = s[k:i]
        s = s[:k] + "*".join([base] * n) + s[j:]
    return s


def _java_body(expr):
    s = _expand_pows(sp.sstr(expr))
    if "**" in s:
        raise AssertionError(f"unexpected power in body: {s}")
    return s.replace("*", " * ").replace("/", " / ")   # checkstyle WhitespaceAround on '*' '/'


def _jscode(expr):
    return " ".join(sp.jscode(expr).split())   # one line, no padding


def _piecewise_str(pw):
    """Render a SymPy Piecewise as a Java conditional `cond ? a : b` (right-assoc for >2
    branches), matching OLF's hand style — no branch-wrapping parens (so no ParenPad /
    UnnecessaryParentheses). Conditions print from the relation's lhs/op/rhs."""
    *cases, (default, _true) = pw.args
    out = _jscode(default)
    for value, cond in reversed(cases):
        out = f"{_jscode(cond.lhs)} {cond.rel_op} {_jscode(cond.rhs)} ? {_jscode(value)} : {out}"
    return out


def _expand_math_pow(s):
    """Rewrite Math.pow(<base>, <int n>) as repeated multiplication (x * x), like the sstr
    path's _expand_pows but for SymPy's JS printer, which emits Math.pow for integer powers.
    Math.pow(x, 2) is far slower than x * x (HotSpot routes it through the pow intrinsic, no
    reliable strength reduction), and it is only an integer power here (sqrt prints Math.sqrt).
    A bare identifier/number base is left unparenthesised (no checkstyle UnnecessaryParentheses);
    a compound base keeps grouping parens for '*' precedence."""
    marker = "Math.pow("
    while True:
        i = s.find(marker)
        if i < 0:
            return s
        depth, k, comma = 0, i + len(marker) - 1, -1   # k starts at the marker's '('
        while k < len(s):
            depth += 1 if s[k] == "(" else (-1 if s[k] == ")" else 0)
            if s[k] == "," and depth == 1:
                comma = k
            if depth == 0:
                break
            k += 1
        base, exp = s[i + len(marker):comma].strip(), s[comma + 1:k].strip()
        n = int(exp)                                   # non-integer would be Math.sqrt, not pow
        if n < 2:
            raise AssertionError(f"unexpected Math.pow exponent {n}: {s}")
        inner = base if re.fullmatch(r"[\w.]+", base) else f"({base})"
        s = s[:i] + "*".join([inner] * n) + s[k + 1:]


def _java_body_raw(expr):
    """Bodies that keep transcendentals (current magnitude: sin/cos/sqrt) or a Piecewise/Abs
    (HVDC): emit via SymPy's JS printer (Math.sin/cos/sqrt/abs). A top-level Piecewise is
    rendered as a ?: ternary; integer Math.pow is expanded to products; operators spaced for
    checkstyle."""
    s = _piecewise_str(expr) if isinstance(expr, sp.Piecewise) else _jscode(expr)
    s = _expand_math_pow(s)
    if "**" in s:
        raise AssertionError(f"unexpected power in raw body: {s}")
    return s.replace("*", " * ").replace("/", " / ")


def _needs_math(expr):
    """Does the body keep a trig call, a non-integer (sqrt) power, or a Piecewise/Abs (HVDC)?
    Plain division (Pow with integer -1) does not, so the P/Q families still render via sstr."""
    if expr.has(sp.sin) or expr.has(sp.cos) or expr.has(sp.atan2) \
            or expr.has(sp.Piecewise) or expr.has(sp.Abs):
        return True
    return any(not p.exp.is_Integer for p in expr.atoms(sp.Pow))


def _emit(expr, mod, syms, thetas, side):
    """Apply the family's trig substitution (precomputed sinKsi/cosKsi/sinTheta/cosTheta
    unless raw_trig), render the Java body (Math.* when a transcendental/sqrt remains, else
    sstr), and derive the param list as the canonical-order subset of the body's free symbols."""
    const_names = set(mod.get("constants", {}))
    raw_trig = mod.get("raw_trig", False)
    subs = {}
    if not raw_trig:
        if "ksi" in syms:
            subs[sp.sin(syms["ksi"])] = sp.Symbol("sinKsi")
            subs[sp.cos(syms["ksi"])] = sp.Symbol("cosKsi")
        if side is not None and side in thetas:
            subs[sp.sin(thetas[side])] = sp.Symbol("sinTheta")
            subs[sp.cos(thetas[side])] = sp.Symbol("cosTheta")
    body = expr.subs(subs) if subs else expr
    java = _java_body_raw(body) if _needs_math(body) else _java_body(body)
    free = {s.name for s in body.free_symbols} - const_names
    unknown = free - set(mod["canonical_order"])
    if unknown:
        raise AssertionError(f"unknown symbol(s) {sorted(unknown)}")
    params = [c for c in mod["canonical_order"] if c in free]
    return java, params


def scalar_methods(mod):
    """[{name, params, body, public}] for the CPU API. Magnitude families (current
    magnitude) emit the structured chain-rule form — named component helpers reI/imI and
    their per-variable derivatives, with the value/derivative methods assembled by calling
    them — instead of one inlined closed form per output. Other families emit the flat
    derived bodies. Each signature is the canonical-order subset in the body."""
    if "magnitudes" in mod:
        return magnitude_methods(mod)
    syms, thetas, out = outputs(mod)
    methods = []
    for name, side, expr in out:
        java, params = _emit(expr, mod, syms, thetas, side)
        methods.append({"name": name, "params": params, "body": java, "public": True})
    return methods


def magnitude_methods(mod):
    """Structured emission for a magnitude family i = hypot(reI, imI): one method per named
    component (reI/imI) and per-variable component derivative (dreId<var>/dimId<var>), all
    private; the value i = hypot(reI(...), imI(...)) and each derivative
    di/d<var> = (reI(...)*dreId<var>(...) + imI(...)*dimId<var>(...)) / i(...) are PUBLIC and
    assembled by CALLING those helpers, reproducing OLF's original chain-rule structure
    rather than inlining the whole closed form. The component derivatives are still SymPy
    sp.diff of the (small) component expressions — only the outer chain-rule skeleton is
    templated, so there is no hand-derived Jacobian. A component derivative that is
    identically zero drops its term (and its helper); a wholly-zero derivative drops the
    public method, matching the original (der declared only for depended-on variables)."""
    syms, thetas = _symbols(mod)
    canonical = mod["canonical_order"]

    def call(name, params):
        return f"{name}({', '.join(params)})"

    def ordered(*paramsets):
        used = set().union(*paramsets)
        return [c for c in canonical if c in used]

    methods = []
    for mag in mod["magnitudes"]:
        name, re_name, im_name = mag["name"], mag["re_name"], mag["im_name"]
        local = {**syms, "sin": sp.sin, "cos": sp.cos}
        re = sp.sympify(mag["re"], locals=local)
        im = sp.sympify(mag["im"], locals=local)

        re_body, re_params = _emit(re, mod, syms, thetas, None)
        im_body, im_params = _emit(im, mod, syms, thetas, None)
        methods.append({"name": re_name, "params": re_params, "body": re_body, "public": False})
        methods.append({"name": im_name, "params": im_params, "body": im_body, "public": False})

        re_call, im_call = call(re_name, re_params), call(im_name, im_params)
        i_params = ordered(set(re_params), set(im_params))
        methods.append({"name": name, "params": i_params,
                        "body": f"Math.hypot({re_call}, {im_call})", "public": True})

        alias = mag.get("alias", {})
        deriv_params = {}                              # var -> the public derivative's params (for aliasing)
        for var in mod["variables"]:
            if var in alias:
                continue                               # emitted as a delegation below
            dre = sp.diff(re, syms[var])
            dim = sp.diff(im, syms[var])
            if dre == 0 and dim == 0:
                continue                               # rotation-invariant / independent: no der()
            # Compute the components ONCE in locals: the derivative is
            # (re*dreId<var> + im*dimId<var>)/hypot(re, im); calling i(...) would re-evaluate
            # reI/imI (their trig). reI/imI are needed for the hypot denominator regardless, so
            # both locals are always bound even when one component-derivative term drops out.
            terms, dparams = [], set()
            if dre != 0:
                dn = f"d{re_name}d{var}"
                db, dp = _emit(dre, mod, syms, thetas, None)
                methods.append({"name": dn, "params": dp, "body": db, "public": False})
                terms.append(f"re * {call(dn, dp)}")
                dparams |= set(dp)
            if dim != 0:
                dn = f"d{im_name}d{var}"
                db, dp = _emit(dim, mod, syms, thetas, None)
                methods.append({"name": dn, "params": dp, "body": db, "public": False})
                terms.append(f"im * {call(dn, dp)}")
                dparams |= set(dp)
            d_params = ordered(set(re_params), set(im_params), dparams)
            deriv_params[var] = d_params
            prelude = [f"double re = {re_call};", f"double im = {im_call};"]
            methods.append({"name": f"d{name}d{var}", "params": d_params, "prelude": prelude,
                            "body": f"({' + '.join(terms)}) / Math.hypot(re, im)", "public": True})

        for var, target in alias.items():             # di<name>d<var> = [-]di<name>d<paired angle>
            sign = "-" if target.startswith("-") else ""
            tvar = target.lstrip("+-")
            tname, tparams = f"d{name}d{tvar}", deriv_params[tvar]
            methods.append({"name": f"d{name}d{var}", "params": tparams,
                            "body": f"{sign}{call(tname, tparams)}", "public": True})
    return methods



def render_scalar(env, mod):
    methods = [{"name": m["name"],
                "vis": "public" if m.get("public", True) else "private",
                "sig": ", ".join("double " + p for p in m["params"]),
                "prelude": m.get("prelude", []),
                "body": m["body"]}
               for m in scalar_methods(mod)]
    return env.get_template("formulas.java.j2").render(
        package=mod["java_package"], imports="\n".join(mod["java_imports"]),
        doc=mod["doc"], regen=REGEN, class_name=mod["java_class"], methods=methods)


# --------------------------------------------------------------------------------
# FUSED target: one evalClosedBranch, sin/cos shared via CSE, SoA output (CUDA)
# --------------------------------------------------------------------------------

def _expand_cpow(s):
    """Rewrite ccode's pow(<expr>, <int n>) as repeated multiplication — integer powers
    (the open-branch shunt denominators) must not go through CUDA's floating pow()."""
    while True:
        i = s.find("pow(")
        if i < 0:
            return s
        depth, k = 0, i + 3
        comma = -1
        while k < len(s):
            depth += 1 if s[k] == "(" else (-1 if s[k] == ")" else 0)
            if s[k] == "," and depth == 1:
                comma = k
            if depth == 0:
                break
            k += 1
        base, exp = s[i + 4:comma], s[comma + 1:k].strip()
        n = int(exp)                                   # non-integer would be a spec bug here
        if n < 2:
            raise AssertionError(f"unexpected pow exponent {n} in CUDA body: {s}")
        s = s[:i] + "*".join([f"({base})"] * n) + s[k + 1:]


def _cuda_expr(s):
    s = _expand_cpow(" ".join(str(s).split()))     # ccode prints Piecewise multi-line
    if "**" in s:
        raise AssertionError(f"unexpected power in CUDA body: {s}")
    return s.replace("*", " * ")


def _guarded_consts(consts):
    """Families share model constants (R2 appears in the closed- AND open-branch headers,
    same namespace), and the full fill kernel includes several fused headers in one TU —
    guard each constexpr so the second definition drops out instead of redefining."""
    out = []
    for c in consts:
        name = re.match(r"constexpr double (\w+)", c).group(1)
        out.append(f"#ifndef OLF_CONST_{name}\n#define OLF_CONST_{name}\n{c}\n#endif")
    return "\n".join(out)


def render_fused(env, mod):
    _syms, _thetas, out = outputs(mod)
    names = [name for name, _side, _e in out]
    repl, reduced = sp.cse([e for _n, _s, e in out], optimizations="basic")
    temps = [{"sym": str(s), "expr": _cuda_expr(sp.ccode(e))} for s, e in repl]
    body = {nm: _cuda_expr(sp.ccode(e)) for nm, e in zip(names, reduced)}
    parts = mod["cuda_namespace"].split("::")
    ns_open = " ".join(f"namespace {p} {{" for p in parts)
    ns_close = "}" * len(parts) + "  // namespace " + mod["cuda_namespace"]
    param_rows = ["double " + ", double ".join(row) + "," for row in mod["cuda_params"]]
    consts = mod["cuda_consts"]
    consts_block = _guarded_consts(consts) + "\n\n" if consts else ""   # shunt: no constants
    return env.get_template("fused.cuh.j2").render(
        doc=mod["doc"], regen=REGEN, ns_open=ns_open, ns_close=ns_close,
        fused_doc=mod["cuda_fused_doc"], fn_macro=mod["cuda_fn_macro"],
        routine=mod["cuda_routine"], param_rows=param_rows,
        consts_block=consts_block, outputs=names, temps=temps, out=body)


# --------------------------------------------------------------------------------
# SCATTER descriptor: where each output lands in OLF's fixed-pattern Jacobian
# --------------------------------------------------------------------------------

def _scatter_outputs(mod):
    """(residual, var-or-None) per fused-routine output, in the EXACT order the routine
    writes them (value first, then its non-zero derivatives in variables order) — zero
    derivatives are dropped by outputs(), so e.g. open-branch has no dp2dv1 row. For an
    EXPLICIT family the values are the outputs not named d<res>d<var>."""
    if "residuals" in mod:
        resid_names = {n for n, _s, _e in mod["residuals"]}
    else:
        resid_names = {n for n, _e in mod["explicit_outputs"]
                       if not (n.startswith("d") and "d" in n[1:])}
    _syms, _thetas, out = outputs(mod)
    rows = []
    for name, _side, _expr in out:
        if name in resid_names:
            rows.append((name, None))
        else:
            res, var = name[1:].split("d", 1)          # d<res>d<var> (residuals have no 'd')
            rows.append((res, var))
    return rows


def scatter_descriptor(mod):
    """The fixed-pattern-J structural data: per residual the bus
    equation it feeds (the F row), and per Jacobian entry the (equation row, variable
    column) it scatters into. Endpoints are symbolic (bus1/bus2/branch) — the host
    resolves them per branch to flat indices. Guarded against the real term-class
    wiring by check_scatter.py."""
    stamp = mod["stamp"]
    req, vcol = stamp["residual_equation"], stamp["variable_column"]
    family = os.path.splitext(os.path.basename(mod["cuda_path"]))[0]
    residuals, jacobian = [], []
    for res, var in _scatter_outputs(mod):
        eq, at = req[res]
        if var is None:
            residuals.append({"name": res, "equation": eq, "at": at})
        else:
            vtype, vat = vcol[var]
            jacobian.append({"name": f"d{res}d{var}",
                             "row": {"equation": eq, "at": at},
                             "col": {"variable": vtype, "at": vat}})
    return {"family": family,
            "doc": f"Scatter map: how each {family.replace('_', '-')} output lands in OLF's "
                   "fixed-pattern AC Jacobian. GENERATED — DO NOT EDIT. " + REGEN,
            "endpoints": stamp.get("endpoints", ["bus1", "bus2", "branch"]),
            "residuals": residuals, "jacobian": jacobian}


# Packing convention shared by the generated scatter header and the GPU fill kernels
# (native/cuda/jni/olf_gpu_nr_jni.cu): a bus owns two equation rows
# (P, Q) and two variable columns (PHI, V); endpoints index which element. Codes:
# 0/1 = the element's first/only and second bus, 2 = the element itself (branch,
# shunt — only ever the endpoint of skip columns like BRANCH_ALPHA1/RHO1, SHUNT_B,
# which are not state variables in plain AC PF).
EQ_OFFSET = {"BUS_TARGET_P": 0, "BUS_TARGET_Q": 1}
# A bus owns two variable columns (PHI=0, V=1) at endpoints 0/1; the branch (endpoint 2)
# owns two control columns — the ratio ALPHA1=0 and RHO1=1 — so col = busOf(endpoint)*2 +
# offset resolves uniformly to the host index pack (… ph1 v1 ph2 v2 | a1col r1col). These
# branch columns exist only when the branch derives that control (transformer phase/voltage
# control); the host marks them absent (-1) otherwise, so the scatter entry skips per element.
# SHUNT_B stays unlisted -> skip (shunt voltage control is not a GPU-supported state variable).
VAR_OFFSET = {"BUS_PHI": 0, "BUS_V": 1, "BRANCH_ALPHA1": 0, "BRANCH_RHO1": 1}
ENDPOINT_CODE = {"bus1": 0, "bus2": 1, "branch": 2, "bus": 0, "shunt": 2}
ROLE_RESIDUAL, ROLE_JACOBIAN, ROLE_SKIP = 0, 1, 2


def render_scatter_header(mod):
    """C++ scatter table for the fill kernel: per fused-routine output (in the
    exact order the routine writes them), where the value lands. role 0=residual(->F),
    1=jacobian(->J), 2=skip (column variable not a state var in plain AC PF). The host
    resolves endpoints to bus rows/cols; this header is generated so the kernel and the
    CPU references share one source (the stamp)."""
    stamp = mod["stamp"]
    req, vcol = stamp["residual_equation"], stamp["variable_column"]
    family_dash = os.path.splitext(os.path.basename(mod["cuda_path"]))[0].replace("_", "-")
    prefix = mod["scatter_prefix"]                     # e.g. "CB" -> CbScatter / CB_NOUT / CB_SCATTER
    struct = prefix.capitalize() + "Scatter"
    rows = []
    for res, var in _scatter_outputs(mod):
        eq, at = req[res]
        if var is None:
            rows.append((ROLE_RESIDUAL, ENDPOINT_CODE[at], EQ_OFFSET[eq], -1, -1, res))
        else:
            vtype, vat = vcol[var]
            if vtype in VAR_OFFSET:
                rows.append((ROLE_JACOBIAN, ENDPOINT_CODE[at], EQ_OFFSET[eq],
                             ENDPOINT_CODE[vat], VAR_OFFSET[vtype], f"d{res}d{var}"))
            else:
                rows.append((ROLE_SKIP, -1, -1, -1, -1, f"d{res}d{var}"))
    ns = mod["cuda_namespace"].split("::")
    ns_open = " ".join(f"namespace {p} {{" for p in ns)
    ns_close = "}" * len(ns) + "  // namespace " + mod["cuda_namespace"]
    body = "\n".join(
        f"    {{{r}, {re}, {rq}, {ce}, {cv}}},  // [{i}] {nm}"
        for i, (r, re, rq, ce, cv, nm) in enumerate(rows))
    endpoints = stamp.get("endpoints", ["bus1", "bus2", "branch"])
    ep_text = ", ".join(f"{ENDPOINT_CODE[e]}={e}" for e in endpoints)
    return f"""// Copyright (c) 2026, RTE (http://www.rte-france.com)
// SPDX-License-Identifier: MPL-2.0
//
// Scatter table for the fused {family_dash} fill kernel. GENERATED — DO NOT EDIT. {REGEN}
//
// One row per output of {mod["cuda_routine"]}, IN ORDER. role: 0=residual (add to F at its
// equation row), 1=jacobian (add to J at (row,col)), 2=skip (column variable is never a state
// variable in plain AC PF, e.g. SHUNT_B). Branch control columns (BRANCH_ALPHA1/RHO1) are
// emitted as jacobian (role 1) on the branch endpoint: they resolve to a real column only when
// the element derives that control (transformer phase/voltage control) and to absent (-1)
// otherwise, so the host marks the entry skipped per element.
// Endpoint codes: {ep_text}. A bus owns rows [P=+0, Q=+1] and cols [PHI=+0, V=+1]; the branch
// (endpoint 2) owns control cols [ALPHA1=+0, RHO1=+1]:
//   row = busOf(rowEnd) * 2 + rowEq ;  col = busOf(colEnd) * 2 + colVar
#pragma once

{ns_open}

struct {struct} {{ int role; int rowEnd; int rowEq; int colEnd; int colVar; }};

constexpr int {prefix}_NOUT = {len(rows)};
constexpr {struct} {prefix}_SCATTER[{prefix}_NOUT] = {{
{body}
}};

{ns_close}
"""


def write(path, text):
    full = os.path.join(ROOT, path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    if not text.endswith("\n"):
        text += "\n"
    with open(full, "w") as f:
        f.write(text)
    print(f"  wrote {path}")


def _env(trim_blocks):
    return Environment(loader=FileSystemLoader(os.path.join(HERE, "templates")),
                       trim_blocks=trim_blocks, lstrip_blocks=False,
                       keep_trailing_newline=True)


def main():
    env_java = _env(trim_blocks=True)     # scalar template tuned for this
    env_cuda = _env(trim_blocks=False)    # fused template uses {%- ... -%} controls
    for mod in MODULES:
        print(f"{mod['java_class']}: {len(scalar_methods(mod))} scalar methods"
              + ("" if "cuda_path" not in mod else " + fused CUDA routine"))
        write(mod["java_path"], render_scalar(env_java, mod))
        if "cuda_path" in mod:                         # fused CUDA target (optional per family)
            write(mod["cuda_path"], render_fused(env_cuda, mod))
        if "stamp" in mod:                             # GPU scatter descriptor (optional)
            write(mod["scatter_path"],
                  json.dumps(scatter_descriptor(mod), indent=2, ensure_ascii=False))
            write(mod["scatter_header_path"], render_scatter_header(mod))


if __name__ == "__main__":
    main()
