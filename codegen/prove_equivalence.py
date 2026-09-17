#!/usr/bin/env python3
# Copyright (c) 2026, RTE (http://www.rte-france.com)
# SPDX-License-Identifier: MPL-2.0
#
# Equivalence proof + correctness audit for the SymPy-derived closed-branch
# formulas. Formatting-robust (compares numbers, never text), so a SymPy version
# bump can never break it. Three independent guards:
#
#   A. signature-lock   — every derived (name, params) matches the frozen golden
#                         manifest, so the non-generated callers (the equation-term
#                         classes, AcNetworkVector) keep compiling unchanged.
#   B. equivalence      — every derived body equals the ORIGINAL hand-written body
#                         (codegen/golden/closed_branch.golden.json, the verbatim
#                         ClosedBranchFormulas at the time of the swap) to 1e-12,
#                         over random inputs. This is the "prove equivalence vs the
#                         current ClosedBranchFormulas" gate.
#   C. autodiff audit   — every emitted Jacobian entry matches a central finite
#                         difference of the closed-form residual (real sin/cos of
#                         the branch angle) to ~1e-7. Independently confirms the
#                         derivatives are genuine — and would have caught any wrong
#                         hand-derivative in the original.
#
# Run:  python3 codegen/prove_equivalence.py        (needs sympy; exit 0 = proven)
import json
import math
import os
import random
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
sys.path.insert(0, HERE)

import sympy as sp  # noqa: E402

import generate  # noqa: E402  (the SymPy+Jinja generator; reused in-memory)
from equations import closed_branch  # noqa: E402
from equations import shunt  # noqa: E402
from equations import open_branch  # noqa: E402
from equations import closed_branch_i  # noqa: E402
from equations import open_branch_i  # noqa: E402
from equations import hvdc  # noqa: E402

# (module, golden file). Every family gets signature-lock + 1e-12 equivalence vs golden;
# the autodiff + fused audits run only for families with a fused CUDA target.
FAMILIES = [
    (closed_branch.MODULE, "closed_branch.golden.json"),
    (shunt.MODULE, "shunt.golden.json"),
    (open_branch.MODULE, "open_branch.golden.json"),
    (closed_branch_i.MODULE, "closed_branch_i.golden.json"),
    (open_branch_i.MODULE, "open_branch_i.golden.json"),
    (hvdc.MODULE, "hvdc.golden.json"),
]

EQUIV_TOL = 1e-12      # algebraic identity derived-vs-golden
FD_TOL = 1e-6          # finite-difference autodiff audit (central diff, h=1e-6)
SAMPLES = 200


def _ternary_to_py(s):
    """Translate a Java conditional `cond ? a : b` into Python `(a) if (cond) else (b)`,
    recursing into the (right-associated) branches. HVDC golden bodies are non-nested."""
    if " ? " not in s:
        return s
    cond, rest = s.split(" ? ", 1)
    then_, else_ = rest.split(" : ", 1)
    return f"(({_ternary_to_py(then_)}) if ({cond}) else ({_ternary_to_py(else_)}))"


def _hvdc_line_losses(p, v, r):           # powsybl HvdcUtils.getHvdcLineLosses closed form
    return r * p * p / (v * v)


def _make_funcs(methods):
    """Compile [{name, params, body}] into callables sharing one namespace, so bodies that call
    siblings (closed-branch -dp1dph1(...)), helpers (open-branch shunt(...)) or the magnitude
    component methods (current magnitude reI1/imI1/dreI1d<var>(...)) resolve. Java conditionals
    (HVDC ?:) are translated to Python and the Math. prefix stripped (current magnitude bodies
    are Math.hypot/sin/cos/pow); constants, math and the HVDC line-loss form are in the namespace."""
    ns = {"R2": 1.0, "A2": 0.0, "hypot": math.hypot, "sqrt": math.sqrt, "pow": math.pow,
          "sin": math.sin, "cos": math.cos, "atan2": math.atan2, "abs": abs,
          "getHvdcLineLosses": _hvdc_line_losses}
    for m in methods:
        lines = []
        for stmt in m.get("prelude", []):              # local vars (magnitude derivatives: double re = reI1(...);)
            py = stmt.strip().removeprefix("double ").rstrip(";").replace("Math.", "")
            lines.append("    " + py)
        lines.append("    return " + _ternary_to_py(m["body"]).replace("Math.", ""))
        src = f"def {m['name']}({', '.join(m['params'])}):\n" + "\n".join(lines) + "\n"
        exec(src, ns)  # noqa: S102 — trusted, self-authored arithmetic
    return ns


