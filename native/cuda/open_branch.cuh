// Copyright (c) 2026, RTE (http://www.rte-france.com)
// SPDX-License-Identifier: MPL-2.0
//
// Open-branch AC active/reactive flow residuals and Jacobian entries, delegated to by the open-branch equation terms.
//
// GENERATED — DO NOT EDIT. Regenerate with: python3 codegen/generate.py (see codegen/README.md).
//
// FUSED per-element kernel body: one routine computes the four open-branch flows
// (p2/q2 for a side-1-open branch, p1/q1 for a side-2-open one) + their four
// voltage derivatives, writing into Struct-of-Arrays at index i. sin/cos of ksi
// and the shunt denominators are computed once and shared via CSE. A real branch
// has only ONE side open: the host stamps that side's pair and resolves the other
// pair's scatter rows to -1 (absent), exactly like the closed-branch fill.
#pragma once
#include <math.h>

#ifndef OB_FN
#define OB_FN __host__ __device__   // also compiles host-side for the diff test
#endif

namespace powsybl { namespace openloadflow {

#ifndef OLF_CONST_R2
#define OLF_CONST_R2
constexpr double R2 = 1.0;
#endif

OB_FN void evalOpenBranch(
        double y, double ksi, double g1, double b1, double g2, double b2,
        double v1, double r1, double v2,
        int i,
        double* p2,
        double* dp2dv2,
        double* q2,
        double* dq2dv2,
        double* p1,
        double* dp1dv1,
        double* q1,
        double* dq1dv1) {
    double x0 = (y) * (y);
    double x1 = y * sin(ksi);
    double x2 = y * cos(ksi);
    double x3 = -x2;
    double x4 = 1.0/((b1 + x3) * (b1 + x3) + (g1 + x1) * (g1 + x1));
    double x5 = x0 * x4;
    double x6 = x4 * ((b1) * (b1) + (g1) * (g1));
    double x7 = g1 * x5 + g2 + x1 * x6;
    double x8 = (R2) * (R2);
    double x9 = (v2) * (v2) * x8;
    double x10 = 2 * v2 * x8;
    double x11 = b1 * x5 + b2 - x2 * x6;
    double x12 = 1.0/((b2 + x3) * (b2 + x3) + (g2 + x1) * (g2 + x1));
    double x13 = x0 * x12;
    double x14 = x12 * ((b2) * (b2) + (g2) * (g2));
    double x15 = g1 + g2 * x13 + x1 * x14;
    double x16 = (r1) * (r1);
    double x17 = (v1) * (v1) * x16;
    double x18 = 2 * v1 * x16;
    double x19 = b1 + b2 * x13 - x14 * x2;
    p2[i] = x7 * x9;
    dp2dv2[i] = x10 * x7;
    q2[i] = -x11 * x9;
    dq2dv2[i] = -x10 * x11;
    p1[i] = x15 * x17;
    dp1dv1[i] = x15 * x18;
    q1[i] = -x17 * x19;
    dq1dv1[i] = -x18 * x19;
}

}}  // namespace powsybl::openloadflow
