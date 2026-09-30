# Copyright (c) 2026, RTE (http://www.rte-france.com)
# SPDX-License-Identifier: MPL-2.0
#
# SINGLE SOURCE OF TRUTH for the open-branch CURRENT-MAGNITUDE formulas.
#
# Magnitude of the current at the connected end of a branch with one side open. Like the
# closed-branch i it is a hypot (rendered Math.* via the generator's transcendental path),
# but MIXED-trig: OLF's API takes the PRECOMPUTED cosKsi/sinKsi yet the RAW end angle ph
# (cos(ph)/sin(ph) computed inside). So raw_trig stays False — sin/cos of ksi are substituted
# to sinKsi/cosKsi, while sin/cos of ph and the sqrt remain and print as Math.*.
#
# The magnitude is rotation-invariant in ph (|gres*cos-bres*sin, gres*sin+bres*cos| = hypot
# (gres,bres)), so OLF declares no di/dph (der(ph) throws) — only di/dv. We still author the
# residual with the cos/sin(ph) so the signature keeps ph, matching OLF (SymPy does not apply
# cos^2+sin^2=1 unless asked).
#
#   side 1 OPEN -> i2 at side 2 (variable v2), shunt(g1,b1), gres/bres in g2,b2, prefactor R2
#   side 2 OPEN -> i1 at side 1 (variable v1), shunt(g2,b2), gres/bres in g1,b1, prefactor r1
#
# Scope: scalar CPU formulas only.

R2 = "R2"


def _shunt(g, b):
    return f"(({g} + y*sin(ksi))**2 + (-{b} + y*cos(ksi))**2)"


def _hypot(re, im):
    return f"sqrt(({re})**2 + ({im})**2)"


# side 1 open -> current at side 2 (g2/b2 the connected side, shunt from g1/b1)
_S1 = _shunt("g1", "b1")
_GRES2 = f"(g2 + (y*y*g1 + (b1*b1 + g1*g1)*y*sin(ksi))/{_S1})"
_BRES2 = f"(b2 + (y*y*b1 - (b1*b1 + g1*g1)*y*cos(ksi))/{_S1})"
_reI2 = f"{R2}*{R2}*v2*({_GRES2}*cos(ph2) - {_BRES2}*sin(ph2))"
_imI2 = f"{R2}*{R2}*v2*({_GRES2}*sin(ph2) + {_BRES2}*cos(ph2))"

# side 2 open -> current at side 1 (g1/b1 the connected side, shunt from g2/b2)
_S2 = _shunt("g2", "b2")
_GRES1 = f"(g1 + (y*y*g2 + (b2*b2 + g2*g2)*y*sin(ksi))/{_S2})"
_BRES1 = f"(b1 + (y*y*b2 - (b2*b2 + g2*g2)*y*cos(ksi))/{_S2})"
_reI1 = f"r1*r1*v1*({_GRES1}*cos(ph1) - {_BRES1}*sin(ph1))"
_imI1 = f"r1*r1*v1*({_GRES1}*sin(ph1) + {_BRES1}*cos(ph1))"

MODULE = {
    "java_class": "OpenBranchCurrentMagnitudeFormulas",
    "java_package": "com.powsybl.openloadflow.ac.equations",
    "java_imports": ["import static com.powsybl.openloadflow.network.PiModel.R2;"],
    "java_path": "src/main/java/com/powsybl/openloadflow/ac/equations/OpenBranchCurrentMagnitudeFormulas.java",
    "doc": "Open-branch current-magnitude residuals and Jacobian entries, delegated to "
           "by the open-branch current-magnitude equation terms.",

    "constants": {"R2": 1},                 # PiModel.R2 == 1; r1 is a passed-in parameter
    "thetas": {},                           # ksi precomputed (sinKsi/cosKsi); ph kept raw -> Math.*

    # Only the connected side's voltage is a state variable (no di/dph, no di/dr1).
    "variables": ["v1", "v2"],
    "base_symbols": ["y", "ksi", "g1", "b1", "g2", "b2", "v1", "ph1", "r1", "v2", "ph2"],

    # Reproduces i2(y,cosKsi,sinKsi,g1,b1,g2,b2,v2,ph2) and i1(...,v1,ph1,r1).
    "canonical_order": ["y", "cosKsi", "sinKsi", "g1", "b1", "g2", "b2",
                        "v1", "ph1", "r1", "v2", "ph2"],

    # Structured emission (see closed_branch_i): named component helpers reI/imI + their
    # per-variable derivatives, assembled into the chain-rule form. Here the magnitude is
    # rotation-invariant in ph, so only di/dv is non-zero (di/dph drops out automatically).
    "magnitudes": [
        {"name": "i2", "re_name": "reI2", "im_name": "imI2", "re": _reI2, "im": _imI2},
        {"name": "i1", "re_name": "reI1", "im_name": "imI1", "re": _reI1, "im": _imI1},
    ],

    "residuals": [
        ("i2", None, _hypot(_reI2, _imI2)),
        ("i1", None, _hypot(_reI1, _imI1)),
    ],
}