def check_signatures(derived, golden):
    gmap = {m["name"]: m["params"] for m in golden}
    dmap = {m["name"]: m["params"] for m in derived}
    if set(gmap) != set(dmap):
        raise SystemExit(f"FAIL signature-lock: method-name set differs\n"
                         f"  derived-only: {sorted(set(dmap) - set(gmap))}\n"
                         f"  golden-only:  {sorted(set(gmap) - set(dmap))}")
    bad = [n for n in gmap if dmap[n] != gmap[n]]
    if bad:
        lines = "\n".join(f"  {n}: derived {dmap[n]} != golden {gmap[n]}" for n in bad)
        raise SystemExit(f"FAIL signature-lock: {len(bad)} signature(s) differ\n{lines}")
    print(f"  A. signature-lock: {len(gmap)}/{len(gmap)} match the golden manifest")


_RANGES = {"v1": (0.9, 1.1), "v2": (0.9, 1.1), "r1": (0.9, 1.1),
           "ph1": (-0.5, 0.5), "ph2": (-0.5, 0.5), "a1": (-0.3, 0.3),
           "ksi": (1.2, 1.9), "y": (10.0, 50.0),
           "g1": (-0.1, 0.1), "g2": (-0.1, 0.1), "b1": (-0.1, 0.1), "b2": (-0.1, 0.1),
           # HVDC: spread rawP = p0 + k*(ph1-ph2) across both signs to hit both ternary branches.
           "p0": (-1.0, 1.0), "k": (1.0, 3.0), "r": (0.0, 0.05),
           "lossFactor1": (0.0, 0.1), "lossFactor2": (0.0, 0.1)}


def check_equivalence_raw(mod, golden_ns):
    """Math.* families (current magnitude): the derived Java body is Math.* (not Python), so
    compare the LAMBDIFIED raw SymPy expressions to the golden over physical operating points.
    The golden may take precomputed cosKsi/sinKsi (open-branch i) — supplied from the point's
    ksi. hypot vs sqrt(re^2+im^2) differ only by rounding (well within 1e-12)."""
    _syms, _thetas, out = generate.outputs(mod)
    consts = {sp.Symbol(c): float(v) for c, v in mod.get("constants", {}).items()}
    base = mod["base_symbols"]
    insyms = [sp.Symbol(n, real=True) for n in base]
    derived = {name: sp.lambdify(insyms, expr.subs(consts), "math") for name, _s, expr in out}
    gns = _make_funcs(golden_ns)
    gparams = {m["name"]: m["params"] for m in golden_ns}
    rng = random.Random(20260611)
    worst = 0.0
    for name, f in derived.items():
        for _ in range(SAMPLES):
            pt = {n: rng.uniform(*_RANGES.get(n, (0.5, 1.5))) for n in base}
            gpt = dict(pt)                             # golden may want precomputed trig of ksi
            if "ksi" in pt:
                gpt["sinKsi"], gpt["cosKsi"] = math.sin(pt["ksi"]), math.cos(pt["ksi"])
            a = f(*[pt[n] for n in base])
            b = gns[name](*[gpt[p] for p in gparams[name]])
            err = abs(a - b) / (1.0 + abs(b))
            worst = max(worst, err)
            if err > EQUIV_TOL:
                raise SystemExit(f"FAIL equivalence: {name} derived={a!r} golden={b!r} relerr={err:.3e}")
    print(f"  B. equivalence: {len(derived)} bodies == golden over {SAMPLES} samples "
          f"(worst relerr {worst:.2e} < {EQUIV_TOL:g})")


