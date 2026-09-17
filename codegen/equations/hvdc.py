# Copyright (c) 2026, RTE (http://www.rte-france.com)
# SPDX-License-Identifier: MPL-2.0
#
# SINGLE SOURCE OF TRUTH for the HVDC AC-emulation active-flow formulas.
#
# EXPLICIT (non-autodiff) family. AC emulation injects, at each end, a power proportional to
# the angle difference: rawP = p0 + k*(ph1 - ph2). When the converter is the controller the
# injection is rawP; otherwise it is minus the power that survives the converter + cable losses.
# This is PIECEWISE (on the sign of rawP), and OLF's Jacobian is an INTENTIONAL APPROXIMATION —
# "derivative of cable loss is neglected" — so it is NOT the true derivative of the value. SymPy
# autodiff would therefore disagree; instead we author BOTH the value AND the (approximated)
# derivatives, and the generator renders them (Piecewise -> ?: ternary, Abs -> Math.abs).
#
# The cable loss is the closed form r*pDc^2 (powsybl HvdcUtils.getHvdcLineLosses with nominalV=1),
# inlined here so the generated class is self-contained (no HvdcUtils dependency).
#
# Targets: the scalar CPU formulas AND the fused CUDA kernel + GPU scatter (Piecewise
# renders to a C ternary, Abs to fabs; both ph columns are state variables, so no skip
# rows). The autodiff audit is skipped for explicit families — the Jacobian is an
# intentional approximation, so a finite-difference check of the value would rightly fail.

import sympy as sp

p0, k, lf1, lf2, r, ph1, ph2 = sp.symbols(
    "p0 k lossFactor1 lossFactor2 r ph1 ph2", real=True)

_rawP = p0 + k * (ph1 - ph2)
_mult = (1 - lf1) * (1 - lf2)              # getVscLossMultiplier (symmetric in lf1/lf2)


def _abs_with_losses(loss_c, loss_nc):     # getAbsActivePowerWithLosses, loss = r*lineIn^2
    line_in = (1 - loss_c) * sp.Abs(_rawP)
    return (1 - loss_nc) * (line_in - r * line_in**2)


# side 1 controls when rawP >= 0; side 2 controls when rawP < 0.
_ctrl1 = _rawP >= 0
_ctrl2 = _rawP < 0

MODULE = {
    "java_class": "HvdcAcEmulationFormulas",
    "java_package": "com.powsybl.openloadflow.ac.equations",
    "java_imports": [],
    "java_path": "src/main/java/com/powsybl/openloadflow/ac/equations/HvdcAcEmulationFormulas.java",
    "cuda_path": "native/cuda/hvdc.cuh",
    "scatter_path": "native/cuda/hvdc.scatter.json",
    "scatter_header_path": "native/cuda/hvdc_scatter.h",
    "cuda_namespace": "powsybl::openloadflow",
    "cuda_consts": [],
    "cuda_routine": "evalHvdc",
    "cuda_fn_macro": "HVDC_FN",
    "scatter_prefix": "HVDC",
    "cuda_params": [["p0", "k", "lossFactor1", "lossFactor2", "r"],
                    ["ph1", "ph2"]],
    "cuda_fused_doc": (
        "// FUSED per-element kernel body: one routine computes the two HVDC AC-emulation\n"
        "// injections p1/p2 + their four (approximated, loss-derivative-neglecting) angle\n"
        "// derivatives for one hvdc, writing into Struct-of-Arrays at index i. The control\n"
        "// side is piecewise on the sign of rawP = p0 + k*(ph1 - ph2) — branchless ternaries,\n"
        "// no trig; the rawP condition is shared via CSE."),
    "doc": "HVDC AC-emulation active-flow formulas (piecewise; derivative neglects the cable "
           "loss, per OLF) — delegated to by the HVDC AC-emulation equation terms.",

    "constants": {},
    "thetas": {},
    "variables": [],                       # explicit family: no autodiff
    "base_symbols": ["p0", "k", "lossFactor1", "lossFactor2", "r", "ph1", "ph2"],
    "canonical_order": ["p0", "k", "lossFactor1", "lossFactor2", "r", "ph1", "ph2"],

    # name -> hand-authored value / approximated derivative (SymPy Piecewise).
    "explicit_outputs": [
        ("p1", sp.Piecewise((_rawP, _ctrl1), (-_abs_with_losses(lf1, lf2), True))),
        ("dp1dph1", sp.Piecewise((k, _ctrl1), (k * _mult, True))),
        ("dp1dph2", sp.Piecewise((-k, _ctrl1), (-k * _mult, True))),
        ("p2", sp.Piecewise((-_rawP, _ctrl2), (-_abs_with_losses(lf2, lf1), True))),
        ("dp2dph1", sp.Piecewise((-k, _ctrl2), (-k * _mult, True))),
        ("dp2dph2", sp.Piecewise((k, _ctrl2), (k * _mult, True))),
    ],

    # --- Scatter stamp (where each output lands in OLF's equation system) -------
    #
    # Scalar terms: ph1Var/ph2Var are both declared (named) in the shared abstract
    # base. The creator wires bare addTerm(p1)/addTerm(p2) inside
    # createHvdcAcEmulationEquations, on getEquation(hvdc.getBus<N>().getNum(), ...).
    "stamp": {
        "sources": {
            "columns": [
                "src/main/java/com/powsybl/openloadflow/ac/equations/"
                "AbstractHvdcAcEmulationFlowEquationTerm.java",
            ],
            "equations": "src/main/java/com/powsybl/openloadflow/ac/equations/"
                         "AcEquationSystemCreator.java",
        },
        "term_prefix": "",
        "equations_method": "createHvdcAcEmulationEquations",
        "endpoints": ["bus1", "bus2"],
        # residual -> (AcEquationType, endpoint) it contributes to (the F row)
        "residual_equation": {
            "p1": ("BUS_TARGET_P", "bus1"),
            "p2": ("BUS_TARGET_P", "bus2"),
        },
        # differentiation variable -> (AcVariableType, endpoint) it is (the J column)
        "variable_column": {
            "ph1": ("BUS_PHI", "bus1"),
            "ph2": ("BUS_PHI", "bus2"),
        },
    },
}
