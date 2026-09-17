/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.equations.vector.gpu;

import org.scijava.nativelib.NativeLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.regex.Pattern;

/**
 * JNI binding: a FULL Newton-Raphson AC power flow run on the GPU behind one native call.
 * The native library libolfgpu (native/cuda/jni/olf_gpu_nr_jni.cu, built by the root
 * CMakeLists — see native/build-gpu.sh) keeps the fixed-pattern CSR Jacobian, the
 * mismatch and the state resident on the device: the four generated fused kernels
 * (closed-branch / open-branch / shunt / HVDC AC emulation) fill J and F each
 * iteration, cuDSS refactorizes and solves, a kernel applies the state update.
 *
 * <p>Distribution: libolfgpu is OUR wrapper (MPL) and is bundled in the jar, built
 * location-independent (no RPATH); what is NOT redistributable is NVIDIA's cuDSS, which
 * the wrapper only references dynamically ({@code NEEDED libcudss.so.0}). At load time
 * this class therefore PRELOADS the NVIDIA chain (libcublasLt → libcublas → libcudss)
 * from the user's install — {@code olf.cudss.path} system property or {@code CUDSS_ROOT}
 * env for cuDSS, {@code olf.cuda.path} property or {@code CUDA_HOME}/{@code CUDA_PATH}/
 * {@code CUDA_TOOLKIT} env for the toolkit libs — so the dynamic linker resolves the
 * bundled wrapper's dependency from the already-loaded sonames, with no RPATH and no
 * LD_LIBRARY_PATH. If cuDSS is already resolvable (ldconfig, LD_LIBRARY_PATH, or a local
 * RPATH-embedded build), the preload is simply unnecessary and load succeeds anyway.
 *
 * <p>The {@code olf.gpu.lib} system property overrides the classpath lookup with an
 * explicit path (dev builds). {@link #isAvailable()} is false when the library or its
 * cuDSS dependency cannot be loaded, so CUDA-free builds are unaffected.
 *
 * @author Gautier Bureau {@literal <gautier.bureau at rte-france.com>}
 */
public final class GpuAcNewtonSolver {

    private static final Logger LOGGER = LoggerFactory.getLogger(GpuAcNewtonSolver.class);

    private static final boolean LOADED = load();

    private GpuAcNewtonSolver() {
    }

    private static boolean load() {
        String path = System.getProperty("olf.gpu.lib");
        if (path != null) {
            try {
                System.load(path);
                LOGGER.info("OLF GPU Newton-Raphson library loaded from olf.gpu.lib: {}", path);
                return true;
            } catch (Throwable t) {
                LOGGER.warn("Failed to load OLF GPU Newton-Raphson library from olf.gpu.lib={}: {}", path, t.toString());
                return false;
            }
        }
        preloadNvidiaChain();
        try {
            NativeLoader.loadLibrary("olfgpu");
            LOGGER.info("OLF GPU Newton-Raphson library loaded from classpath (natives/<arch>/libolfgpu)");
            return true;
        } catch (Throwable t) {
            LOGGER.debug("OLF GPU Newton-Raphson library not available: {}", t.toString());
            return false;
        }
    }

    /**
     * Best-effort preload of libolfgpu's dynamic NVIDIA dependencies, in dependency
     * order (libcublasLt → libcublas → libcudss), from the configured install dirs. A
     * library that is missing here may still resolve through the system loader paths,
     * so failures are only logged — the libolfgpu load is the real verdict.
     */
    private static void preloadNvidiaChain() {
        File cuda = firstExistingDir(
                System.getProperty("olf.cuda.path"),
                subdir(System.getenv("CUDA_HOME"), "lib64"), subdir(System.getenv("CUDA_HOME"), "lib"),
                subdir(System.getenv("CUDA_PATH"), "lib64"), subdir(System.getenv("CUDA_PATH"), "lib"),
                subdir(System.getenv("CUDA_TOOLKIT"), "lib64"), subdir(System.getenv("CUDA_TOOLKIT"), "lib"));
        File cudss = firstExistingDir(
                System.getProperty("olf.cudss.path"),
                subdir(System.getenv("CUDSS_ROOT"), "lib"));
        preloadOne("libcublasLt", cuda, cudss);
        preloadOne("libcublas", cuda, cudss);
        preloadOne("libcudss", cudss, cuda);
    }

