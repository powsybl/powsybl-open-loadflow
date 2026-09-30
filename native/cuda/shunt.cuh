// Copyright (c) 2026, RTE (http://www.rte-france.com)
// SPDX-License-Identifier: MPL-2.0
//
// Shunt-compensator AC flow residuals and Jacobian entries, delegated to by the shunt equation terms.
//
// GENERATED — DO NOT EDIT. Regenerate with: python3 codegen/generate.py (see codegen/README.md).
//
// FUSED per-element kernel body: one routine computes the shunt injections p/q
// + their three non-zero derivatives for one shunt, writing into Struct-of-Arrays
// at index i. No trig at all — the cheapest fill of the families; dqdb only
// matters when the shunt is controllable (SHUNT_B is a skip column in plain PF).
#pragma once
#include <math.h>

#ifndef SH_FN
#define SH_FN __host__ __device__   // also compiles host-side for the diff test
#endif

namespace powsybl { namespace openloadflow {

SH_FN void evalShunt(
        double v, double g, double b,
        int i,
        double* p,
        double* dpdv,
        double* q,
        double* dqdv,
        double* dqdb) {
    double x0 = (v) * (v);
    double x1 = 2 * v;
    p[i] = g * x0;
    dpdv[i] = g * x1;
    q[i] = -b * x0;
    dqdv[i] = -b * x1;
    dqdb[i] = -x0;
}

}}  // namespace powsybl::openloadflow
