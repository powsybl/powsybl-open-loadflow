// Copyright (c) 2026, RTE (http://www.rte-france.com)
// SPDX-License-Identifier: MPL-2.0
//
// FULL Newton-Raphson AC power flow on the GPU, behind ONE JNI call. Java hands over
// the network data (per-family element parameters + the equation/variable indices the
// scatter tables resolve against), the initial state and the targets; the native side
// then keeps everything resident on the device:
//
//   - the fixed-pattern CSR Jacobian structure is built ONCE on the host from the
//     generated scatter tables (closed-branch, open-branch, shunt, HVDC AC emulation
//     + the slack/PV identity rows) — cuDSS analyses it once;
//   - each iteration, the four generated fused kernels (evalClosedBranch /
//     evalOpenBranch / evalShunt / evalHvdc) fill the CSR values AND the mismatch F
//     directly in device memory, reading the device state vector through the
//     precomputed index arrays (no host repack);
//   - cuDSS REFACTORIZATION + SOLVE produce dx on the device, a kernel applies
//     x += dx, and only the n-double mismatch crosses PCIe for the convergence check.
//
// Orientation: J is true CSR with row = equation column, col = variable row (OLF's
// indices), so cuDSS solves J dx = -F directly — no transposition games (contrast
// powsybl-math's CSC-as-CSR convention, which exists to serve solveTransposed).
//
// The host-built destination array folds the scatter ROLE in, so the kernels never
// index the constexpr scatter tables:  dest >= 0 -> J values slot;  dest == -1 ->
// skip;  dest <= -2 -> mismatch row (-2 - dest).
//
// Java: com.powsybl.openloadflow.ac.equations.vector.gpu.GpuAcNewtonSolver
//   static native double[] solve(double[] x0, double[] target, int maxIter, double tol,
//       double[] cbIn, int[] cbIdx, double[] obIn, int[] obIdx,
//       double[] shIn, int[] shIdx, double[] hvIn, int[] hvIdx, int[] idIdx)
//     x0/target: length n (state by variable row / target by equation column)
//     cbIn nbr*8  (y g1 g2 b1 b2 ksi r1 a1)   cbIdx nbr*10 (P1 Q1 P2 Q2 ph1 v1 ph2 v2 a1col r1col)
//     obIn nob*7  (y ksi g1 b1 g2 b2 r1)      obIdx nob*6 (P1 Q1 P2 Q2 v1 v2)
//     shIn nsh*2  (g b)                       shIdx nsh*3 (P Q v)
//     hvIn nhv*5  (p0 k lf1 lf2 r)            hvIdx nhv*4 (P1 P2 ph1 ph2)
//     idIdx nid*2 (eqRow varCol)              -1 = absent equation / variable
//     returns double[n + 2] = converged state, iterations, final ||F||inf
//
// This library links NVIDIA cuDSS DYNAMICALLY (never bundled, same licensing posture
// as powsybl-math-native's libmathcudss); cudart is linked statically.
#include <jni.h>
#include <cuda_runtime.h>
#include <cudss.h>

#include <algorithm>
#include <cmath>
#include <cstdint>
#include <memory>
#include <stdexcept>
#include <string>
#include <utility>
#include <vector>

#include "closed_branch.cuh"
#include "closed_branch_scatter.h"
#include "open_branch.cuh"
#include "open_branch_scatter.h"
#include "shunt.cuh"
#include "shunt_scatter.h"
#include "hvdc.cuh"
#include "hvdc_scatter.h"

using namespace powsybl::openloadflow;

#define CUDA_CHECK(x) do { cudaError_t e_ = (x); if (e_ != cudaSuccess) { \
    throw std::runtime_error(std::string("CUDA error: ") + cudaGetErrorString(e_)); } } while (0)