    private static String subdir(String root, String sub) {
        return root == null ? null : root + File.separator + sub;
    }

    private static File firstExistingDir(String... paths) {
        for (String p : paths) {
            if (p != null) {
                File d = new File(p);
                if (d.isDirectory()) {
                    return d;
                }
            }
        }
        return null;
    }

    /** Load {@code <base>.so.<major>} (the soname form) from the first dir that has it. */
    private static void preloadOne(String base, File... dirs) {
        Pattern soname = Pattern.compile(Pattern.quote(base) + "\\.so\\.\\d+");
        for (File dir : dirs) {
            if (dir == null) {
                continue;
            }
            File[] candidates = dir.listFiles((d, name) -> soname.matcher(name).matches());
            File lib = candidates != null && candidates.length > 0 ? candidates[0]
                    : new File(dir, base + ".so");                  // unversioned symlink fallback
            if (lib.isFile()) {
                try {
                    System.load(lib.getAbsolutePath());
                    LOGGER.debug("Preloaded {} for libolfgpu", lib);
                    return;
                } catch (Throwable t) {
                    LOGGER.debug("Preload of {} failed: {}", lib, t.toString());
                }
            }
        }
    }

    public static boolean isAvailable() {
        return LOADED;
    }

    /**
     * Build a CACHED solver context for one equation-system STRUCTURE: the fixed SUPERSET
     * CSR pattern (every row carries its full power-equation entries + a guaranteed
     * diagonal), the destination arrays, the device buffers and the cuDSS objects all
     * persist in it, and cuDSS's symbolic ANALYSIS is done once — subsequent
     * {@link #solveContext} calls only refactorize. Rows use the STABLE indexing (the
     * diagonal-paired variable row, see GpuAcDataExtractor), so the context survives
     * activation switching; element counts are implied by the index array lengths and
     * -1 marks an absent branch side.
     *
     * @param n     number of variables / equations
     * @param cbIn  closed branches, {@code ncb*8}: (y g1 g2 b1 b2 ksi r1 a1)
     * @param cbIdx closed branches, {@code ncb*10}: (Prow1 Qrow1 Prow2 Qrow2 ph1col v1col ph2col v2col a1col r1col)
     * @param obIn  open branches, {@code nob*7}: (y ksi g1 b1 g2 b2 r1)
     * @param obIdx open branches, {@code nob*6}: (Prow1 Qrow1 Prow2 Qrow2 v1col v2col)
     * @param shIn  shunts, {@code nsh*2}: (g b)
     * @param shIdx shunts, {@code nsh*3}: (Prow Qrow vcol)
     * @param hvIn  HVDC AC emulation, {@code nhv*5}: (p0 k lossFactor1 lossFactor2 r)
     * @param hvIdx HVDC AC emulation, {@code nhv*4}: (Prow1 Prow2 ph1col ph2col)
     * @return an opaque native handle; release it with {@link #destroyContext}
     */
    public static native long createContext(int n,
                                            double[] cbIn, int[] cbIdx, double[] obIn, int[] obIdx,
                                            double[] shIn, int[] shIdx, double[] hvIn, int[] hvIdx,
                                            int[] dqElem, int[] dqSide, double[] dqWeight, int[] dqRow, int[] dqKind,
                                            double[] ziIn, int[] ziIdx,
                                            int[] drRow, int[] drCol, double[] drCoef);