def check_equivalence(derived, golden_ns):
    dns = _make_funcs(derived)
    gns = _make_funcs(golden_ns)           # methods + helpers, so helper calls resolve
    rng = random.Random(20260609)
    worst = 0.0
    for dm in derived:
        params = dm["params"]
        df, gf = dns[dm["name"]], gns[dm["name"]]
        for _ in range(SAMPLES):
            args = [rng.uniform(-2.0, 2.0) for _ in params]
            a, b = df(*args), gf(*args)
            err = abs(a - b) / (1.0 + abs(b))
            worst = max(worst, err)
            if err > EQUIV_TOL:
                raise SystemExit(f"FAIL equivalence: {dm['name']}{tuple(params)} "
                                 f"derived={a!r} golden={b!r} relerr={err:.3e}")
    print(f"  B. equivalence: {len(derived)} bodies == golden over {SAMPLES} samples "
          f"(worst relerr {worst:.2e} < {EQUIV_TOL:g})")


def check_rendered(derived, golden_ns, mod):
    """Magnitude families render the structured chain-rule form (component helpers reI/imI +
    their derivatives, assembled by CALLING them). check_equivalence_raw proves the flat MATH
    equals the golden; this proves the RENDERED ASSEMBLY does too — compile the emitted methods
    (public + private helpers, so the helper calls resolve) and compare each PUBLIC method to
    the golden over physical operating points, catching any templating bug in the assembly."""
    dns = _make_funcs(derived)             # public + helpers
    gns = _make_funcs(golden_ns)
    gparams = {m["name"]: m["params"] for m in golden_ns}
    base = mod["base_symbols"]
    publics = [m for m in derived if m.get("public", True)]
    rng = random.Random(20260613)
    worst = 0.0
    for m in publics:
        for _ in range(SAMPLES):
            pt = {n: rng.uniform(*_RANGES.get(n, (0.5, 1.5))) for n in base}
            vals = dict(pt)
            if "ksi" in pt:                # families taking precomputed trig of ksi
                vals["sinKsi"], vals["cosKsi"] = math.sin(pt["ksi"]), math.cos(pt["ksi"])
            a = dns[m["name"]](*[vals[p] for p in m["params"]])
            b = gns[m["name"]](*[vals[p] for p in gparams[m["name"]]])
            err = abs(a - b) / (1.0 + abs(b))
            worst = max(worst, err)
            if err > EQUIV_TOL:
                raise SystemExit(f"FAIL rendered: {m['name']} derived={a!r} golden={b!r} relerr={err:.3e}")
    print(f"  B2. rendered assembly: {len(publics)} public methods == golden over {SAMPLES} samples "
          f"(worst relerr {worst:.2e} < {EQUIV_TOL:g})")


# --- autodiff audit: emitted Jacobian vs finite-difference of the residual -----

def _consts_num(mod):
    return {c: float(v) for c, v in mod.get("constants", {}).items()}


def _thetas_num(mod, p):
    """Numeric branch angles at point p, from the spec's theta strings (A2 etc. from
    the spec's constants)."""
    cn = _consts_num(mod)
    return {side: eval(expr, {"__builtins__": {}}, {**p, **cn})  # noqa: S307 — trusted spec
            for side, expr in mod.get("thetas", {}).items()}


def _rand_point(rng, names):
    return {n: rng.uniform(*_RANGES.get(n, (0.5, 1.5))) for n in names}


def _precomputed_trig(mod, p, side):
    """The precomputed-trig namespace the scalar methods take: sin/cos of ksi, and of
    this residual's branch angle when the family has one."""
    ns = {**p, **_consts_num(mod)}
    if "ksi" in p:
        ns["sinKsi"], ns["cosKsi"] = math.sin(p["ksi"]), math.cos(p["ksi"])
    th = _thetas_num(mod, p)
    if side is not None and side in th:
        ns["sinTheta"], ns["cosTheta"] = math.sin(th[side]), math.cos(th[side])
    return ns


