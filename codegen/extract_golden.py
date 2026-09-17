#!/usr/bin/env python3
# Copyright (c) 2026, RTE (http://www.rte-france.com)
# SPDX-License-Identifier: MPL-2.0
#
# Build a family's golden reference from its ORIGINAL hand-written Java formula
# methods. The golden is the frozen baseline the equivalence proof checks the
# SymPy-generated formulas against (see codegen/prove_equivalence.py), so it must be
# extracted ONCE, from the original source, and then never regenerated.
#
#   public static double <name>(double a, double b, ...) { return <expr>; }
#       -->  {"name": "<name>", "params": ["a","b",...], "body": "<expr>"}
#
# ⚠️  Point this at the ORIGINAL hand-written methods, NOT a file the codegen now
#     produces — extracting from a generated file would make the proof circular.
#     - For a NOT-yet-ported family: its term classes on disk (the hand formulas).
#     - For closed-branch (already swapped): the pre-swap Java from git history, e.g.
#         git show 7e0c769a:src/main/java/.../ClosedBranchFormulas.java \
#           | python3 codegen/extract_golden.py --class ClosedBranchFormulas --out - -
#
# Usage:
#   python3 codegen/extract_golden.py --class <Name> --out <golden.json> <java...|->
#   (use '-' for an input or output to read stdin / write stdout)
import argparse
import json
import re
import sys

# Java math calls -> bare names so the golden body is valid Python in the proof
# (prove_equivalence provides hypot/sqrt/sin/cos/atan2 in its namespace; pow/abs are
# Python builtins). Extend if a new family uses another function.
MATH_CALL = re.compile(r"\b(?:FastMath|Math)\.(hypot|sqrt|sin|cos|atan2|pow|abs)\b")
# External powsybl-iidm helper kept as a bare call; prove_equivalence supplies its closed form
# getHvdcLineLosses(p, v, r) = r*p*p/(v*v) in its namespace.
EXT_CALL = re.compile(r"\bHvdcUtils\.(getHvdcLineLosses)\b")

# [MOD] static {double|boolean} NAME(PARAMS) { BODY }   (no nested braces in the body).
# boolean helpers (HVDC isController) are inlined into the conditions they guard.
METHOD = re.compile(
    r"(public|protected|private)\s+static\s+(?:double|boolean)\s+(\w+)\s*\(([^)]*)\)\s*\{(.*?)\}",
    re.DOTALL)
_DECL = re.compile(r"double\s+(\w+)\s*=\s*(.*?);", re.DOTALL)
_RETURN = re.compile(r"return\s+(.*?);", re.DOTALL)


def params_of(param_list):
    out = []
    for p in param_list.split(","):
        p = p.strip()
        if p:
            out.append(p.split()[-1])         # "final double sinKsi" -> "sinKsi"
    return out


def _inline(name, rhs, text):
    return re.sub(rf"\b{name}\b(?!\s*\()", f"({rhs})", text)   # local use, not a call


def _split_args(s):
    """Top-level comma split, respecting nested parentheses."""
    args, depth, cur = [], 0, ""
    for c in s:
        if c == "," and depth == 0:
            args.append(cur.strip())
            cur = ""
        else:
            depth += 1 if c == "(" else (-1 if c == ")" else 0)
            cur += c
    if cur.strip():
        args.append(cur.strip())
    return args


def _subst(body, params, args):
    """Simultaneously replace each helper parameter (whole word) by its (parenthesised)
    call argument, so g1->g2 / b1->b2 etc. can't collide."""
    mapping = {p: f"({a})" for p, a in zip(params, args)}
    return re.compile(r"\b(" + "|".join(re.escape(p) for p in params) + r")\b").sub(
        lambda m: mapping[m.group(1)], body)


def inline_helpers(body, helpers):
    """Recursively inline calls to helper methods (keyed by (name, arity)) — private/
    protected internals like shunt / reI1 / theta — until none remain. Calls to OUTPUT
    methods (e.g. closed-branch's sibling -dp1dph1(...)) are left for the proof namespace."""
    progress = True
    while progress:
        progress = False
        for (name, arity), (params, hbody) in helpers.items():
            for m in re.finditer(rf"\b{re.escape(name)}\s*\(", body):
                op = m.end() - 1                       # index of the '('
                depth, k = 0, op
                while k < len(body):
                    depth += 1 if body[k] == "(" else (-1 if body[k] == ")" else 0)
                    if depth == 0:
                        break
                    k += 1
                if len(_split_args(body[op + 1:k])) == arity:
                    args = _split_args(body[op + 1:k])
                    body = body[:m.start()] + "(" + _subst(hbody, params, args) + ")" + body[k + 1:]
                    progress = True
                    break                              # body changed: restart the scan
            if progress:
                break
    return body


