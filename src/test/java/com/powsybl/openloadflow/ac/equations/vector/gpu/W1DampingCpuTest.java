/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.equations.vector.gpu;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.SparseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.ac.AcLoadFlowParameters;
import com.powsybl.openloadflow.ac.AcTargetVector;
import com.powsybl.openloadflow.ac.equations.AcEquationSystemCreator;
import com.powsybl.openloadflow.ac.equations.AcEquationType;
import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.ac.solver.AcSolverResult;
import com.powsybl.openloadflow.ac.solver.AcSolverStatus;
import com.powsybl.openloadflow.ac.solver.NewtonRaphson;
import com.powsybl.openloadflow.ac.solver.NewtonRaphsonParameters;
import com.powsybl.openloadflow.ac.solver.StateVectorScalingMode;
import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.equations.EquationVector;
import com.powsybl.openloadflow.equations.JacobianMatrix;
import com.powsybl.openloadflow.graph.EvenShiloachGraphDecrementalConnectivityFactory;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.impl.LfNetworkLoaderImpl;
import com.powsybl.openloadflow.network.util.VoltageInitializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * CPU-only: does W1 damping (OLF's {@code MAX_VOLTAGE_CHANGE} state-vector scaling, the RTE
 * default) actually REDUCE the Newton iteration count vs undamped ({@code NONE})? This must
 * be confirmed on the CPU BEFORE implementing the scaling on the GPU — damping trades step
 * size for robustness and may well INCREASE iterations on cases that already converge, only
 * helping cases where undamped Newton oscillates/diverges.
 *
 * <p>Runs OLF's own {@link NewtonRaphson} (the exact production inner solver) on PEGASE-9241
 * with DC_VALUES init, base case + a sample of single-branch N-1 contingencies, under both
 * scaling modes, and reports iteration counts to convergence (RTE damping params:
 * maxDv=0.4, maxDphi=1). No GPU required — gated only on the case file existing.
 *
 * @author Gautier Bureau {@literal <gautier.bureau at rte-france.com>}
 */
class W1DampingCpuTest {

    private static final Path CASES = GpuTestPaths.caseDir();
    private static final String CASE = "case9241pegase.xiidm.gz";
    private static final int N_CONTINGENCIES = 50;
    private static final int MAX_ITER = 30;
    private static final double MAX_DV = 0.4;       // RTE maxVoltageChangeStateVectorScalingMaxDv
    private static final double MAX_DPHI = 1.0;      // RTE maxVoltageChangeStateVectorScalingMaxDphi

    @BeforeEach
    void check() {
        assumeTrue(Files.exists(CASES.resolve(CASE)), CASE + " not found");
    }

    private record Problem(LfNetwork network, EquationSystem<AcVariableType, AcEquationType> es,
                           AcTargetVector target, EquationVector<AcVariableType, AcEquationType> eq,
                           VoltageInitializer vi) {
    }

    private static Problem load() {
        Network iidm = Network.read(CASES.resolve(CASE));
        LoadFlowParameters lfp = new LoadFlowParameters()
                .setVoltageInitMode(LoadFlowParameters.VoltageInitMode.DC_VALUES);
        OpenLoadFlowParameters olfp = OpenLoadFlowParameters.create(lfp)
                .setVoltageRemoteControl(false)
                .setLowImpedanceBranchMode(OpenLoadFlowParameters.LowImpedanceBranchMode.REPLACE_BY_MIN_IMPEDANCE_LINE);
        AcLoadFlowParameters acp = OpenLoadFlowParameters.createAcParameters(iidm, lfp, olfp,
                new SparseMatrixFactory(), new EvenShiloachGraphDecrementalConnectivityFactory<>(), false, false);
        acp.getNetworkParameters().setMaxSlackBusCount(1);
        LfNetwork network = LfNetwork.load(iidm, new LfNetworkLoaderImpl(), acp.getNetworkParameters(), ReportNode.NO_OP).get(0);
        EquationSystem<AcVariableType, AcEquationType> es =
                new AcEquationSystemCreator(network, acp.getEquationSystemCreationParameters()).create();
        return new Problem(network, es, new AcTargetVector(network, es), new EquationVector<>(es), acp.getVoltageInitializer());
    }

