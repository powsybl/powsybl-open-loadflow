// Copyright (c) 2026, RTE (http://www.rte-france.com)
// SPDX-License-Identifier: MPL-2.0
//
// Scatter table for the fused closed-branch fill kernel. GENERATED — DO NOT EDIT. Regenerate with: python3 codegen/generate.py (see codegen/README.md).
//
// One row per output of evalClosedBranch, IN ORDER. role: 0=residual (add to F at its
// equation row), 1=jacobian (add to J at (row,col)), 2=skip (column variable is never a state
// variable in plain AC PF, e.g. SHUNT_B). Branch control columns (BRANCH_ALPHA1/RHO1) are
// emitted as jacobian (role 1) on the branch endpoint: they resolve to a real column only when
// the element derives that control (transformer phase/voltage control) and to absent (-1)
// otherwise, so the host marks the entry skipped per element.
// Endpoint codes: 0=bus1, 1=bus2, 2=branch. A bus owns rows [P=+0, Q=+1] and cols [PHI=+0, V=+1]; the branch
// (endpoint 2) owns control cols [ALPHA1=+0, RHO1=+1]:
//   row = busOf(rowEnd) * 2 + rowEq ;  col = busOf(colEnd) * 2 + colVar
#pragma once

namespace powsybl { namespace openloadflow {

struct CbScatter { int role; int rowEnd; int rowEq; int colEnd; int colVar; };

constexpr int CB_NOUT = 28;
constexpr CbScatter CB_SCATTER[CB_NOUT] = {
    {0, 0, 0, -1, -1},  // [0] p1
    {1, 0, 0, 0, 1},  // [1] dp1dv1
    {1, 0, 0, 1, 1},  // [2] dp1dv2
    {1, 0, 0, 0, 0},  // [3] dp1dph1
    {1, 0, 0, 1, 0},  // [4] dp1dph2
    {1, 0, 0, 2, 0},  // [5] dp1da1
    {1, 0, 0, 2, 1},  // [6] dp1dr1
    {0, 0, 1, -1, -1},  // [7] q1
    {1, 0, 1, 0, 1},  // [8] dq1dv1
    {1, 0, 1, 1, 1},  // [9] dq1dv2
    {1, 0, 1, 0, 0},  // [10] dq1dph1
    {1, 0, 1, 1, 0},  // [11] dq1dph2
    {1, 0, 1, 2, 0},  // [12] dq1da1
    {1, 0, 1, 2, 1},  // [13] dq1dr1
    {0, 1, 0, -1, -1},  // [14] p2
    {1, 1, 0, 0, 1},  // [15] dp2dv1
    {1, 1, 0, 1, 1},  // [16] dp2dv2
    {1, 1, 0, 0, 0},  // [17] dp2dph1
    {1, 1, 0, 1, 0},  // [18] dp2dph2
    {1, 1, 0, 2, 0},  // [19] dp2da1
    {1, 1, 0, 2, 1},  // [20] dp2dr1
    {0, 1, 1, -1, -1},  // [21] q2
    {1, 1, 1, 0, 1},  // [22] dq2dv1
    {1, 1, 1, 1, 1},  // [23] dq2dv2
    {1, 1, 1, 0, 0},  // [24] dq2dph1
    {1, 1, 1, 1, 0},  // [25] dq2dph2
    {1, 1, 1, 2, 0},  // [26] dq2da1
    {1, 1, 1, 2, 1},  // [27] dq2dr1
};

}}  // namespace powsybl::openloadflow
