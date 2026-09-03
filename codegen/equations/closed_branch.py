# Copyright (c) 2026, RTE (http://www.rte-france.com)
# SPDX-License-Identifier: MPL-2.0
#
# SINGLE SOURCE OF TRUTH for the closed-branch AC flow formulas.
#
# RESIDUALS-ONLY spec. This file declares only the four power-flow residuals
# (p1, q1, p2, q2), the two branch angles theta1/theta2, the model constants and
# the canonical parameter order. Everything else — the 24 Jacobian entries, every
# method name and signature, the Java class and the CUDA header — is *derived*:
#
#     closed_branch.py  --(derive.py, SymPy autodiff)-->  generated/closed_branch.methods.json
#                       --(generate.py, stdlib render)-->  ClosedBranchFormulas.java
#                                                          closed_branch.cuh
#
# Derivatives are obtained by symbolic differentiation (sp.diff), so there is no
# hand-derived Jacobian to get wrong. The trig is precomputed and passed in
# (sinKsi, cosKsi, sinTheta, cosTheta — matching how AcNetworkVector factors it
# out of the loop): after differentiating, sin/cos of ksi and of the branch angle
# are substituted back to those symbols, so the emitted bodies contain no
# transcendental call and are identical in Java and CUDA.
#
# Edit ONLY the residuals below (and rerun codegen/regenerate.sh); never edit the
# generated .methods.json / .java / .cuh by hand. The derived method names and
# arities are locked to the OLF call sites by codegen/golden/closed_branch.golden.json
# (see prove_equivalence.py).

