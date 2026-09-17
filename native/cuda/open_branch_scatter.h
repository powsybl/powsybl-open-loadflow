// Copyright (c) 2026, RTE (http://www.rte-france.com)
// SPDX-License-Identifier: MPL-2.0
//
// Scatter table for the fused open-branch fill kernel. GENERATED — DO NOT EDIT. Regenerate with: python3 codegen/generate.py (see codegen/README.md).
//
// One row per output of evalOpenBranch, IN ORDER. role: 0=residual (add to F at its
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

struct ObScatter { int role; int rowEnd; int rowEq; int colEnd; int colVar; };

constexpr int OB_NOUT = 8;
constexpr ObScatter OB_SCATTER[OB_NOUT] = {
    {0, 1, 0, -1, -1},  // [0] p2
    {1, 1, 0, 1, 1},  // [1] dp2dv2
    {0, 1, 1, -1, -1},  // [2] q2
    {1, 1, 1, 1, 1},  // [3] dq2dv2
    {0, 0, 0, -1, -1},  // [4] p1
    {1, 0, 0, 0, 1},  // [5] dp1dv1
    {0, 0, 1, -1, -1},  // [6] q1
    {1, 0, 1, 0, 1},  // [7] dq1dv1
};

}}  // namespace powsybl::openloadflow
