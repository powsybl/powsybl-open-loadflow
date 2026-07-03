#!/usr/bin/env python3
# Copyright (c) 2026, RTE (http://www.rte-france.com)
# SPDX-License-Identifier: MPL-2.0
#
# Validate a family's scatter stamp (codegen/equations/<family>.py "stamp") against
# OLF's REAL equation-system wiring, so the generated scatter descriptor
# (native/cuda/<family>.scatter.json) cannot silently drift from the Java the GPU
# kernel must reproduce. Two authoritative, parseable sources per family:
#
#   columns   — array families (closed-branch): the vector evaluator's
#               getDerivatives(): each Jacobian column is
#               variableSet.getVariable(<endpoint>, AcVariableType.<TYPE>), <col>
#               => variable -> (AcVariableType, endpoint)
#               scalar families (open-branch, shunt, HVDC): the term classes declare
#               their variables as <var>Var = variableSet.getVariable(<owner>
#               .getNum(), AcVariableType.<TYPE>) (sources.columns is then a LIST of
#               term files; every named declaration is parsed, cross-file conflicts
#               fail, and each stamp variable must be declared as stamped)
#   equations — the creator: getEquation(<bus>.getNum(), AcEquationType.<TYPE>)
#               ...addTerm(<prefix><XX>) or createEquation(<bus>, AcEquationType
#               .<TYPE>).addTerm(<prefix><XX>) => residual -> (AcEquationType,
#               endpoint); prefix = stamp's term_prefix ("" = bare residual names,
#               then scoped to stamp's equations_method to avoid same-name terms
#               in unrelated creator methods)
#
# Run:  python3 codegen/check_scatter.py        (stdlib only; exit 0 = stamp matches)
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
sys.path.insert(0, HERE)

from equations import closed_branch  # noqa: E402
from equations import open_branch  # noqa: E402
from equations import shunt  # noqa: E402
from equations import hvdc  # noqa: E402

MODULES = [closed_branch.MODULE, open_branch.MODULE, shunt.MODULE, hvdc.MODULE]

# variableSet.getVariable(bus1Num, AcVariableType.BUS_V), 0)
COL = re.compile(r"getVariable\(\s*(\w+)\s*,\s*AcVariableType\.(\w+)\s*\)\s*,\s*(\d+)\s*\)")
# scalar term class: ph2Var = variableSet.getVariable(bus2.getNum(), AcVariableType.BUS_PHI)
NAMED_VAR = re.compile(r"(\w+)Var\s*=\s*variableSet\.getVariable\(\s*([\w.()]+?)\.getNum\(\)"
                       r"\s*,\s*AcVariableType\.(\w+)\s*\)")


# getEquation(bus1.getNum(), AcEquationType.BUS_TARGET_P).orElseThrow().addTerm(closedP1)
# or createEquation(bus, AcEquationType.BUS_TARGET_Q).addTerm(q); the endpoint may be
# dotted (hvdc.getBus1())
def _eq_patterns(prefix):
    return [re.compile(r"getEquation\(\s*([\w.()]+?)\.getNum\(\)\s*,\s*AcEquationType\.(\w+)\s*\)"
                       r"\s*\.orElseThrow\(\)\s*\.addTerm\(\s*(" + prefix + r"\w+)\s*\)"),
            re.compile(r"createEquation\(\s*(\w+)\s*,\s*AcEquationType\.(\w+)\s*\)"
                       r"\s*\.addTerm\(\s*(" + prefix + r"\w*)\s*\)")]


ENDPOINT = {"bus1Num": "bus1", "bus2Num": "bus2", "branchNum": "branch",
            "bus1": "bus1", "bus2": "bus2",
            "bus": "bus", "shunt": "shunt",
            "hvdc.getBus1()": "bus1", "hvdc.getBus2()": "bus2"}


def _method_body(text, method):
    """The brace-matched body of `... <method>(...) {`, so bare-named terms (shunt's
    addTerm(p)/addTerm(q)) are matched only inside their own creator method."""
    m = re.search(rf"\b{method}\s*\([^)]*\)\s*\{{", text, re.DOTALL)
    if not m:
        raise SystemExit(f"FAIL: method {method} not found in the equations source")
    depth, k = 0, m.end() - 1
    while k < len(text):
        depth += 1 if text[k] == "{" else (-1 if text[k] == "}" else 0)
        if depth == 0:
            break
        k += 1
    return text[m.start():k + 1]


