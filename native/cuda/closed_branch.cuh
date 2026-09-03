// Copyright (c) 2026, RTE (http://www.rte-france.com)
// SPDX-License-Identifier: MPL-2.0
//
// Closed-branch AC flow residuals and Jacobian entries, shared by the scalar equation-term path and the vectorized AcNetworkVector loop.
//
// GENERATED — DO NOT EDIT. Regenerate with: python3 codegen/generate.py (see codegen/README.md).
//
// FUSED per-element kernel body: one routine computes p1/q1/p2/q2 + all 24 Jacobian
// entries for one branch, writing into Struct-of-Arrays at index i. sin/cos of ksi,
// theta1, theta2 are computed once and shared via common-subexpression elimination —
// the per-element device routine the GPU assembly kernel calls (one thread = one
// branch, inline trig, SoA output — a fused per-branch kernel).
#pragma once
#include <math.h>

#ifndef CB_FN
#define CB_FN __host__ __device__   // also compiles host-side for the diff test
#endif

namespace powsybl { namespace openloadflow {

#ifndef OLF_CONST_R2
#define OLF_CONST_R2
constexpr double R2 = 1.0;
#endif
#ifndef OLF_CONST_A2
#define OLF_CONST_A2
constexpr double A2 = 0.0;
#endif

CB_FN void evalClosedBranch(
        double y, double g1, double g2, double b1, double b2,
        double v1, double r1, double v2, double ksi, double a1, double ph1, double ph2,
        int i,
        double* p1,
        double* dp1dv1,
        double* dp1dv2,
        double* dp1dph1,
        double* dp1dph2,
        double* dp1da1,
        double* dp1dr1,
        double* q1,
        double* dq1dv1,
        double* dq1dv2,
        double* dq1dph1,
        double* dq1dph2,
        double* dq1da1,
        double* dq1dr1,
        double* p2,
        double* dp2dv1,
        double* dp2dv2,
        double* dp2dph1,
        double* dp2dph2,
        double* dp2da1,
        double* dp2dr1,
        double* q2,
        double* dq2dv1,
        double* dq2dv2,
        double* dq2dph1,
        double* dq2dph2,
        double* dq2da1,
        double* dq2dr1) {
    double x0 = r1 * v1;
    double x1 = y * sin(ksi);
    double x2 = A2 - a1 + ksi - ph1 + ph2;
    double x3 = sin(x2);
    double x4 = R2 * v2;
    double x5 = x4 * y;
    double x6 = x3 * x5;
    double x7 = g1 * x0 + x0 * x1 - x6;
    double x8 = x0 * (g1 + x1) + x7;
    double x9 = x0 * y;
    double x10 = R2 * x9;
    double x11 = cos(x2);
    double x12 = x11 * x5;
    double x13 = x0 * x12;
    double x14 = y * cos(ksi);
    double x15 = b1 * x0 - x0 * x14 + x12;
    double x16 = -x14;
    double x17 = x0 * (b1 + x16) + x15;
    double x18 = x0 * x6;
    double x19 = -x18;
    double x20 = -A2 + a1 + ksi + ph1 - ph2;
    double x21 = sin(x20);
    double x22 = g2 * x4 + x1 * x4 - x21 * x9;
    double x23 = x21 * x5;
    double x24 = cos(x20);
    double x25 = x24 * x5;
    double x26 = x0 * x25;
    double x27 = -x26;
    double x28 = b2 * x4 - x14 * x4 + x24 * x9;
    double x29 = x0 * x23;
    p1[i] = x0 * x7;
    dp1dv1[i] = r1 * x8;
    dp1dv2[i] = -x10 * x3;
    dp1dph1[i] = x13;
    dp1dph2[i] = -x13;
    dp1da1[i] = x13;
    dp1dr1[i] = v1 * x8;
    q1[i] = -x0 * x15;
    dq1dv1[i] = -r1 * x17;
    dq1dv2[i] = -x10 * x11;
    dq1dph1[i] = x19;
    dq1dph2[i] = x18;
    dq1da1[i] = x19;
    dq1dr1[i] = -v1 * x17;
    p2[i] = x22 * x4;
    dp2dv1[i] = -r1 * x23;
    dp2dv2[i] = R2 * (x22 + x4 * (g2 + x1));
    dp2dph1[i] = x27;
    dp2dph2[i] = x26;
    dp2da1[i] = x27;
    dp2dr1[i] = -v1 * x23;
    q2[i] = -x28 * x4;
    dq2dv1[i] = -r1 * x25;
    dq2dv2[i] = -R2 * (x28 + x4 * (b2 + x16));
    dq2dph1[i] = x29;
    dq2dph2[i] = -x29;
    dq2da1[i] = x29;
    dq2dr1[i] = -v1 * x25;
}

}}  // namespace powsybl::openloadflow