def body_of(java_body):
    """Reduce a method body (local decls + a single return) to one expression. Local
    variables are inlined into the return; helper CALLS remain (inline_helpers resolves
    the private/protected ones afterwards)."""
    env = []                                  # (local, inlined rhs), in declaration order
    for local, rhs in _DECL.findall(java_body):
        for ln, lv in env:
            rhs = _inline(ln, lv, rhs)
        env.append((local, rhs.strip()))
    m = _RETURN.search(java_body)
    ret = m.group(1) if m else java_body
    for ln, lv in env:
        ret = _inline(ln, lv, ret)
    ret = EXT_CALL.sub(r"\1", MATH_CALL.sub(r"\1", ret))      # FastMath.hypot/HvdcUtils.x -> bare
    return " ".join(ret.split())                              # collapse newlines/indentation


def extract(text):
    methods = []
    for mod, name, plist, body in METHOD.findall(text):
        methods.append({"mod": mod, "name": name, "params": params_of(plist), "body": body_of(body)})
    return methods


def main():
    ap = argparse.ArgumentParser(description="Extract a golden reference from "
                                             "original hand-written Java formula methods.")
    ap.add_argument("--class", dest="java_class", required=True,
                    help="value of the golden's java_class field")
    ap.add_argument("--out", required=True, help="output golden .json ('-' = stdout)")
    ap.add_argument("--helper", default="", help="comma-separated method names that are "
                    "hand-written HELPERS (e.g. shunt) — kept for the proof's namespace but not "
                    "signature-locked, since the generator inlines them")
    ap.add_argument("--exclude", default="", help="comma-separated method names to drop entirely "
                    "(hand-written helpers the generator does not emit AND no golden body calls, "
                    "e.g. calculateSensi)")
    ap.add_argument("--output", default="", help="comma-separated PRIVATE method names to force as "
                    "outputs (e.g. open-branch i1/i2, which OLF keeps private but the generator "
                    "emits as the value method eval() delegates to)")
    ap.add_argument("inputs", nargs="+", help="Java source file(s) ('-' = stdin)")
    args = ap.parse_args()
    excluded = {n for n in args.exclude.split(",") if n}
    forced_helpers = {n for n in args.helper.split(",") if n}
    forced_outputs = {n for n in args.output.split(",") if n}

    # OUTPUTS = public methods (the API the term classes call) + any --output'd private ones;
    # HELPERS = the remaining private/protected internals (+ --helper'd), which get inlined.
    # Helpers resolve with a PER-FILE scope (a file's own helper wins) over a global pool — two
    # files can define a different `theta(...)` of the same arity (side 1 vs side 2), yet a
    # cross-file helper like the open-branch `shunt` (in an abstract base) still resolves.
    def is_output(m):
        return (m["mod"] == "public" or m["name"] in forced_outputs) and m["name"] not in forced_helpers

    def is_helper(m):
        return not is_output(m)

    per_file = []
    for path in args.inputs:
        text = sys.stdin.read() if path == "-" else open(path).read()
        per_file.append([m for m in extract(text) if m["name"] not in excluded])

    global_helpers = {}
    for fms in per_file:
        for m in fms:
            if is_helper(m):
                global_helpers.setdefault((m["name"], len(m["params"])), (m["params"], m["body"]))

    methods, seen = [], set()
    for fms in per_file:
        local = {(m["name"], len(m["params"])): (m["params"], m["body"]) for m in fms if is_helper(m)}
        hmap = {**global_helpers, **local}
        for m in fms:
            if is_helper(m) or m["name"] in seen:
                continue
            seen.add(m["name"])
            methods.append({"name": m["name"], "params": m["params"],
                            "body": inline_helpers(m["body"], hmap)})
    helpers = global_helpers

    if not methods:
        sys.exit("error: no 'public static double NAME(...) { ... }' methods found")
    # every remaining `ident(` must be a math function or a sibling OUTPUT (kept on purpose);
    # anything else is a helper the inliner failed to resolve.
    known = {"hypot", "sqrt", "sin", "cos", "atan2", "pow", "abs", "getHvdcLineLosses"} \
        | {m["name"] for m in methods}
    for m in methods:
        bad = {c for c in re.findall(r"\b([A-Za-z_]\w*)\s*\(", m["body"]) if c not in known}
        if bad:
            print(f"  warning: unresolved call(s) {sorted(bad)} in {m['name']}", file=sys.stderr)

    payload = json.dumps({"java_class": args.java_class, "methods": methods}, indent=2) + "\n"
    if args.out == "-":
        sys.stdout.write(payload)
    else:
        with open(args.out, "w") as f:
            f.write(payload)
        print(f"  wrote {args.out}  ({len(methods)} methods, {len(helpers)} helpers inlined)",
              file=sys.stderr)


if __name__ == "__main__":
    main()
