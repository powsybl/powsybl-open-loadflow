// Copyright (c) 2026, RTE (http://www.rte-france.com)
// SPDX-License-Identifier: MPL-2.0
//
// Scatter table for the fused hvdc fill kernel. GENERATED — DO NOT EDIT. Regenerate with: python3 codegen/generate.py (see codegen/README.md).
//
// One row per output of evalHvdc, IN ORDER. role: 0=residual (add to F at its
// equation row), 1=jacobian (add to J at (row,col)), 2=skip (column variable is never a state
// variable in plain AC PF, e.g. SHUNT_B). Branch control columns (BRANCH_ALPHA1/RHO1) are
// emitted as jacobian (role 1) on the branch endpoint: they resolve to a real column only when
// the element derives that control (transformer phase/voltage control) and to absent (-1)
// otherwise, so the host marks the entry skipped per element.
// Endpoint codes: 0=bus1, 1=bus2. A bus owns rows [P=+0, Q=+1] and cols [PHI=+0, V=+1]; the branch
// (endpoint 2) owns control cols [ALPHA1=+0, RHO1=+1]:
//   row = busOf(rowEnd) * 2 + rowEq ;  col = busOf(colEnd) * 2 + colVar
#pragma once

namespace powsybl { namespace openloadflow {

struct HvdcScatter { int role; int rowEnd; int rowEq; int colEnd; int colVar; };

constexpr int HVDC_NOUT = 6;
constexpr HvdcScatter HVDC_SCATTER[HVDC_NOUT] = {
    {0, 0, 0, -1, -1},  // [0] p1
    {1, 0, 0, 0, 0},  // [1] dp1dph1
    {1, 0, 0, 1, 0},  // [2] dp1dph2
    {0, 1, 0, -1, -1},  // [3] p2
    {1, 1, 0, 0, 0},  // [4] dp2dph1
    {1, 1, 0, 1, 0},  // [5] dp2dph2
};

}}  // namespace powsybl::openloadflow
