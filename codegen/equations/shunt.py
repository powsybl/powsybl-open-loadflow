# Copyright (c) 2026, RTE (http://www.rte-france.com)
# SPDX-License-Identifier: MPL-2.0
#
# SINGLE SOURCE OF TRUTH for the shunt-compensator AC flow formulas.
#
# RESIDUALS-ONLY spec (see codegen/README.md). The shunt injects, at its bus,
#   p = g * v^2      (active, g constant)
#   q = -b * v^2     (reactive, b a state variable when the shunt is controllable)
# Derivatives come from sp.diff; a zero derivative (e.g. dp/db) is dropped, matching
# OLF's term, which declares der() only for the variables its formula depends on.
# No branch angle, so no thetas / trig — the simplest worked example of a non-branch
# family ported to the same generator as closed_branch.py.
#
# Targets: the scalar CPU formulas AND the fused CUDA kernel + GPU scatter. The shunt
# is a ONE-bus element: its endpoints are "bus" (code 0, like a branch's bus1) and
# "shunt" (code 2, the element itself — only the endpoint of the SHUNT_B skip column,
# since b is not a state variable in plain AC PF, exactly like BRANCH_ALPHA1/RHO1).

MODULE = {
    "java_class": "ShuntCompensatorFormulas",
    "java_package": "com.powsybl.openloadflow.ac.equations",
    "java_imports": [],
    "java_path": "src/main/java/com/powsybl/openloadflow/ac/equations/ShuntCompensatorFormulas.java",
    "cuda_path": "native/cuda/shunt.cuh",
    "scatter_path": "native/cuda/shunt.scatter.json",
    "scatter_header_path": "native/cuda/shunt_scatter.h",
    "cuda_namespace": "powsybl::openloadflow",
    "cuda_consts": [],                     # no model constants (R2/A2 are branch-side)
    "cuda_routine": "evalShunt",
    "cuda_fn_macro": "SH_FN",
    "scatter_prefix": "SH",
    "cuda_params": [["v", "g", "b"]],
    "cuda_fused_doc": (
        "// FUSED per-element kernel body: one routine computes the shunt injections p/q\n"
        "// + their three non-zero derivatives for one shunt, writing into Struct-of-Arrays\n"
        "// at index i. No trig at all — the cheapest fill of the families; dqdb only\n"
        "// matters when the shunt is controllable (SHUNT_B is a skip column in plain PF)."),
    "doc": "Shunt-compensator AC flow residuals and Jacobian entries, delegated to by "
           "the shunt equation terms.",

    # No constants, no branch angle.
    "constants": {},
    "thetas": {},

    # Differentiation variables: v always, b when the susceptance is controllable.
    "variables": ["v", "b"],

    # All non-constant symbols in the residuals (g is a passed-in parameter, not a var).
    "base_symbols": ["v", "g", "b"],

    # Canonical parameter order -> reproduces OLF's signatures p(v, g) / q(v, b) / dqdb(v).
    "canonical_order": ["v", "g", "b"],

    # name, side (None: no branch angle), expression.
    "residuals": [
        ("p", None, "g*v*v"),
        ("q", None, "-b*v*v"),
    ],

    # --- Scatter stamp (where each output lands in OLF's equation system) -------
    #
    # Scalar terms (no vector-array evaluator): vVar is declared once in the shared
    # abstract base, bVar in the reactive term (when the shunt is controllable). The
    # creator wires bare-named terms addTerm(p)/addTerm(q) inside createShuntEquation
    # — term_prefix "" + equations_method scope the check to that method, since other
    # creator methods also use bare p/q names for unrelated terms.
    "stamp": {
        "sources": {
            # the term classes whose named <var>Var declarations cover v and b
            "columns": [
                "src/main/java/com/powsybl/openloadflow/ac/equations/"
                "AbstractShuntCompensatorEquationTerm.java",
                "src/main/java/com/powsybl/openloadflow/ac/equations/"
                "ShuntCompensatorReactiveFlowEquationTerm.java",
            ],
            "equations": "src/main/java/com/powsybl/openloadflow/ac/equations/"
                         "AcEquationSystemCreator.java",
        },
        "term_prefix": "",
        "equations_method": "createShuntEquation",
        "endpoints": ["bus", "shunt"],
        # residual -> (AcEquationType, endpoint) it contributes to (the F row)
        "residual_equation": {
            "p": ("BUS_TARGET_P", "bus"),
            "q": ("BUS_TARGET_Q", "bus"),
        },
        # differentiation variable -> (AcVariableType, endpoint) it is (the J column)
        "variable_column": {
            "v": ("BUS_V", "bus"),
            "b": ("SHUNT_B", "shunt"),
        },
    },
}