#define CUDSS_CHECK(x) do { cudssStatus_t s_ = (x); if (s_ != CUDSS_STATUS_SUCCESS) { \
    throw std::runtime_error(std::string("cuDSS error, status ") + std::to_string((int) s_) \
        + " at " __FILE__ ":" + std::to_string(__LINE__) + " [" #x "]"); } } while (0)

// Batched N-1 solve path. BOTH implementations of the per-iteration linear solve are
// compiled in; the batch context picks one at RUNTIME from its `mode` field (set by the
// Java caller via -Dolf.gpu.batchMode, default 0):
//
//   mode 1 : the UNIFORM BATCHED cuDSS API (cudssMatrixCreateBatchCsr/Dn) — one ANALYSIS on
//     the shared pattern, then ONE batched FACTORIZATION + SOLVE over every scenario's value
//     set. Fastest where it fits; holds ~one factorization per scenario, so chunk it on small
//     GPUs (olf.gpu.sa.maxBatch).
//
//   mode 2 : BLOCK-DIAGONAL single matrix — assemble blkdiag(J_1..J_nS) as ONE (n*nS) CSR and
//     ANALYZE+FACTORIZE once, then REFACTORIZE every iteration (reuse pivots — a
//     refactor-every-iteration strategy). Measured ~parity with mode 1 (cuDSS refactorization ≈ factorization;
//     unlike CPU KLU it is not pivot-dominated). Same memory profile as mode 1 (chunk it).
//
//   mode 3 : UBATCH — a SINGLE per-block CSR with value/RHS pointers aimed at the batched buffers
//     + CUDSS_CONFIG_UBATCH_SIZE=nS. cuDSS analyzes ONE block (cheap: ~74 ms vs ~1.6 s for mode 1's
//     all-blocks analysis — a cheap single-block analysis). BUT on cuDSS 0.8 the UBATCH
//     factorize/solve is ~4x slower than the batch API (0.8 deprecated UBATCH for the batch API),
//     so it is a NET LOSS on 0.8 despite the cheap analysis. Kept as an alternative mechanism
//     (a win on cuDSS 0.7.x / if 0.8's UBATCH solve improves). DEFAULT mode.
//
//   mode 4 : UBATCH + DirectIter0Only — same setup as mode 3 but FACTORIZE only at each chunk's
//     iter 0, SOLVE-only for the remaining iters (the Jacobian is frozen at the AC-hot-started
//     point, the mismatch/RHS is still recomputed every iter). Skips ~75% of the factorizations
//     at K=4 — the factorization is ~39% of GPU time. EXPERIMENTAL (convergence bet: a single-branch
//     N-1 is a small perturbation so the frozen J still converges from the warm start).
//
//   mode 5 : UBATCH + DirectRefactorEvery — same setup as mode 3 but FACTORIZE once (ever) then
//     REFACTORIZE after (reuse pivot order, recompute values). The UBATCH analog of mode 2;
//     EXPERIMENTAL probe of whether cuDSS refactorization is cheaper than factorization on the
//     UBATCH path (mode 2 showed ~parity on the block-diagonal path).
//
//   mode 0 (DEFAULT) : the per-scenario loop — repoint a single matrix and refactorize+solve
//     each scenario. Analysis is still amortized over the set; memory-light, scales to large
//     cases on small GPUs.
//
// cuDSS >= 0.7.1 is required. The cudssMatrixCreate{Csr,Dn,BatchCsr,BatchDn} data-type
// arguments differ between 0.7.x and 0.8 (0.8 added a separate `offsetType` and renamed the
// enum cudaDataType_t -> cudssDataType_t with CUDSS_R_* aliasing CUDA_R_*), so the type-arg
// groups are shimmed by CUDSS_VERSION below and the same source builds against either.
static_assert(CUDSS_VERSION >= 701,
              "libolfgpu requires cuDSS >= 0.7.1 (CUDSS_VERSION >= 701)");
#if CUDSS_VERSION >= 800
#  define OLF_CUDSS_CSR_TYPES  CUDSS_R_32I, CUDSS_R_32I, CUDSS_R_64F   // offsetType, indexType, valueType
#  define OLF_CUDSS_DN_TYPE    CUDSS_R_64F                             // valueType
#  define OLF_CUDSS_BDN_TYPES  CUDSS_R_32I, CUDSS_R_64F                // integerType, valueType
#else
#  define OLF_CUDSS_CSR_TYPES  CUDA_R_32I, CUDA_R_64F                  // indexType, valueType (no offsetType)
#  define OLF_CUDSS_DN_TYPE    CUDA_R_64F
#  define OLF_CUDSS_BDN_TYPES  CUDA_R_32I, CUDA_R_64F
#endif

// Auto-sizing the batch chunk: query cuDSS's reported LU fill (CUDSS_DATA_LU_NNZ) via a
// cheap single-matrix probe analysis on the shared pattern, instead of guessing the fill
// with a heuristic multiplier. CUDSS_DATA_LU_NNZ has been in cuDSS since early versions;
// set -DOLF_GPU_CUDSS_LU_NNZ=0 if the installed headers lack the enum (the heuristic is
// then used). Even with it on, a runtime query failure falls back to the heuristic.
#ifndef OLF_GPU_CUDSS_LU_NNZ
#  define OLF_GPU_CUDSS_LU_NNZ 1
#endif

namespace {

// cuDSS iterative-refinement steps. Default 0: the OUTER Newton loop self-corrects any
// linear-solve inexactness, so refining each solve is redundant for NR — measured ~30% faster
// on batched N-1 with IDENTICAL convergence (vs the old IR=2 inherited from powsybl-math-native).
// Override per run with OLF_GPU_IR_STEPS=<n> (e.g. 2) if a stiff/near-singular Jacobian needs it.
// NB reusing pivots via CUDSS_PHASE_REFACTORIZATION was also benchmarked and gives NO speedup in
// cuDSS batched mode (its factorization cost is the numeric fill, not pivoting), so it is not used.
constexpr int IR_N_STEPS = 0;
constexpr int BLOCK = 64;

inline int irSteps() {
    const char* e = std::getenv("OLF_GPU_IR_STEPS");
    return e ? std::atoi(e) : IR_N_STEPS;
}

// ---------------------------------------------------------------------------------
// Host: resolve each scatter-table row to (equation row, variable col) from one
// element's packed index array. Role 2 (skip) resolves to absent.
// ---------------------------------------------------------------------------------

struct RC { int row = -1; int col = -1; };

RC resolveCb(const CbScatter& s, const int* ix) {            // P1 Q1 P2 Q2 ph1 v1 ph2 v2 a1col r1col
    RC rc;
    if (s.role == 2) return rc;
    rc.row = ix[s.rowEnd * 2 + s.rowEq];
    // col = 4 + endpoint*2 + var: endpoint 0/1 = bus1/bus2 (PHI/V cols), endpoint 2 = the branch's
    // own control cols (ALPHA1 at ix[8], RHO1 at ix[9]). A control col is -1 when not derived, so
    // the entry resolves to absent and buildDest skips it for that element.
    if (s.role == 1) rc.col = ix[4 + s.colEnd * 2 + s.colVar];
    return rc;
}

RC resolveOb(const ObScatter& s, const int* ix) {            // P1 Q1 P2 Q2 v1 v2
    RC rc;
    if (s.role == 2) return rc;
    rc.row = ix[s.rowEnd * 2 + s.rowEq];
    if (s.role == 1) rc.col = ix[4 + s.colEnd];              // BUS_V columns only
    return rc;
}

RC resolveSh(const ShScatter& s, const int* ix) {            // P Q v
    RC rc;
    if (s.role == 2) return rc;
    rc.row = ix[s.rowEq];                                    // single bus: rowEnd == 0
    if (s.role == 1) rc.col = ix[2];
    return rc;
}

RC resolveHv(const HvdcScatter& s, const int* ix) {          // P1 P2 ph1 ph2
    RC rc;
    if (s.role == 2) return rc;
    rc.row = ix[s.rowEnd];                                   // P rows only: rowEq == 0
    if (s.role == 1) rc.col = ix[2 + s.colEnd];
    return rc;
}

// One pass per family: collect the J pattern entries (role 1, both indices present).
template <typename S, typename F>
void collectPattern(int ne, const int* idx, int stride, const S* table, int nout, F resolve,
                    std::vector<std::pair<int, int>>& pat) {
    for (int e = 0; e < ne; e++) {
        for (int k = 0; k < nout; k++) {
            RC rc = resolve(table[k], idx + e * stride);
            if (table[k].role == 1 && rc.row >= 0 && rc.col >= 0) {
                pat.emplace_back(rc.row, rc.col);
            }
        }
    }
}

// Lookup of a (row, col) entry's slot in the SORTED CSR pattern. The pattern is built by a
// std::sort of (row, col) pairs, so colIdx within each row is ascending — a binary search over
// colIdx[rowPtr[row] .. rowPtr[row+1]) resolves (row, col) -> slot with no extra memory (replaces
// a std::map<pair,int> tree, which was pure overhead on an already-sorted pattern).
struct CsrPattern {
    const int* rowPtr;
    const int* colIdx;
    int at(int row, int col) const {
        int lo = rowPtr[row];
        int hi = rowPtr[row + 1];
        while (lo < hi) {
            int mid = (lo + hi) >> 1;
            if (colIdx[mid] < col) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        if (lo >= rowPtr[row + 1] || colIdx[lo] != col) {
            throw std::runtime_error("CSR pattern lookup miss");
        }
        return lo;
    }
};

// Second pass: the per-(element, output) destination with the role folded in:
//   role 1 -> CSR values slot (>= 0);  role 0 -> -2 - F row;  absent/skip -> -1.
template <typename S, typename F>
std::vector<int> buildDest(int ne, const int* idx, int stride, const S* table, int nout,
                           F resolve, const CsrPattern& pos) {
    std::vector<int> dest(static_cast<size_t>(ne) * nout, -1);
    for (int e = 0; e < ne; e++) {
        for (int k = 0; k < nout; k++) {
            RC rc = resolve(table[k], idx + e * stride);
            if (table[k].role == 1 && rc.row >= 0 && rc.col >= 0) {
                dest[static_cast<size_t>(e) * nout + k] = pos.at(rc.row, rc.col);
            } else if (table[k].role == 0 && rc.row >= 0) {
                dest[static_cast<size_t>(e) * nout + k] = -2 - rc.row;
            }
        }
    }
    return dest;
}

// ---------------------------------------------------------------------------------
// Device: the four family fill kernels + the loop helpers
// ---------------------------------------------------------------------------------

__device__ void scatterAdd(int nout, const int* dest, const double* o, double* vals, double* F) {
    for (int k = 0; k < nout; k++) {
        int d = dest[k];
        if (d >= 0) {
            atomicAdd(&vals[d], o[k]);
        } else if (d <= -2) {
            atomicAdd(&F[-2 - d], o[k]);
        }
    }
}

__global__ void fillClosedBranch(int ne, const double* in, const int* ix, const int* dest,
                                 const double* x, double* vals, double* F) {
    int e = blockIdx.x * blockDim.x + threadIdx.x;
    if (e >= ne) return;
    const double* p = in + e * 8;                            // y g1 g2 b1 b2 ksi r1 a1
    const int* q = ix + e * 10;                              // P1 Q1 P2 Q2 ph1 v1 ph2 v2 a1col r1col
    double v1 = x[q[5]], v2 = x[q[7]], ph1 = x[q[4]], ph2 = x[q[6]];
    // a controlling transformer's ratio/shift are STATE variables — read them from x at their
    // column; otherwise the fixed pi-model value from the input pack (q[8]/q[9] are -1 then).
    double r1 = q[9] >= 0 ? x[q[9]] : p[6];
    double a1 = q[8] >= 0 ? x[q[8]] : p[7];
    double o[CB_NOUT];
    evalClosedBranch(p[0], p[1], p[2], p[3], p[4], v1, r1, v2, p[5], a1, ph1, ph2, 0,
                     o + 0, o + 1, o + 2, o + 3, o + 4, o + 5, o + 6, o + 7, o + 8, o + 9,
                     o + 10, o + 11, o + 12, o + 13, o + 14, o + 15, o + 16, o + 17, o + 18,
                     o + 19, o + 20, o + 21, o + 22, o + 23, o + 24, o + 25, o + 26, o + 27);
    scatterAdd(CB_NOUT, dest + e * CB_NOUT, o, vals, F);
}

__global__ void fillOpenBranch(int ne, const double* in, const int* ix, const int* dest,
                               const double* x, double* vals, double* F) {
    int e = blockIdx.x * blockDim.x + threadIdx.x;
    if (e >= ne) return;
    const double* p = in + e * 7;                            // y ksi g1 b1 g2 b2 r1
    const int* q = ix + e * 6;
    double v1 = q[4] >= 0 ? x[q[4]] : 1.0;                   // absent side: value discarded
    double v2 = q[5] >= 0 ? x[q[5]] : 1.0;                   // (its outputs have dest -1)
    double o[OB_NOUT];
    evalOpenBranch(p[0], p[1], p[2], p[3], p[4], p[5], v1, p[6], v2, 0,
                   o + 0, o + 1, o + 2, o + 3, o + 4, o + 5, o + 6, o + 7);
    scatterAdd(OB_NOUT, dest + e * OB_NOUT, o, vals, F);
}

__global__ void fillShunt(int ne, const double* in, const int* ix, const int* dest,
                          const double* x, double* vals, double* F) {
    int e = blockIdx.x * blockDim.x + threadIdx.x;
    if (e >= ne) return;
    const double* p = in + e * 2;                            // g b
    double v = x[ix[e * 3 + 2]];
    double o[SH_NOUT];
    evalShunt(v, p[0], p[1], 0, o + 0, o + 1, o + 2, o + 3, o + 4);
    scatterAdd(SH_NOUT, dest + e * SH_NOUT, o, vals, F);
}

__global__ void fillHvdc(int ne, const double* in, const int* ix, const int* dest,
                         const double* x, double* vals, double* F) {
    int e = blockIdx.x * blockDim.x + threadIdx.x;
    if (e >= ne) return;
    const double* p = in + e * 5;                            // p0 k lf1 lf2 r
    const int* q = ix + e * 4;
    double ph1 = x[q[2]], ph2 = x[q[3]];
    double o[HVDC_NOUT];
    evalHvdc(p[0], p[1], p[2], p[3], p[4], ph1, ph2, 0, o + 0, o + 1, o + 2, o + 3, o + 4, o + 5);
    scatterAdd(HVDC_NOUT, dest + e * HVDC_NOUT, o, vals, F);
}

// DISTR_Q (remote generator voltage control) reactive-distribution rows: one thread per weighted
// contribution. Each is a closed branch's side-s reactive flow (q1 or q2, value + the 5 derivatives
// dq/d{v1,v2,φ1,φ2,r1}) re-evaluated from the SAME closed-branch params/state and scattered, scaled
// by the controller weight, into a DISTR_Q row. dest[6] = {F-row, the 5 J slots} (built like buildDest;
// dq/dr1 slot is -1 when the branch derives no ratio). The DISTR_Q row is a power row (mode 1), so
// applyRowMode leaves it; the controllers' own per-bus Q scatter was suppressed (qRow=-1) on the host.
__global__ void fillDistrQ(int ndq, const int* dqElem, const int* dqSide, const int* dqKind,
                           const double* dqWeight, const int* dqDest,
                           const double* cbIn, const int* cbIx, const double* shIn, const int* shIx,
                           const double* obIn, const int* obIx,
                           const double* x, double* vals, double* F) {
    int c = blockIdx.x * blockDim.x + threadIdx.x;
    if (c >= ndq) return;
    int e = dqElem[c];
    double w = dqWeight[c];
    const int* dst = dqDest + c * 6;
    if (dqKind[c] == 1) {                                    // shunt q: {q value -> F, dq/dv -> J(row, vcol)}
        const double* sp = shIn + e * 2;                     // g b
        int vcol = shIx[e * 3 + 2];
        double so[SH_NOUT];
        evalShunt(x[vcol], sp[0], sp[1], 0, so + 0, so + 1, so + 2, so + 3, so + 4);
        if (dst[0] <= -2) atomicAdd(&F[-2 - dst[0]], w * so[2]);    // q
        if (dst[1] >= 0) atomicAdd(&vals[dst[1]], w * so[3]);       // dq/dv
        return;
    }
    if (dqKind[c] == 3) {                                    // zero-impedance DUMMY_Q: linear w·dummyQ term
        int col = e;                                         // dqElem holds the DUMMY_Q variable column directly
        if (dst[0] <= -2) atomicAdd(&F[-2 - dst[0]], w * x[col]);
        if (dst[1] >= 0) atomicAdd(&vals[dst[1]], w);        // d(w·dummyQ)/d(dummyQ) = w
        return;
    }
    if (dqKind[c] == 2) {                                    // half-open branch: connected-side q + dq/dv
        const double* op = obIn + e * 7;                     // y ksi g1 b1 g2 b2 r1
        const int* oq = obIx + e * 6;                        // P1 Q1 P2 Q2 v1col v2col
        int vcol = dqSide[c] == 0 ? oq[4] : oq[5];           // the controller's (connected) side V column
        double v1 = dqSide[c] == 0 ? x[vcol] : 1.0;
        double v2 = dqSide[c] == 1 ? x[vcol] : 1.0;          // absent side: value discarded
        double oo[OB_NOUT];
        evalOpenBranch(op[0], op[1], op[2], op[3], op[4], op[5], v1, op[6], v2, 0,
                       oo + 0, oo + 1, oo + 2, oo + 3, oo + 4, oo + 5, oo + 6, oo + 7);
        int qi = dqSide[c] == 0 ? 6 : 2;                     // side-1 q1[6]/dq1dv1[7], side-2 q2[2]/dq2dv2[3]
        if (dst[0] <= -2) atomicAdd(&F[-2 - dst[0]], w * oo[qi]);
        if (dst[1] >= 0) atomicAdd(&vals[dst[1]], w * oo[qi + 1]);
        return;
    }
    const double* p = cbIn + e * 8;                          // y g1 g2 b1 b2 ksi r1 a1
    const int* q = cbIx + e * 10;                            // P1 Q1 P2 Q2 ph1 v1 ph2 v2 a1col r1col
    double v1 = x[q[5]], v2 = x[q[7]], ph1 = x[q[4]], ph2 = x[q[6]];
    double r1 = q[9] >= 0 ? x[q[9]] : p[6];
    double a1 = q[8] >= 0 ? x[q[8]] : p[7];
    double o[CB_NOUT];
    evalClosedBranch(p[0], p[1], p[2], p[3], p[4], v1, r1, v2, p[5], a1, ph1, ph2, 0,
                     o + 0, o + 1, o + 2, o + 3, o + 4, o + 5, o + 6, o + 7, o + 8, o + 9,
                     o + 10, o + 11, o + 12, o + 13, o + 14, o + 15, o + 16, o + 17, o + 18,
                     o + 19, o + 20, o + 21, o + 22, o + 23, o + 24, o + 25, o + 26, o + 27);
    // side 1 q outputs: q1[7] dq1dv1[8] dq1dv2[9] dq1dph1[10] dq1dph2[11] dq1dr1[13];  side 2: +14 (dr1 +14)
    int qi = dqSide[c] == 0 ? 7 : 21;
    double out6[6] = {o[qi], o[qi + 1], o[qi + 2], o[qi + 3], o[qi + 4], o[qi + 6]};
    for (int m = 0; m < 6; m++) {
        int d = dst[m];
        double val = w * out6[m];
        if (d >= 0) {
            atomicAdd(&vals[d], val);
        } else if (d <= -2) {
            atomicAdd(&F[-2 - d], val);
        }
    }
}

// Scatter one (dest, value): dest >= 0 -> J slot, dest <= -2 -> F row, -1 -> skip.
__device__ inline void addAt(int dest, double val, double* vals, double* F) {
    if (dest >= 0) {
        atomicAdd(&vals[dest], val);
    } else if (dest <= -2) {
        atomicAdd(&F[-2 - dest], val);
    }
}

// Zero-impedance branch (bus coupler): a LINEAR family. ix (NOT row-mapped — it carries the variable
// COLUMNS the kernel reads; the mapped rows live in dest): dpCol dqCol v1 v2 ph1 ph2 tree at ix[4..10].
// dest[14] (built host-side through the CSR pattern): the dummy ±1 couplings into both buses' P/Q
// balances [0..7], then, for a spanning-tree edge, ZERO_V (v1−rho·v2) on the DUMMY_P row and ZERO_PHI
// (ph1−ph2) on the DUMMY_Q row [8..13] (−1/skip on a non-tree edge, whose dummy rows are mode-0 identities
// the row-mode mask handles). The Jacobian entries are constants; only F depends on the state.
__global__ void fillZeroImp(int nzi, const double* in, const int* ix, const int* dest,
                            const double* x, double* vals, double* F) {
    int e = blockIdx.x * blockDim.x + threadIdx.x;
    if (e >= nzi) return;
    double rho = in[e];
    const int* q = ix + e * 11;
    double dummyP = x[q[4]], dummyQ = x[q[5]];
    const int* d = dest + e * 14;
    addAt(d[0], dummyP, vals, F);                            // F[bus1 P] += dummyP
    addAt(d[1], -dummyP, vals, F);                           // F[bus2 P] -= dummyP
    addAt(d[2], dummyQ, vals, F);                            // F[bus1 Q] += dummyQ
    addAt(d[3], -dummyQ, vals, F);                           // F[bus2 Q] -= dummyQ
    addAt(d[4], 1.0, vals, F);                               // J(bus1 P, dummyP)
    addAt(d[5], -1.0, vals, F);                              // J(bus2 P, dummyP)
    addAt(d[6], 1.0, vals, F);                               // J(bus1 Q, dummyQ)
    addAt(d[7], -1.0, vals, F);                              // J(bus2 Q, dummyQ)
    if (q[10]) {                                             // spanning-tree edge: voltage/angle coupling
        double v1 = x[q[6]], v2 = x[q[7]], ph1 = x[q[8]], ph2 = x[q[9]];
        addAt(d[8], v1 - rho * v2, vals, F);                 // F[ZERO_V row] += v1 - rho*v2
        addAt(d[9], 1.0, vals, F);                           // J(ZERO_V, v1)
        addAt(d[10], -rho, vals, F);                         // J(ZERO_V, v2)
        addAt(d[11], ph1 - ph2, vals, F);                    // F[ZERO_PHI row] += ph1 - ph2
        addAt(d[12], 1.0, vals, F);                          // J(ZERO_PHI, ph1)
        addAt(d[13], -1.0, vals, F);                         // J(ZERO_PHI, ph2)
    }
}

// DISTR_RHO (transformer ratio distribution): one thread per linear contribution. A controller's
// DISTR_RHO row = (1/count)·Σ r1 − r1_k, so each is coef·r1_j into the row's F + a constant coef in
// J(row, r1_j). dest[2] = {F row, J slot} (built host-side through the CSR pattern).
__global__ void fillDistrRho(int ndr, const int* drCol, const double* drCoef, const int* drDest,
                             const double* x, double* vals, double* F) {
    int c = blockIdx.x * blockDim.x + threadIdx.x;
    if (c >= ndr) return;
    double coef = drCoef[c];
    addAt(drDest[c * 2 + 0], coef * x[drCol[c]], vals, F);   // F[DISTR_RHO row] += coef * r1
    addAt(drDest[c * 2 + 1], coef, vals, F);                 // J(DISTR_RHO row, r1) += coef
}

// Slack/PV target identity rows, applied as a PER-SOLVE mask over the fixed superset
// pattern: a row in identity mode (mode 0) is zeroed, its diagonal set to 1, and its
// mismatch overwritten with x - target. The fill kernels may have scattered into it —
// this runs after them and overwrites, which is what keeps the structure (and cuDSS's
// analysis) immutable across PV->PQ activation switching.
__global__ void applyRowMode(int n, const int* mode, const int* rowPtr, const int* diagPos,
                             const double* x, const double* target, double* vals, double* F) {
    int r = blockIdx.x * blockDim.x + threadIdx.x;
    if (r >= n || mode[r] != 0) return;
    for (int k = rowPtr[r]; k < rowPtr[r + 1]; k++) {
        vals[k] = 0.0;
    }
    vals[diagPos[r]] = 1.0;
    F[r] = x[r] - target[r];                                 // row r is variable r's row
}

__global__ void initF(int n, const double* target, double* F) {
    int i = blockIdx.x * blockDim.x + threadIdx.x;
    if (i < n) F[i] = -target[i];                            // F = eval - target
}

__global__ void negateInto(int n, const double* F, double* b) {
    int i = blockIdx.x * blockDim.x + threadIdx.x;
    if (i < n) b[i] = -F[i];                                 // rhs of J dx = -F
}

__global__ void axpy1(int n, const double* dx, double* x) {
    int i = blockIdx.x * blockDim.x + threadIdx.x;
    if (i < n) x[i] += dx[i];
}

// Line-search backtrack: x = xPrev + mu*dx (mu in (0,1]; mu==1 reproduces the full step).
__global__ void stepFromPrev(int n, const double* xPrev, const double* dx, double mu, double* x) {
    int i = blockIdx.x * blockDim.x + threadIdx.x;
    if (i < n) x[i] = xPrev[i] + mu * dx[i];
}

inline int grid(int n) { return (n + BLOCK - 1) / BLOCK; }

// ---------------------------------------------------------------------------------
// The solver context: every device buffer + the cuDSS objects, freed by destructor.
// ---------------------------------------------------------------------------------

struct DeviceBuf {
    void* p = nullptr;
    ~DeviceBuf() { if (p) cudaFree(p); }
    template <typename T> T* as() { return static_cast<T*>(p); }
    void alloc(size_t bytes) { CUDA_CHECK(cudaMalloc(&p, bytes)); }
    void upload(const void* host, size_t bytes) {
        alloc(bytes);
        CUDA_CHECK(cudaMemcpy(p, host, bytes, cudaMemcpyHostToDevice));
    }
};

struct CudssCtx {
    cudssHandle_t handle = nullptr;
    cudssConfig_t config = nullptr;
    cudssData_t data = nullptr;
    cudssMatrix_t matA = nullptr, matB = nullptr, matX = nullptr;
    ~CudssCtx() {
        if (matA) cudssMatrixDestroy(matA);
        if (matB) cudssMatrixDestroy(matB);
        if (matX) cudssMatrixDestroy(matX);
        if (data && handle) cudssDataDestroy(handle, data);
        if (config) cudssConfigDestroy(config);
        if (handle) cudssDestroy(handle);
    }
};

struct Family {
    int ne = 0, instride = 0, ixstride = 0, nout = 0;
    DeviceBuf in, ix, dest;
};

// Build one family's device-side data (inputs, indices, role-folded destinations).
template <typename S, typename R>
void setupFamily(Family& f, int ne, const double* in, int instride, const int* idx,
                 int ixstride, const S* table, int nout, R resolve,
                 const CsrPattern& pos) {
    f.ne = ne; f.instride = instride; f.ixstride = ixstride; f.nout = nout;
    if (ne == 0) return;
    f.in.upload(in, static_cast<size_t>(ne) * instride * sizeof(double));
    f.ix.upload(idx, static_cast<size_t>(ne) * ixstride * sizeof(int));
    std::vector<int> dest = buildDest(ne, idx, ixstride, table, nout, resolve, pos);
    f.dest.upload(dest.data(), dest.size() * sizeof(int));
}

struct Result {
    std::vector<double> x;
    int iters = 0;
    double norm = 0.0;
};

// Row permutation: pair each equation row with its dominant-diagonal variable column —
// P rows with their own bus's PHI column (dP/dθ self term), Q rows with the bus's V
// column (dQ/dV), slack/PV identities with their column — and USE THE PAIRED COLUMN as
// the row index. The CSR diagonal is then structurally strong regardless of OLF's
// equation numbering: the plain creator happens to interleave P/Q/V per bus (fine), but
// the VECTORIZED creator numbers array equations in per-type blocks, whose leading
// minors defeat cuDSS's static pivoting (KLU pivots dynamically and never noticed).
// Choosing the ordering ourselves — a bus-ordered J — fixes any
// numbering. The mismatch/targets are permuted the same way; columns are untouched, so
// the solution dx needs no back-permutation.
std::vector<int> buildRowMap(int n, int ncb, const int* cbIdx, int nob, const int* obIdx,
                             int nsh, const int* shIdx, int nhv, const int* hvIdx,
                             int ndq, const int* dqRow, int nzi, const int* ziIdx) {
    std::vector<int> rowMap(n, -1);
    auto pairRc = [&](int row, int col) {
        if (row < 0 || col < 0) return;
        if (rowMap[row] == -1) {
            rowMap[row] = col;
        } else if (rowMap[row] != col) {
            throw std::runtime_error("conflicting diagonal pairing for equation row " + std::to_string(row));
        }
    };
    // A bus whose reactive balance was ROUTED ELSEWHERE (Qrow != its own V col) leaves that V col
    // carrying the v−target identity (PV / transformer / generator-pilot) or another power row that
    // no Q self-pair claims — claim it here. For an ordinary bus Qrow == V col, so this is a no-op.
    auto claimMovedV = [&](int qrow, int vcol) {
        if (vcol >= 0 && qrow != vcol) {
            pairRc(vcol, vcol);
        }
    };
    for (int e = 0; e < ncb; e++) {                          // P1 Q1 P2 Q2 ph1 v1 ph2 v2 a1col r1col
        const int* ix = cbIdx + e * 10;
        pairRc(ix[0], ix[4]);                                // P1 -> ph1
        pairRc(ix[2], ix[6]);                                // P2 -> ph2
        // Q rows pair with their own stable index — its V col on an ordinary bus (Qrow==v col), the
        // controlling transformer's RHO col or a controller's DISTR_Q row on a voltage-controlled bus
        // (the extractor set Qrow to it). So the strong diagonal is dq/dv, dq/dr1 or the DISTR_Q diag.
        pairRc(ix[1], ix[1]);
        pairRc(ix[3], ix[3]);
        claimMovedV(ix[1], ix[5]);                           // side 1 moved-V identity
        claimMovedV(ix[3], ix[7]);                           // side 2 moved-V identity
        if (ix[9] >= 0) {
            // claim the rho row: its diagonal is the rho col either way — dq/dr1 when the ratio is a
            // free control, or the unit derivative of the r1−target identity when it is held constant.
            pairRc(ix[9], ix[9]);
        }
    }
    for (int e = 0; e < nob; e++) {                          // P1 Q1 P2 Q2 v1 v2 (no ph cols:
        const int* ix = obIdx + e * 6;                       // P rows pair via closed branches)
        pairRc(ix[1], ix[1]);                                // Q rows self-pair (v col, or rho col if controlled)
        pairRc(ix[3], ix[3]);
        claimMovedV(ix[1], ix[4]);
        claimMovedV(ix[3], ix[5]);
    }
    for (int e = 0; e < nsh; e++) {                          // P Q v
        pairRc(shIdx[e * 3 + 1], shIdx[e * 3 + 1]);          // Q self-pairs (v col, or rho col if controlled)
        claimMovedV(shIdx[e * 3 + 1], shIdx[e * 3 + 2]);
    }
    for (int e = 0; e < nhv; e++) {                          // P1 P2 ph1 ph2
        const int* ix = hvIdx + e * 4;
        pairRc(ix[0], ix[2]);
        pairRc(ix[1], ix[3]);
    }
    // DISTR_Q reactive-distribution rows sit on a controller's BUS_V row and self-pair: their strong
    // diagonal is that controller's own weighted dq/dV (qPct_k·∂q_Gk/∂V_Gk − ∂q_Gk/∂V_Gk). Each
    // distinct dqRow appears for several contributions; pairRc is idempotent so re-pairing is fine.
    for (int c = 0; c < ndq; c++) {
        pairRc(dqRow[c], dqRow[c]);
    }
    // Zero-impedance branch: claim its DUMMY_P / DUMMY_Q rows (ZERO_V/ZERO_PHI or DUMMY_TARGET identities)
    // and, defensively, its bus P/Q rows (a coupler-only bus has no closed/open branch to claim them).
    for (int e = 0; e < nzi; e++) {
        const int* ix = ziIdx + e * 11;                      // b1P b1Q b2P b2Q dpRow dqRow ...
        for (int s = 0; s < 6; s++) {
            pairRc(ix[s], ix[s]);
        }
    }
    std::vector<char> colUsed(n, 0);
    for (int r = 0; r < n; r++) {
        if (rowMap[r] < 0) {                                 // every index in [0,n) is an active equation
            throw std::runtime_error("equation row " + std::to_string(r) + " has no diagonal pairing");
        }
        if (colUsed[rowMap[r]]) {
            throw std::runtime_error("diagonal pairing is not a bijection (column "
                                     + std::to_string(rowMap[r]) + " claimed twice)");
        }
        colUsed[rowMap[r]] = 1;
    }
    return rowMap;
}

// The DISTR_Q / DISTR_RHO / zero-impedance family contributions to the CSR pattern (the rows they add
// variable columns to). Shared by createBatchContext and computePattern so the chunk-size estimate sees the
// SAME nnz the real context builds. cbIdx/obIdx/shIdx are the row-mapped packs; only the family ROWS are
// taken through rowMap here, the contributor columns (v/ph/dummy/r1) are variable columns left as-is.
void collectFamilyPattern(const std::vector<int>& rowMap,
                          const int* cbIdx, const int* obIdx, const int* shIdx,
                          int ndq, const int* dqElem, const int* dqSide, const int* dqKind, const int* dqRow,
                          int ndr, const int* drRow, const int* drCol,
                          int nzi, const int* ziIdx,
                          std::vector<std::pair<int, int>>& pat) {
    for (int c = 0; c < ndq; c++) {
        int mappedRow = rowMap[dqRow[c]];
        if (dqKind[c] == 1) {                                // shunt q: bus V column
            pat.emplace_back(mappedRow, shIdx[static_cast<size_t>(dqElem[c]) * 3 + 2]);
        } else if (dqKind[c] == 2) {                         // half-open: connected-side V column
            const int* oq = obIdx + static_cast<size_t>(dqElem[c]) * 6;
            pat.emplace_back(mappedRow, dqSide[c] == 0 ? oq[4] : oq[5]);
        } else if (dqKind[c] == 3) {                         // zero-imp DUMMY_Q column
            pat.emplace_back(mappedRow, dqElem[c]);
        } else {                                             // closed branch: v1 v2 ph1 ph2 r1
            const int* q = cbIdx + static_cast<size_t>(dqElem[c]) * 10;
            pat.emplace_back(mappedRow, q[5]);
            pat.emplace_back(mappedRow, q[7]);
            pat.emplace_back(mappedRow, q[4]);
            pat.emplace_back(mappedRow, q[6]);
            if (q[9] >= 0) pat.emplace_back(mappedRow, q[9]);
        }
    }
    for (int c = 0; c < ndr; c++) {                          // DISTR_RHO: controller r1 column in a DISTR_RHO row
        pat.emplace_back(rowMap[drRow[c]], drCol[c]);
    }
    for (int e = 0; e < nzi; e++) {                          // zero-impedance: dummy couplings + (tree) ZERO_V/PHI
        const int* z = ziIdx + static_cast<size_t>(e) * 11;
        int b1P = rowMap[z[0]], b2P = rowMap[z[2]];
        int b1Q = z[1] >= 0 ? rowMap[z[1]] : -1, b2Q = z[3] >= 0 ? rowMap[z[3]] : -1;
        pat.emplace_back(b1P, z[4]);
        pat.emplace_back(b2P, z[4]);
        if (b1Q >= 0) pat.emplace_back(b1Q, z[5]);
        if (b2Q >= 0) pat.emplace_back(b2Q, z[5]);
        if (z[10]) {
            int dpRow = rowMap[z[4]], dqRow2 = rowMap[z[5]];
            pat.emplace_back(dpRow, z[6]);
            pat.emplace_back(dpRow, z[7]);
            pat.emplace_back(dqRow2, z[8]);
            pat.emplace_back(dqRow2, z[9]);
        }
    }
}

// Jacobian CSR pattern for this structure (the diagonal-paired pattern + the guaranteed
// diagonal), used to size GPU batch chunks. Mirrors the pattern build in createBatchContext
// but allocates nothing on the device.
struct PatternInfo {
    int nnz = 0;
    std::vector<int> rowPtr;
    std::vector<int> colIdx;
};

PatternInfo computePattern(int n, int ncb, const int* cbIdx, int nob, const int* obIdx,
                           int nsh, const int* shIdx, int nhv, const int* hvIdx,
                           int ndq, const int* dqElem, const int* dqSide, const int* dqKind, const int* dqRow,
                           int ndr, const int* drRow, const int* drCol, int nzi, const int* ziIdx) {
    std::vector<int> rowMap = buildRowMap(n, ncb, cbIdx, nob, obIdx, nsh, shIdx, nhv, hvIdx, ndq, dqRow, nzi, ziIdx);
    auto mapRows = [&](const int* idx, int ne, int stride, std::initializer_list<int> rowSlots) {
        std::vector<int> out(idx, idx + static_cast<size_t>(ne) * stride);
        for (int e = 0; e < ne; e++) {
            for (int s : rowSlots) {
                int& r = out[static_cast<size_t>(e) * stride + s];
                if (r >= 0) {
                    r = rowMap[r];
                }
            }
        }
        return out;
    };
    std::vector<int> cbIdxM = mapRows(cbIdx, ncb, 10, {0, 1, 2, 3});
    std::vector<int> obIdxM = mapRows(obIdx, nob, 6, {0, 1, 2, 3});
    std::vector<int> shIdxM = mapRows(shIdx, nsh, 3, {0, 1});
    std::vector<int> hvIdxM = mapRows(hvIdx, nhv, 4, {0, 1});
    std::vector<std::pair<int, int>> pat;
    collectPattern(ncb, cbIdxM.data(), 10, CB_SCATTER, CB_NOUT, resolveCb, pat);
    collectPattern(nob, obIdxM.data(), 6, OB_SCATTER, OB_NOUT, resolveOb, pat);
    collectPattern(nsh, shIdxM.data(), 3, SH_SCATTER, SH_NOUT, resolveSh, pat);
    collectPattern(nhv, hvIdxM.data(), 4, HVDC_SCATTER, HVDC_NOUT, resolveHv, pat);
    collectFamilyPattern(rowMap, cbIdxM.data(), obIdxM.data(), shIdxM.data(),
                         ndq, dqElem, dqSide, dqKind, dqRow, ndr, drRow, drCol, nzi, ziIdx, pat);
    for (int r = 0; r < n; r++) {
        pat.emplace_back(r, r);
    }
    std::sort(pat.begin(), pat.end());
    pat.erase(std::unique(pat.begin(), pat.end()), pat.end());

    PatternInfo info;
    info.nnz = static_cast<int>(pat.size());
    info.rowPtr.assign(n + 1, 0);
    info.colIdx.resize(info.nnz);
    for (int i = 0; i < info.nnz; i++) {
        info.colIdx[i] = pat[i].second;
        info.rowPtr[pat[i].first + 1]++;
    }
    for (int r = 0; r < n; r++) {
        info.rowPtr[r + 1] += info.rowPtr[r];
    }
    return info;
}

constexpr double LU_FILL = 12.0;     // heuristic LU-fill multiplier over nnz (fallback when LU_NNZ is unavailable)

// Per-scenario bytes the LU numeric factors occupy. The exact fill-in is only known after a
// symbolic ANALYSIS, so run a CHEAP one on a single matrix with the shared pattern and read
// cuDSS's reported CUDSS_DATA_LU_NNZ. If that is unavailable (older cuDSS, query failure),
// fall back to the LU_FILL heuristic. The LU index structure is shared across the uniform
// batch; only the luNnz numeric values are per-scenario.
double luBytesPerScenario(int n, const PatternInfo& pat) {
#if OLF_GPU_CUDSS_LU_NNZ
    try {
        DeviceBuf dRowPtr;
        DeviceBuf dColIdx;
        DeviceBuf dVals;
        DeviceBuf dB;
        DeviceBuf dX;
        dRowPtr.upload(pat.rowPtr.data(), (n + 1) * sizeof(int));
        dColIdx.upload(pat.colIdx.data(), pat.nnz * sizeof(int));
        dVals.alloc(pat.nnz * sizeof(double));               // ANALYSIS is symbolic — values unused
        CUDA_CHECK(cudaMemset(dVals.p, 0, pat.nnz * sizeof(double)));
        dB.alloc(n * sizeof(double));
        dX.alloc(n * sizeof(double));
        CudssCtx ds;
        CUDSS_CHECK(cudssCreate(&ds.handle));
        CUDSS_CHECK(cudssConfigCreate(&ds.config));
        CUDSS_CHECK(cudssDataCreate(ds.handle, &ds.data));
        CUDSS_CHECK(cudssMatrixCreateCsr(&ds.matA, n, n, pat.nnz, dRowPtr.as<int>(), nullptr,
                                         dColIdx.as<int>(), dVals.as<double>(), OLF_CUDSS_CSR_TYPES,
                                         CUDSS_MTYPE_GENERAL, CUDSS_MVIEW_FULL, CUDSS_BASE_ZERO));
        CUDSS_CHECK(cudssMatrixCreateDn(&ds.matB, n, 1, n, dB.as<double>(), OLF_CUDSS_DN_TYPE, CUDSS_LAYOUT_COL_MAJOR));
        CUDSS_CHECK(cudssMatrixCreateDn(&ds.matX, n, 1, n, dX.as<double>(), OLF_CUDSS_DN_TYPE, CUDSS_LAYOUT_COL_MAJOR));
        CUDSS_CHECK(cudssExecute(ds.handle, CUDSS_PHASE_ANALYSIS, ds.config, ds.data, ds.matA, ds.matX, ds.matB));
        int64_t luNnz = 0;
        size_t written = 0;
        CUDSS_CHECK(cudssDataGet(ds.handle, ds.data, CUDSS_DATA_LU_NNZ, &luNnz, sizeof(luNnz), &written));
        if (luNnz > 0) {
            return static_cast<double>(luNnz) * sizeof(double);
        }
    } catch (...) {
        // fall through to the heuristic
    }
#endif
    return LU_FILL * static_cast<double>(pat.nnz) * sizeof(double);
}


// ---------------------------------------------------------------------------------
// The CACHED solver context: everything that depends only on the STRUCTURE (the
// index packs) — the row permutation, the CSR pattern, the destination arrays, the
// device buffers, the cuDSS objects and its ANALYSIS — built once per structure and
// reused across solver runs (outer loops re-run the solver many times with only
// targets / state changing). Per solve, only x0 and the targets cross JNI; cuDSS
// keeps its symbolic factorization and REFACTORIZES on the in-place-filled values.
// Java owns the lifetime (GpuNewtonRaphson caches the handle, a Cleaner frees it).
// ---------------------------------------------------------------------------------

struct SolverContext {
    int n = 0;
    int nnz = 0;
    std::vector<int> rowMap;                                 // eq row -> paired variable col
    std::vector<double> hF;                                  // host mismatch buffer
    Family cb, ob, sh, hv;
    int ndq = 0;                                             // DISTR_Q weighted reactive contributions
    DeviceBuf dDqElem, dDqSide, dDqKind, dDqWeight, dDqDest; // ndq each (dDqDest is ndq*6)
    int nzi = 0;                                             // zero-impedance branches
    DeviceBuf dZiIn, dZiIdx, dZiDest;                        // nzi (dZiIn) / nzi*11 (dZiIdx) / nzi*14 (dZiDest)
    int ndr = 0;                                             // DISTR_RHO linear contributions
    DeviceBuf dDrCol, dDrCoef, dDrDest;                      // ndr each (dDrDest is ndr*2)
    DeviceBuf dX, dTarget, dF, dB, dDx, dRowPtr, dColIdx, dVals, dDiagPos, dMode;
    DeviceBuf dXprev;                                        // state before the last full step (line search)
    CudssCtx ds;
    bool analyzed = false;                                   // ANALYSIS+first FACTORIZATION done
};

SolverContext* createContext(int n,
                             int ncb, const double* cbIn, const int* cbIdx,
                             int nob, const double* obIn, const int* obIdx,
                             int nsh, const double* shIn, const int* shIdx,
                             int nhv, const double* hvIn, const int* hvIdx,
                             int ndq, const int* dqElem, const int* dqSide, const double* dqWeight,
                             const int* dqRow, const int* dqKind,
                             int nzi, const double* ziIn, const int* ziIdx,
                             int ndr, const int* drRow, const int* drCol, const double* drCoef) {
    auto ctx = std::make_unique<SolverContext>();
    ctx->n = n;
    ctx->hF.resize(n);

    // ---- diagonal-pairing row permutation + index packs rewritten through it ----
    ctx->rowMap = buildRowMap(n, ncb, cbIdx, nob, obIdx, nsh, shIdx, nhv, hvIdx, ndq, dqRow, nzi, ziIdx);
    const std::vector<int>& rowMap = ctx->rowMap;
    auto mapRows = [&](const int* idx, int ne, int stride, std::initializer_list<int> rowSlots) {
        std::vector<int> out(idx, idx + static_cast<size_t>(ne) * stride);
        for (int e = 0; e < ne; e++) {
            for (int s : rowSlots) {
                int& r = out[static_cast<size_t>(e) * stride + s];
                if (r >= 0) {
                    r = rowMap[r];
                }
            }
        }
        return out;
    };
    std::vector<int> cbIdxM = mapRows(cbIdx, ncb, 10, {0, 1, 2, 3});
    std::vector<int> obIdxM = mapRows(obIdx, nob, 6, {0, 1, 2, 3});
    std::vector<int> shIdxM = mapRows(shIdx, nsh, 3, {0, 1});
    std::vector<int> hvIdxM = mapRows(hvIdx, nhv, 4, {0, 1});
    cbIdx = cbIdxM.data();
    obIdx = obIdxM.data();
    shIdx = shIdxM.data();
    hvIdx = hvIdxM.data();

    // ---- fixed CSR pattern (rows = permuted equations, cols = variables) ----
    std::vector<std::pair<int, int>> pat;
    collectPattern(ncb, cbIdx, 10, CB_SCATTER, CB_NOUT, resolveCb, pat);
    collectPattern(nob, obIdx, 6, OB_SCATTER, OB_NOUT, resolveOb, pat);
    collectPattern(nsh, shIdx, 3, SH_SCATTER, SH_NOUT, resolveSh, pat);
    collectPattern(nhv, hvIdx, 4, HVDC_SCATTER, HVDC_NOUT, resolveHv, pat);
    // DISTR_Q contributions: each adds, in its weighted reactive row, the branch's variable columns
    // (v1 v2 φ1 φ2, plus r1 when derived) — the same columns the closed-branch q-derivatives touch.
    for (int c = 0; c < ndq; c++) {
        int mappedRow = rowMap[dqRow[c]];
        if (dqKind[c] == 1) {                                // shunt q: only the bus V column
            pat.emplace_back(mappedRow, shIdx[static_cast<size_t>(dqElem[c]) * 3 + 2]);
            continue;
        }
        if (dqKind[c] == 2) {                                // half-open branch q: only its connected-side V column
            const int* oq = obIdx + static_cast<size_t>(dqElem[c]) * 6;
            pat.emplace_back(mappedRow, dqSide[c] == 0 ? oq[4] : oq[5]);
            continue;
        }
        if (dqKind[c] == 3) {                                // zero-impedance DUMMY_Q: the dummyQ variable column
            pat.emplace_back(mappedRow, dqElem[c]);
            continue;
        }
        const int* q = cbIdx + static_cast<size_t>(dqElem[c]) * 10;
        pat.emplace_back(mappedRow, q[5]);                   // v1
        pat.emplace_back(mappedRow, q[7]);                   // v2
        pat.emplace_back(mappedRow, q[4]);                   // ph1
        pat.emplace_back(mappedRow, q[6]);                   // ph2
        if (q[9] >= 0) {
            pat.emplace_back(mappedRow, q[9]);               // r1
        }
    }
    // Zero-impedance branches: the dummy ±1 couplings (dummyP col in both buses' P rows, dummyQ in Q rows)
    // and, for a tree edge, ZERO_V (v1,v2) on the DUMMY_P row + ZERO_PHI (ph1,ph2) on the DUMMY_Q row.
    for (int e = 0; e < nzi; e++) {
        const int* z = ziIdx + static_cast<size_t>(e) * 11;  // b1P b1Q b2P b2Q dpRow dqRow v1 v2 ph1 ph2 tree
        // A bus Q row is -1 when its reactive balance is suppressed (a DISTR_Q controller — the dummyQ then
        // joins that controller's DISTR_Q rows via kind 3 instead of its own Q balance); skip the coupling.
        int b1P = rowMap[z[0]], b2P = rowMap[z[2]];
        int b1Q = z[1] >= 0 ? rowMap[z[1]] : -1, b2Q = z[3] >= 0 ? rowMap[z[3]] : -1;
        pat.emplace_back(b1P, z[4]);                         // dummyP col in bus1 P
        pat.emplace_back(b2P, z[4]);                         // dummyP col in bus2 P
        if (b1Q >= 0) pat.emplace_back(b1Q, z[5]);           // dummyQ col in bus1 Q
        if (b2Q >= 0) pat.emplace_back(b2Q, z[5]);           // dummyQ col in bus2 Q
        if (z[10]) {
            int dpRow = rowMap[z[4]], dqRow2 = rowMap[z[5]];
            pat.emplace_back(dpRow, z[6]);                   // ZERO_V: v1
            pat.emplace_back(dpRow, z[7]);                   // ZERO_V: v2
            pat.emplace_back(dqRow2, z[8]);                  // ZERO_PHI: ph1
            pat.emplace_back(dqRow2, z[9]);                  // ZERO_PHI: ph2
        }
    }
    // DISTR_RHO contributions: each puts a controller's r1 column into a DISTR_RHO row.
    for (int c = 0; c < ndr; c++) {
        pat.emplace_back(rowMap[drRow[c]], drCol[c]);
    }
    for (int r = 0; r < n; r++) {
        pat.emplace_back(r, r);                              // guaranteed diagonal: the per-solve
    }                                                        // row-mode mask writes 1.0 there
    std::sort(pat.begin(), pat.end());
    pat.erase(std::unique(pat.begin(), pat.end()), pat.end());
    ctx->nnz = static_cast<int>(pat.size());
    if (ctx->nnz == 0) throw std::runtime_error("empty Jacobian pattern");

    std::vector<int> rowPtr(n + 1, 0), colIdx(ctx->nnz);
    for (int i = 0; i < ctx->nnz; i++) {                     // pat is row-major sorted
        colIdx[i] = pat[i].second;
        rowPtr[pat[i].first + 1]++;
    }
    for (int r = 0; r < n; r++) rowPtr[r + 1] += rowPtr[r];
    CsrPattern pos{rowPtr.data(), colIdx.data()};            // (row,col) -> slot by binary search

    // ---- device state (persists across solves) ----
    ctx->dX.alloc(n * sizeof(double));
    ctx->dTarget.alloc(n * sizeof(double));
    ctx->dF.alloc(n * sizeof(double));
    ctx->dB.alloc(n * sizeof(double));
    ctx->dDx.alloc(n * sizeof(double));
    ctx->dXprev.alloc(n * sizeof(double));
    ctx->dRowPtr.upload(rowPtr.data(), (n + 1) * sizeof(int));
    ctx->dColIdx.upload(colIdx.data(), ctx->nnz * sizeof(int));
    ctx->dVals.alloc(ctx->nnz * sizeof(double));

    setupFamily(ctx->cb, ncb, cbIn, 8, cbIdx, 10, CB_SCATTER, CB_NOUT, resolveCb, pos);
    setupFamily(ctx->ob, nob, obIn, 7, obIdx, 6, OB_SCATTER, OB_NOUT, resolveOb, pos);
    setupFamily(ctx->sh, nsh, shIn, 2, shIdx, 3, SH_SCATTER, SH_NOUT, resolveSh, pos);
    setupFamily(ctx->hv, nhv, hvIn, 5, hvIdx, 4, HVDC_SCATTER, HVDC_NOUT, resolveHv, pos);

    // ---- DISTR_Q destinations: per contribution {F-row, dq/dv1, dq/dv2, dq/dφ1, dq/dφ2, dq/dr1} ----
    ctx->ndq = ndq;
    if (ndq > 0) {
        std::vector<int> dqDest(static_cast<size_t>(ndq) * 6, -1);
        for (int c = 0; c < ndq; c++) {
            int mappedRow = rowMap[dqRow[c]];
            dqDest[c * 6 + 0] = -2 - mappedRow;              // q value -> F row
            if (dqKind[c] == 1) {                            // shunt: only dq/dv on the bus V column
                dqDest[c * 6 + 1] = pos.at(mappedRow, shIdx[static_cast<size_t>(dqElem[c]) * 3 + 2]);
                continue;
            }
            if (dqKind[c] == 2) {                            // half-open branch: only dq/dv on its connected-side V col
                const int* oq = obIdx + static_cast<size_t>(dqElem[c]) * 6;
                dqDest[c * 6 + 1] = pos.at(mappedRow, dqSide[c] == 0 ? oq[4] : oq[5]);
                continue;
            }
            if (dqKind[c] == 3) {                            // zero-impedance DUMMY_Q: J slot on the dummyQ column
                dqDest[c * 6 + 1] = pos.at(mappedRow, dqElem[c]);
                continue;
            }
            const int* q = cbIdx + static_cast<size_t>(dqElem[c]) * 10;
            dqDest[c * 6 + 1] = pos.at(mappedRow, q[5]);     // dq/dv1
            dqDest[c * 6 + 2] = pos.at(mappedRow, q[7]);     // dq/dv2
            dqDest[c * 6 + 3] = pos.at(mappedRow, q[4]);     // dq/dph1
            dqDest[c * 6 + 4] = pos.at(mappedRow, q[6]);     // dq/dph2
            dqDest[c * 6 + 5] = q[9] >= 0 ? pos.at(mappedRow, q[9]) : -1;   // dq/dr1
        }
        ctx->dDqElem.upload(dqElem, ndq * sizeof(int));
        ctx->dDqSide.upload(dqSide, ndq * sizeof(int));
        ctx->dDqKind.upload(dqKind, ndq * sizeof(int));
        ctx->dDqWeight.upload(dqWeight, ndq * sizeof(double));
        ctx->dDqDest.upload(dqDest.data(), dqDest.size() * sizeof(int));
    }

    // ---- zero-impedance destinations: per branch the 8 dummy couplings + (tree) the 6 ZERO_V/ZERO_PHI ----
    ctx->nzi = nzi;
    if (nzi > 0) {
        std::vector<int> ziDest(static_cast<size_t>(nzi) * 14, -1);
        for (int e = 0; e < nzi; e++) {
            const int* z = ziIdx + static_cast<size_t>(e) * 11;
            // -1 Q row = a DISTR_Q controller whose reactive balance is suppressed (its dummyQ joins the
            // DISTR_Q rows via kind 3, not its own Q balance) — skip the coupling there.
            int b1P = rowMap[z[0]], b2P = rowMap[z[2]];
            int b1Q = z[1] >= 0 ? rowMap[z[1]] : -1, b2Q = z[3] >= 0 ? rowMap[z[3]] : -1;
            int* dd = ziDest.data() + static_cast<size_t>(e) * 14;
            dd[0] = -2 - b1P;                                // F bus1 P (+dummyP)
            dd[1] = -2 - b2P;                                // F bus2 P (-dummyP)
            dd[2] = b1Q >= 0 ? -2 - b1Q : -1;               // F bus1 Q (+dummyQ), skip if suppressed
            dd[3] = b2Q >= 0 ? -2 - b2Q : -1;               // F bus2 Q (-dummyQ), skip if suppressed
            dd[4] = pos.at(b1P, z[4]);                       // J(bus1 P, dummyP)
            dd[5] = pos.at(b2P, z[4]);                       // J(bus2 P, dummyP)
            dd[6] = b1Q >= 0 ? pos.at(b1Q, z[5]) : -1;       // J(bus1 Q, dummyQ)
            dd[7] = b2Q >= 0 ? pos.at(b2Q, z[5]) : -1;       // J(bus2 Q, dummyQ)
            if (z[10]) {
                int dpRow = rowMap[z[4]], dqRow2 = rowMap[z[5]];
                dd[8] = -2 - dpRow;                          // F ZERO_V row
                dd[9] = pos.at(dpRow, z[6]);                 // J(ZERO_V, v1)
                dd[10] = pos.at(dpRow, z[7]);                // J(ZERO_V, v2)
                dd[11] = -2 - dqRow2;                        // F ZERO_PHI row
                dd[12] = pos.at(dqRow2, z[8]);               // J(ZERO_PHI, ph1)
                dd[13] = pos.at(dqRow2, z[9]);               // J(ZERO_PHI, ph2)
            }
        }
        ctx->dZiIn.upload(ziIn, nzi * sizeof(double));
        ctx->dZiIdx.upload(ziIdx, static_cast<size_t>(nzi) * 11 * sizeof(int));
        ctx->dZiDest.upload(ziDest.data(), ziDest.size() * sizeof(int));
    }

    // ---- DISTR_RHO destinations: per contribution {F row, J slot (row, r1 col)} ----
    ctx->ndr = ndr;
    if (ndr > 0) {
        std::vector<int> drDest(static_cast<size_t>(ndr) * 2);
        for (int c = 0; c < ndr; c++) {
            int mappedRow = rowMap[drRow[c]];
            drDest[c * 2 + 0] = -2 - mappedRow;
            drDest[c * 2 + 1] = pos.at(mappedRow, drCol[c]);
        }
        ctx->dDrCol.upload(drCol, ndr * sizeof(int));
        ctx->dDrCoef.upload(drCoef, ndr * sizeof(double));
        ctx->dDrDest.upload(drDest.data(), drDest.size() * sizeof(int));
    }

    std::vector<int> diagPos(n);
    for (int r = 0; r < n; r++) diagPos[r] = pos.at(r, r);
    ctx->dDiagPos.upload(diagPos.data(), n * sizeof(int));
    ctx->dMode.alloc(n * sizeof(int));

    // ---- cuDSS objects: J as true CSR; kernels fill d_ax in place; ANALYSIS lazy ----
    CUDSS_CHECK(cudssCreate(&ctx->ds.handle));
    CUDSS_CHECK(cudssConfigCreate(&ctx->ds.config));
    int ir = irSteps();
    CUDSS_CHECK(cudssConfigSet(ctx->ds.config, CUDSS_CONFIG_IR_N_STEPS, &ir, sizeof(ir)));
    CUDSS_CHECK(cudssDataCreate(ctx->ds.handle, &ctx->ds.data));
    CUDSS_CHECK(cudssMatrixCreateCsr(&ctx->ds.matA, n, n, ctx->nnz,
                                     ctx->dRowPtr.as<int>(), nullptr, ctx->dColIdx.as<int>(), ctx->dVals.as<double>(),
                                     OLF_CUDSS_CSR_TYPES,
                                     CUDSS_MTYPE_GENERAL, CUDSS_MVIEW_FULL, CUDSS_BASE_ZERO));
    CUDSS_CHECK(cudssMatrixCreateDn(&ctx->ds.matB, n, 1, n, ctx->dB.as<double>(), OLF_CUDSS_DN_TYPE, CUDSS_LAYOUT_COL_MAJOR));
    CUDSS_CHECK(cudssMatrixCreateDn(&ctx->ds.matX, n, 1, n, ctx->dDx.as<double>(), OLF_CUDSS_DN_TYPE, CUDSS_LAYOUT_COL_MAJOR));
    return ctx.release();
}

Result solveContext(SolverContext* ctx, const double* x0, const double* target, const int* rowMode,
                    int maxIter, double tol, int lsMode, int lsMaxIter, double lsStepFold,
                    double maxDv, double maxDphi, const int* varType) {
    int n = ctx->n;
    CUDA_CHECK(cudaMemcpy(ctx->dX.p, x0, n * sizeof(double), cudaMemcpyHostToDevice));
    CUDA_CHECK(cudaMemcpy(ctx->dTarget.p, target, n * sizeof(double), cudaMemcpyHostToDevice));
    CUDA_CHECK(cudaMemcpy(ctx->dMode.p, rowMode, n * sizeof(int), cudaMemcpyHostToDevice));

    // Fill J values + mismatch F at the current dX, read F back, and return its infnorm (for the
    // convergence test). Also sets curNorm2 to the EUCLIDEAN 2-norm — the quantity OLF's line search
    // compares (DefaultNewtonRaphsonStoppingCriteria uses Vectors.norm2): the Newton direction is a
    // descent direction for ‖F‖₂ but NOT for ‖F‖∞, so backtracking must judge on the 2-norm or it
    // stalls. Both norms are set to HUGE_VAL when any mismatch entry is non-finite.
    double curNorm2 = 0.0;
    auto fillAndNorm = [&]() -> double {
        CUDA_CHECK(cudaMemset(ctx->dVals.p, 0, ctx->nnz * sizeof(double)));
        initF<<<grid(n), BLOCK>>>(n, ctx->dTarget.as<double>(), ctx->dF.as<double>());
        if (ctx->cb.ne) fillClosedBranch<<<grid(ctx->cb.ne), BLOCK>>>(ctx->cb.ne, ctx->cb.in.as<double>(), ctx->cb.ix.as<int>(), ctx->cb.dest.as<int>(), ctx->dX.as<double>(), ctx->dVals.as<double>(), ctx->dF.as<double>());
        if (ctx->ob.ne) fillOpenBranch<<<grid(ctx->ob.ne), BLOCK>>>(ctx->ob.ne, ctx->ob.in.as<double>(), ctx->ob.ix.as<int>(), ctx->ob.dest.as<int>(), ctx->dX.as<double>(), ctx->dVals.as<double>(), ctx->dF.as<double>());
        if (ctx->sh.ne) fillShunt<<<grid(ctx->sh.ne), BLOCK>>>(ctx->sh.ne, ctx->sh.in.as<double>(), ctx->sh.ix.as<int>(), ctx->sh.dest.as<int>(), ctx->dX.as<double>(), ctx->dVals.as<double>(), ctx->dF.as<double>());
        if (ctx->hv.ne) fillHvdc<<<grid(ctx->hv.ne), BLOCK>>>(ctx->hv.ne, ctx->hv.in.as<double>(), ctx->hv.ix.as<int>(), ctx->hv.dest.as<int>(), ctx->dX.as<double>(), ctx->dVals.as<double>(), ctx->dF.as<double>());
        if (ctx->ndq) fillDistrQ<<<grid(ctx->ndq), BLOCK>>>(ctx->ndq, ctx->dDqElem.as<int>(), ctx->dDqSide.as<int>(), ctx->dDqKind.as<int>(), ctx->dDqWeight.as<double>(), ctx->dDqDest.as<int>(), ctx->cb.in.as<double>(), ctx->cb.ix.as<int>(), ctx->sh.in.as<double>(), ctx->sh.ix.as<int>(), ctx->ob.in.as<double>(), ctx->ob.ix.as<int>(), ctx->dX.as<double>(), ctx->dVals.as<double>(), ctx->dF.as<double>());
        if (ctx->nzi) fillZeroImp<<<grid(ctx->nzi), BLOCK>>>(ctx->nzi, ctx->dZiIn.as<double>(), ctx->dZiIdx.as<int>(), ctx->dZiDest.as<int>(), ctx->dX.as<double>(), ctx->dVals.as<double>(), ctx->dF.as<double>());
        if (ctx->ndr) fillDistrRho<<<grid(ctx->ndr), BLOCK>>>(ctx->ndr, ctx->dDrCol.as<int>(), ctx->dDrCoef.as<double>(), ctx->dDrDest.as<int>(), ctx->dX.as<double>(), ctx->dVals.as<double>(), ctx->dF.as<double>());
        applyRowMode<<<grid(n), BLOCK>>>(n, ctx->dMode.as<int>(), ctx->dRowPtr.as<int>(), ctx->dDiagPos.as<int>(), ctx->dX.as<double>(), ctx->dTarget.as<double>(), ctx->dVals.as<double>(), ctx->dF.as<double>());
        CUDA_CHECK(cudaDeviceSynchronize());
        CUDA_CHECK(cudaMemcpy(ctx->hF.data(), ctx->dF.p, n * sizeof(double), cudaMemcpyDeviceToHost));
        double infn = 0.0;
        double sumsq = 0.0;
        for (double v : ctx->hF) {
            if (!std::isfinite(v)) {
                curNorm2 = HUGE_VAL;
                return HUGE_VAL;
            }
            infn = std::max(infn, std::fabs(v));
            sumsq += v * v;
        }
        curNorm2 = std::sqrt(sumsq);
        return infn;
    };

    Result res;
    res.x.resize(n);
    std::vector<double> hDx(lsMode == 2 ? n : 0);            // host step buffer for MAX_VOLTAGE_CHANGE clamp
    double lastNorm2 = 0.0;
    bool haveLast = false;                                   // a full step was applied at the previous iteration
    int it = 0;
    for (; it <= maxIter; it++) {
        // fill J + F; res.norm is the infnorm (convergence test), norm2 the 2-norm (line search)
        res.norm = fillAndNorm();
        double norm2 = curNorm2;

        // Line search (LINE_SEARCH mode): the full step applied at it-1 may have RAISED ‖F‖₂;
        // backtrack x = xPrev + mu*dx with mu = stepFold^-k (k=1..lsMaxIter) until the 2-norm
        // improves, mirroring OLF's LineSearchStateVectorScaling. mu==1 (step already improved)
        // means no backtrack — the undamped path stays bit-identical. The re-fill leaves J at the
        // accepted x, so the next solve uses the correct Jacobian.
        if (lsMode == 1 && haveLast && norm2 >= lastNorm2) {
            for (int k = 1; k <= lsMaxIter && norm2 >= lastNorm2; k++) {
                double mu = 1.0 / std::pow(lsStepFold, k);
                stepFromPrev<<<grid(n), BLOCK>>>(n, ctx->dXprev.as<double>(), ctx->dDx.as<double>(), mu, ctx->dX.as<double>());
                CUDA_CHECK(cudaDeviceSynchronize());
                res.norm = fillAndNorm();
                norm2 = curNorm2;
            }
        }
        if (!std::isfinite(res.norm)) {
            throw std::runtime_error("mismatch diverged to NaN/Inf at iteration " + std::to_string(it));
        }
        lastNorm2 = norm2;
        haveLast = true;
        if (res.norm < tol || it == maxIter) break;

        // J dx = -F on the device (analyze ONCE per structure, refactorize after —
        // including across solver runs reusing this context)
        negateInto<<<grid(n), BLOCK>>>(n, ctx->dF.as<double>(), ctx->dB.as<double>());
        if (!ctx->analyzed) {
            CUDSS_CHECK(cudssExecute(ctx->ds.handle, CUDSS_PHASE_ANALYSIS, ctx->ds.config, ctx->ds.data, ctx->ds.matA, ctx->ds.matX, ctx->ds.matB));
            CUDSS_CHECK(cudssExecute(ctx->ds.handle, CUDSS_PHASE_FACTORIZATION, ctx->ds.config, ctx->ds.data, ctx->ds.matA, ctx->ds.matX, ctx->ds.matB));
            ctx->analyzed = true;
        } else {
            CUDSS_CHECK(cudssExecute(ctx->ds.handle, CUDSS_PHASE_REFACTORIZATION, ctx->ds.config, ctx->ds.data, ctx->ds.matA, ctx->ds.matX, ctx->ds.matB));
        }
        CUDSS_CHECK(cudssExecute(ctx->ds.handle, CUDSS_PHASE_SOLVE, ctx->ds.config, ctx->ds.data, ctx->ds.matA, ctx->ds.matX, ctx->ds.matB));
        CUDA_CHECK(cudaMemcpy(ctx->dXprev.p, ctx->dX.p, n * sizeof(double), cudaMemcpyDeviceToDevice));
        if (lsMode == 2) {
            // MAX_VOLTAGE_CHANGE: one global step factor so the largest |Δv| / |Δφ| stays within
            // maxDv / maxDphi (mirrors MaxVoltageChangeStateVectorScaling). Read dx back (same per-iter
            // cost as the mismatch readback) and reduce on the host; the whole step is then scaled —
            // including non-V/φ rows like BRANCH_RHO1 — via stepFromPrev(x = xPrev + s*dx).
            CUDA_CHECK(cudaMemcpy(hDx.data(), ctx->dDx.p, n * sizeof(double), cudaMemcpyDeviceToHost));
            double stepSize = 1.0;
            for (int r = 0; r < n; r++) {
                double a = std::fabs(hDx[r]);
                if (varType[r] == 1) {
                    if (a > maxDv) stepSize = std::min(stepSize, maxDv / a);
                } else if (varType[r] == 0) {
                    if (a > maxDphi) stepSize = std::min(stepSize, maxDphi / a);
                }
            }
            stepFromPrev<<<grid(n), BLOCK>>>(n, ctx->dXprev.as<double>(), ctx->dDx.as<double>(), stepSize, ctx->dX.as<double>());
        } else {
            axpy1<<<grid(n), BLOCK>>>(n, ctx->dDx.as<double>(), ctx->dX.as<double>());
        }
        CUDA_CHECK(cudaDeviceSynchronize());
    }
    res.iters = it;
    CUDA_CHECK(cudaMemcpy(res.x.data(), ctx->dX.p, n * sizeof(double), cudaMemcpyDeviceToHost));
    return res;
}

// VALUES-level refresh: re-upload only the per-element PARAMETER packs (cbIn/obIn/shIn/hvIn) into the
// cached context, in place. The CSR pattern, the diagonal pairing, the index/dest packs and cuDSS's
// symbolic ANALYSIS are UNCHANGED — they depend on the index packs and structure, not on these values
// (a transformer tap or shunt-b move changes r1/a1/b only). The fill kernels read in[] every iteration,
// so the next solveContext refactorizes on the fresh values. Element COUNTS must match the cached
// structure (the Java side only takes this path when the index packs are identical), so we copy into
// the EXISTING device buffers (no realloc — DeviceBuf::alloc would leak the old pointer).
void refreshContextValues(SolverContext* ctx, int ncb, const double* cbIn, int nob, const double* obIn,
                          int nsh, const double* shIn, int nhv, const double* hvIn) {
    auto refresh = [](Family& f, int ne, const double* in) {
        if (ne != f.ne) {
            throw std::runtime_error("refreshContextValues: element count changed (not a VALUES event)");
        }
        if (ne) {
            CUDA_CHECK(cudaMemcpy(f.in.p, in, static_cast<size_t>(ne) * f.instride * sizeof(double),
                                  cudaMemcpyHostToDevice));
        }
    };
    refresh(ctx->cb, ncb, cbIn);
    refresh(ctx->ob, nob, obIn);
    refresh(ctx->sh, nsh, shIn);
    refresh(ctx->hv, nhv, hvIn);
}

// =================================================================================
// BATCHED N-1 security analysis: many post-contingency scenarios on the SAME network
// structure, solved in one batched Newton-Raphson. The CSR pattern, the family index
// packs, the destination arrays and cuDSS's SYMBOLIC ANALYSIS are SHARED across all
// scenarios (built once); only the value/state/mismatch arrays gain a scenario
// dimension. Per-scenario element disablement (a branch outage) is a device mask the
// fill kernels honor (a disabled element simply does not scatter — its entries stay
// structurally present but numerically zero, and the diagonal-pairing guarantee keeps
// every row factorizable). Converged scenarios retire from the batch (an active mask),
// so the per-iteration cost shrinks as scenarios finish.
//
// cuDSS (OLF_GPU_BATCHED_CUDSS, default on for cuDSS >= 0.3.0): the UNIFORM BATCHED API —
// one batch CSR matrix whose scenarios share the rowPtr/colIdx pattern with per-scenario
// value sets, batched dense B/X. ANALYSIS runs ONCE on the shared pattern; each Newton
// iteration is ONE batched FACTORIZATION + ONE batched SOLVE over all scenarios. Retired
// scenarios (converged/diverged) are frozen to an identity system so a singular sub-block
// never fails the batch. On older cuDSS the fallback path repoints a single matrix and
// refactorizes+solves per scenario. Either way the symbolic analysis — the dominant
// per-case cost in sequential SA — is amortized over the entire contingency set.
// =================================================================================

// Batched fill kernels: thread = element x scenario. Skip if the scenario has retired
// (converged) or the element is outaged in that scenario. Values/mismatch are per-scenario.
__global__ void fillClosedBranchBatch(int ne, int nScenarios, int n, int nnz, int nElements, int elemOffset,
                                      const double* in, const int* ix, const int* dest,
                                      const double* x, double* vals, double* F,
                                      const char* disabled, const char* active) {
    int tid = blockIdx.x * blockDim.x + threadIdx.x;
    if (tid >= ne * nScenarios) return;
    int e = tid % ne;
    int s = tid / ne;
    if (!active[s] || disabled[s * nElements + elemOffset + e]) return;
    const double* p = in + e * 8;                            // y g1 g2 b1 b2 ksi r1 a1
    const int* q = ix + e * 10;                              // P1 Q1 P2 Q2 ph1 v1 ph2 v2 a1col r1col
    const double* xs = x + s * n;
    double v1 = xs[q[5]], v2 = xs[q[7]], ph1 = xs[q[4]], ph2 = xs[q[6]];
    double r1 = q[9] >= 0 ? xs[q[9]] : p[6];                 // ratio/shift from state when controlled
    double a1 = q[8] >= 0 ? xs[q[8]] : p[7];
    double o[CB_NOUT];
    evalClosedBranch(p[0], p[1], p[2], p[3], p[4], v1, r1, v2, p[5], a1, ph1, ph2, 0,
                     o + 0, o + 1, o + 2, o + 3, o + 4, o + 5, o + 6, o + 7, o + 8, o + 9,
                     o + 10, o + 11, o + 12, o + 13, o + 14, o + 15, o + 16, o + 17, o + 18,
                     o + 19, o + 20, o + 21, o + 22, o + 23, o + 24, o + 25, o + 26, o + 27);
    scatterAdd(CB_NOUT, dest + e * CB_NOUT, o, vals + (size_t) s * nnz, F + (size_t) s * n);
}

__global__ void fillOpenBranchBatch(int ne, int nScenarios, int n, int nnz, int nElements, int elemOffset,
                                    const double* in, const int* ix, const int* dest,
                                    const double* x, double* vals, double* F,
                                    const char* disabled, const char* active) {
    int tid = blockIdx.x * blockDim.x + threadIdx.x;
    if (tid >= ne * nScenarios) return;
    int e = tid % ne;
    int s = tid / ne;
    if (!active[s] || disabled[s * nElements + elemOffset + e]) return;
    const double* p = in + e * 7;                            // y ksi g1 b1 g2 b2 r1
    const int* q = ix + e * 6;
    const double* xs = x + s * n;
    double v1 = q[4] >= 0 ? xs[q[4]] : 1.0;
    double v2 = q[5] >= 0 ? xs[q[5]] : 1.0;
    double o[OB_NOUT];
    evalOpenBranch(p[0], p[1], p[2], p[3], p[4], p[5], v1, p[6], v2, 0,
                   o + 0, o + 1, o + 2, o + 3, o + 4, o + 5, o + 6, o + 7);
    scatterAdd(OB_NOUT, dest + e * OB_NOUT, o, vals + (size_t) s * nnz, F + (size_t) s * n);
}

__global__ void fillShuntBatch(int ne, int nScenarios, int n, int nnz, int nElements, int elemOffset,
                               const double* in, const int* ix, const int* dest,
                               const double* x, double* vals, double* F,
                               const char* disabled, const char* active) {
    int tid = blockIdx.x * blockDim.x + threadIdx.x;
    if (tid >= ne * nScenarios) return;
    int e = tid % ne;
    int s = tid / ne;
    if (!active[s] || disabled[s * nElements + elemOffset + e]) return;
    const double* p = in + e * 2;                            // g b
    double v = x[(size_t) s * n + ix[e * 3 + 2]];
    double o[SH_NOUT];
    evalShunt(v, p[0], p[1], 0, o + 0, o + 1, o + 2, o + 3, o + 4);
    scatterAdd(SH_NOUT, dest + e * SH_NOUT, o, vals + (size_t) s * nnz, F + (size_t) s * n);
}

__global__ void fillHvdcBatch(int ne, int nScenarios, int n, int nnz, int nElements, int elemOffset,
                              const double* in, const int* ix, const int* dest,
                              const double* x, double* vals, double* F,
                              const char* disabled, const char* active) {
    int tid = blockIdx.x * blockDim.x + threadIdx.x;
    if (tid >= ne * nScenarios) return;
    int e = tid % ne;
    int s = tid / ne;
    if (!active[s] || disabled[s * nElements + elemOffset + e]) return;
    const double* p = in + e * 5;                            // p0 k lf1 lf2 r
    const int* q = ix + e * 4;
    const double* xs = x + (size_t) s * n;
    double ph1 = xs[q[2]], ph2 = xs[q[3]];
    double o[HVDC_NOUT];
    evalHvdc(p[0], p[1], p[2], p[3], p[4], ph1, ph2, 0, o + 0, o + 1, o + 2, o + 3, o + 4, o + 5);
    scatterAdd(HVDC_NOUT, dest + e * HVDC_NOUT, o, vals + (size_t) s * nnz, F + (size_t) s * n);
}

// Batched DISTR_Q fill: one thread per (contribution, scenario). Mirrors the single-solve fillDistrQ but
// strides x/vals/F per scenario and DROPS a contribution whose underlying closed (kind 0) / open (kind 2)
// branch is outaged in this scenario — matching the CPU, which removes that branch's q from the controller's
// reactive sum (the controller weight qPercent is unchanged, so only the term vanishes). kinds 1 (shunt) and
// 3 (zero-imp dummy) cannot be a single-branch contingency in a qualified batched scenario, so no mask check.
__global__ void fillDistrQBatch(int ndq, int nScenarios, int n, int nnz, int nElements,
                                int cbOffset, int obOffset,
                                const int* dqElem, const int* dqSide, const int* dqKind,
                                const double* dqWeight, const int* dqDest,
                                const double* cbIn, const int* cbIx, const double* shIn, const int* shIx,
                                const double* obIn, const int* obIx,
                                const double* x, double* vals, double* F,
                                const char* disabled, const char* active) {
    int tid = blockIdx.x * blockDim.x + threadIdx.x;
    if (tid >= ndq * nScenarios) return;
    int c = tid % ndq;
    int s = tid / ndq;
    if (!active[s]) return;
    int e = dqElem[c];
    int kind = dqKind[c];
    if (kind == 0 && disabled[s * nElements + cbOffset + e]) return;   // outaged closed contributor
    if (kind == 2 && disabled[s * nElements + obOffset + e]) return;   // outaged open contributor
    double w = dqWeight[c];
    const int* dst = dqDest + c * 6;
    const double* xs = x + (size_t) s * n;
    double* valsS = vals + (size_t) s * nnz;
    double* FS = F + (size_t) s * n;

    if (kind == 1) {                             // shunt q: {q -> F, dq/dv -> J(row, vcol)}
        const double* sp = shIn + e * 2;
        int vcol = shIx[e * 3 + 2];
        double so[SH_NOUT];
        evalShunt(xs[vcol], sp[0], sp[1], 0, so + 0, so + 1, so + 2, so + 3, so + 4);
        if (dst[0] <= -2) atomicAdd(&FS[-2 - dst[0]], w * so[2]);
        if (dst[1] >= 0) atomicAdd(&valsS[dst[1]], w * so[3]);
        return;
    }
    if (kind == 3) {                             // zero-impedance DUMMY_Q: linear w*dummyQ
        if (dst[0] <= -2) atomicAdd(&FS[-2 - dst[0]], w * xs[e]);
        if (dst[1] >= 0) atomicAdd(&valsS[dst[1]], w);
        return;
    }
    if (kind == 2) {                             // half-open branch: connected-side q + dq/dv
        const double* op = obIn + e * 7;
        const int* oq = obIx + e * 6;
        int vcol = dqSide[c] == 0 ? oq[4] : oq[5];
        double v1 = dqSide[c] == 0 ? xs[vcol] : 1.0;
        double v2 = dqSide[c] == 1 ? xs[vcol] : 1.0;
        double oo[OB_NOUT];
        evalOpenBranch(op[0], op[1], op[2], op[3], op[4], op[5], v1, op[6], v2, 0,
                       oo + 0, oo + 1, oo + 2, oo + 3, oo + 4, oo + 5, oo + 6, oo + 7);
        int qi = dqSide[c] == 0 ? 6 : 2;
        if (dst[0] <= -2) atomicAdd(&FS[-2 - dst[0]], w * oo[qi]);
        if (dst[1] >= 0) atomicAdd(&valsS[dst[1]], w * oo[qi + 1]);
        return;
    }
    // kind 0: closed-branch q1/q2 + its derivatives
    const double* p = cbIn + e * 8;
    const int* q = cbIx + e * 10;
    double v1 = xs[q[5]], v2 = xs[q[7]], ph1 = xs[q[4]], ph2 = xs[q[6]];
    double r1 = q[9] >= 0 ? xs[q[9]] : p[6];
    double a1 = q[8] >= 0 ? xs[q[8]] : p[7];
    double o[CB_NOUT];
    evalClosedBranch(p[0], p[1], p[2], p[3], p[4], v1, r1, v2, p[5], a1, ph1, ph2, 0,
                     o + 0, o + 1, o + 2, o + 3, o + 4, o + 5, o + 6, o + 7, o + 8, o + 9,
                     o + 10, o + 11, o + 12, o + 13, o + 14, o + 15, o + 16, o + 17, o + 18,
                     o + 19, o + 20, o + 21, o + 22, o + 23, o + 24, o + 25, o + 26, o + 27);
    int qi = dqSide[c] == 0 ? 7 : 21;            // side-1 q1[7]..dq1dr1[13]; side-2 q2[21]..dq2dr1[27]
    double out6[6] = {o[qi], o[qi + 1], o[qi + 2], o[qi + 3], o[qi + 4], o[qi + 6]};
    for (int m = 0; m < 6; m++) {
        int d = dst[m];
        double val = w * out6[m];
        if (d >= 0) atomicAdd(&valsS[d], val);
        else if (d <= -2) atomicAdd(&FS[-2 - d], val);
    }
}

// Batched DISTR_RHO fill: one thread per (contribution, scenario). Linear coef*r1 into the controller's
// BRANCH_RHO1 row — strided per scenario, active-gated, no mask (controller-outage contingencies are
// rejected to the CPU because the 1/count coefficients reform).
__global__ void fillDistrRhoBatch(int ndr, int nScenarios, int n, int nnz,
                                  const int* drCol, const double* drCoef, const int* drDest,
                                  const double* x, double* vals, double* F, const char* active) {
    int tid = blockIdx.x * blockDim.x + threadIdx.x;
    if (tid >= ndr * nScenarios) return;
    int c = tid % ndr;
    int s = tid / ndr;
    if (!active[s]) return;
    double coef = drCoef[c];
    int dF = drDest[c * 2 + 0];
    int dJ = drDest[c * 2 + 1];
    if (dF <= -2) atomicAdd(&F[(size_t) s * n + (-2 - dF)], coef * x[(size_t) s * n + drCol[c]]);
    if (dJ >= 0) atomicAdd(&vals[(size_t) s * nnz + dJ], coef);
}

// Batched zero-impedance fill: one thread per (branch, scenario). Mirrors the single-solve fillZeroImp with
// per-scenario striding — DUMMY_P/Q ±1 couplings into both buses' P/Q balances and, on a spanning-tree edge,
// the ZERO_V (v1−rho·v2) / ZERO_PHI (ph1−ph2) coupling. Active-gated, no mask (bus-coupler outage is rejected).
__global__ void fillZeroImpBatch(int nzi, int nScenarios, int n, int nnz,
                                 const double* in, const int* ix, const int* dest,
                                 const double* x, double* vals, double* F, const char* active) {
    int tid = blockIdx.x * blockDim.x + threadIdx.x;
    if (tid >= nzi * nScenarios) return;
    int e = tid % nzi;
    int s = tid / nzi;
    if (!active[s]) return;
    double rho = in[e];
    const int* q = ix + e * 11;                              // b1P b1Q b2P b2Q dpCol dqCol v1 v2 ph1 ph2 tree
    const double* xs = x + (size_t) s * n;
    double* valsS = vals + (size_t) s * nnz;
    double* FS = F + (size_t) s * n;
    const int* d = dest + e * 14;
    double dummyP = xs[q[4]], dummyQ = xs[q[5]];
    addAt(d[0], dummyP, valsS, FS);                          // F[bus1 P] += dummyP
    addAt(d[1], -dummyP, valsS, FS);                         // F[bus2 P] -= dummyP
    addAt(d[2], dummyQ, valsS, FS);                          // F[bus1 Q] += dummyQ
    addAt(d[3], -dummyQ, valsS, FS);                         // F[bus2 Q] -= dummyQ
    addAt(d[4], 1.0, valsS, FS);                             // J(bus1 P, dummyP)
    addAt(d[5], -1.0, valsS, FS);                            // J(bus2 P, dummyP)
    addAt(d[6], 1.0, valsS, FS);                             // J(bus1 Q, dummyQ)
    addAt(d[7], -1.0, valsS, FS);                            // J(bus2 Q, dummyQ)
    if (q[10]) {                                             // spanning-tree edge: voltage/angle coupling
        double v1 = xs[q[6]], v2 = xs[q[7]], ph1 = xs[q[8]], ph2 = xs[q[9]];
        addAt(d[8], v1 - rho * v2, valsS, FS);               // F[ZERO_V row]
        addAt(d[9], 1.0, valsS, FS);                         // J(ZERO_V, v1)
        addAt(d[10], -rho, valsS, FS);                       // J(ZERO_V, v2)
        addAt(d[11], ph1 - ph2, valsS, FS);                  // F[ZERO_PHI row]
        addAt(d[12], 1.0, valsS, FS);                        // J(ZERO_PHI, ph1)
        addAt(d[13], -1.0, valsS, FS);                       // J(ZERO_PHI, ph2)
    }
}

// Per-scenario slack/PV identity rows (rowMode is per scenario: a contingency can change
// which buses are PV/slack). Same mask semantics as the single-solve applyRowMode.
__global__ void applyRowModeBatch(int n, int nScenarios, int nnz, const int* mode, const int* rowPtr,
                                  const int* diagPos, const double* x, const double* target,
                                  double* vals, double* F, const char* active) {
    int tid = blockIdx.x * blockDim.x + threadIdx.x;
    if (tid >= n * nScenarios) return;
    int r = tid % n;
    int s = tid / n;
    if (!active[s] || mode[(size_t) s * n + r] != 0) return;
    double* valsS = vals + (size_t) s * nnz;
    for (int k = rowPtr[r]; k < rowPtr[r + 1]; k++) {
        valsS[k] = 0.0;
    }
    valsS[diagPos[r]] = 1.0;
    F[(size_t) s * n + r] = x[(size_t) s * n + r] - target[(size_t) s * n + r];
}

__global__ void initFBatch(int n, int nScenarios, const double* target, double* F, const char* active) {
    int tid = blockIdx.x * blockDim.x + threadIdx.x;
    if (tid >= n * nScenarios) return;
    int s = tid / n;
    if (!active[s]) return;
    F[tid] = -target[tid];
}

// Seed the per-(controller, scenario) limitType from the BASE state, so a controller that OLF had already
// switched to PQ in the base case (packed by the extractor as a voltage-CAPABLE PQ bus, base row mode 1)
// starts pinned at the RIGHT limit and can be switched BACK to PV by a relieving contingency. A base-PV
// controller (mode 0) starts at limitType 0. For a base-PQ controller, v below its target ⇒ it is producing
// max reactive yet cannot reach target ⇒ maxQ-pinned (2); v above target ⇒ minQ-pinned (1). switchCount
// starts at 0 either way (a separate memset).
__global__ void initRlLimitTypeBatch(int nrl, int nScenarios, int n, const int* rlVrow,
                                     const double* rlVtarget, const int* baseMode, const double* x, char* limitType) {
    int tid = blockIdx.x * blockDim.x + threadIdx.x;
    if (tid >= nrl * nScenarios) return;
    int i = tid % nrl;
    int s = tid / nrl;
    int vrow = rlVrow[i];
    size_t idx = (size_t) s * n + vrow;
    size_t st = (size_t) s * nrl + i;
    // Read the BASE row mode (length n, contingency-free) — NOT the per-scenario mode: a controller flipped to
    // PQ by a per-scenario gen-loss OVERRIDE (its generator is gone) has base mode 0 and must get limitType 0 so
    // the reactive-limits switch-back leaves it PQ. Only a controller that is PQ in the BASE (an actual Q-limit
    // switch during the base loadflow) gets a pinned limit type so a relieving contingency can switch it back.
    if (baseMode[vrow] == 1) {                              // PQ in the base case (a real Q-limit switch)
        limitType[st] = (x[idx] < rlVtarget[i]) ? 2 : 1;    // v below target ⇒ maxQ pinned; above ⇒ minQ pinned
    } else {
        limitType[st] = 0;                                  // base PV (incl. a per-scenario gen-override flip)
    }
}

// Reactive-limits (PV↔PQ) pass: thread = (controller, scenario), state per (controller, scenario) in
// limitType (0 PV / 1 minQ / 2 maxQ) + switchCount (PV→PQ transitions, capped to block oscillation).
//  - PV (mode 0): generator q = (Σ branch q at the BUS_V row, in qBuf) + loadQ. If q < minQ−tol, pin to
//    minQ; if q > maxQ+tol, pin to maxQ → flip the row PV→PQ (mode 0→1, target = qLimit − loadQ), record
//    the limit type, increment the switch count.
//  - PQ (mode 1, pinned): if it has not yet switched MAX_SWITCH times, resume voltage control (PQ→PV) when
//    the voltage indicates the limit is no longer binding: a maxQ-pinned bus whose v rose ABOVE target, or
//    a minQ-pinned bus whose v fell BELOW target (mirrors ReactiveLimitsOuterLoop). Restore mode 1→0,
//    target → Vtarget. The count cap (matching OLF's maxPqPvSwitch) bounds oscillation.
// Either switch marks the scenario for another inner Newton solve.
__global__ void reactiveLimitsBatch(int nrl, int nScenarios, int n, double tol, int maxSwitch,
                                    const int* rlVrow, const double* rlMinQ, const double* rlMaxQ,
                                    const double* rlLoadQ, const double* rlVtarget, const double* qBuf,
                                    const double* x, const char* rlActive, int* mode, double* target,
                                    char* limitType, char* switchCount, char* switched) {
    int tid = blockIdx.x * blockDim.x + threadIdx.x;
    if (tid >= nrl * nScenarios) return;
    int i = tid % nrl;
    int s = tid / nrl;
    if (!rlActive[s]) return;
    int vrow = rlVrow[i];
    size_t idx = (size_t) s * n + vrow;
    size_t st = (size_t) s * nrl + i;
    if (mode[idx] == 0 && limitType[st] == 0) {             // currently PV — check the reactive limits
        double q = qBuf[idx] + rlLoadQ[i];
        if (q < rlMinQ[i] - tol) {
            mode[idx] = 1;
            target[idx] = rlMinQ[i] - rlLoadQ[i];
            limitType[st] = 1;
            switchCount[st]++;
            switched[s] = 1;
        } else if (q > rlMaxQ[i] + tol) {
            mode[idx] = 1;
            target[idx] = rlMaxQ[i] - rlLoadQ[i];
            limitType[st] = 2;
            switchCount[st]++;
            switched[s] = 1;
        }
    } else if (limitType[st] != 0 && switchCount[st] < maxSwitch) {   // pinned at a limit — check switchback
        double v = x[idx];
        bool back = (limitType[st] == 2 && v > rlVtarget[i])         // maxQ-pinned but v above target
                 || (limitType[st] == 1 && v < rlVtarget[i]);        // minQ-pinned but v below target
        if (back) {
            mode[idx] = 0;                                  // PQ → PV: resume voltage control
            target[idx] = rlVtarget[i];
            limitType[st] = 0;
            switched[s] = 1;
        }
    }
}

// Distributed-slack pass: thread = (participating bus, scenario). The slack mismatch is
// qBuf[slackPhiRow] (= Σ branch p at the slack) − slackTargetP. If it exceeds the tolerance, each
// participating bus takes factor·mismatch into its BUS_TARGET_P target (factor normalized to sum 1, so
// the whole mismatch is absorbed), mirroring ActivePowerDistribution (one-shot, no Pmin/Pmax saturation
// — exact vs OLF when no generator saturates). A distribution marks its scenario for another inner solve.
// Freeze, on the FIRST distribution pass of a solve, the active-power share the slack/reference bus must
// RETAIN when its own generators participate: retain = (1 − Σfactor)·mismatch0. Σfactor over the emitted
// (non-slack) buses is < 1 exactly when the slack participates (its factor sits in the normalization
// denominator but emits no row); 1 − Σfactor is the slack's participation fraction ρ. Without this the
// geometric outer loop would drive the slack mismatch to 0 and leak the slack's whole share to the
// non-slack buses. ρ = 0 (non-participating slack) ⇒ retain = 0 ⇒ the slack keeps nothing extra.
// The slack/reference bus retains ρ = (1 − Σfactor) of the TOTAL active imbalance currently being shared,
// where total = the slack's residual mismatch (qBuf at slackPhiRow − slackTargetP) PLUS what the non-slack
// participating buses have already absorbed (Σ of their cumulative target deltas). Re-evaluated EVERY outer
// pass: freezing ρ·mismatch0 at the first pass ignores the AC loss change during redistribution, so the
// slack would keep ρ·mismatch0 and the non-slack side would over-absorb by ρ·Δlosses; tracking the current
// total instead makes the non-slack buses absorb exactly (1 − ρ)·total_final, matching OLF's per-pass
// distribution of the full mismatch. ρ = 0 (non-participating slack, incl. all batched gen-loss scenarios)
// ⇒ retain = 0, so this is a no-op there.
__global__ void computeSlackRetain(int nScenarios, int n, int nds, int slackPhiRow, double slackTargetP,
                                   double oneMinusFactorSum, const int* dsPhiRow, const double* dsBaseTargetP,
                                   const double* target, const double* qBuf, const char* rlActive,
                                   double* retain) {
    int s = blockIdx.x * blockDim.x + threadIdx.x;
    if (s >= nScenarios) return;
    if (!rlActive[s]) return;
    double absorbed = 0.0;
    for (int i = 0; i < nds; i++) {
        absorbed += target[(size_t) s * n + dsPhiRow[i]] - dsBaseTargetP[i];
    }
    retain[s] = oneMinusFactorSum * ((qBuf[(size_t) s * n + slackPhiRow] - slackTargetP) + absorbed);
}

__global__ void distributeSlackBatch(int nds, int nScenarios, int n, double tol, int slackPhiRow,
                                     double slackTargetP, double factorSum, const int* dsPhiRow, const double* dsFactor,
                                     const double* dsBaseTargetP, const double* dsMaxDelta, const double* dsMinDelta,
                                     const double* dsRetain, const double* qBuf, const char* rlActive,
                                     const int* dsOvBus, const double* dsOvFactorDelta, const double* dsOvBaseDelta,
                                     const double* dsOvMaxDelta, const double* dsOvMinDelta,
                                     double* target, char* switched) {
    int tid = blockIdx.x * blockDim.x + threadIdx.x;
    if (tid >= nds * nScenarios) return;
    int i = tid % nds;
    int s = tid / nds;
    if (!rlActive[s]) return;
    // Distribute only the mismatch in EXCESS of the slack's frozen retained share — so a participating slack
    // keeps its part and the loop converges to OLF's allocation instead of leaking everything to the others.
    double distributable = (qBuf[(size_t) s * n + slackPhiRow] - slackTargetP) - dsRetain[s];
    if (distributable > tol || distributable < -tol) {
        // Per-scenario generator-loss reweight (dsOvBus[s] = the DS-pack index losing a participating gen, -1
        // if none): the lost gen drops out of the slack participation, so that bus's factor / base P / head-
        // room / footroom shrink by the gen's contribution, AND the total factor sum (the share denominator)
        // shrinks by the same factor delta — so the OTHER buses pick up the lost gen's share, matching OLF.
        double f = dsFactor[i], base = dsBaseTargetP[i], maxD = dsMaxDelta[i], minD = dsMinDelta[i];
        double fsum = factorSum;
        if (dsOvBus != nullptr && dsOvBus[s] >= 0) {
            fsum -= dsOvFactorDelta[s];                      // the lost gen left the participation
            if (dsOvBus[s] == i) {                           // ... and it sat on this bus
                f -= dsOvFactorDelta[s];
                base -= dsOvBaseDelta[s];
                maxD -= dsOvMaxDelta[s];
                minD -= dsOvMinDelta[s];
            }
        }
        // Clamp the bus's CUMULATIVE delta (current target − base) to [footroom, headroom]. An over-allocation
        // past a generator-saturation limit is clamped here, so its share stays unabsorbed and reappears at the
        // slack — redistributed to unsaturated buses next outer pass (∝ their factors), converging to OLF's
        // water-filling. Only flag a re-solve if the bus actually moved. fsum renormalizes the per-bus shares.
        size_t idx = (size_t) s * n + dsPhiRow[i];
        double cum = target[idx] - base;
        double clamped = fmin(fmax(cum + (f / fsum) * distributable, minD), maxD);
        if (clamped != cum) {
            target[idx] = base + clamped;
            switched[s] = 1;
        }
    }
}

// Distribute the slack mismatch by FULL WATER-FILLING per scenario (one thread = one scenario), matching OLF's
// ActivePowerDistribution.run + GenerationActivePowerDistributionStep.run: distribute the whole mismatch among
// the participating buses, re-normalizing the factors over the UNSATURATED buses each round and removing buses
// that hit a limit, iterating until the residual falls below residueEps (P_RESIDUE_EPS) or all buses saturate —
// all WITHOUT a power-flow re-solve. The previous per-(bus,scenario) kernel did ONE clamp step and let the
// saturated residual reappear at the slack to be redistributed on the NEXT outer pass (AFTER a re-solve), so
// the loss change between steps made it converge to a different in-tolerance point than OLF under saturation.
__global__ void distributeSlackWaterfillBatch(int nds, int nScenarios, int n, double slackDistTol, double residueEps,
                                              int slackPhiRow, double slackTargetP, const int* dsPhiRow,
                                              const double* dsFactor, const double* dsBaseTargetP,
                                              const double* dsMaxDelta, const double* dsMinDelta, const double* dsRetain,
                                              const double* qBuf, const char* rlActive,
                                              const int* dsOvBus, const double* dsOvFactorDelta, const double* dsOvBaseDelta,
                                              const double* dsOvMaxDelta, const double* dsOvMinDelta,
                                              double* target, char* switched) {
    int s = blockIdx.x * blockDim.x + threadIdx.x;
    if (s >= nScenarios) return;
    if (!rlActive[s]) return;
    // The participating buses share the mismatch in EXCESS of the slack's retained share (rem); only distribute
    // past the slackBusPMaxMismatch outer gate, exactly like OLF's DistributedSlackOuterLoop.check.
    double rem = (qBuf[(size_t) s * n + slackPhiRow] - slackTargetP) - dsRetain[s];
    if (fabs(rem) <= slackDistTol) {
        return;
    }
    int ov = (dsOvBus != nullptr) ? dsOvBus[s] : -1;        // the bus losing a participating gen (gen contingency), -1 = none
    bool changed = false;
    for (int iter = 0; iter < nds && fabs(rem) > residueEps; iter++) {
        double fsumActive = 0.0;                            // re-normalize factors over the still-unsaturated buses
        for (int i = 0; i < nds; i++) {
            double f = dsFactor[i], base = dsBaseTargetP[i], maxD = dsMaxDelta[i], minD = dsMinDelta[i];
            if (ov == i) { f -= dsOvFactorDelta[s]; base -= dsOvBaseDelta[s]; maxD -= dsOvMaxDelta[s]; minD -= dsOvMinDelta[s]; }
            double cum = target[(size_t) s * n + dsPhiRow[i]] - base;
            bool sat = (rem > 0) ? (cum >= maxD) : (cum <= minD);
            if (!sat) {
                fsumActive += f;
            }
        }
        if (fsumActive <= 0.0) {
            break;                                          // every participating bus saturated — residual stays at the slack
        }
        double done = 0.0;
        for (int i = 0; i < nds; i++) {
            double f = dsFactor[i], base = dsBaseTargetP[i], maxD = dsMaxDelta[i], minD = dsMinDelta[i];
            if (ov == i) { f -= dsOvFactorDelta[s]; base -= dsOvBaseDelta[s]; maxD -= dsOvMaxDelta[s]; minD -= dsOvMinDelta[s]; }
            size_t idx = (size_t) s * n + dsPhiRow[i];
            double cum = target[idx] - base;
            bool sat = (rem > 0) ? (cum >= maxD) : (cum <= minD);
            if (sat) {
                continue;
            }
            double newCum = fmin(fmax(cum + rem * (f / fsumActive), minD), maxD);
            if (newCum != cum) {
                target[idx] = base + newCum;
                done += newCum - cum;
                changed = true;
            }
        }
        rem -= done;
    }
    if (changed) {
        switched[s] = 1;
    }
}

__global__ void negateIntoOne(int n, const double* F, double* b) {       // single scenario slice
    int i = blockIdx.x * blockDim.x + threadIdx.x;
    if (i < n) b[i] = -F[i];
}

__global__ void axpy1One(int n, const double* dx, double* x) {           // single scenario slice
    int i = blockIdx.x * blockDim.x + threadIdx.x;
    if (i < n) x[i] += dx[i];
}

__global__ void negateIntoBatch(int total, const double* F, double* b) {  // b = -F over all scenarios
    int i = blockIdx.x * blockDim.x + threadIdx.x;
    if (i < total) b[i] = -F[i];
}

__global__ void axpy1Batch(int total, const double* dx, double* x) {      // x += dx over all scenarios
    int i = blockIdx.x * blockDim.x + threadIdx.x;
    if (i < total) x[i] += dx[i];
}

// Tile the base row (row 0, already in d[0..n)) across all nS scenarios: d[s*n+i] = d[i] for
// s in 1..nS-1. The base state/targets/row-mode are identical for every contingency, so the host
// uploads ONE n-row and this replicates it on-device — no 32x JNI marshalling / H2D of the base.
template <typename T>
__global__ void tileBaseRow(int total, int n, T* d) {
    int idx = blockIdx.x * blockDim.x + threadIdx.x;
    if (idx >= n && idx < total) {                       // rows 1..nS-1 copy from row 0; row 0 is the source
        d[idx] = d[idx % n];
    }
}

// Make a RETIRED scenario's system an identity (diagonal 1, mismatch 0) so it stays
// well-posed inside the uniform batched factorization (a singular sub-block would fail the
// whole batch) and its state is frozen (dx = 0). A scenario retired THIS iteration still
// holds its filled values, so the row is zeroed before the diagonal is set — this is
// correct whether the scenario was just filled or has been inactive (zero) for a while.
__global__ void writeIdentityForInactive(int n, int nScenarios, int nnz, const int* rowPtr,
                                         const int* diagPos, const char* active, double* vals, double* F) {
    int tid = blockIdx.x * blockDim.x + threadIdx.x;
    if (tid >= n * nScenarios) return;
    int r = tid % n;
    int s = tid / n;
    if (active[s]) return;
    double* valsS = vals + (size_t) s * nnz;
    for (int k = rowPtr[r]; k < rowPtr[r + 1]; k++) {
        valsS[k] = 0.0;
    }
    valsS[diagPos[r]] = 1.0;
    F[(size_t) s * n + r] = 0.0;
}

// The batched solver context: shared structure (pattern, families, analysis) + per-scenario
// value/state arrays. Mirrors SolverContext but scaled by nScenarios where a scenario differs.
struct BatchSolverContext {
    int n = 0;
    int nnz = 0;
    int nScenarios = 0;
    int nElements = 0;
    int cbOffset = 0, obOffset = 0, shOffset = 0, hvOffset = 0;   // element-mask offsets per family
    std::vector<int> rowMap;
    Family cb, ob, sh, hv;                                   // SHARED across scenarios (structure)
    // DISTR_Q (remote/distributed generator voltage control): weighted reactive-sum rows assembled from
    // closed/open-branch and shunt q contributions. SHARED across scenarios (the controller structure is
    // fixed); the per-scenario disable mask drops an outaged contributor's term (kinds 0/2 = closed/open).
    int ndq = 0;
    DeviceBuf dDqElem, dDqSide, dDqKind, dDqWeight, dDqDest;   // ndq, ndq, ndq, ndq, ndq*6
    // DISTR_RHO (transformer ratio distribution): linear coef*r1 rows. SHARED; no per-scenario mask — a
    // contingency that outages a controlling transformer is rejected to the CPU (the 1/count coefficients
    // would reform), so a qualified scenario never disables a contributor.
    int ndr = 0;
    DeviceBuf dDrCol, dDrCoef, dDrDest;                       // ndr, ndr, ndr*2
    // Zero-impedance branches (bus couplers): DUMMY_P/Q ±1 couplings + (tree edge) ZERO_V/ZERO_PHI. SHARED;
    // no per-scenario mask — a contingency that outages a zero-imp branch is auto-rejected (the element mapper
    // maps it to -1), so a qualified scenario never disables one and the fill just replicates the base.
    int nzi = 0;
    DeviceBuf dZiIn, dZiIdx, dZiDest;                         // nzi, nzi*11, nzi*14
    DeviceBuf dRowPtr, dColIdx, dDiagPos;                    // SHARED pattern
    DeviceBuf dX, dTarget, dF, dB, dDx, dVals, dMode;        // per-scenario (n or nnz strided)
    DeviceBuf dDisabled, dActive;                            // per-scenario masks
    // Reactive limits (full-loadflow PV→PQ outer loop): per local-PV controller bus, its (rowMap-mapped)
    // BUS_V row + reactive limits + load Q. dQbuf holds Σ branch q at every row (a fill into a zeroed
    // buffer); dRlActive marks which scenarios to RL-check this pass; dRlSwitched flags scenarios that switched.
    int nrl = 0;
    DeviceBuf dRlVrow, dRlMinQ, dRlMaxQ, dRlLoadQ, dRlVtarget;   // per controller (nrl)
    DeviceBuf dQbuf, dRlActive, dRlSwitched;               // dQbuf nS*n, dRlActive/dRlSwitched nS
    DeviceBuf dRlLimitType, dRlSwitchCount;               // per (controller, scenario): 0 PV / 1 minQ / 2 maxQ; PV→PQ count
    // Distributed slack: the (mapped) slack BUS_PHI row + its scheduled targetP give the per-scenario
    // mismatch (read from dQbuf); dDsPhiRow/dDsFactor are the participating buses' P-target rows + normalized
    // participation factors. Reuses dRlActive/dRlSwitched (same outer schedule as reactive limits).
    int nds = 0, dsSlackPhiRow = -1;
    double dsSlackTargetP = 0.0;
    DeviceBuf dDsPhiRow, dDsFactor;                        // per participating bus (nds)
    DeviceBuf dDsBaseTargetP, dDsMaxDelta, dDsMinDelta;   // per participating bus (nds): base P target + headroom/footroom
    double dsFactorSum = 1.0;                              // Σ emitted factors (< 1 ⇒ slack participates, keeps 1−Σ)
    DeviceBuf dDsRetain;                                   // per scenario (nS): frozen slack-retained mismatch share
    CudssCtx ds;
    bool analyzed = false;
    bool factored = false;                                   // first FACTORIZATION done (mode 2: then REFACTORIZE)
    int mode = 0;                                            // 0 = per-scenario loop, 1 = batched API, 2 = block-diagonal, 3 = UBATCH
    // Uniform batched cuDSS matrices (mode 1 only): A (CSR) shares the rowPtr/colIdx pattern
    // across all scenarios with per-scenario value pointers; B/X are batched dense (n x 1).
    // The host pointer + size arrays must outlive the matrix objects, so they live here.
    cudssMatrix_t matABatch = nullptr, matBBatch = nullptr, matXBatch = nullptr;
    std::vector<void*> valPtrs, rowStartPtrs, rowEndPtrs, colPtrs, bPtrs, xPtrs;   // host: arrays of device pointers
    DeviceBuf dValPtrs, dRowStartPtrs, dRowEndPtrs, dColPtrs, dBPtrs, dXPtrs;       // device copies (cuDSS 0.8 wants device arrays)
    std::vector<int> nrowsArr, ncolsArr, ncols1Arr, nnzArr, ldArr;                  // host size arrays: int32 + CUDSS_R_32I
    // Block-diagonal single matrix (mode 2 only): one (n*nS) CSR = blkdiag of all scenarios,
    // values = dVals (per-scenario contiguous), B/X = dB/dDx. Factorize once, REFACTORIZE after
    // (reuse pivots — the refactor-every strategy; refactor IS cheap on a single matrix, unlike the batch API).
    cudssMatrix_t matBig = nullptr, matBigB = nullptr, matBigX = nullptr;
    DeviceBuf dBigRowPtr, dBigColIdx;
    // UBATCH single matrices (mode 3 only): a SINGLE per-block CSR (n, nnz, shared rowPtr/colIdx)
    // whose value/RHS pointers are the batched buffers (dVals = nS*nnz, dB/dDx = nS*n); cuDSS
    // analyzes ONE block (cheap) and factorizes/solves all nS via CUDSS_CONFIG_UBATCH_SIZE.
    cudssMatrix_t matUb = nullptr, matUbB = nullptr, matUbX = nullptr;
    ~BatchSolverContext() {
        if (matABatch) cudssMatrixDestroy(matABatch);
        if (matBBatch) cudssMatrixDestroy(matBBatch);
        if (matXBatch) cudssMatrixDestroy(matXBatch);
        if (matBig) cudssMatrixDestroy(matBig);
        if (matBigB) cudssMatrixDestroy(matBigB);
        if (matBigX) cudssMatrixDestroy(matBigX);
        if (matUb) cudssMatrixDestroy(matUb);
        if (matUbB) cudssMatrixDestroy(matUbB);
        if (matUbX) cudssMatrixDestroy(matUbX);
    }
};

BatchSolverContext* createBatchContext(int n, int nScenarios, int mode,
                                       int ncb, const double* cbIn, const int* cbIdx,
                                       int nob, const double* obIn, const int* obIdx,
                                       int nsh, const double* shIn, const int* shIdx,
                                       int nhv, const double* hvIn, const int* hvIdx,
                                       int nrl, const int* rlVrow, const double* rlMinQ,
                                       const double* rlMaxQ, const double* rlLoadQ, const double* rlVtarget,
                                       int nds, const int* dsPhiRow, const double* dsFactor,
                                       int dsSlackPhiRow, double dsSlackTargetP,
                                       const double* dsBaseTargetP, const double* dsMaxDelta, const double* dsMinDelta,
                                       int ndq, const int* dqElem, const int* dqSide, const double* dqWeight,
                                       const int* dqRow, const int* dqKind,
                                       int ndr, const int* drRow, const int* drCol, const double* drCoef,
                                       int nzi, const double* ziIn, const int* ziIdx) {
    auto ctx = std::make_unique<BatchSolverContext>();
    ctx->n = n;
    ctx->nScenarios = nScenarios;
    ctx->mode = mode;
    ctx->nElements = ncb + nob + nsh + nhv;
    ctx->cbOffset = 0;
    ctx->obOffset = ncb;
    ctx->shOffset = ncb + nob;
    ctx->hvOffset = ncb + nob + nsh;

    // ---- diagonal-pairing row permutation (identical to the single-solve context) ----
    ctx->rowMap = buildRowMap(n, ncb, cbIdx, nob, obIdx, nsh, shIdx, nhv, hvIdx, ndq, dqRow, nzi, ziIdx);
    const std::vector<int>& rowMap = ctx->rowMap;
    auto mapRows = [&](const int* idx, int ne, int stride, std::initializer_list<int> rowSlots) {
        std::vector<int> out(idx, idx + static_cast<size_t>(ne) * stride);
        for (int e = 0; e < ne; e++) {
            for (int s : rowSlots) {
                int& r = out[static_cast<size_t>(e) * stride + s];
                if (r >= 0) {
                    r = rowMap[r];
                }
            }
        }
        return out;
    };
    std::vector<int> cbIdxM = mapRows(cbIdx, ncb, 10, {0, 1, 2, 3});
    std::vector<int> obIdxM = mapRows(obIdx, nob, 6, {0, 1, 2, 3});
    std::vector<int> shIdxM = mapRows(shIdx, nsh, 3, {0, 1});
    std::vector<int> hvIdxM = mapRows(hvIdx, nhv, 4, {0, 1});
    cbIdx = cbIdxM.data();
    obIdx = obIdxM.data();
    shIdx = shIdxM.data();
    hvIdx = hvIdxM.data();

    // ---- shared CSR pattern (rows = permuted equations, cols = variables) ----
    std::vector<std::pair<int, int>> pat;
    collectPattern(ncb, cbIdx, 10, CB_SCATTER, CB_NOUT, resolveCb, pat);
    collectPattern(nob, obIdx, 6, OB_SCATTER, OB_NOUT, resolveOb, pat);
    collectPattern(nsh, shIdx, 3, SH_SCATTER, SH_NOUT, resolveSh, pat);
    collectPattern(nhv, hvIdx, 4, HVDC_SCATTER, HVDC_NOUT, resolveHv, pat);
    // DISTR_Q / DISTR_RHO / zero-impedance family contributions (shared with computePattern's sizing).
    collectFamilyPattern(rowMap, cbIdx, obIdx, shIdx, ndq, dqElem, dqSide, dqKind, dqRow,
                         ndr, drRow, drCol, nzi, ziIdx, pat);
    for (int r = 0; r < n; r++) {
        pat.emplace_back(r, r);
    }
    std::sort(pat.begin(), pat.end());
    pat.erase(std::unique(pat.begin(), pat.end()), pat.end());
    ctx->nnz = static_cast<int>(pat.size());
    if (ctx->nnz == 0) throw std::runtime_error("empty Jacobian pattern");

    std::vector<int> rowPtr(n + 1, 0), colIdx(ctx->nnz);
    for (int i = 0; i < ctx->nnz; i++) {                     // pat is row-major sorted
        colIdx[i] = pat[i].second;
        rowPtr[pat[i].first + 1]++;
    }
    for (int r = 0; r < n; r++) rowPtr[r + 1] += rowPtr[r];
    CsrPattern pos{rowPtr.data(), colIdx.data()};            // (row,col) -> slot by binary search

    // ---- per-scenario device state ----
    size_t nS = static_cast<size_t>(n) * nScenarios;
    size_t nnzS = static_cast<size_t>(ctx->nnz) * nScenarios;
    ctx->dX.alloc(nS * sizeof(double));
    ctx->dTarget.alloc(nS * sizeof(double));
    ctx->dF.alloc(nS * sizeof(double));
    ctx->dB.alloc(nS * sizeof(double));
    ctx->dDx.alloc(nS * sizeof(double));
    ctx->dVals.alloc(nnzS * sizeof(double));
    ctx->dMode.alloc(nS * sizeof(int));
    ctx->dDisabled.alloc(static_cast<size_t>(ctx->nElements) * nScenarios * sizeof(char));
    ctx->dActive.alloc(static_cast<size_t>(nScenarios) * sizeof(char));

    // ---- shared pattern + family device data ----
    ctx->dRowPtr.upload(rowPtr.data(), (n + 1) * sizeof(int));
    ctx->dColIdx.upload(colIdx.data(), ctx->nnz * sizeof(int));
    setupFamily(ctx->cb, ncb, cbIn, 8, cbIdx, 10, CB_SCATTER, CB_NOUT, resolveCb, pos);
    setupFamily(ctx->ob, nob, obIn, 7, obIdx, 6, OB_SCATTER, OB_NOUT, resolveOb, pos);
    setupFamily(ctx->sh, nsh, shIn, 2, shIdx, 3, SH_SCATTER, SH_NOUT, resolveSh, pos);
    setupFamily(ctx->hv, nhv, hvIn, 5, hvIdx, 4, HVDC_SCATTER, HVDC_NOUT, resolveHv, pos);
    std::vector<int> diagPos(n);
    for (int r = 0; r < n; r++) diagPos[r] = pos.at(r, r);
    ctx->dDiagPos.upload(diagPos.data(), n * sizeof(int));

    // ---- DISTR_Q destinations: per contribution {F-row, dq/dv1, dq/dv2, dq/dph1, dq/dph2, dq/dr1}
    //      (mirrors the single-solve createContext; -2-row encodes an F row, >=0 a J slot, -1 skip) ----
    ctx->ndq = ndq;
    if (ndq > 0) {
        std::vector<int> dqDest(static_cast<size_t>(ndq) * 6, -1);
        for (int c = 0; c < ndq; c++) {
            int mappedRow = rowMap[dqRow[c]];
            dqDest[c * 6 + 0] = -2 - mappedRow;              // q value -> F row
            if (dqKind[c] == 1) {                            // shunt: dq/dv on the bus V column
                dqDest[c * 6 + 1] = pos.at(mappedRow, shIdx[static_cast<size_t>(dqElem[c]) * 3 + 2]);
            } else if (dqKind[c] == 2) {                     // half-open: dq/dv on the connected-side V col
                const int* oq = obIdx + static_cast<size_t>(dqElem[c]) * 6;
                dqDest[c * 6 + 1] = pos.at(mappedRow, dqSide[c] == 0 ? oq[4] : oq[5]);
            } else if (dqKind[c] == 3) {                     // zero-impedance: J slot on the dummyQ column
                dqDest[c * 6 + 1] = pos.at(mappedRow, dqElem[c]);
            } else {                                         // closed branch
                const int* q = cbIdx + static_cast<size_t>(dqElem[c]) * 10;
                dqDest[c * 6 + 1] = pos.at(mappedRow, q[5]);     // dq/dv1
                dqDest[c * 6 + 2] = pos.at(mappedRow, q[7]);     // dq/dv2
                dqDest[c * 6 + 3] = pos.at(mappedRow, q[4]);     // dq/dph1
                dqDest[c * 6 + 4] = pos.at(mappedRow, q[6]);     // dq/dph2
                dqDest[c * 6 + 5] = q[9] >= 0 ? pos.at(mappedRow, q[9]) : -1;  // dq/dr1
            }
        }
        ctx->dDqElem.upload(dqElem, ndq * sizeof(int));
        ctx->dDqSide.upload(dqSide, ndq * sizeof(int));
        ctx->dDqKind.upload(dqKind, ndq * sizeof(int));
        ctx->dDqWeight.upload(dqWeight, ndq * sizeof(double));
        ctx->dDqDest.upload(dqDest.data(), dqDest.size() * sizeof(int));
    }

    // ---- DISTR_RHO destinations: per contribution {F-row, J slot (row, r1 col)} ----
    ctx->ndr = ndr;
    if (ndr > 0) {
        std::vector<int> drDest(static_cast<size_t>(ndr) * 2);
        for (int c = 0; c < ndr; c++) {
            int mappedRow = rowMap[drRow[c]];
            drDest[c * 2 + 0] = -2 - mappedRow;              // DISTR_RHO F row
            drDest[c * 2 + 1] = pos.at(mappedRow, drCol[c]); // J slot
        }
        ctx->dDrCol.upload(drCol, ndr * sizeof(int));
        ctx->dDrCoef.upload(drCoef, ndr * sizeof(double));
        ctx->dDrDest.upload(drDest.data(), drDest.size() * sizeof(int));
    }

    // ---- zero-impedance destinations: per branch 8 dummy couplings + (tree) 6 ZERO_V/ZERO_PHI ----
    ctx->nzi = nzi;
    if (nzi > 0) {
        std::vector<int> ziDest(static_cast<size_t>(nzi) * 14, -1);
        for (int e = 0; e < nzi; e++) {
            const int* z = ziIdx + static_cast<size_t>(e) * 11;
            int b1P = rowMap[z[0]], b2P = rowMap[z[2]];
            int b1Q = z[1] >= 0 ? rowMap[z[1]] : -1, b2Q = z[3] >= 0 ? rowMap[z[3]] : -1;
            int* dd = ziDest.data() + static_cast<size_t>(e) * 14;
            dd[0] = -2 - b1P;                                // F bus1 P (+dummyP)
            dd[1] = -2 - b2P;                                // F bus2 P (-dummyP)
            dd[2] = b1Q >= 0 ? -2 - b1Q : -1;                // F bus1 Q (+dummyQ)
            dd[3] = b2Q >= 0 ? -2 - b2Q : -1;                // F bus2 Q (-dummyQ)
            dd[4] = pos.at(b1P, z[4]);                        // J(bus1 P, dummyP)
            dd[5] = pos.at(b2P, z[4]);                        // J(bus2 P, dummyP)
            dd[6] = b1Q >= 0 ? pos.at(b1Q, z[5]) : -1;        // J(bus1 Q, dummyQ)
            dd[7] = b2Q >= 0 ? pos.at(b2Q, z[5]) : -1;        // J(bus2 Q, dummyQ)
            if (z[10]) {                                     // spanning-tree edge
                int dpRow = rowMap[z[4]], dqRow2 = rowMap[z[5]];
                dd[8] = -2 - dpRow;                          // F ZERO_V row
                dd[9] = pos.at(dpRow, z[6]);                  // J(ZERO_V, v1)
                dd[10] = pos.at(dpRow, z[7]);                 // J(ZERO_V, v2)
                dd[11] = -2 - dqRow2;                        // F ZERO_PHI row
                dd[12] = pos.at(dqRow2, z[8]);                // J(ZERO_PHI, ph1)
                dd[13] = pos.at(dqRow2, z[9]);                // J(ZERO_PHI, ph2)
            }
        }
        ctx->dZiIn.upload(ziIn, nzi * sizeof(double));
        ctx->dZiIdx.upload(ziIdx, static_cast<size_t>(nzi) * 11 * sizeof(int));
        ctx->dZiDest.upload(ziDest.data(), ziDest.size() * sizeof(int));
    }

    // ---- reactive-limits pack: the controller BUS_V rows mapped through the diagonal-pairing
    //      permutation (= identity in the batch path, no families, but mapped for correctness) ----
    ctx->nrl = nrl;
    ctx->dQbuf.alloc(static_cast<size_t>(n) * nScenarios * sizeof(double));
    ctx->dRlActive.alloc(static_cast<size_t>(nScenarios) * sizeof(char));
    ctx->dRlSwitched.alloc(static_cast<size_t>(nScenarios) * sizeof(char));
    if (nrl > 0) {
        std::vector<int> rlVrowM(nrl);
        for (int i = 0; i < nrl; i++) {
            rlVrowM[i] = rowMap[rlVrow[i]];
        }
        ctx->dRlVrow.upload(rlVrowM.data(), nrl * sizeof(int));
        ctx->dRlMinQ.upload(rlMinQ, nrl * sizeof(double));
        ctx->dRlMaxQ.upload(rlMaxQ, nrl * sizeof(double));
        ctx->dRlLoadQ.upload(rlLoadQ, nrl * sizeof(double));
        ctx->dRlVtarget.upload(rlVtarget, nrl * sizeof(double));
        ctx->dRlLimitType.alloc(static_cast<size_t>(nrl) * nScenarios * sizeof(char));
        ctx->dRlSwitchCount.alloc(static_cast<size_t>(nrl) * nScenarios * sizeof(char));
    }
    ctx->nds = nds;
    ctx->dsSlackTargetP = dsSlackTargetP;
    ctx->dsSlackPhiRow = dsSlackPhiRow >= 0 ? rowMap[dsSlackPhiRow] : -1;
    if (nds > 0) {
        std::vector<int> dsPhiRowM(nds);
        for (int i = 0; i < nds; i++) {
            dsPhiRowM[i] = rowMap[dsPhiRow[i]];
        }
        ctx->dDsPhiRow.upload(dsPhiRowM.data(), nds * sizeof(int));
        ctx->dDsFactor.upload(dsFactor, nds * sizeof(double));
        ctx->dDsBaseTargetP.upload(dsBaseTargetP, nds * sizeof(double));
        ctx->dDsMaxDelta.upload(dsMaxDelta, nds * sizeof(double));
        ctx->dDsMinDelta.upload(dsMinDelta, nds * sizeof(double));
        double factorSum = 0.0;
        for (int i = 0; i < nds; i++) {
            factorSum += dsFactor[i];
        }
        ctx->dsFactorSum = factorSum > 0 ? factorSum : 1.0;
        ctx->dDsRetain.alloc(static_cast<size_t>(nScenarios) * sizeof(double));
    }

    // ---- cuDSS handle/config/data (shared by both solve paths) ----
    CUDSS_CHECK(cudssCreate(&ctx->ds.handle));
    CUDSS_CHECK(cudssConfigCreate(&ctx->ds.config));
    int ir = irSteps();
    CUDSS_CHECK(cudssConfigSet(ctx->ds.config, CUDSS_CONFIG_IR_N_STEPS, &ir, sizeof(ir)));
    CUDSS_CHECK(cudssDataCreate(ctx->ds.handle, &ctx->ds.data));

    if (mode == 1) {
    // ---- uniform batched matrices: one A (CSR) per scenario sharing the pattern, batched
    //      dense B/X. The host pointer + size arrays persist in the context. ----
        int* rowPtrDev = ctx->dRowPtr.as<int>();
        int* colDev = ctx->dColIdx.as<int>();
        double* valsDev = ctx->dVals.as<double>();
        double* bDev = ctx->dB.as<double>();
        double* xDev = ctx->dDx.as<double>();
        ctx->valPtrs.resize(nScenarios);
        ctx->rowStartPtrs.resize(nScenarios);
        ctx->rowEndPtrs.resize(nScenarios);
        ctx->colPtrs.resize(nScenarios);
        ctx->bPtrs.resize(nScenarios);
        ctx->xPtrs.resize(nScenarios);
        ctx->nrowsArr.assign(nScenarios, n);
        ctx->ncolsArr.assign(nScenarios, n);
        ctx->ncols1Arr.assign(nScenarios, 1);
        ctx->nnzArr.assign(nScenarios, ctx->nnz);
        ctx->ldArr.assign(nScenarios, n);
        for (int s = 0; s < nScenarios; s++) {
            ctx->valPtrs[s] = valsDev + (size_t) s * ctx->nnz;
            ctx->rowStartPtrs[s] = rowPtrDev;                // shared CSR row offsets
            ctx->rowEndPtrs[s] = rowPtrDev + 1;              // rowEnd = rowStart + 1
            ctx->colPtrs[s] = colDev;                        // shared column indices
            ctx->bPtrs[s] = bDev + (size_t) s * n;
            ctx->xPtrs[s] = xDev + (size_t) s * n;
        }
        // cuDSS 0.8 batch API wants the pointer arrays in DEVICE memory (arrays of device
        // pointers); the size arrays stay on the host. Upload the six host pointer arrays.
        size_t ptrBytes = static_cast<size_t>(nScenarios) * sizeof(void*);
        ctx->dValPtrs.upload(ctx->valPtrs.data(), ptrBytes);
        ctx->dRowStartPtrs.upload(ctx->rowStartPtrs.data(), ptrBytes);
        ctx->dRowEndPtrs.upload(ctx->rowEndPtrs.data(), ptrBytes);
        ctx->dColPtrs.upload(ctx->colPtrs.data(), ptrBytes);
        ctx->dBPtrs.upload(ctx->bPtrs.data(), ptrBytes);
        ctx->dXPtrs.upload(ctx->xPtrs.data(), ptrBytes);
        CUDSS_CHECK(cudssMatrixCreateBatchCsr(&ctx->matABatch, nScenarios,
                ctx->nrowsArr.data(), ctx->ncolsArr.data(), ctx->nnzArr.data(),
                ctx->dRowStartPtrs.as<void*>(), nullptr, ctx->dColPtrs.as<void*>(), ctx->dValPtrs.as<void*>(),
                OLF_CUDSS_CSR_TYPES,
                CUDSS_MTYPE_GENERAL, CUDSS_MVIEW_FULL, CUDSS_BASE_ZERO));
        CUDSS_CHECK(cudssMatrixCreateBatchDn(&ctx->matBBatch, nScenarios,
                ctx->nrowsArr.data(), ctx->ncols1Arr.data(), ctx->ldArr.data(),
                ctx->dBPtrs.as<void*>(), OLF_CUDSS_BDN_TYPES, CUDSS_LAYOUT_COL_MAJOR));
        CUDSS_CHECK(cudssMatrixCreateBatchDn(&ctx->matXBatch, nScenarios,
                ctx->nrowsArr.data(), ctx->ncols1Arr.data(), ctx->ldArr.data(),
                ctx->dXPtrs.as<void*>(), OLF_CUDSS_BDN_TYPES, CUDSS_LAYOUT_COL_MAJOR));
    } else if (mode == 2) {
    // ---- block-diagonal single matrix: one (n*nS) CSR = blkdiag(J_1..J_nS). Its values array
    //      IS dVals (per-scenario nnz contiguous); we only build the big rowPtr/colIdx (per-block
    //      pattern with column offsets s*n). One ANALYZE+FACTORIZE, then REFACTORIZE every solve. ----
    {
        long bigN = (long) n * nScenarios;
        long bigNnz = (long) ctx->nnz * nScenarios;
        std::vector<int> bigRowPtr(bigN + 1), bigColIdx(bigNnz);
        bigRowPtr[0] = 0;
        for (int s = 0; s < nScenarios; s++) {
            for (int r = 0; r < n; r++) {
                long gRow = (long) s * n + r;
                bigRowPtr[gRow + 1] = bigRowPtr[gRow] + (rowPtr[r + 1] - rowPtr[r]);
                for (int k = rowPtr[r]; k < rowPtr[r + 1]; k++) {
                    bigColIdx[(long) s * ctx->nnz + k] = colIdx[k] + s * n;   // shift cols into block s
                }
            }
        }
        ctx->dBigRowPtr.upload(bigRowPtr.data(), (bigN + 1) * sizeof(int));
        ctx->dBigColIdx.upload(bigColIdx.data(), bigNnz * sizeof(int));
        CUDSS_CHECK(cudssMatrixCreateCsr(&ctx->matBig, bigN, bigN, bigNnz,
                ctx->dBigRowPtr.as<int>(), nullptr, ctx->dBigColIdx.as<int>(), ctx->dVals.as<double>(),
                OLF_CUDSS_CSR_TYPES,
                CUDSS_MTYPE_GENERAL, CUDSS_MVIEW_FULL, CUDSS_BASE_ZERO));
        CUDSS_CHECK(cudssMatrixCreateDn(&ctx->matBigB, bigN, 1, bigN, ctx->dB.as<double>(), OLF_CUDSS_DN_TYPE, CUDSS_LAYOUT_COL_MAJOR));
        CUDSS_CHECK(cudssMatrixCreateDn(&ctx->matBigX, bigN, 1, bigN, ctx->dDx.as<double>(), OLF_CUDSS_DN_TYPE, CUDSS_LAYOUT_COL_MAJOR));
    }
    } else if (mode == 3 || mode == 4 || mode == 5) {
    // ---- UBATCH: ONE per-block CSR (n, nnz, shared rowPtr/colIdx) with batched value/RHS pointers;
    //      cuDSS analyzes a single block (cheap) and factorizes/solves all nS via UBATCH_SIZE.
    //      Modes 4/5 share this exact setup and differ only in the per-iteration factorize policy
    //      (mode 4 = factor at chunk iter 0 only; mode 5 = factor once + refactorize after). ----
    {
        int ub = nScenarios;
        CUDSS_CHECK(cudssConfigSet(ctx->ds.config, CUDSS_CONFIG_UBATCH_SIZE, &ub, sizeof(ub)));
        CUDSS_CHECK(cudssMatrixCreateCsr(&ctx->matUb, n, n, ctx->nnz,
                ctx->dRowPtr.as<int>(), nullptr, ctx->dColIdx.as<int>(), ctx->dVals.as<double>(),
                OLF_CUDSS_CSR_TYPES,
                CUDSS_MTYPE_GENERAL, CUDSS_MVIEW_FULL, CUDSS_BASE_ZERO));
        CUDSS_CHECK(cudssMatrixCreateDn(&ctx->matUbB, n, 1, n, ctx->dB.as<double>(), OLF_CUDSS_DN_TYPE, CUDSS_LAYOUT_COL_MAJOR));
        CUDSS_CHECK(cudssMatrixCreateDn(&ctx->matUbX, n, 1, n, ctx->dDx.as<double>(), OLF_CUDSS_DN_TYPE, CUDSS_LAYOUT_COL_MAJOR));
    }
    } else {
    // ---- fallback: a single matrix repointed per scenario inside the solve loop ----
    CUDSS_CHECK(cudssMatrixCreateCsr(&ctx->ds.matA, n, n, ctx->nnz,
                                     ctx->dRowPtr.as<int>(), nullptr, ctx->dColIdx.as<int>(), ctx->dVals.as<double>(),
                                     OLF_CUDSS_CSR_TYPES,
                                     CUDSS_MTYPE_GENERAL, CUDSS_MVIEW_FULL, CUDSS_BASE_ZERO));
    CUDSS_CHECK(cudssMatrixCreateDn(&ctx->ds.matB, n, 1, n, ctx->dB.as<double>(), OLF_CUDSS_DN_TYPE, CUDSS_LAYOUT_COL_MAJOR));
    CUDSS_CHECK(cudssMatrixCreateDn(&ctx->ds.matX, n, 1, n, ctx->dDx.as<double>(), OLF_CUDSS_DN_TYPE, CUDSS_LAYOUT_COL_MAJOR));
    }
    return ctx.release();
}

struct BatchResult {
    std::vector<double> x;                                   // nScenarios * n
    std::vector<int> iters;                                  // nScenarios
    std::vector<double> norm;                                // nScenarios
    std::vector<int> status;                                 // nScenarios: 0 conv, 1 maxiter, -1 diverged
};

// Per-scenario target overrides: each entry writes target[ovIndex[k]] = ovValue[k], where ovIndex is the
// FLAT dTarget index (scenarioSlot*n + row). Used by generator contingencies to set a scenario's bus P/Q
// target (remove the lost generator's P, set BUS_TARGET_Q on a PV→PQ flip) after the base row is tiled.
__global__ void applyTargetOverrideBatch(int nOv, const int* ovIndex, const double* ovValue, double* target) {
    int k = blockIdx.x * blockDim.x + threadIdx.x;
    if (k < nOv) {
        target[ovIndex[k]] = ovValue[k];
    }
}

BatchResult solveBatchContext(BatchSolverContext* ctx, const double* x0, const double* target,
                              const int* rowMode, const int* baseRowMode, const char* disabled,
                              int nOv, const int* ovIndex, const double* ovValue,
                              const int* dsOvBus, const double* dsOvFactorDelta, const double* dsOvBaseDelta,
                              const double* dsOvMaxDelta, const double* dsOvMinDelta, int maxIter, double tol,
                              double reactiveLimitTol, double slackDistTol, double convEpsPerEq) {
    int n = ctx->n;
    int nnz = ctx->nnz;
    int nS = ctx->nScenarios;
    size_t nTot = static_cast<size_t>(n) * nS;
    DeviceBuf dOvIndex, dOvValue;                            // function-scoped: outlive the async override kernel
    DeviceBuf dBaseMode;                                     // the contingency-free base row mode (length n)
    // Per-scenario distributed-slack reweight (generator loss at a DS-participant bus): one affected DS bus per
    // scenario (dsOvBus[s], -1 if none) whose factor/base/headroom/footroom shrink by the lost gen's share, and
    // a per-scenario factor-sum drop so the other buses pick up that share. nullptr when no DS scenarios.
    DeviceBuf dDsOvBus, dDsOvFactorDelta, dDsOvBaseDelta, dDsOvMaxDelta, dDsOvMinDelta;
    bool hasDsOverride = (dsOvBus != nullptr && ctx->nds > 0);

    // x0/target are the BASE row (length n), identical for every scenario: upload once and tile across
    // nS on-device. rowMode is PER-SCENARIO (length n*nS): a contingency can freeze islanded buses
    // (Phase 0b) or flip PV↔PQ via reactive limits (Phase 1), so it is NOT tiled — uploaded as-is and
    // mutated in place on-device by the outer-loop kernels. Only the disabled mask is also per-scenario.
    CUDA_CHECK(cudaMemcpy(ctx->dX.p, x0, n * sizeof(double), cudaMemcpyHostToDevice));
    CUDA_CHECK(cudaMemcpy(ctx->dTarget.p, target, n * sizeof(double), cudaMemcpyHostToDevice));
    CUDA_CHECK(cudaMemcpy(ctx->dMode.p, rowMode, nTot * sizeof(int), cudaMemcpyHostToDevice));
    dBaseMode.upload(baseRowMode, n * sizeof(int));         // base (contingency-free) row mode for the RL limit-type seed
    tileBaseRow<<<grid(n * nS), BLOCK>>>(n * nS, n, ctx->dX.as<double>());
    tileBaseRow<<<grid(n * nS), BLOCK>>>(n * nS, n, ctx->dTarget.as<double>());
    if (nOv > 0) {                                           // per-scenario target overrides (generator loss)
        dOvIndex.upload(ovIndex, nOv * sizeof(int));
        dOvValue.upload(ovValue, nOv * sizeof(double));
        applyTargetOverrideBatch<<<grid(nOv), BLOCK>>>(nOv, dOvIndex.as<int>(), dOvValue.as<double>(), ctx->dTarget.as<double>());
    }
    if (hasDsOverride) {                                     // per-scenario generator-loss DS reweight
        dDsOvBus.upload(dsOvBus, nS * sizeof(int));
        dDsOvFactorDelta.upload(dsOvFactorDelta, nS * sizeof(double));
        dDsOvBaseDelta.upload(dsOvBaseDelta, nS * sizeof(double));
        dDsOvMaxDelta.upload(dsOvMaxDelta, nS * sizeof(double));
        dDsOvMinDelta.upload(dsOvMinDelta, nS * sizeof(double));
    }
    CUDA_CHECK(cudaMemcpy(ctx->dDisabled.p, disabled,
                          static_cast<size_t>(ctx->nElements) * nS * sizeof(char), cudaMemcpyHostToDevice));

    std::vector<char> active(nS, 1);

    BatchResult res;
    res.x.resize(nTot);
    res.iters.assign(nS, 0);
    res.norm.assign(nS, 0.0);
    res.status.assign(nS, 1);                                // default: max_iter (overwritten on converge/diverge)

    std::vector<double> hF(nTot);

    // Device-side fixed-iteration mode: tol <= 0 means "run exactly maxIter Newton steps
    // on the device with NO per-iteration host sync and NO convergence retirement" (the
    // a fixed-iteration NR loop). tol > 0 keeps the per-iteration retirement loop.
    bool deviceFixedIter = (tol <= 0.0);

    // Full-loadflow mode (tol > 0) with local PV controllers runs the reactive-limits (PV→PQ) OUTER loop:
    // converge the inner Newton, switch any generator over its Q limit, re-converge the switched scenarios,
    // repeat until a pass switches nothing. Fixed-iter mode (tol <= 0, the throughput benchmark) and
    // family-free networks with no controllers do ONE pass (no outer loop) — byte-identical to before.
    bool fullLoadflow = !deviceFixedIter && (ctx->nrl > 0 || ctx->nds > 0);
    const int RL_MAX_OUTER = 20;
    const int RL_MAX_SWITCH = 3;                            // per-bus PV→PQ count cap (OLF maxPqPvSwitch)
    // Outer-loop convergence tolerances (pu) — passed in so the device uses the SAME criteria as the CPU
    // loadflow that built the base case (maxReactivePowerMismatch / slackBusPMaxMismatch, per-unitized), NOT
    // hardcoded constants. With matching parameters and the same equations the GPU outer-loop fixed point
    // matches the CPU's to the inner-Newton precision; a loose tolerance (e.g. the 1 MW slack default) leaves
    // a redistribution band, so a test wanting machine-precision agreement passes tight values on both sides.
    const double REACTIVE_LIMIT_TOL = reactiveLimitTol;
    const double SLACK_DIST_TOL = slackDistTol;
    const double P_RESIDUE_EPS = 1e-5;                       // OLF ActivePowerDistribution.P_RESIDUE_EPS: the
                                                            // water-filling distributes the mismatch down to this
    // RL_MAX_OUTER bounds the PRODUCTIVE outer iterations (an outer loop that re-solved), matching OLF's
    // outerLoopTotalIterations budget. The raw pass loop gets a generous safety bound on top, because the
    // sequential driver also spends non-productive passes (phase transitions / stability re-checks) that OLF
    // does not count.
    int maxOuter = fullLoadflow ? 8 * RL_MAX_OUTER : 1;
    std::vector<char> rlActive(nS), rlSwitched(nS), wasActive(nS);
    if (fullLoadflow) {                                      // per-solve PV↔PQ state: 0 switches, limitType from base
        CUDA_CHECK(cudaMemset(ctx->dRlSwitchCount.p, 0, static_cast<size_t>(ctx->nrl) * nS * sizeof(char)));
        if (ctx->nrl > 0) {                                  // seed the pinned limit for base-PQ controllers (else all PV)
            initRlLimitTypeBatch<<<grid(ctx->nrl * nS), BLOCK>>>(ctx->nrl, nS, n, ctx->dRlVrow.as<int>(),
                    ctx->dRlVtarget.as<double>(), dBaseMode.as<int>(), ctx->dX.as<double>(),
                    ctx->dRlLimitType.as<char>());
        }
        if (ctx->nds > 0) {                                  // slack-retained share: 0 until frozen at the first pass
            CUDA_CHECK(cudaMemset(ctx->dDsRetain.p, 0, nS * sizeof(double)));
        }
    }

    // SEQUENTIAL outer loops, exactly like OLF's AcloadFlowEngine: fully stabilize the reactive-limits loop
    // (switch + re-solve until it switches nothing), THEN fully stabilize the distributed-slack loop
    // (distribute + re-solve until balanced), and repeat the whole RL->DS sequence until a full sequence
    // triggers no inner Newton re-solve. Running RL and DS in the SAME pass converges to a different (still
    // in-tolerance) point when switching interacts with slack distribution. phase 0 = reactive limits, 1 = slack.
    std::vector<char> live(active);                          // scenarios still iterating outer loops (dropped on failure)
    // OLF's DefaultAcOuterLoopConfig orders DistributedSlack BEFORE ReactiveLimits (innermost first), and the
    // engine stabilizes them in that order. Match it: phase 0 = distributed slack, phase 1 = reactive limits.
    // (Switching PV->PQ before the slack is balanced leaves a large active imbalance that the undamped device
    // Newton diverges on — DS must stabilize first.)
    int phase = (ctx->nds > 0) ? 0 : 1;                      // start with distributed slack if present, else reactive limits
    bool seqChanged = false;                                 // did any outer loop re-solve during the current RL->DS sequence
    int prodIters = 0;                                       // productive outer iterations (OLF's outerLoopTotalIterations)
    for (int outer = 0; outer < maxOuter; outer++) {
    std::copy(active.begin(), active.end(), wasActive.begin());   // scenarios (re)solved in this inner pass
    CUDA_CHECK(cudaMemcpy(ctx->dActive.p, active.data(), nS * sizeof(char), cudaMemcpyHostToDevice));
    int nActive = 0;
    for (char a : active) {
        if (a) nActive++;
    }

    for (int it = 0; it <= maxIter && nActive > 0; it++) {
        // ---- batched fill of J + F for all active scenarios ----
        CUDA_CHECK(cudaMemset(ctx->dVals.p, 0, static_cast<size_t>(nnz) * nS * sizeof(double)));
        initFBatch<<<grid(n * nS), BLOCK>>>(n, nS, ctx->dTarget.as<double>(), ctx->dF.as<double>(), ctx->dActive.as<char>());
        if (ctx->cb.ne) fillClosedBranchBatch<<<grid(ctx->cb.ne * nS), BLOCK>>>(ctx->cb.ne, nS, n, nnz, ctx->nElements, ctx->cbOffset, ctx->cb.in.as<double>(), ctx->cb.ix.as<int>(), ctx->cb.dest.as<int>(), ctx->dX.as<double>(), ctx->dVals.as<double>(), ctx->dF.as<double>(), ctx->dDisabled.as<char>(), ctx->dActive.as<char>());
        if (ctx->ob.ne) fillOpenBranchBatch<<<grid(ctx->ob.ne * nS), BLOCK>>>(ctx->ob.ne, nS, n, nnz, ctx->nElements, ctx->obOffset, ctx->ob.in.as<double>(), ctx->ob.ix.as<int>(), ctx->ob.dest.as<int>(), ctx->dX.as<double>(), ctx->dVals.as<double>(), ctx->dF.as<double>(), ctx->dDisabled.as<char>(), ctx->dActive.as<char>());
        if (ctx->sh.ne) fillShuntBatch<<<grid(ctx->sh.ne * nS), BLOCK>>>(ctx->sh.ne, nS, n, nnz, ctx->nElements, ctx->shOffset, ctx->sh.in.as<double>(), ctx->sh.ix.as<int>(), ctx->sh.dest.as<int>(), ctx->dX.as<double>(), ctx->dVals.as<double>(), ctx->dF.as<double>(), ctx->dDisabled.as<char>(), ctx->dActive.as<char>());
        if (ctx->hv.ne) fillHvdcBatch<<<grid(ctx->hv.ne * nS), BLOCK>>>(ctx->hv.ne, nS, n, nnz, ctx->nElements, ctx->hvOffset, ctx->hv.in.as<double>(), ctx->hv.ix.as<int>(), ctx->hv.dest.as<int>(), ctx->dX.as<double>(), ctx->dVals.as<double>(), ctx->dF.as<double>(), ctx->dDisabled.as<char>(), ctx->dActive.as<char>());
        if (ctx->ndq) fillDistrQBatch<<<grid(ctx->ndq * nS), BLOCK>>>(ctx->ndq, nS, n, nnz, ctx->nElements, ctx->cbOffset, ctx->obOffset, ctx->dDqElem.as<int>(), ctx->dDqSide.as<int>(), ctx->dDqKind.as<int>(), ctx->dDqWeight.as<double>(), ctx->dDqDest.as<int>(), ctx->cb.in.as<double>(), ctx->cb.ix.as<int>(), ctx->sh.in.as<double>(), ctx->sh.ix.as<int>(), ctx->ob.in.as<double>(), ctx->ob.ix.as<int>(), ctx->dX.as<double>(), ctx->dVals.as<double>(), ctx->dF.as<double>(), ctx->dDisabled.as<char>(), ctx->dActive.as<char>());
        if (ctx->ndr) fillDistrRhoBatch<<<grid(ctx->ndr * nS), BLOCK>>>(ctx->ndr, nS, n, nnz, ctx->dDrCol.as<int>(), ctx->dDrCoef.as<double>(), ctx->dDrDest.as<int>(), ctx->dX.as<double>(), ctx->dVals.as<double>(), ctx->dF.as<double>(), ctx->dActive.as<char>());
        if (ctx->nzi) fillZeroImpBatch<<<grid(ctx->nzi * nS), BLOCK>>>(ctx->nzi, nS, n, nnz, ctx->dZiIn.as<double>(), ctx->dZiIdx.as<int>(), ctx->dZiDest.as<int>(), ctx->dX.as<double>(), ctx->dVals.as<double>(), ctx->dF.as<double>(), ctx->dActive.as<char>());
        applyRowModeBatch<<<grid(n * nS), BLOCK>>>(n, nS, nnz, ctx->dMode.as<int>(), ctx->dRowPtr.as<int>(), ctx->dDiagPos.as<int>(), ctx->dX.as<double>(), ctx->dTarget.as<double>(), ctx->dVals.as<double>(), ctx->dF.as<double>(), ctx->dActive.as<char>());
        if (!deviceFixedIter) CUDA_CHECK(cudaDeviceSynchronize());   // fixed-iter: stream-ordered into the solve, no host sync

        // ---- residual check + retirement ----
        // Normal mode (tol > 0): every iteration copies the batched mismatch to the host (the
        // only thing crossing PCIe) and retires converged/diverged scenarios.
        // Device fixed-iteration mode (tol <= 0): NO per-iteration host sync and NO retirement
        // — every scenario runs exactly maxIter steps on the device; the mismatch is read back
        // ONCE, on the final pass, only to report ||F||inf. This drops the per-iteration D->H
        // copy of the full batched mismatch. Tradeoff: a scenario going non-finite is not
        // isolated, so it can poison a uniform-batched factorization — use tol > 0 if
        // scenarios may diverge.
        if (!deviceFixedIter || it == maxIter) {
            CUDA_CHECK(cudaMemcpy(hF.data(), ctx->dF.p, nTot * sizeof(double), cudaMemcpyDeviceToHost));
            bool anyChanged = false;
            // Inner-Newton convergence test. Default (convEpsPerEq <= 0): the device infinity-norm floor
            // ‖F‖∞ < tol (stricter than OLF's usual criterion). When convEpsPerEq > 0: OLF's EXACT criterion
            // — DefaultNewtonRaphsonStoppingCriteria stops at ‖F‖₂ < sqrt(convEpsPerEq² · n) — so a batched SA
            // can be run with the SAME stopping decision as the CPU loadflow for an apples-to-apples timing
            // comparison. ‖F‖∞ is still recorded in res.norm[s] for reporting either way. n counts the padded
            // system rows; for batched scenarios (no islanding — those go to the CPU) that equals OLF's active
            // equation count, since disabled-branch rows keep the bus equations and identity pads contribute 0.
            const double olf2NormThreshold = convEpsPerEq > 0.0 ? std::sqrt(convEpsPerEq * convEpsPerEq * n) : 0.0;
            for (int s = 0; s < nS; s++) {
                if (!active[s]) continue;
                double norm = 0.0;
                double ssq = 0.0;
                bool finite = true;
                for (int i = 0; i < n; i++) {
                    double v = hF[(size_t) s * n + i];
                    if (!std::isfinite(v)) {
                        finite = false;
                        break;
                    }
                    norm = std::max(norm, std::fabs(v));
                    ssq += v * v;
                }
                bool converged = convEpsPerEq > 0.0 ? (std::sqrt(ssq) < olf2NormThreshold) : (norm < tol);
                res.iters[s] = it;
                res.norm[s] = norm;
                if (deviceFixedIter) {
                    res.status[s] = finite ? 1 : -1;         // ran maxIter; no convergence test
                } else if (!finite) {                        // a diverged scenario must not poison the batch
                    res.status[s] = -1;
                    active[s] = 0;
                    live[s] = 0;                             // failed: stop iterating its outer loops
                    nActive--;
                    anyChanged = true;
                } else if (converged) {
                    res.status[s] = 0;
                    active[s] = 0;
                    nActive--;
                    anyChanged = true;
                } else if (it == maxIter) {
                    res.status[s] = 1;                       // exhausted iterations
                    active[s] = 0;
                    live[s] = 0;                             // did not converge: stop iterating its outer loops
                    nActive--;
                    anyChanged = true;
                }
            }
            if (!deviceFixedIter && anyChanged) {
                CUDA_CHECK(cudaMemcpy(ctx->dActive.p, active.data(), nS * sizeof(char), cudaMemcpyHostToDevice));
            }
        }
        if (deviceFixedIter) {
            if (it == maxIter) {
                break;                                       // final residual recorded; done
            }
        } else if (nActive == 0) {
            break;
        }

        if (ctx->mode == 1) {
        // ---- UNIFORM BATCHED solve: one ANALYSIS on the shared pattern, then ONE batched
        //      FACTORIZATION + SOLVE over every scenario's value set. Retired scenarios are
        //      frozen to identity so the batch stays non-singular. ----
        writeIdentityForInactive<<<grid(n * nS), BLOCK>>>(n, nS, nnz, ctx->dRowPtr.as<int>(),
                ctx->dDiagPos.as<int>(), ctx->dActive.as<char>(), ctx->dVals.as<double>(), ctx->dF.as<double>());
        negateIntoBatch<<<grid(n * nS), BLOCK>>>(n * nS, ctx->dF.as<double>(), ctx->dB.as<double>());
        if (!ctx->analyzed) {
            CUDSS_CHECK(cudssExecute(ctx->ds.handle, CUDSS_PHASE_ANALYSIS, ctx->ds.config, ctx->ds.data, ctx->matABatch, ctx->matXBatch, ctx->matBBatch));
            ctx->analyzed = true;
        }
        CUDSS_CHECK(cudssExecute(ctx->ds.handle, CUDSS_PHASE_FACTORIZATION, ctx->ds.config, ctx->ds.data, ctx->matABatch, ctx->matXBatch, ctx->matBBatch));
        CUDSS_CHECK(cudssExecute(ctx->ds.handle, CUDSS_PHASE_SOLVE, ctx->ds.config, ctx->ds.data, ctx->matABatch, ctx->matXBatch, ctx->matBBatch));
        axpy1Batch<<<grid(n * nS), BLOCK>>>(n * nS, ctx->dDx.as<double>(), ctx->dX.as<double>());
        if (!deviceFixedIter) CUDA_CHECK(cudaDeviceSynchronize());   // fixed-iter: no per-iter host sync (final readback syncs)
        } else if (ctx->mode == 2) {
        // ---- BLOCK-DIAGONAL solve: one big blkdiag matrix; ANALYZE+FACTORIZE once (ever, across
        //      chunks), then REFACTORIZE every iteration reusing the pivot order (the
        //      refactor-every strategy) — refactor IS cheap on a single matrix, unlike the batch API. ----
        writeIdentityForInactive<<<grid(n * nS), BLOCK>>>(n, nS, nnz, ctx->dRowPtr.as<int>(),
                ctx->dDiagPos.as<int>(), ctx->dActive.as<char>(), ctx->dVals.as<double>(), ctx->dF.as<double>());
        negateIntoBatch<<<grid(n * nS), BLOCK>>>(n * nS, ctx->dF.as<double>(), ctx->dB.as<double>());
        if (!ctx->analyzed) {
            CUDSS_CHECK(cudssExecute(ctx->ds.handle, CUDSS_PHASE_ANALYSIS, ctx->ds.config, ctx->ds.data, ctx->matBig, ctx->matBigX, ctx->matBigB));
            ctx->analyzed = true;
        }
        CUDSS_CHECK(cudssExecute(ctx->ds.handle, ctx->factored ? CUDSS_PHASE_REFACTORIZATION : CUDSS_PHASE_FACTORIZATION,
                ctx->ds.config, ctx->ds.data, ctx->matBig, ctx->matBigX, ctx->matBigB));
        ctx->factored = true;
        CUDSS_CHECK(cudssExecute(ctx->ds.handle, CUDSS_PHASE_SOLVE, ctx->ds.config, ctx->ds.data, ctx->matBig, ctx->matBigX, ctx->matBigB));
        axpy1Batch<<<grid(n * nS), BLOCK>>>(n * nS, ctx->dDx.as<double>(), ctx->dX.as<double>());
        if (!deviceFixedIter) CUDA_CHECK(cudaDeviceSynchronize());   // fixed-iter: no per-iter host sync (final readback syncs)
        } else if (ctx->mode == 3 || ctx->mode == 4 || ctx->mode == 5) {
        // ---- UBATCH solve: ANALYZE one block once (cheap), then a linear solve over all nS systems
        //      via CUDSS_CONFIG_UBATCH_SIZE on the single per-block matrix. The FACTORIZE policy varies:
        //        mode 3 = FACTORIZE every iteration (honest Newton, DirectRefactorEvery analog);
        //        mode 4 = FACTORIZE only at this chunk's iter 0, SOLVE-only after (DirectIter0Only:
        //                 the J is frozen at the AC-hot-started point, the RHS/F is still fresh each iter);
        //        mode 5 = FACTORIZE once (ever), REFACTORIZE after (DirectRefactorEvery on UBATCH:
        //                 reuse the pivot order, recompute numeric values each iter). ----
        writeIdentityForInactive<<<grid(n * nS), BLOCK>>>(n, nS, nnz, ctx->dRowPtr.as<int>(),
                ctx->dDiagPos.as<int>(), ctx->dActive.as<char>(), ctx->dVals.as<double>(), ctx->dF.as<double>());
        negateIntoBatch<<<grid(n * nS), BLOCK>>>(n * nS, ctx->dF.as<double>(), ctx->dB.as<double>());
        if (!ctx->analyzed) {
            CUDSS_CHECK(cudssExecute(ctx->ds.handle, CUDSS_PHASE_ANALYSIS, ctx->ds.config, ctx->ds.data, ctx->matUb, ctx->matUbX, ctx->matUbB));
            ctx->analyzed = true;
        }
        if (ctx->mode == 5) {
            CUDSS_CHECK(cudssExecute(ctx->ds.handle, ctx->factored ? CUDSS_PHASE_REFACTORIZATION : CUDSS_PHASE_FACTORIZATION,
                    ctx->ds.config, ctx->ds.data, ctx->matUb, ctx->matUbX, ctx->matUbB));
            ctx->factored = true;
        } else if (ctx->mode == 3 || it == 0) {              // mode 3 every iter; mode 4 only at chunk iter 0
            CUDSS_CHECK(cudssExecute(ctx->ds.handle, CUDSS_PHASE_FACTORIZATION, ctx->ds.config, ctx->ds.data, ctx->matUb, ctx->matUbX, ctx->matUbB));
        }
        CUDSS_CHECK(cudssExecute(ctx->ds.handle, CUDSS_PHASE_SOLVE, ctx->ds.config, ctx->ds.data, ctx->matUb, ctx->matUbX, ctx->matUbB));
        axpy1Batch<<<grid(n * nS), BLOCK>>>(n * nS, ctx->dDx.as<double>(), ctx->dX.as<double>());
        if (!deviceFixedIter) CUDA_CHECK(cudaDeviceSynchronize());   // fixed-iter: no per-iter host sync (final readback syncs)
        } else {
        // ---- fallback: analyze once (shared pattern), refactorize + solve per scenario ----
        for (int s = 0; s < nS; s++) {
            if (!active[s]) continue;
            double* valsS = ctx->dVals.as<double>() + (size_t) s * nnz;
            double* bS = ctx->dB.as<double>() + (size_t) s * n;
            double* dxS = ctx->dDx.as<double>() + (size_t) s * n;
            double* fS = ctx->dF.as<double>() + (size_t) s * n;
            negateIntoOne<<<grid(n), BLOCK>>>(n, fS, bS);
            CUDSS_CHECK(cudssMatrixSetValues(ctx->ds.matA, valsS));
            CUDSS_CHECK(cudssMatrixSetValues(ctx->ds.matB, bS));
            CUDSS_CHECK(cudssMatrixSetValues(ctx->ds.matX, dxS));
            if (!ctx->analyzed) {
                CUDSS_CHECK(cudssExecute(ctx->ds.handle, CUDSS_PHASE_ANALYSIS, ctx->ds.config, ctx->ds.data, ctx->ds.matA, ctx->ds.matX, ctx->ds.matB));
                ctx->analyzed = true;
            }
            CUDSS_CHECK(cudssExecute(ctx->ds.handle, CUDSS_PHASE_FACTORIZATION, ctx->ds.config, ctx->ds.data, ctx->ds.matA, ctx->ds.matX, ctx->ds.matB));
            CUDSS_CHECK(cudssExecute(ctx->ds.handle, CUDSS_PHASE_SOLVE, ctx->ds.config, ctx->ds.data, ctx->ds.matA, ctx->ds.matX, ctx->ds.matB));
            double* xS = ctx->dX.as<double>() + (size_t) s * n;
            axpy1One<<<grid(n), BLOCK>>>(n, dxS, xS);
        }
        if (!deviceFixedIter) CUDA_CHECK(cudaDeviceSynchronize());   // fixed-iter: no per-iter host sync
        }
    }   // ---- end inner Newton loop ----

    if (!fullLoadflow) {
        break;                                              // fixed-iter / no controllers: one pass only
    }

    // ---- REACTIVE LIMITS pass: switch any local PV controller over its Q limit to PQ ----
    // Only scenarios (re)solved this pass that converged can have new violations.
    for (int s = 0; s < nS; s++) {
        rlActive[s] = (wasActive[s] && res.status[s] == 0) ? 1 : 0;
    }
    CUDA_CHECK(cudaMemcpy(ctx->dRlActive.p, rlActive.data(), nS * sizeof(char), cudaMemcpyHostToDevice));
    // generator Q per controller = (Σ branch q + shunt q) at its BUS_V row + load Q. Compute the first
    // part by a fill into a ZEROED buffer: no initF (−target), no applyRowMode, so qBuf[row] is the raw
    // bus reactive balance. Disabled branches are skipped (outage); dVals is scratch (the next inner
    // iteration memsets and refills it).
    CUDA_CHECK(cudaMemset(ctx->dQbuf.p, 0, nTot * sizeof(double)));
    if (ctx->cb.ne) fillClosedBranchBatch<<<grid(ctx->cb.ne * nS), BLOCK>>>(ctx->cb.ne, nS, n, nnz, ctx->nElements, ctx->cbOffset, ctx->cb.in.as<double>(), ctx->cb.ix.as<int>(), ctx->cb.dest.as<int>(), ctx->dX.as<double>(), ctx->dVals.as<double>(), ctx->dQbuf.as<double>(), ctx->dDisabled.as<char>(), ctx->dRlActive.as<char>());
    if (ctx->ob.ne) fillOpenBranchBatch<<<grid(ctx->ob.ne * nS), BLOCK>>>(ctx->ob.ne, nS, n, nnz, ctx->nElements, ctx->obOffset, ctx->ob.in.as<double>(), ctx->ob.ix.as<int>(), ctx->ob.dest.as<int>(), ctx->dX.as<double>(), ctx->dVals.as<double>(), ctx->dQbuf.as<double>(), ctx->dDisabled.as<char>(), ctx->dRlActive.as<char>());
    if (ctx->sh.ne) fillShuntBatch<<<grid(ctx->sh.ne * nS), BLOCK>>>(ctx->sh.ne, nS, n, nnz, ctx->nElements, ctx->shOffset, ctx->sh.in.as<double>(), ctx->sh.ix.as<int>(), ctx->sh.dest.as<int>(), ctx->dX.as<double>(), ctx->dVals.as<double>(), ctx->dQbuf.as<double>(), ctx->dDisabled.as<char>(), ctx->dRlActive.as<char>());
    CUDA_CHECK(cudaMemset(ctx->dRlSwitched.p, 0, nS * sizeof(char)));
    // Apply ONLY the current phase's outer loop this pass (sequential, like OLF) — never both at once.
    bool inDsPhase = (phase == 0);
    if (inDsPhase) {                                       // distributed-slack outer loop: spread the slack mismatch
        if (ctx->nds > 0) {
            // Recompute the slack's retained share from the CURRENT total imbalance every pass (NOT frozen at
            // pass 0) so a participating slack tracks the loss change during redistribution.
            computeSlackRetain<<<grid(nS), BLOCK>>>(nS, n, ctx->nds, ctx->dsSlackPhiRow, ctx->dsSlackTargetP,
                    1.0 - ctx->dsFactorSum, ctx->dDsPhiRow.as<int>(), ctx->dDsBaseTargetP.as<double>(),
                    ctx->dTarget.as<double>(), ctx->dQbuf.as<double>(), ctx->dRlActive.as<char>(),
                    ctx->dDsRetain.as<double>());
            distributeSlackWaterfillBatch<<<grid(nS), BLOCK>>>(ctx->nds, nS, n, SLACK_DIST_TOL, P_RESIDUE_EPS,
                    ctx->dsSlackPhiRow, ctx->dsSlackTargetP, ctx->dDsPhiRow.as<int>(),
                    ctx->dDsFactor.as<double>(), ctx->dDsBaseTargetP.as<double>(),
                    ctx->dDsMaxDelta.as<double>(), ctx->dDsMinDelta.as<double>(), ctx->dDsRetain.as<double>(),
                    ctx->dQbuf.as<double>(), ctx->dRlActive.as<char>(),
                    hasDsOverride ? dDsOvBus.as<int>() : nullptr, dDsOvFactorDelta.as<double>(),
                    dDsOvBaseDelta.as<double>(), dDsOvMaxDelta.as<double>(), dDsOvMinDelta.as<double>(),
                    ctx->dTarget.as<double>(), ctx->dRlSwitched.as<char>());
        }
    } else {                                               // reactive-limits outer loop: switch PV->PQ over Q limit
        if (ctx->nrl > 0) {
            reactiveLimitsBatch<<<grid(ctx->nrl * nS), BLOCK>>>(ctx->nrl, nS, n, REACTIVE_LIMIT_TOL, RL_MAX_SWITCH,
                    ctx->dRlVrow.as<int>(), ctx->dRlMinQ.as<double>(), ctx->dRlMaxQ.as<double>(), ctx->dRlLoadQ.as<double>(),
                    ctx->dRlVtarget.as<double>(), ctx->dQbuf.as<double>(), ctx->dX.as<double>(), ctx->dRlActive.as<char>(),
                    ctx->dMode.as<int>(), ctx->dTarget.as<double>(),
                    ctx->dRlLimitType.as<char>(), ctx->dRlSwitchCount.as<char>(), ctx->dRlSwitched.as<char>());
        }
    }
    CUDA_CHECK(cudaDeviceSynchronize());
    CUDA_CHECK(cudaMemcpy(rlSwitched.data(), ctx->dRlSwitched.p, nS * sizeof(char), cudaMemcpyDeviceToHost));
    int nChanged = 0;
    for (int s = 0; s < nS; s++) {
        if (rlSwitched[s]) {
            nChanged++;
        }
    }
    if (nChanged > 0) {                                     // this outer loop is still unstable
        for (int s = 0; s < nS; s++) {
            active[s] = rlSwitched[s];                      // re-solve only the scenarios it changed, stay in this phase
        }
        seqChanged = true;
        if (++prodIters >= RL_MAX_OUTER) {
            break;                                          // OLF's max-outer-loop-iterations budget reached
        }
    } else {                                               // this outer loop stabilized
        if (inDsPhase && ctx->nrl > 0) {
            phase = 1;                                      // distributed slack done -> move to reactive limits
        } else {
            if (!seqChanged) {
                break;                                      // a full DS->RL sequence re-solved nothing -> converged
            }
            seqChanged = false;
            phase = (ctx->nds > 0) ? 0 : 1;                 // restart the sequence at the first loop (distributed slack)
        }
        for (int s = 0; s < nS; s++) {
            active[s] = live[s];                            // re-check every live scenario in the next phase
        }
    }
    }   // ---- end sequential outer-loop driver ----

    CUDA_CHECK(cudaMemcpy(res.x.data(), ctx->dX.p, nTot * sizeof(double), cudaMemcpyDeviceToHost));
    return res;
}

}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_powsybl_openloadflow_ac_equations_vector_gpu_GpuAcNewtonSolver_createContext(
        JNIEnv* env, jclass, jint n,
        jdoubleArray jcbIn, jintArray jcbIdx, jdoubleArray jobIn, jintArray jobIdx,
        jdoubleArray jshIn, jintArray jshIdx, jdoubleArray jhvIn, jintArray jhvIdx,
        jintArray jdqElem, jintArray jdqSide, jdoubleArray jdqWeight, jintArray jdqRow, jintArray jdqKind,
        jdoubleArray jziIn, jintArray jziIdx,
        jintArray jdrRow, jintArray jdrCol, jdoubleArray jdrCoef) {
    jdouble* cbIn = env->GetDoubleArrayElements(jcbIn, nullptr);
    jint* cbIdx = env->GetIntArrayElements(jcbIdx, nullptr);
    jdouble* obIn = env->GetDoubleArrayElements(jobIn, nullptr);
    jint* obIdx = env->GetIntArrayElements(jobIdx, nullptr);
    jdouble* shIn = env->GetDoubleArrayElements(jshIn, nullptr);
    jint* shIdx = env->GetIntArrayElements(jshIdx, nullptr);
    jdouble* hvIn = env->GetDoubleArrayElements(jhvIn, nullptr);
    jint* hvIdx = env->GetIntArrayElements(jhvIdx, nullptr);
    jint* dqElem = env->GetIntArrayElements(jdqElem, nullptr);
    jint* dqSide = env->GetIntArrayElements(jdqSide, nullptr);
    jdouble* dqWeight = env->GetDoubleArrayElements(jdqWeight, nullptr);
    jint* dqRow = env->GetIntArrayElements(jdqRow, nullptr);
    jint* dqKind = env->GetIntArrayElements(jdqKind, nullptr);
    jdouble* ziIn = env->GetDoubleArrayElements(jziIn, nullptr);
    jint* ziIdx = env->GetIntArrayElements(jziIdx, nullptr);
    jint* drRow = env->GetIntArrayElements(jdrRow, nullptr);
    jint* drCol = env->GetIntArrayElements(jdrCol, nullptr);
    jdouble* drCoef = env->GetDoubleArrayElements(jdrCoef, nullptr);

    int ncb = env->GetArrayLength(jcbIdx) / 10;
    int nob = env->GetArrayLength(jobIdx) / 6;
    int nsh = env->GetArrayLength(jshIdx) / 3;
    int nhv = env->GetArrayLength(jhvIdx) / 4;
    int ndq = env->GetArrayLength(jdqElem);
    int nzi = env->GetArrayLength(jziIdx) / 11;
    int ndr = env->GetArrayLength(jdrRow);

    std::string error;
    SolverContext* ctx = nullptr;
    try {
        ctx = createContext(n, ncb, cbIn, cbIdx, nob, obIn, obIdx,
                            nsh, shIn, shIdx, nhv, hvIn, hvIdx,
                            ndq, dqElem, dqSide, dqWeight, dqRow, dqKind,
                            nzi, ziIn, ziIdx,
                            ndr, drRow, drCol, drCoef);
    } catch (const std::exception& e) {
        error = e.what();
    } catch (...) {
        error = "unknown native error";
    }

    env->ReleaseDoubleArrayElements(jcbIn, cbIn, JNI_ABORT);
    env->ReleaseIntArrayElements(jcbIdx, cbIdx, JNI_ABORT);
    env->ReleaseDoubleArrayElements(jobIn, obIn, JNI_ABORT);
    env->ReleaseIntArrayElements(jobIdx, obIdx, JNI_ABORT);
    env->ReleaseDoubleArrayElements(jshIn, shIn, JNI_ABORT);
    env->ReleaseIntArrayElements(jshIdx, shIdx, JNI_ABORT);
    env->ReleaseDoubleArrayElements(jhvIn, hvIn, JNI_ABORT);
    env->ReleaseIntArrayElements(jhvIdx, hvIdx, JNI_ABORT);
    env->ReleaseIntArrayElements(jdqElem, dqElem, JNI_ABORT);
    env->ReleaseIntArrayElements(jdqSide, dqSide, JNI_ABORT);
    env->ReleaseDoubleArrayElements(jdqWeight, dqWeight, JNI_ABORT);
    env->ReleaseIntArrayElements(jdqRow, dqRow, JNI_ABORT);
    env->ReleaseIntArrayElements(jdqKind, dqKind, JNI_ABORT);
    env->ReleaseDoubleArrayElements(jziIn, ziIn, JNI_ABORT);
    env->ReleaseIntArrayElements(jziIdx, ziIdx, JNI_ABORT);
    env->ReleaseIntArrayElements(jdrRow, drRow, JNI_ABORT);
    env->ReleaseIntArrayElements(jdrCol, drCol, JNI_ABORT);
    env->ReleaseDoubleArrayElements(jdrCoef, drCoef, JNI_ABORT);

    if (!error.empty()) {
        env->ThrowNew(env->FindClass("java/lang/RuntimeException"),
                      ("GPU Newton-Raphson context creation failed: " + error).c_str());
        return 0;
    }
    return reinterpret_cast<jlong>(ctx);
}

extern "C" JNIEXPORT jdoubleArray JNICALL
Java_com_powsybl_openloadflow_ac_equations_vector_gpu_GpuAcNewtonSolver_solveContext(
        JNIEnv* env, jclass, jlong handle, jdoubleArray jx0, jdoubleArray jtarget,
        jintArray jrowMode, jint maxIter, jdouble tol, jint lsMode, jint lsMaxIter, jdouble lsStepFold,
        jdouble maxDv, jdouble maxDphi, jintArray jvarType) {
    auto* ctx = reinterpret_cast<SolverContext*>(handle);
    jdouble* x0 = env->GetDoubleArrayElements(jx0, nullptr);
    jdouble* target = env->GetDoubleArrayElements(jtarget, nullptr);
    jint* rowMode = env->GetIntArrayElements(jrowMode, nullptr);
    jint* varType = env->GetIntArrayElements(jvarType, nullptr);
    int n = env->GetArrayLength(jx0);

    std::string error;
    Result res;
    try {
        if (ctx == nullptr || n != ctx->n) {
            throw std::runtime_error("invalid solver context or state size");
        }
        res = solveContext(ctx, x0, target, rowMode, maxIter, tol, lsMode, lsMaxIter, lsStepFold,
                           maxDv, maxDphi, varType);
    } catch (const std::exception& e) {
        error = e.what();
    } catch (...) {
        error = "unknown native error";
    }

    env->ReleaseDoubleArrayElements(jx0, x0, JNI_ABORT);
    env->ReleaseDoubleArrayElements(jtarget, target, JNI_ABORT);
    env->ReleaseIntArrayElements(jrowMode, rowMode, JNI_ABORT);
    env->ReleaseIntArrayElements(jvarType, varType, JNI_ABORT);

    if (!error.empty()) {
        env->ThrowNew(env->FindClass("java/lang/RuntimeException"),
                      ("GPU Newton-Raphson failed: " + error).c_str());
        return nullptr;
    }
    jdoubleArray out = env->NewDoubleArray(n + 2);
    env->SetDoubleArrayRegion(out, 0, n, res.x.data());
    double tail[2] = {static_cast<double>(res.iters), res.norm};
    env->SetDoubleArrayRegion(out, n, 2, tail);
    return out;
}

extern "C" JNIEXPORT void JNICALL
Java_com_powsybl_openloadflow_ac_equations_vector_gpu_GpuAcNewtonSolver_destroyContext(
        JNIEnv*, jclass, jlong handle) {
    delete reinterpret_cast<SolverContext*>(handle);
}

extern "C" JNIEXPORT void JNICALL
Java_com_powsybl_openloadflow_ac_equations_vector_gpu_GpuAcNewtonSolver_refreshContextValues(
        JNIEnv* env, jclass, jlong handle, jdoubleArray jcbIn, jdoubleArray jobIn,
        jdoubleArray jshIn, jdoubleArray jhvIn) {
    auto* ctx = reinterpret_cast<SolverContext*>(handle);
    jdouble* cbIn = env->GetDoubleArrayElements(jcbIn, nullptr);
    jdouble* obIn = env->GetDoubleArrayElements(jobIn, nullptr);
    jdouble* shIn = env->GetDoubleArrayElements(jshIn, nullptr);
    jdouble* hvIn = env->GetDoubleArrayElements(jhvIn, nullptr);
    std::string error;
    try {
        if (ctx == nullptr) {
            throw std::runtime_error("invalid solver context");
        }
        refreshContextValues(ctx, env->GetArrayLength(jcbIn) / 8, cbIn, env->GetArrayLength(jobIn) / 7, obIn,
                             env->GetArrayLength(jshIn) / 2, shIn, env->GetArrayLength(jhvIn) / 5, hvIn);
    } catch (const std::exception& e) {
        error = e.what();
    } catch (...) {
        error = "unknown native error";
    }
    env->ReleaseDoubleArrayElements(jcbIn, cbIn, JNI_ABORT);
    env->ReleaseDoubleArrayElements(jobIn, obIn, JNI_ABORT);
    env->ReleaseDoubleArrayElements(jshIn, shIn, JNI_ABORT);
    env->ReleaseDoubleArrayElements(jhvIn, hvIn, JNI_ABORT);
    if (!error.empty()) {
        env->ThrowNew(env->FindClass("java/lang/RuntimeException"),
                      ("GPU values refresh failed: " + error).c_str());
    }
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_powsybl_openloadflow_ac_equations_vector_gpu_GpuAcNewtonSolver_createBatchContext(
        JNIEnv* env, jclass, jint n, jint nScenarios, jint mode,
        jdoubleArray jcbIn, jintArray jcbIdx, jdoubleArray jobIn, jintArray jobIdx,
        jdoubleArray jshIn, jintArray jshIdx, jdoubleArray jhvIn, jintArray jhvIdx,
        jintArray jrlVrow, jdoubleArray jrlMinQ, jdoubleArray jrlMaxQ, jdoubleArray jrlLoadQ,
        jdoubleArray jrlVtarget, jintArray jdsPhiRow, jdoubleArray jdsFactor,
        jint jdsSlackPhiRow, jdouble jdsSlackTargetP,
        jdoubleArray jdsBaseTargetP, jdoubleArray jdsMaxDelta, jdoubleArray jdsMinDelta,
        jintArray jdqElem, jintArray jdqSide, jdoubleArray jdqWeight, jintArray jdqRow, jintArray jdqKind,
        jintArray jdrRow, jintArray jdrCol, jdoubleArray jdrCoef,
        jdoubleArray jziIn, jintArray jziIdx) {
    jdouble* cbIn = env->GetDoubleArrayElements(jcbIn, nullptr);
    jint* cbIdx = env->GetIntArrayElements(jcbIdx, nullptr);
    jdouble* obIn = env->GetDoubleArrayElements(jobIn, nullptr);
    jint* obIdx = env->GetIntArrayElements(jobIdx, nullptr);
    jdouble* shIn = env->GetDoubleArrayElements(jshIn, nullptr);
    jint* shIdx = env->GetIntArrayElements(jshIdx, nullptr);
    jdouble* hvIn = env->GetDoubleArrayElements(jhvIn, nullptr);
    jint* hvIdx = env->GetIntArrayElements(jhvIdx, nullptr);
    jint* rlVrow = env->GetIntArrayElements(jrlVrow, nullptr);
    jdouble* rlMinQ = env->GetDoubleArrayElements(jrlMinQ, nullptr);
    jdouble* rlMaxQ = env->GetDoubleArrayElements(jrlMaxQ, nullptr);
    jdouble* rlLoadQ = env->GetDoubleArrayElements(jrlLoadQ, nullptr);
    jdouble* rlVtarget = env->GetDoubleArrayElements(jrlVtarget, nullptr);
    jint* dsPhiRow = env->GetIntArrayElements(jdsPhiRow, nullptr);
    jdouble* dsFactor = env->GetDoubleArrayElements(jdsFactor, nullptr);
    jdouble* dsBaseTargetP = env->GetDoubleArrayElements(jdsBaseTargetP, nullptr);
    jdouble* dsMaxDelta = env->GetDoubleArrayElements(jdsMaxDelta, nullptr);
    jdouble* dsMinDelta = env->GetDoubleArrayElements(jdsMinDelta, nullptr);
    jint* dqElem = env->GetIntArrayElements(jdqElem, nullptr);
    jint* dqSide = env->GetIntArrayElements(jdqSide, nullptr);
    jdouble* dqWeight = env->GetDoubleArrayElements(jdqWeight, nullptr);
    jint* dqRow = env->GetIntArrayElements(jdqRow, nullptr);
    jint* dqKind = env->GetIntArrayElements(jdqKind, nullptr);
    jint* drRow = env->GetIntArrayElements(jdrRow, nullptr);
    jint* drCol = env->GetIntArrayElements(jdrCol, nullptr);
    jdouble* drCoef = env->GetDoubleArrayElements(jdrCoef, nullptr);
    jdouble* ziIn = env->GetDoubleArrayElements(jziIn, nullptr);
    jint* ziIdx = env->GetIntArrayElements(jziIdx, nullptr);

    int ncb = env->GetArrayLength(jcbIdx) / 10;
    int nob = env->GetArrayLength(jobIdx) / 6;
    int nsh = env->GetArrayLength(jshIdx) / 3;
    int nhv = env->GetArrayLength(jhvIdx) / 4;
    int nrl = env->GetArrayLength(jrlVrow);
    int nds = env->GetArrayLength(jdsPhiRow);
    int ndq = env->GetArrayLength(jdqElem);
    int ndr = env->GetArrayLength(jdrRow);
    int nzi = env->GetArrayLength(jziIdx) / 11;

    std::string error;
    BatchSolverContext* ctx = nullptr;
    try {
        ctx = createBatchContext(n, nScenarios, mode, ncb, cbIn, cbIdx, nob, obIn, obIdx,
                                 nsh, shIn, shIdx, nhv, hvIn, hvIdx,
                                 nrl, rlVrow, rlMinQ, rlMaxQ, rlLoadQ, rlVtarget,
                                 nds, dsPhiRow, dsFactor, jdsSlackPhiRow, jdsSlackTargetP,
                                 dsBaseTargetP, dsMaxDelta, dsMinDelta,
                                 ndq, dqElem, dqSide, dqWeight, dqRow, dqKind,
                                 ndr, drRow, drCol, drCoef,
                                 nzi, ziIn, ziIdx);
    } catch (const std::exception& e) {
        error = e.what();
    } catch (...) {
        error = "unknown native error";
    }

    env->ReleaseDoubleArrayElements(jcbIn, cbIn, JNI_ABORT);
    env->ReleaseIntArrayElements(jcbIdx, cbIdx, JNI_ABORT);
    env->ReleaseDoubleArrayElements(jobIn, obIn, JNI_ABORT);
    env->ReleaseIntArrayElements(jobIdx, obIdx, JNI_ABORT);
    env->ReleaseDoubleArrayElements(jshIn, shIn, JNI_ABORT);
    env->ReleaseIntArrayElements(jshIdx, shIdx, JNI_ABORT);
    env->ReleaseDoubleArrayElements(jhvIn, hvIn, JNI_ABORT);
    env->ReleaseIntArrayElements(jhvIdx, hvIdx, JNI_ABORT);
    env->ReleaseIntArrayElements(jrlVrow, rlVrow, JNI_ABORT);
    env->ReleaseDoubleArrayElements(jrlMinQ, rlMinQ, JNI_ABORT);
    env->ReleaseDoubleArrayElements(jrlMaxQ, rlMaxQ, JNI_ABORT);
    env->ReleaseDoubleArrayElements(jrlLoadQ, rlLoadQ, JNI_ABORT);
    env->ReleaseDoubleArrayElements(jrlVtarget, rlVtarget, JNI_ABORT);
    env->ReleaseIntArrayElements(jdsPhiRow, dsPhiRow, JNI_ABORT);
    env->ReleaseDoubleArrayElements(jdsFactor, dsFactor, JNI_ABORT);
    env->ReleaseDoubleArrayElements(jdsBaseTargetP, dsBaseTargetP, JNI_ABORT);
    env->ReleaseDoubleArrayElements(jdsMaxDelta, dsMaxDelta, JNI_ABORT);
    env->ReleaseDoubleArrayElements(jdsMinDelta, dsMinDelta, JNI_ABORT);
    env->ReleaseIntArrayElements(jdqElem, dqElem, JNI_ABORT);
    env->ReleaseIntArrayElements(jdqSide, dqSide, JNI_ABORT);
    env->ReleaseDoubleArrayElements(jdqWeight, dqWeight, JNI_ABORT);
    env->ReleaseIntArrayElements(jdqRow, dqRow, JNI_ABORT);
    env->ReleaseIntArrayElements(jdqKind, dqKind, JNI_ABORT);
    env->ReleaseIntArrayElements(jdrRow, drRow, JNI_ABORT);
    env->ReleaseIntArrayElements(jdrCol, drCol, JNI_ABORT);
    env->ReleaseDoubleArrayElements(jdrCoef, drCoef, JNI_ABORT);
    env->ReleaseDoubleArrayElements(jziIn, ziIn, JNI_ABORT);
    env->ReleaseIntArrayElements(jziIdx, ziIdx, JNI_ABORT);

    if (!error.empty()) {
        env->ThrowNew(env->FindClass("java/lang/RuntimeException"),
                      ("GPU batched context creation failed: " + error).c_str());
        return 0;
    }
    return reinterpret_cast<jlong>(ctx);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_powsybl_openloadflow_ac_equations_vector_gpu_GpuAcNewtonSolver_solveBatchContext(
        JNIEnv* env, jclass, jlong handle, jdoubleArray jx0Base, jdoubleArray jtargetBase,
        jobjectArray jrowMode, jintArray jbaseRowMode, jobjectArray jdisabledMask,
        jintArray jtargetOverrideIndex, jdoubleArray jtargetOverrideValue,
        jintArray jdsOverrideBus, jdoubleArray jdsOverrideFactorDelta, jdoubleArray jdsOverrideBaseDelta,
        jdoubleArray jdsOverrideMaxDelta, jdoubleArray jdsOverrideMinDelta, jint maxIter, jdouble tol,
        jdouble reactiveLimitTol, jdouble slackDistTol, jdouble convEpsPerEq) {
    auto* ctx = reinterpret_cast<BatchSolverContext*>(handle);

    std::string error;
    BatchResult res;
    try {
        if (ctx == nullptr) {
            throw std::runtime_error("invalid batch solver context");
        }
        int n = ctx->n;
        int nS = ctx->nScenarios;
        int nElem = ctx->nElements;
        if (env->GetArrayLength(jx0Base) != n) {
            throw std::runtime_error("base state length mismatch with batch context");
        }
        if (env->GetArrayLength(jdisabledMask) != nS) {
            throw std::runtime_error("scenario count mismatch with batch context");
        }

        if (env->GetArrayLength(jrowMode) != nS) {
            throw std::runtime_error("rowMode scenario count mismatch with batch context");
        }
        // x0/target are the BASE row (length n), identical for every scenario — read once and tiled
        // across nS on-device. rowMode is PER-SCENARIO ([nS][n]) — read all nS rows. Only the disabled
        // mask is also per-scenario.
        std::vector<double> x0(n);
        std::vector<double> target(n);
        env->GetDoubleArrayRegion(jx0Base, 0, n, x0.data());
        env->GetDoubleArrayRegion(jtargetBase, 0, n, target.data());
        std::vector<int> rowMode(static_cast<size_t>(n) * nS);
        for (int s = 0; s < nS; s++) {
            auto mRow = (jintArray) env->GetObjectArrayElement(jrowMode, s);
            if (env->GetArrayLength(mRow) != n) {
                env->DeleteLocalRef(mRow);
                throw std::runtime_error("rowMode row length mismatch with batch context");
            }
            env->GetIntArrayRegion(mRow, 0, n, rowMode.data() + (size_t) s * n);
            env->DeleteLocalRef(mRow);
        }
        std::vector<int> baseRowMode(n);                     // contingency-free base row mode (length n)
        env->GetIntArrayRegion(jbaseRowMode, 0, n, baseRowMode.data());
        std::vector<char> disabled(static_cast<size_t>(nElem) * nS, 0);
        for (int s = 0; s < nS; s++) {
            auto dRow = (jbooleanArray) env->GetObjectArrayElement(jdisabledMask, s);
            jboolean* d = env->GetBooleanArrayElements(dRow, nullptr);
            int dLen = env->GetArrayLength(dRow);
            for (int e = 0; e < dLen && e < nElem; e++) {
                disabled[(size_t) s * nElem + e] = d[e] ? 1 : 0;
            }
            env->ReleaseBooleanArrayElements(dRow, d, JNI_ABORT);
            env->DeleteLocalRef(dRow);
        }

        int nOv = env->GetArrayLength(jtargetOverrideIndex);
        std::vector<int> ovIndex(nOv);
        std::vector<double> ovValue(nOv);
        if (nOv > 0) {
            env->GetIntArrayRegion(jtargetOverrideIndex, 0, nOv, ovIndex.data());
            env->GetDoubleArrayRegion(jtargetOverrideValue, 0, nOv, ovValue.data());
        }

        // Per-scenario distributed-slack reweight: present (length nS) only when a generator-loss scenario
        // sits on a DS-participant bus; empty array → no DS override (pass nullptr to the solver).
        int nDsOv = env->GetArrayLength(jdsOverrideBus);
        std::vector<int> dsOvBus(nDsOv);
        std::vector<double> dsOvFactorDelta(nDsOv), dsOvBaseDelta(nDsOv), dsOvMaxDelta(nDsOv), dsOvMinDelta(nDsOv);
        if (nDsOv > 0) {
            env->GetIntArrayRegion(jdsOverrideBus, 0, nDsOv, dsOvBus.data());
            env->GetDoubleArrayRegion(jdsOverrideFactorDelta, 0, nDsOv, dsOvFactorDelta.data());
            env->GetDoubleArrayRegion(jdsOverrideBaseDelta, 0, nDsOv, dsOvBaseDelta.data());
            env->GetDoubleArrayRegion(jdsOverrideMaxDelta, 0, nDsOv, dsOvMaxDelta.data());
            env->GetDoubleArrayRegion(jdsOverrideMinDelta, 0, nDsOv, dsOvMinDelta.data());
        }

        res = solveBatchContext(ctx, x0.data(), target.data(), rowMode.data(), baseRowMode.data(), disabled.data(),
                                nOv, ovIndex.data(), ovValue.data(),
                                nDsOv > 0 ? dsOvBus.data() : nullptr, dsOvFactorDelta.data(),
                                dsOvBaseDelta.data(), dsOvMaxDelta.data(), dsOvMinDelta.data(), maxIter, tol,
                                reactiveLimitTol, slackDistTol, convEpsPerEq);
    } catch (const std::exception& e) {
        error = e.what();
    } catch (...) {
        error = "unknown native error";
    }

    if (!error.empty()) {
        env->ThrowNew(env->FindClass("java/lang/RuntimeException"),
                      ("GPU batched Newton-Raphson failed: " + error).c_str());
        return nullptr;
    }

    // Pack per-scenario results: [state(n), iterations, finalMismatch, convergenceStatus].
    int n = ctx->n;
    int nS = ctx->nScenarios;
    jclass doubleArrayClass = env->FindClass("[D");
    jobjectArray out = env->NewObjectArray(nS, doubleArrayClass, nullptr);
    for (int s = 0; s < nS; s++) {
        jdoubleArray row = env->NewDoubleArray(n + 3);
        env->SetDoubleArrayRegion(row, 0, n, res.x.data() + (size_t) s * n);
        double tail[3] = {static_cast<double>(res.iters[s]), res.norm[s], static_cast<double>(res.status[s])};
        env->SetDoubleArrayRegion(row, n, 3, tail);
        env->SetObjectArrayElement(out, s, row);
        env->DeleteLocalRef(row);
    }
    return out;
}

extern "C" JNIEXPORT void JNICALL
Java_com_powsybl_openloadflow_ac_equations_vector_gpu_GpuAcNewtonSolver_destroyBatchContext(
        JNIEnv*, jclass, jlong handle) {
    delete reinterpret_cast<BatchSolverContext*>(handle);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_powsybl_openloadflow_ac_equations_vector_gpu_GpuAcNewtonSolver_recommendBatchSize(
        JNIEnv* env, jclass, jint n, jintArray jcbIdx, jintArray jobIdx, jintArray jshIdx, jintArray jhvIdx,
        jintArray jdqElem, jintArray jdqSide, jintArray jdqKind, jintArray jdqRow,
        jintArray jdrRow, jintArray jdrCol, jintArray jziIdx, jint maxCap) {
    jint* cbIdx = env->GetIntArrayElements(jcbIdx, nullptr);
    jint* obIdx = env->GetIntArrayElements(jobIdx, nullptr);
    jint* shIdx = env->GetIntArrayElements(jshIdx, nullptr);
    jint* hvIdx = env->GetIntArrayElements(jhvIdx, nullptr);
    jint* dqElem = env->GetIntArrayElements(jdqElem, nullptr);
    jint* dqSide = env->GetIntArrayElements(jdqSide, nullptr);
    jint* dqKind = env->GetIntArrayElements(jdqKind, nullptr);
    jint* dqRow = env->GetIntArrayElements(jdqRow, nullptr);
    jint* drRow = env->GetIntArrayElements(jdrRow, nullptr);
    jint* drCol = env->GetIntArrayElements(jdrCol, nullptr);
    jint* ziIdx = env->GetIntArrayElements(jziIdx, nullptr);
    int ncb = env->GetArrayLength(jcbIdx) / 10;
    int nob = env->GetArrayLength(jobIdx) / 6;
    int nsh = env->GetArrayLength(jshIdx) / 3;
    int nhv = env->GetArrayLength(jhvIdx) / 4;
    int ndq = env->GetArrayLength(jdqElem);
    int ndr = env->GetArrayLength(jdrRow);
    int nzi = env->GetArrayLength(jziIdx) / 11;
    int nElements = ncb + nob + nsh + nhv;

    int rec = 1;
    try {
        PatternInfo pat = computePattern(n, ncb, cbIdx, nob, obIdx, nsh, shIdx, nhv, hvIdx,
                                         ndq, dqElem, dqSide, dqKind, dqRow, ndr, drRow, drCol, nzi, ziIdx);
        // Per-scenario device footprint: the dominant term is cuDSS's LU numeric factors
        // (the exact fill from a probe analysis, or the heuristic), plus the value/state
        // arrays (vals + x/target/F/B/dx). The LU index structure is shared across the batch.
        constexpr double SAFETY = 0.7;                       // leave headroom for cuDSS workspace + fragmentation
        double luBytes = luBytesPerScenario(n, pat);
        size_t freeBytes = 0;
        size_t totalBytes = 0;
        CUDA_CHECK(cudaMemGetInfo(&freeBytes, &totalBytes));  // after the probe freed its buffers
        double perScenarioBytes = luBytes
                + (static_cast<double>(pat.nnz) + 5.0 * n) * sizeof(double)
                + static_cast<double>(n) * sizeof(int)       // dMode
                + static_cast<double>(nElements);            // dDisabled (char)
        double budget = static_cast<double>(freeBytes) * SAFETY;
        long fit = perScenarioBytes > 0 ? static_cast<long>(budget / perScenarioBytes) : maxCap;
        rec = static_cast<int>(std::max<long>(1, std::min<long>(maxCap, fit)));
    } catch (...) {
        rec = 1;                                             // a query failure yields the safest chunk
    }

    env->ReleaseIntArrayElements(jcbIdx, cbIdx, JNI_ABORT);
    env->ReleaseIntArrayElements(jobIdx, obIdx, JNI_ABORT);
    env->ReleaseIntArrayElements(jshIdx, shIdx, JNI_ABORT);
    env->ReleaseIntArrayElements(jhvIdx, hvIdx, JNI_ABORT);
    env->ReleaseIntArrayElements(jdqElem, dqElem, JNI_ABORT);
    env->ReleaseIntArrayElements(jdqSide, dqSide, JNI_ABORT);
    env->ReleaseIntArrayElements(jdqKind, dqKind, JNI_ABORT);
    env->ReleaseIntArrayElements(jdqRow, dqRow, JNI_ABORT);
    env->ReleaseIntArrayElements(jdrRow, drRow, JNI_ABORT);
    env->ReleaseIntArrayElements(jdrCol, drCol, JNI_ABORT);
    env->ReleaseIntArrayElements(jziIdx, ziIdx, JNI_ABORT);
    return rec;
}
