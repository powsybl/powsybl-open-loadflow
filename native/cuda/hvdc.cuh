// Copyright (c) 2026, RTE (http://www.rte-france.com)
// SPDX-License-Identifier: MPL-2.0
//
// HVDC AC-emulation active-flow formulas (piecewise; derivative neglects the cable loss, per OLF) — delegated to by the HVDC AC-emulation equation terms.
//
// GENERATED — DO NOT EDIT. Regenerate with: python3 codegen/generate.py (see codegen/README.md).
//
// FUSED per-element kernel body: one routine computes the two HVDC AC-emulation
// injections p1/p2 + their four (approximated, loss-derivative-neglecting) angle
// derivatives for one hvdc, writing into Struct-of-Arrays at index i. The control
// side is piecewise on the sign of rawP = p0 + k*(ph1 - ph2) — branchless ternaries,
// no trig; the rawP condition is shared via CSE.
#pragma once
#include <math.h>

#ifndef HVDC_FN
#define HVDC_FN __host__ __device__   // also compiles host-side for the diff test
#endif

namespace powsybl { namespace openloadflow {

HVDC_FN void evalHvdc(
        double p0, double k, double lossFactor1, double lossFactor2, double r,
        double ph1, double ph2,
        int i,
        double* p1,
        double* dp1dph1,
        double* dp1dph2,
        double* p2,
        double* dp2dph1,
        double* dp2dph2) {
    double x0 = k * (ph1 - ph2) + p0;
    double x1 = x0 >= 0;
    double x2 = fabs(x0);
    double x3 = lossFactor1 - 1;
    double x4 = r * (x0) * (x0);
    double x5 = lossFactor2 - 1;
    double x6 = x3 * x5;
    double x7 = k * x6;
    double x8 = -k;
    double x9 = -x7;
    double x10 = x0 < 0;
    p1[i] = ((x1) ? ( x0 ) : ( -x6 * (x2 + x3 * x4) ));
    dp1dph1[i] = ((x1) ? ( k ) : ( x7 ));
    dp1dph2[i] = ((x1) ? ( x8 ) : ( x9 ));
    p2[i] = ((x10) ? ( -x0 ) : ( -x6 * (x2 + x4 * x5) ));
    dp2dph1[i] = ((x10) ? ( x8 ) : ( x9 ));
    dp2dph2[i] = ((x10) ? ( k ) : ( x7 ));
}

}}  // namespace powsybl::openloadflow