def _read(path):
    with open(os.path.join(ROOT, path)) as f:
        return f.read()


def check_columns(mod):
    """getDerivatives() column k must be the spec's variables[k] -> (type, endpoint)."""
    text = _read(mod["stamp"]["sources"]["columns"])
    parsed = {}  # col -> (type, endpoint)
    for endpoint_var, vtype, col in COL.findall(text):
        if endpoint_var not in ENDPOINT:
            continue
        parsed[int(col)] = (vtype, ENDPOINT[endpoint_var])
    variables = mod["variables"]
    vcol = mod["stamp"]["variable_column"]
    bad = []
    for k, var in enumerate(variables):
        want = tuple(vcol[var])
        got = parsed.get(k)
        if got != want:
            bad.append(f"col {k} ({var}): stamp {want} != OLF getDerivatives {got}")
    if bad:
        raise SystemExit("FAIL scatter columns vs OLF getDerivatives():\n  " + "\n  ".join(bad))
    print(f"  columns:   {len(variables)}/{len(variables)} variables match the vector "
          f"evaluator's getDerivatives()")


def check_columns_scalar(mod):
    """Scalar family: parse every named declaration `<var>Var = variableSet.getVariable(
    <owner>.getNum(), AcVariableType.<TYPE>)` across the stamp's term files; cross-file
    conflicts fail, and each stamp variable must be declared as variable_column says."""
    stamp = mod["stamp"]
    vcol = stamp["variable_column"]
    declared = {}                                  # var -> ((type, endpoint), file)
    bad = []
    for path in stamp["sources"]["columns"]:
        for var, owner, vtype in NAMED_VAR.findall(_read(path)):
            got = (vtype, ENDPOINT.get(owner, owner))
            prev = declared.get(var)
            if prev and prev[0] != got:
                bad.append(f"{var}: {os.path.basename(path)} declares {got} but "
                           f"{prev[1]} declared {prev[0]}")
            declared[var] = (got, os.path.basename(path))
    for var, want in vcol.items():
        got = declared.get(var, (None, "?"))[0]
        if got != tuple(want):
            bad.append(f"{var}: stamp {tuple(want)} != term classes {got}")
    if bad:
        raise SystemExit("FAIL scatter columns vs the term classes' getVariable():\n  "
                         + "\n  ".join(bad))
    print(f"  columns:   {len(vcol)}/{len(vcol)} variables match their declaring term "
          f"class(es) ({len(stamp['sources']['columns'])} files)")


def check_equations(mod):
    """Each BUS_TARGET_P/Q term of this family in the creator must match the spec's
    residual_equation (e.g. closedP1->p1 / openP2->p2 per the stamp's term_prefix)."""
    text = _read(mod["stamp"]["sources"]["equations"])
    if "equations_method" in mod["stamp"]:          # bare term names: scope to one method
        text = _method_body(text, mod["stamp"]["equations_method"])
    req = mod["stamp"]["residual_equation"]
    prefix = mod["stamp"].get("term_prefix", "closed")
    found = {}
    for pat in _eq_patterns(prefix):
        for bus, eqtype, term in pat.findall(text):
            resid = term[len(prefix):].lower()      # closedP1 -> p1 / openP2 -> p2 / q -> q
            if resid not in req:
                continue
            found[resid] = (eqtype, ENDPOINT.get(bus, bus))
    bad = []
    for resid, want in req.items():
        got = found.get(resid)
        if got != tuple(want):
            bad.append(f"{resid}: stamp {tuple(want)} != OLF creator {got}")
    if bad:
        raise SystemExit("FAIL scatter equations vs OLF AcEquationSystemCreator:\n  "
                         + "\n  ".join(bad))
    print(f"  equations: {len(req)}/{len(req)} residuals match the creator's "
          f"addTerm(...) wiring")


def main():
    for mod in MODULES:
        if "stamp" not in mod:
            continue
        print(f"Validating {mod['java_class']} scatter stamp vs OLF wiring:")
        if isinstance(mod["stamp"]["sources"]["columns"], list):
            check_columns_scalar(mod)
        else:
            check_columns(mod)
        check_equations(mod)
    print("SCATTER STAMP MATCHES OLF — the generated scatter descriptor reflects the "
          "real equation-system assembly.")


if __name__ == "__main__":
    main()