    /** Run OLF's NewtonRaphson once under the given scaling mode; returns [iterations, status]. */
    private static int[] runNr(Problem p, StateVectorScalingMode mode) {
        NewtonRaphsonParameters params = new NewtonRaphsonParameters()
                .setMaxIterations(MAX_ITER)
                .setStateVectorScalingMode(mode)
                .setMaxVoltageChangeStateVectorScalingMaxDv(MAX_DV)
                .setMaxVoltageChangeStateVectorScalingMaxDphi(MAX_DPHI);
        try (JacobianMatrix<AcVariableType, AcEquationType> j =
                     new JacobianMatrix<>(p.es(), new SparseMatrixFactory())) {
            NewtonRaphson nr = new NewtonRaphson(p.network(), params, p.es(), j, p.target(), p.eq(), false);
            AcSolverResult r = nr.run(p.vi(), ReportNode.NO_OP);
            return new int[] {r.getIterations(), r.getStatus() == AcSolverStatus.CONVERGED ? 1 : 0};
        }
    }

    @Test
    void pegase9241DampingVsUndamped() {
        Problem p = load();
        System.out.printf("W1 damping check on %s (DC-init) — NONE vs MAX_VOLTAGE_CHANGE (maxDv=%.1f, maxDphi=%.1f)%n",
                CASE.replace(".xiidm.gz", ""), MAX_DV, MAX_DPHI);

        int[] base0 = runNr(p, StateVectorScalingMode.NONE);
        int[] base1 = runNr(p, StateVectorScalingMode.MAX_VOLTAGE_CHANGE);
        System.out.printf("  BASE CASE: NONE = %d iters (conv=%d) | MAX_VOLTAGE_CHANGE = %d iters (conv=%d)%n",
                base0[0], base0[1], base1[0], base1[1]);

        List<LfBranch> sample = new ArrayList<>();
        for (LfBranch br : p.network().getBranches()) {
            if (!br.isDisabled() && br.getBus1() != null && br.getBus2() != null) {
                sample.add(br);
                if (sample.size() >= N_CONTINGENCIES) {
                    break;
                }
            }
        }

        int sumNone = 0;
        int sumMvc = 0;
        int convNone = 0;
        int convMvc = 0;
        int mvcFewer = 0;
        int mvcMore = 0;
        int counted = 0;
        for (LfBranch br : sample) {
            br.setDisabled(true);
            try {
                int[] rn = runNr(p, StateVectorScalingMode.NONE);
                int[] rm = runNr(p, StateVectorScalingMode.MAX_VOLTAGE_CHANGE);
                counted++;
                sumNone += rn[0];
                sumMvc += rm[0];
                convNone += rn[1];
                convMvc += rm[1];
                if (rm[1] == 1 && rn[1] == 1) {           // both converged → compare iteration counts fairly
                    if (rm[0] < rn[0]) {
                        mvcFewer++;
                    } else if (rm[0] > rn[0]) {
                        mvcMore++;
                    }
                }
            } catch (RuntimeException e) {
                // fragmenting / singular contingency — skip (the GPU routes these to CPU too)
            } finally {
                br.setDisabled(false);
            }
        }
        System.out.printf("  %d contingencies: NONE mean=%.2f iters (%d/%d converged) | "
                        + "MAX_VOLTAGE_CHANGE mean=%.2f iters (%d/%d converged)%n",
                counted, sumNone / (double) Math.max(1, counted), convNone, counted,
                sumMvc / (double) Math.max(1, counted), convMvc, counted);
        System.out.printf("  Among both-converged: MAX_VOLTAGE_CHANGE used FEWER iters in %d, MORE in %d%n",
                mvcFewer, mvcMore);
        System.out.println("  => W1 reduces iterations only if mean(MVC) < mean(NONE) and/or it converges more cases.");
    }
}
