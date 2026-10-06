# Copyright (c) 2026, RTE (http://www.rte-france.com)
# SPDX-License-Identifier: MPL-2.0
#
# SINGLE SOURCE OF TRUTH for the closed-branch CURRENT-MAGNITUDE formulas.
#
# i = |I| = hypot(reI, imI), the magnitude of the complex current at one end. Unlike the
# P/Q families this is a RAW-TRIG family: OLF's methods take the raw angles and call
# Math.cos/sin/hypot internally (no precomputed sinKsi), so the generator keeps the
# transcendentals and renders them as Math.* (raw_trig=True). The residual is written with
# hypot expanded to sqrt(re^2 + im^2) so SymPy can differentiate it; its autodiff is exactly
# OLF's chain rule  di/dx = (reI*dreI/dx + imI*dimI/dx) / i.
#
#   side 1 current: theta1 = ksi - a1 + A2 + ph2,  reI1/imI1 use g1,b1,ph1, prefactor r1
#   side 2 current: theta2 = ksi + a1 - A2 + ph1,  reI2/imI2 use g2,b2,ph2, prefactor R2
#
# r1 is NOT a differentiation variable (OLF's der(r1) throws "not implemented").
# Scope: scalar CPU formulas only (no fused CUDA / scatter).

A2, R2 = "A2", "R2"        # kept symbolic, rendered via static import (PiModel.A2/R2)


def _hypot(re, im):
    return f"sqrt(({re})**2 + ({im})**2)"


# --- side 1 ---
_TH1 = f"(ksi - a1 + {A2} + ph2)"
_RE1 = f"(g1*cos(ph1) - b1*sin(ph1) + y*sin(ph1 + ksi))"
_IM1 = f"(g1*sin(ph1) + b1*cos(ph1) - y*cos(ph1 + ksi))"
_reI1 = f"r1*(r1*v1*{_RE1} - y*{R2}*v2*sin{_TH1})"
_imI1 = f"r1*(r1*v1*{_IM1} + y*{R2}*v2*cos{_TH1})"

# --- side 2 ---
_TH2 = f"(ksi + a1 - {A2} + ph1)"
_RE2 = f"(g2*cos(ph2) - b2*sin(ph2) + y*sin(ph2 + ksi))"
_IM2 = f"(g2*sin(ph2) + b2*cos(ph2) - y*cos(ph2 + ksi))"
_reI2 = f"{R2}*({R2}*v2*{_RE2} - y*r1*v1*sin{_TH2})"
_imI2 = f"{R2}*({R2}*v2*{_IM2} + y*r1*v1*cos{_TH2})"

MODULE = {
    "java_class": "ClosedBranchCurrentMagnitudeFormulas",
    "java_package": "com.powsybl.openloadflow.ac.equations",
    "java_imports": ["import static com.powsybl.openloadflow.network.PiModel.A2;",
                     "import static com.powsybl.openloadflow.network.PiModel.R2;"],
    "java_path": "src/main/java/com/powsybl/openloadflow/ac/equations/ClosedBranchCurrentMagnitudeFormulas.java",
    "doc": "Closed-branch current-magnitude residuals and Jacobian entries, delegated to "
           "by the closed-branch current-magnitude equation terms.",

    "raw_trig": True,                       # keep sin/cos/sqrt -> Math.* (no precomputed trig)
    "constants": {"R2": 1, "A2": 0},        # PiModel.R2 == 1, PiModel.A2 == 0 (kept symbolic)
    "thetas": {},                           # angles inlined directly in the residuals

    # r1 is a parameter, not a state variable (no di/dr1).
    "variables": ["v1", "v2", "ph1", "ph2", "a1"],
    "base_symbols": ["y", "ksi", "g1", "b1", "g2", "b2", "v1", "ph1", "r1", "a1", "v2", "ph2"],

    # Reproduces i1(y,ksi,g1,b1,v1,ph1,r1,a1,v2,ph2) and i2(y,ksi,g2,b2,...).
    "canonical_order": ["y", "ksi", "g1", "b1", "g2", "b2", "v1", "ph1", "r1", "a1", "v2", "ph2"],

    # Structured emission: i = hypot(reI, imI) with named component helpers + their
    # per-variable derivatives, assembled by the generator into the chain-rule form OLF
    # originally hand-wrote (reI1/imI1/i1/dreI1d<var>/dimI1d<var>), instead of one inlined
    # closed form per output. The flat `residuals` below remain the math source of truth
    # (sp.diff + the equivalence proof); `magnitudes` drives only the scalar-Java rendering.
    # a1 enters only through the branch angle, exactly like ph2 (side 1) / ph1 (side 2) but with
    # the opposite / same sign, so di/da1 = -di/dph2 (side 1) and di/da1 = +di/dph1 (side 2). Declare
    # the antisymmetry so the generator emits di<i>da1 as a delegation (matching OLF's original),
    # dropping the redundant da1 component helpers (sign-flips of the dph ones).
    "magnitudes": [
        {"name": "i1", "re_name": "reI1", "im_name": "imI1", "re": _reI1, "im": _imI1,
         "alias": {"a1": "-ph2"}},
        {"name": "i2", "re_name": "reI2", "im_name": "imI2", "re": _reI2, "im": _imI2,
         "alias": {"a1": "ph1"}},
    ],

    "residuals": [
        ("i1", None, _hypot(_reI1, _imI1)),
        ("i2", None, _hypot(_reI2, _imI2)),
    ],
}
