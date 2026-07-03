# Copyright (c) 2026, RTE (http://www.rte-france.com)
# SPDX-License-Identifier: MPL-2.0
#
# SINGLE SOURCE OF TRUTH for the open-branch AC flow formulas (active + reactive).
#
# A branch with ONE side open carries no through-flow; the connected side sees only
# the series + the open side's shunt admittance, so the flow is radial (no phase
# dependence — no thetas). OLF factors a denominator
#     shunt = (g + y*sinKsi)^2 + (-b + y*cosKsi)^2
# into a helper; here it is inlined into each residual (the generator differentiates the
# inlined expression). shunt does not depend on the voltage, so the Jacobian is just the
# v-derivative of the v^2 prefactor.
#
#   side 1 OPEN  -> flow at side 2 (variable v2), uses shunt(g1, b1), prefactor R2^2
#   side 2 OPEN  -> flow at side 1 (variable v1), uses shunt(g2, b2), prefactor r1^2
#
# Targets: the scalar CPU formulas AND the fused CUDA kernel + GPU scatter (each output
# depends on ONE voltage only, so the scatter has no skip rows and no cross derivatives).
# The current-magnitude open-branch terms (hypot) are a separate family, not ported here.

_SHUNT1 = "((g1 + y*sin(ksi))**2 + (-b1 + y*cos(ksi))**2)"
_SHUNT2 = "((g2 + y*sin(ksi))**2 + (-b2 + y*cos(ksi))**2)"

MODULE = {
    "java_class": "OpenBranchFormulas",
    "java_package": "com.powsybl.openloadflow.ac.equations",
    "java_imports": ["import static com.powsybl.openloadflow.network.PiModel.R2;"],
    "java_path": "src/main/java/com/powsybl/openloadflow/ac/equations/OpenBranchFormulas.java",
    "cuda_path": "native/cuda/open_branch.cuh",
    "scatter_path": "native/cuda/open_branch.scatter.json",
    "scatter_header_path": "native/cuda/open_branch_scatter.h",
    "cuda_namespace": "powsybl::openloadflow",
    "cuda_consts": ["constexpr double R2 = 1.0;"],
    "cuda_routine": "evalOpenBranch",
    "cuda_fn_macro": "OB_FN",
    "scatter_prefix": "OB",
    "cuda_params": [["y", "ksi", "g1", "b1", "g2", "b2"],
                    ["v1", "r1", "v2"]],
    "cuda_fused_doc": (
        "// FUSED per-element kernel body: one routine computes the four open-branch flows\n"
        "// (p2/q2 for a side-1-open branch, p1/q1 for a side-2-open one) + their four\n"
        "// voltage derivatives, writing into Struct-of-Arrays at index i. sin/cos of ksi\n"
        "// and the shunt denominators are computed once and shared via CSE. A real branch\n"
        "// has only ONE side open: the host stamps that side's pair and resolves the other\n"
        "// pair's scatter rows to -1 (absent), exactly like the closed-branch fill."),
    "doc": "Open-branch AC active/reactive flow residuals and Jacobian entries, "
           "delegated to by the open-branch equation terms.",

    "constants": {"R2": 1},      # PiModel.R2 == 1 (side-2 ratio); r1 is a passed-in parameter
    "thetas": {},                # radial flow: no branch angle

    # Only the connected side's voltage is a variable (r1 is fixed -> no dp1dr1).
    "variables": ["v1", "v2"],
    "base_symbols": ["v1", "v2", "r1", "ksi", "y", "g1", "g2", "b1", "b2"],

    # Reproduces the OLF signatures across both sides (verified against all 8 methods).
    "canonical_order": ["y", "cosKsi", "sinKsi", "g1", "b1", "g2", "b2", "v1", "r1", "v2"],

    # name, side (None: no branch angle), expression (shunt inlined).
    "residuals": [
        ("p2", None, f"R2*v2*R2*v2*(g2 + y*y*g1/{_SHUNT1} + (b1*b1 + g1*g1)*y*sin(ksi)/{_SHUNT1})"),
        ("q2", None, f"-R2*v2*R2*v2*(b2 + y*y*b1/{_SHUNT1} - (b1*b1 + g1*g1)*y*cos(ksi)/{_SHUNT1})"),
        ("p1", None, f"r1*v1*r1*v1*(g1 + y*y*g2/{_SHUNT2} + (b2*b2 + g2*g2)*y*sin(ksi)/{_SHUNT2})"),
        ("q1", None, f"-r1*v1*r1*v1*(b1 + y*y*b2/{_SHUNT2} - (b2*b2 + g2*g2)*y*cos(ksi)/{_SHUNT2})"),
    ],

    # --- Scatter stamp (where each output lands in OLF's equation system) -------
    #
    # The open-branch terms are SCALAR equation terms (no vector-array evaluator):
    # each term class declares its variable as
    # v<N>Var = variableSet.getVariable(bus<N>.getNum(), AcVariableType.BUS_V).
    # check_scatter.py parses every named declaration across these files (cross-file
    # conflicts fail) and checks the rows against the creator's addTerm(open<XX>).
    "stamp": {
        "sources": {
            # the term classes that declare v2Var (side-1-open) / v1Var (side-2-open)
            "columns": [
                "src/main/java/com/powsybl/openloadflow/ac/equations/"
                "OpenBranchSide1ActiveFlowEquationTerm.java",
                "src/main/java/com/powsybl/openloadflow/ac/equations/"
                "OpenBranchSide1ReactiveFlowEquationTerm.java",
                "src/main/java/com/powsybl/openloadflow/ac/equations/"
                "OpenBranchSide2ActiveFlowEquationTerm.java",
                "src/main/java/com/powsybl/openloadflow/ac/equations/"
                "OpenBranchSide2ReactiveFlowEquationTerm.java",
            ],
            "equations": "src/main/java/com/powsybl/openloadflow/ac/equations/"
                         "AcEquationSystemCreator.java",
        },
        "term_prefix": "open",                 # creator wires addTerm(openP1) etc.
        # residual -> (AcEquationType, endpoint) it contributes to (the F row)
        "residual_equation": {
            "p2": ("BUS_TARGET_P", "bus2"),
            "q2": ("BUS_TARGET_Q", "bus2"),
            "p1": ("BUS_TARGET_P", "bus1"),
            "q1": ("BUS_TARGET_Q", "bus1"),
        },
        # differentiation variable -> (AcVariableType, endpoint) it is (the J column)
        "variable_column": {
            "v1": ("BUS_V", "bus1"),
            "v2": ("BUS_V", "bus2"),
        },
    },
}