def check_autodiff(mod, derived):
    """Family-agnostic: FD the spec's residual strings vs the emitted (precomputed-trig)
    scalar derivatives. Only runs for cuda_path families, whose scalar bodies are plain
    Python-evaluable (sstr) — a raw_trig family would need lambdify here instead."""
    resid = {n: (side, expr) for n, side, expr in mod["residuals"]}
    cn = _consts_num(mod)
    dns = _make_funcs(derived)

    def residual_value(name, p):
        side, expr = resid[name]
        ns = {**p, **cn, "sin": math.sin, "cos": math.cos, "sqrt": math.sqrt}
        th = _thetas_num(mod, p)
        if side is not None and side in th:
            ns["theta"] = th[side]
        return eval(expr, {"__builtins__": {}}, ns)  # noqa: S307 — trusted spec

    rng = random.Random(424242)
    h = 1e-6
    worst = 0.0
    n_entries = 0
    n_checked = 0
    for dm in derived:
        name = dm["name"]
        if "d" not in name[1:]:        # value method (p1/q1/p2/q2), nothing to FD
            continue
        res, var = name[1:].split("d", 1)
        n_entries += 1
        side = resid[res][0]
        for _ in range(SAMPLES // 4):
            p = _rand_point(rng, mod["base_symbols"])
            hp, hm = dict(p), dict(p)
            hp[var] = p[var] + h
            hm[var] = p[var] - h
            fd = (residual_value(res, hp) - residual_value(res, hm)) / (2 * h)
            ns = _precomputed_trig(mod, p, side)
            an = dns[name](*[ns[q] for q in dm["params"]])
            err = abs(fd - an) / (1.0 + abs(an))
            worst = max(worst, err)
            n_checked += 1
            if err > FD_TOL:
                raise SystemExit(f"FAIL autodiff audit: {name} fd={fd!r} "
                                 f"analytic={an!r} relerr={err:.3e}")
    print(f"  C. autodiff audit: {n_entries} Jacobian entries vs finite differences "
          f"({n_checked} points, worst relerr {worst:.2e} < {FD_TOL:g})")


def check_fused(mod, golden):
    """The FUSED CUDA target (raw-angle, inline trig) must also equal the golden —
    proves the second generated artifact, not just the scalar one. Evaluate the
    fused expressions over the routine's raw inputs (the spec's cuda_params) and
    compare to the golden methods fed the precomputed trig of the corresponding
    branch angle."""
    _syms, _thetas, out = generate.outputs(mod)
    consts = {sp.Symbol(c): v for c, v in mod["constants"].items()}
    inputs = [n for row in mod["cuda_params"] for n in row]
    insyms = [sp.Symbol(n, real=True) for n in inputs]
    fused = {name: sp.lambdify(insyms, expr.subs(consts), "math") for name, _s, expr in out}
    side_of = {name: side for name, side, _ in out}
    gns = _make_funcs(golden)
    gmap = {m["name"]: m["params"] for m in golden}
    rng = random.Random(770077)
    worst = 0.0
    for _ in range(SAMPLES):
        e = _rand_point(rng, inputs)
        args = [e[n] for n in inputs]
        for name in fused:
            pc = _precomputed_trig(mod, e, side_of[name])
            gval = gns[name](*[pc[q] for q in gmap[name]])
            fval = fused[name](*args)
            worst = max(worst, abs(fval - gval) / (1.0 + abs(gval)))
            if worst > EQUIV_TOL:
                raise SystemExit(f"FAIL fused: {name} fused={fval!r} golden={gval!r}")
    print(f"  D. fused target: {mod['cuda_routine']}'s {len(fused)} outputs == golden over "
          f"{SAMPLES} samples (worst relerr {worst:.2e} < {EQUIV_TOL:g})")


def _strip_wrap(s):
    """Drop redundant outermost parentheses (ccode wraps a Piecewise ternary as `(...)`, which
    the Java-format ternary translator does not expect)."""
    s = s.strip()
    while s.startswith("(") and s.endswith(")"):
        depth = 0
        wraps = True
        for i, c in enumerate(s):
            depth += 1 if c == "(" else (-1 if c == ")" else 0)
            if depth == 0 and i != len(s) - 1:
                wraps = False
                break
        if not wraps:
            break
        s = s[1:-1].strip()
    return s


def _eval_cuda(expr, ns):
    """Evaluate a rendered fused-CUDA expression (CSE temps already in ns) in Python. The
    .cuh arithmetic is Python-evaluable: sin/cos/sqrt/hypot/fabs are in ns, a Piecewise's
    C ternary (`(cond) ? (a) : (b)`) is unwrapped and translated, R2/A2 are the constants."""
    return eval(_ternary_to_py(_strip_wrap(expr)), {"__builtins__": {}}, ns)  # noqa: S307 — trusted arithmetic


def check_java_vs_cuda(mod):
    """Golden-INDEPENDENT proof that the two GENERATED artifacts agree: the rendered scalar
    Java and the rendered fused CUDA both come from the ONE residual source, so they must
    compute identical numbers. Edit an equation and BOTH regenerate from it; this guard
    confirms they still agree with EACH OTHER (the golden guards separately flag the intended
    math change). It evaluates the RENDERED .cuh (CSE temps + ccode) against the COMPILED
    scalar .java bodies — fed the precomputed trig of the same physical point — so a render
    bug in either backend is caught, not just the SymPy behind them."""
    _syms, _thetas, out = generate.outputs(mod)
    names = [n for n, _s, _e in out]
    side_of = {n: s for n, s, _e in out}
    repl, reduced = sp.cse([e for _n, _s, e in out], optimizations="basic")
    temps = [(str(s), generate._cuda_expr(sp.ccode(e))) for s, e in repl]
    cuda_expr = {nm: generate._cuda_expr(sp.ccode(e)) for nm, e in zip(names, reduced)}

    methods = generate.scalar_methods(mod)
    dns = _make_funcs(methods)
    sparams = {m["name"]: m["params"] for m in methods}
    inputs = [n for row in mod["cuda_params"] for n in row]
    rng = random.Random(20260616)
    worst = 0.0
    for _ in range(SAMPLES):
        pt = _rand_point(rng, inputs)
        cns = {"sin": math.sin, "cos": math.cos, "sqrt": math.sqrt, "hypot": math.hypot,
               "pow": math.pow, "fabs": abs, "R2": 1.0, "A2": 0.0, **pt}
        for sym, e in temps:                              # CSE temps, in dependency order
            cns[sym] = _eval_cuda(e, cns)
        for name in names:
            cuda_val = _eval_cuda(cuda_expr[name], cns)
            pc = _precomputed_trig(mod, pt, side_of[name])
            java_val = dns[name](*[pc[q] for q in sparams[name]])
            err = abs(cuda_val - java_val) / (1.0 + abs(java_val))
            worst = max(worst, err)
            if err > EQUIV_TOL:
                raise SystemExit(f"FAIL java-vs-cuda: {name} java={java_val!r} cuda={cuda_val!r} relerr={err:.3e}")
    print(f"  E. java == cuda: {len(names)} outputs — rendered .java and rendered .cuh agree "
          f"over {SAMPLES} samples (worst relerr {worst:.2e} < {EQUIV_TOL:g}), golden-independent")


def main():
    for mod, golden_file in FAMILIES:
        derived = generate.scalar_methods(mod)
        public = [m for m in derived if m.get("public", True)]   # private helpers (magnitude families) are not part of the locked API
        with open(os.path.join(HERE, "golden", golden_file)) as f:
            gold = json.load(f)
        golden = gold["methods"]
        golden_ns = golden + gold.get("helpers", [])  # helpers kept for the namespace only
        print(f"Proving {mod['java_class']} ({len(public)} public"
              + (f" + {len(derived) - len(public)} helper" if len(derived) != len(public) else "")
              + " methods) equivalent to golden:")
        check_signatures(public, golden)
        if any("Math." in m["body"] for m in derived):  # Math.* bodies: compare lambdified exprs
            check_equivalence_raw(mod, golden_ns)
            if "magnitudes" in mod:                   # also prove the rendered chain-rule assembly
                check_rendered(derived, golden_ns, mod)
        else:
            check_equivalence(derived, golden_ns)     # scalar Java target
        if "cuda_path" in mod:                        # autodiff + fused audits (CUDA families)
            if "residuals" in mod:
                check_autodiff(mod, derived)
            else:                                     # explicit family (HVDC): the Jacobian is an
                print("  C. autodiff audit: skipped — explicit family, the Jacobian is an "
                      "intentional approximation (FD of the value would rightly disagree)")
            check_fused(mod, golden_ns)               # helpers included (open-branch shunt)
            check_java_vs_cuda(mod)                    # the two artifacts agree with each other (golden-independent)
    print("PROVEN — all families signature-locked + equivalent to the original; "
          "fused/autodiff audited; scalar Java == fused CUDA where both are generated.")


if __name__ == "__main__":
    main()