    /**
     * Run a full Newton-Raphson AC power flow on the GPU against a cached context — only
     * the initial state, the targets and the activation mask cross JNI.
     *
     * @param handle  a context from {@link #createContext}
     * @param x0      initial state, indexed by variable row (length n)
     * @param target  targets in the STABLE row order (GpuAcDataExtractor#extractTargets)
     * @param rowMode per row: 1 = power equation, 0 = slack/PV identity (the device mask
     *                zeroes the row, sets the diagonal to 1 and F = x - target)
     * @param maxIter maximum Newton iterations
     * @param tol     convergence tolerance on the mismatch infinity norm
     * @param lsMode  device step scaling: 0 = none, 1 = line search, 2 = max voltage change
     * @param maxDv   MAX_VOLTAGE_CHANGE (lsMode 2): clamp on |Δv| per iteration (BUS_V rows)
     * @param maxDphi MAX_VOLTAGE_CHANGE (lsMode 2): clamp on |Δφ| per iteration (BUS_PHI rows, radians)
     * @param varType per row, the variable kind for the maxVoltageChange clamp (0 = BUS_PHI,
     *                1 = BUS_V, 2 = other); see GpuAcDataExtractor#extractVarType. Unused for lsMode 0/1.
     * @return {@code double[n + 2]}: the converged state, then iterations, then the final
     *         mismatch infinity norm
     */
    public static native double[] solveContext(long handle, double[] x0, double[] target,
                                               int[] rowMode, int maxIter, double tol,
                                               int lsMode, int lsMaxIter, double lsStepFold,
                                               double maxDv, double maxDphi, int[] varType);

    /**
     * VALUES-level refresh: re-upload only the per-element parameter packs into an existing context,
     * keeping its CSR pattern and cuDSS symbolic analysis. Valid only when the element counts / index
     * packs are unchanged (a transformer tap or shunt-susceptance move) — the caller guarantees this by
     * taking this path only when {@link GpuAcData#sameStructureAs} holds. Far cheaper than a rebuild.
     */
    public static native void refreshContextValues(long handle, double[] cbIn, double[] obIn,
                                                   double[] shIn, double[] hvIn);

    /** Release a context from {@link #createContext} (idempotent for handle 0). */
    public static native void destroyContext(long handle);

    /**
     * Recommend how many scenarios fit in one GPU batch chunk for this structure, from the
     * free device memory ({@code cudaMemGetInfo}) and the per-scenario footprint. The
     * dominant term, cuDSS's LU numeric factors, is sized from a cheap single-matrix probe
     * ANALYSIS on the shared pattern that reads cuDSS's reported {@code CUDSS_DATA_LU_NNZ}
     * (the exact fill-in), falling back to a heuristic multiplier over the Jacobian nnz only
     * if that query is unavailable. The value/state arrays are added and a safety margin
     * applied; the result is clamped to {@code [1, maxCap]}. Index packs only — the pattern
     * (hence nnz and the LU fill) depends on structure, not values.
     *
     * @param n      number of variables / equations
     * @param cbIdx  closed-branch index pack ({@code ncb*10})
     * @param obIdx  open-branch index pack ({@code nob*6})
     * @param shIdx  shunt index pack ({@code nsh*3})
     * @param hvIdx  HVDC index pack ({@code nhv*4})
     * @param maxCap absolute upper bound on the returned chunk size
     * @return a recommended chunk size in {@code [1, maxCap]}
     */
    public static native int recommendBatchSize(int n, int[] cbIdx, int[] obIdx, int[] shIdx, int[] hvIdx,
                                                 int[] dqElem, int[] dqSide, int[] dqKind, int[] dqRow,
                                                 int[] drRow, int[] drCol, int[] ziIdx, int maxCap);

    /**
     * One-shot convenience: {@link #createContext} + {@link #solveContext} +
     * {@link #destroyContext}. For repeated solves on the same structure (outer loops),
     * cache the context instead — see GpuNewtonRaphson.
     */
    public static double[] solve(double[] x0, double[] target, int[] rowMode, int maxIter, double tol,
                                 double[] cbIn, int[] cbIdx, double[] obIn, int[] obIdx,
                                 double[] shIn, int[] shIdx, double[] hvIn, int[] hvIdx,
                                 int[] dqElem, int[] dqSide, double[] dqWeight, int[] dqRow, int[] dqKind,
                                 double[] ziIn, int[] ziIdx,
                                 int[] drRow, int[] drCol, double[] drCoef) {
        long handle = createContext(x0.length, cbIn, cbIdx, obIn, obIdx, shIn, shIdx, hvIn, hvIdx,
                dqElem, dqSide, dqWeight, dqRow, dqKind, ziIn, ziIdx, drRow, drCol, drCoef);
        try {
            // undamped (no device step scaling): lsMode 0 ignores maxDv/maxDphi/varType
            return solveContext(handle, x0, target, rowMode, maxIter, tol, 0, 0, 0.0, 0.0, 0.0, new int[x0.length]);
        } finally {
            destroyContext(handle);
        }
    }