MODULE = {
    # --- Java / CUDA emission targets (metadata; consumed by generate.py) -------
    "java_class": "ClosedBranchFormulas",
    "java_package": "com.powsybl.openloadflow.ac.equations",
    "java_imports": ["import static com.powsybl.openloadflow.network.PiModel.R2;"],
    "java_path": "src/main/java/com/powsybl/openloadflow/ac/equations/ClosedBranchFormulas.java",
    "cuda_path": "native/cuda/closed_branch.cuh",
    "scatter_path": "native/cuda/closed_branch.scatter.json",
    "scatter_header_path": "native/cuda/closed_branch_scatter.h",
    "cuda_namespace": "powsybl::openloadflow",
    # Fused-routine identity: name, the per-family __host__ __device__ macro, the raw
    # input signature (rows = source lines), the scatter struct/macro prefix (CB ->
    # CbScatter / CB_NOUT / CB_SCATTER) and the kernel-style doc block.
    "cuda_routine": "evalClosedBranch",
    "cuda_fn_macro": "CB_FN",
    "scatter_prefix": "CB",
    "cuda_params": [["y", "g1", "g2", "b1", "b2"],
                    ["v1", "r1", "v2", "ksi", "a1", "ph1", "ph2"]],
    "cuda_fused_doc": (
        "// FUSED per-element kernel body: one routine computes p1/q1/p2/q2 + all 24 Jacobian\n"
        "// entries for one branch, writing into Struct-of-Arrays at index i. sin/cos of ksi,\n"
        "// theta1, theta2 are computed once and shared via common-subexpression elimination —\n"
        "// the per-element device routine the GPU assembly kernel calls (one thread = one\n"
        "// branch, inline trig, SoA output — a fused per-branch kernel)."),
    # Model constants kept symbolic in the formulas; the fused kernel computes the
    # branch angle inline (theta = ksi - a1 + A2 - ...), so both are emitted here as
    # constexpr (A2 = 0 / R2 = 1 fold away at compile time, no runtime cost).
    "cuda_consts": ["constexpr double R2 = 1.0;", "constexpr double A2 = 0.0;"],
    "doc": "Closed-branch AC flow residuals and Jacobian entries, shared by the "
           "scalar equation-term path and the vectorized AcNetworkVector loop.",

    # --- Residual authoring (the ONLY physics in this file) --------------------
    #
    # Constants kept SYMBOLIC in the emitted bodies (R2) or only used inside the
    # branch angle (A2). R2 is PiModel.R2 == 1 (static import in Java, constexpr in
    # CUDA); A2 == 0. They are NOT method parameters.
    "constants": {"R2": 1, "A2": 0},

    # Differentiation variables, in the order their Jacobian columns are taken.
    # Each becomes a derivative-method name suffix: f -> d<f>d<var>.
    "variables": ["v1", "v2", "ph1", "ph2", "a1", "r1"],

    # All non-constant symbols that appear in the residuals or branch angles.
    "base_symbols": ["v1", "v2", "ph1", "ph2", "a1", "r1", "ksi", "y",
                     "g1", "g2", "b1", "b2"],

    # Branch angles (verbatim from AbstractClosedBranchAcFlowEquationTerm). sp.diff
    # chain-rules through these automatically; sin/cos of the angle are then
    # substituted to sinTheta/cosTheta per the residual's side.
    "thetas": {
        1: "ksi - a1 + A2 - ph1 + ph2",
        2: "ksi + a1 - A2 + ph1 - ph2",
    },

    # Canonical parameter order. Every derived signature is the subset of this list
    # that actually appears in the (post-substitution) body, taken in this order —
    # which reproduces the exact OLF granular signatures (locked by the golden
    # manifest). R2/A2 are constants and never appear here.
    "canonical_order": [
        "y", "sinKsi", "cosKsi", "g1", "g2", "b1", "b2",
        "v1", "r1", "v2", "sinTheta", "cosTheta",
    ],

    # The four residuals: (name, side, expression). `side` selects which theta the
    # body's sin/cos belong to (theta1 for side 1, theta2 for side 2). Use sin()/
    # cos() of `ksi` and of `theta` (the side's angle); base symbols are the pi-model
    # quantities y, g1/g2, b1/b2, the voltages v1/v2, the angles ph1/ph2, the phase
    # shift a1, the ratio r1, plus the constant R2.
    "residuals": [
        ("p1", 1, "r1*v1*(g1*r1*v1 + y*r1*v1*sin(ksi) - y*R2*v2*sin(theta))"),
        ("q1", 1, "r1*v1*(-b1*r1*v1 + y*r1*v1*cos(ksi) - y*R2*v2*cos(theta))"),
        ("p2", 2, "R2*v2*(g2*R2*v2 - y*r1*v1*sin(theta) + y*R2*v2*sin(ksi))"),
        ("q2", 2, "R2*v2*(-b2*R2*v2 - y*r1*v1*cos(theta) + y*R2*v2*cos(ksi))"),
    ],

    # --- Scatter stamp (where each output lands in OLF's equation system) -------
    #
    # The data to assemble the fixed-pattern Jacobian once (the fixed-pattern-J
    # structural data) and ride the batched-cuDSS / low-rank-N-1
    # machinery — see the GPU-fit note on the claude/codegen-experimental branch.
    #
    # Each residual feeds one bus power-balance equation; each derivative d<res>d<var>
    # scatters into J[row = res's equation, col = var's variable]. Endpoints (bus1 /
    # bus2 / branch) are symbolic — the host resolves them per branch to flat
    # row/col indices (exactly as AcNetworkVector does). Names are the real OLF
    # AcEquationType / AcVariableType enums; codegen/check_scatter.py asserts this
    # stamp against the actual term-class wiring (the vector evaluator's
    # getDerivatives() and AcEquationSystemCreator), so it cannot silently drift.
    "stamp": {
        # Authoritative OLF sources check_scatter.py parses to validate this stamp:
        #  - columns: the vector evaluator's getDerivatives() — (variable, endpoint, col)
        #  - equations: the creator's addTerm(...) into getEquation(bus, AcEquationType)
        "sources": {
            "columns": "src/main/java/com/powsybl/openloadflow/ac/equations/vector/"
                       "AbstractClosedBranchEquationTermArrayEvaluator.java",
            "equations": "src/main/java/com/powsybl/openloadflow/ac/equations/"
                         "AcEquationSystemCreator.java",
        },
        # residual -> (AcEquationType, endpoint) it contributes to (the F row)
        "residual_equation": {
            "p1": ("BUS_TARGET_P", "bus1"),
            "q1": ("BUS_TARGET_Q", "bus1"),
            "p2": ("BUS_TARGET_P", "bus2"),
            "q2": ("BUS_TARGET_Q", "bus2"),
        },
        # differentiation variable -> (AcVariableType, endpoint) it is (the J column)
        "variable_column": {
            "v1": ("BUS_V", "bus1"),
            "v2": ("BUS_V", "bus2"),
            "ph1": ("BUS_PHI", "bus1"),
            "ph2": ("BUS_PHI", "bus2"),
            "a1": ("BRANCH_ALPHA1", "branch"),
            "r1": ("BRANCH_RHO1", "branch"),
        },
    },
}