    /**
     * Build a CACHED solver context for BATCHED N-1 security analysis: multiple scenarios
     * (post-contingency states) against the same network STRUCTURE and superset pattern.
     * Per-scenario element disablement is encoded as device masks; converged scenarios
     * can stop early. cuDSS's symbolic analysis is done once over the superset pattern.
     *
     * @param n           number of variables / equations
     * @param nScenarios  number of contingency scenarios to solve in parallel
     * @param cbIn        closed branches, {@code ncb*8}: (y g1 g2 b1 b2 ksi r1 a1)
     * @param cbIdx       closed branches, {@code ncb*10}: (Prow1 Qrow1 Prow2 Qrow2 ph1col v1col ph2col v2col a1col r1col)
     * @param obIn        open branches, {@code nob*7}: (y ksi g1 b1 g2 b2 r1)
     * @param obIdx       open branches, {@code nob*6}: (Prow1 Qrow1 Prow2 Qrow2 v1col v2col)
     * @param shIn        shunts, {@code nsh*2}: (g b)
     * @param shIdx       shunts, {@code nsh*3}: (Prow Qrow vcol)
     * @param hvIn        HVDC AC emulation, {@code nhv*5}: (p0 k lossFactor1 lossFactor2 r)
     * @param hvIdx       HVDC AC emulation, {@code nhv*4}: (Prow1 Prow2 ph1col ph2col)
     * @return an opaque native handle for batched solves; release with {@link #destroyBatchContext}
     */
    public static native long createBatchContext(int n, int nScenarios, int mode,
                                                  double[] cbIn, int[] cbIdx, double[] obIn, int[] obIdx,
                                                  double[] shIn, int[] shIdx, double[] hvIn, int[] hvIdx,
                                                  int[] rlVrow, double[] rlMinQ, double[] rlMaxQ, double[] rlLoadQ,
                                                  double[] rlVtarget, int[] dsPhiRow, double[] dsFactor,
                                                  int dsSlackPhiRow, double dsSlackTargetP,
                                                  double[] dsBaseTargetP, double[] dsMaxDelta, double[] dsMinDelta,
                                                  int[] dqElem, int[] dqSide, double[] dqWeight, int[] dqRow, int[] dqKind,
                                                  int[] drRow, int[] drCol, double[] drCoef,
                                                  double[] ziIn, int[] ziIdx);

    /**
     * Run batched Newton-Raphson AC power flow on the GPU: one iteration loop over all
     * scenarios, with per-scenario convergence retirement (stop filling/solving once
     * converged). Scenarios remain in the batch until they converge or hit maxIter.
     *
     * <p>The base state/targets are SHARED by every scenario (a single-branch N-1 starts from the same
     * converged base operating point): they are passed as single length-{@code n} rows and tiled across
     * the batch on-device. The row-mode is PER-SCENARIO ({@code [nScenarios][n]}) so a contingency can
     * freeze islanded buses or flip PV↔PQ (reactive limits) independently; it is uploaded as-is and
     * mutated in place on-device by the outer-loop kernels. The disabled-element mask is also
     * per-scenario. {@code nScenarios} is {@code disabledElementMask.length}.
     *
     * @param handle              a batch context from {@link #createBatchContext}
     * @param x0Base              shared initial state, {@code [n]}: tiled to every scenario on-device
     * @param targetBase          shared targets, {@code [n]}: in STABLE row order, tiled per scenario
     * @param rowMode             per-scenario row modes, {@code [nScenarios][n]}: 1 = equation, 0 = identity
     *                            (islanded buses frozen, slack/PV identity rows)
     * @param disabledElementMask per-scenario disabled elements, {@code [nScenarios][nElements]}: bit-packed
     *                            (true if that element is outaged in this scenario; elements enumerated
     *                            as: first ncb closed branches, then nob open branches, then nsh shunts, then nhv HVDCs)
     * @param maxIter             maximum Newton iterations per scenario
     * @param tol                 convergence tolerance on mismatch infinity norm (the device floor)
     * @param convEpsPerEq        if {@code > 0}, the inner-Newton convergence test uses OLF's EXACT criterion
     *                            ({@code DefaultNewtonRaphsonStoppingCriteria}: {@code ‖F‖₂ < sqrt(convEpsPerEq² · n)})
     *                            instead of the infinity-norm floor — so a batched SA can stop on the SAME
     *                            decision as the CPU loadflow (apples-to-apples timing). {@code <= 0} keeps the
     *                            {@code tol} infinity-norm floor.
     * @return {@code [nScenarios][n + 3]}: per-scenario [state(n), iterations, finalMismatch, convergenceStatus]
     *         where convergenceStatus is: 0 = converged, 1 = max_iter, -1 = diverged/NaN
     */
    public static native double[][] solveBatchContext(long handle, double[] x0Base, double[] targetBase,
                                                       int[][] rowMode, int[] baseRowMode, boolean[][] disabledElementMask,
                                                       int[] targetOverrideIndex, double[] targetOverrideValue,
                                                       int[] dsOverrideBus, double[] dsOverrideFactorDelta,
                                                       double[] dsOverrideBaseDelta, double[] dsOverrideMaxDelta,
                                                       double[] dsOverrideMinDelta, int maxIter, double tol,
                                                       double reactiveLimitTol, double slackDistTol, double convEpsPerEq);

    /** Release a batch context from {@link #createBatchContext} (idempotent for handle 0). */
    public static native void destroyBatchContext(long handle);

    /**
     * One-shot batch convenience: {@link #createBatchContext} + {@link #solveBatchContext} +
     * {@link #destroyBatchContext}. For repeated batches on the same structure,
     * cache the context instead. The base state/targets/row-mode are shared by every scenario
     * (tiled on-device); {@code nScenarios} is {@code disabledElementMask.length}.
     */
    public static double[][] solveBatch(double[] x0Base, double[] targetBase, int[][] rowMode,
                                         boolean[][] disabledElementMask, int mode, int maxIter, double tol,
                                         double reactiveLimitTol, double slackDistTol,
                                         double[] cbIn, int[] cbIdx, double[] obIn, int[] obIdx,
                                         double[] shIn, int[] shIdx, double[] hvIn, int[] hvIdx,
                                         int[] rlVrow, double[] rlMinQ, double[] rlMaxQ, double[] rlLoadQ,
                                         double[] rlVtarget, int[] dsPhiRow, double[] dsFactor,
                                         int dsSlackPhiRow, double dsSlackTargetP,
                                         double[] dsBaseTargetP, double[] dsMaxDelta, double[] dsMinDelta,
                                         int[] dqElem, int[] dqSide, double[] dqWeight, int[] dqRow, int[] dqKind,
                                         int[] drRow, int[] drCol, double[] drCoef, double[] ziIn, int[] ziIdx) {
        int n = x0Base.length;
        int nScenarios = disabledElementMask.length;
        long handle = createBatchContext(n, nScenarios, mode, cbIn, cbIdx, obIn, obIdx, shIn, shIdx, hvIn, hvIdx,
                rlVrow, rlMinQ, rlMaxQ, rlLoadQ, rlVtarget, dsPhiRow, dsFactor, dsSlackPhiRow, dsSlackTargetP,
                dsBaseTargetP, dsMaxDelta, dsMinDelta, dqElem, dqSide, dqWeight, dqRow, dqKind,
                drRow, drCol, drCoef, ziIn, ziIdx);
        try {
            // one-shot: no per-scenario gen-override flips, so any scenario's row mode IS the base row mode
            return solveBatchContext(handle, x0Base, targetBase, rowMode, rowMode[0], disabledElementMask,
                    new int[0], new double[0], new int[0], new double[0], new double[0], new double[0], new double[0],
                    maxIter, tol, reactiveLimitTol, slackDistTol, 0.0);
        } finally {
            destroyBatchContext(handle);
        }
    }
}
