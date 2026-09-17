/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.solver;

import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.report.ReportNode;
import com.powsybl.iidm.network.TwoSides;
import com.powsybl.openloadflow.ac.equations.AcEquationType;
import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.ac.equations.vector.gpu.GpuAcActivation;
import com.powsybl.openloadflow.ac.equations.vector.gpu.GpuAcData;
import com.powsybl.openloadflow.ac.equations.vector.gpu.GpuAcDataExtractor;
import com.powsybl.openloadflow.ac.equations.vector.gpu.GpuAcNewtonSolver;
import com.powsybl.openloadflow.equations.EquationArray;
import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.equations.EquationSystemIndex;
import com.powsybl.openloadflow.equations.EquationSystemIndexListener;
import com.powsybl.openloadflow.equations.EquationTermArray;
import com.powsybl.openloadflow.equations.EquationVector;
import com.powsybl.openloadflow.equations.JacobianMatrix;
import com.powsybl.openloadflow.equations.SingleEquation;
import com.powsybl.openloadflow.equations.SingleEquationTerm;
import com.powsybl.openloadflow.equations.TargetVector;
import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.network.GeneratorVoltageControl;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfElement;
import com.powsybl.openloadflow.network.LfGenerator;
import com.powsybl.openloadflow.network.LfHvdc;
import com.powsybl.openloadflow.network.LfLoad;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.LfNetworkListener;
import com.powsybl.openloadflow.network.LfShunt;
import com.powsybl.openloadflow.network.LfZeroImpedanceNetwork;
import com.powsybl.openloadflow.network.LoadFlowModel;
import com.powsybl.openloadflow.network.TransformerVoltageControl;
import com.powsybl.openloadflow.network.util.VoltageInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.Cleaner;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Newton-Raphson run ENTIRELY on the GPU behind one JNI call ({@link GpuAcNewtonSolver}):
 * the fixed superset-pattern CSR Jacobian and the mismatch are filled on the device by
 * the generated fused kernels (closed-branch / open-branch / shunt / HVDC AC emulation),
 * cuDSS analyzes once and refactorizes/solves per iteration, the state update is a
 * kernel.
 *
 * <p>Invalidation is EVENT-DRIVEN, mirroring {@link JacobianMatrix}'s pattern (the same
 * {@link EquationSystemIndexListener} / {@link LfNetworkListener} interfaces, with this
 * consumer's own granularity): structural events (topology, branch parameters) force a
 * full re-extract + device context rebuild; activation events (PV→PQ switching, slack
 * change) only refresh the row-mode mask and target mapping; with no events, a rerun
 * goes straight to the device solve — targets and state are re-read every solve. Both
 * interfaces are implemented DIRECTLY (not via the no-op adapter) so any event added to
 * OLF later forces a compile-time classification here, keeping invalidation conservative.
 *
 * <p>Scope: plain AC power flow. {@link GpuAcDataExtractor} validates the equation
 * system on every structural rebuild and throws on anything not covered; an unsupported
 * equation type ACTIVATED between rebuilds fails loudly in the activation walk. The
 * device loop iterates to an infinity-norm mismatch of {@value GPU_TOLERANCE} (stricter
 * than the usual stopping criteria); the final CONVERGED/MAX_ITERATION_REACHED status is
 * then decided by the CONFIGURED stopping criteria, evaluated on the CPU from the
 * written-back state, exactly as for the CPU Newton-Raphson. State-vector scaling
 * options are not applied (no line search on the device).
 *
 * @author Gautier Bureau {@literal <gautier.bureau at rte-france.com>}
 */
public class GpuNewtonRaphson extends AbstractAcSolver {

    private static final Logger LOGGER = LoggerFactory.getLogger(GpuNewtonRaphson.class);

    private static final Cleaner CLEANER = Cleaner.create();

    protected static final double GPU_TOLERANCE = 1e-8;

    // Diagnostics for the invalidation taxonomy: how the cached native context is reconciled across
    // solves — STRUCTURE rebuilds (pattern + cuDSS re-analysis) vs cheap VALUES re-uploads (a transformer
    // tap / shunt-susceptance move keeps the pattern, only the element packs change). Process-wide totals.
    private static final java.util.concurrent.atomic.AtomicLong CONTEXT_REBUILDS = new java.util.concurrent.atomic.AtomicLong();
    private static final java.util.concurrent.atomic.AtomicLong VALUE_REFRESHES = new java.util.concurrent.atomic.AtomicLong();

    public static long contextRebuildCount() {
        return CONTEXT_REBUILDS.get();
    }

    public static long valueRefreshCount() {
        return VALUE_REFRESHES.get();
    }

    protected final NewtonRaphsonParameters parameters;

    /**
     * The cached native solver context (superset CSR pattern, device buffers, cuDSS
     * analysis) + the listener deregistration, released when this solver becomes
     * unreachable (the AcSolver SPI has no close hook). The Runnable must not reference
     * the solver itself.
     */
    private static final class NativeContext implements Runnable {
        private final AtomicLong handle = new AtomicLong();
        private Runnable removeListeners;

        @Override
        public void run() {
            GpuAcNewtonSolver.destroyContext(handle.getAndSet(0));
            if (removeListeners != null) {
                removeListeners.run();
            }
        }
    }

    private final NativeContext context = new NativeContext();

    private final Invalidation invalidation = new Invalidation();

    private GpuAcData cachedData;

    private GpuAcActivation activation;

    // per-row variable kind (0=BUS_PHI, 1=BUS_V, 2=other) for the MAX_VOLTAGE_CHANGE device step
    // clamp; structural, recomputed whenever the activation/structure is rebuilt
    private int[] varType;

    public GpuNewtonRaphson(LfNetwork network, NewtonRaphsonParameters parameters,
                            EquationSystem<AcVariableType, AcEquationType> equationSystem,
                            JacobianMatrix<AcVariableType, AcEquationType> j,
                            TargetVector<AcVariableType, AcEquationType> targetVector,
                            EquationVector<AcVariableType, AcEquationType> equationVector,
                            boolean detailedReport) {
        super(network, equationSystem, j, targetVector, equationVector, detailedReport);
        this.parameters = Objects.requireNonNull(parameters);
        EquationSystemIndex<AcVariableType, AcEquationType> index = equationSystem.getIndex();
        index.addListener(invalidation);
        network.addListener(invalidation);
        // the cleaning action must not reference the solver itself (it would never become
        // phantom-reachable); the Invalidation listener object is referenced instead
        Invalidation listener = invalidation;
        context.removeListeners = () -> {
            index.removeListener(listener);
            network.removeListener(listener);
        };
        CLEANER.register(this, context);
    }

    @Override
    public String getName() {
        return "GPU Newton-Raphson";
    }

    @Override
    public AcSolverResult run(VoltageInitializer voltageInitializer, ReportNode reportNode) {
        if (!GpuAcNewtonSolver.isAvailable()) {
            throw new PowsyblException("GPU Newton-Raphson requested but the native library is not available "
                    + "(build with native/build-gpu.sh and provide cuDSS — see GpuAcNewtonSolver)");
        }
        AcSolverUtil.initStateVector(network, equationSystem, voltageInitializer);

        AcSolverStatus status;
        int iterations = 0;
        int n = equationSystem.getIndex().getSortedVariablesToFind().size();
        try {
            long t0 = System.nanoTime();
            boolean rebuilt = false;
            if (invalidation.structureDirty) {
                GpuAcData data = GpuAcDataExtractor.extract(network, equationSystem);   // validate + build
                // Three-level reconciliation, decided by the DATA not the event: a changed CSR pattern
                // (index packs) is STRUCTURE -> rebuild + cuDSS re-analysis; an unchanged pattern with
                // only changed element PARAMETERS (tap r1/a1, shunt b) is VALUES -> a cheap in-place
                // pack re-upload reusing the analysis; an unchanged data is a no-op (context reused).
                if (!data.sameStructureAs(cachedData)) {
                    GpuAcNewtonSolver.destroyContext(context.handle.getAndSet(0));
                    context.handle.set(GpuAcNewtonSolver.createContext(n,
                            data.cbIn(), data.cbIdx(), data.obIn(), data.obIdx(),
                            data.shIn(), data.shIdx(), data.hvIn(), data.hvIdx(),
                            data.dqElem(), data.dqSide(), data.dqWeight(), data.dqRow(), data.dqKind(),
                            data.ziIn(), data.ziIdx(),
                            data.drRow(), data.drCol(), data.drCoef()));
                    cachedData = data;
                    rebuilt = true;
                    CONTEXT_REBUILDS.incrementAndGet();
                } else if (!data.sameAs(cachedData)) {
                    GpuAcNewtonSolver.refreshContextValues(context.handle.get(),
                            data.cbIn(), data.obIn(), data.shIn(), data.hvIn());
                    cachedData = data;
                    VALUE_REFRESHES.incrementAndGet();
                }
                invalidation.structureDirty = false;
                invalidation.activationDirty = true;
            }
            if (invalidation.activationDirty) {
                activation = GpuAcDataExtractor.extractActivation(network, equationSystem, n);
                varType = GpuAcDataExtractor.extractVarType(equationSystem, n);
                invalidation.activationDirty = false;
            }
            double[] target = activation.applyTargets(targetVector.getArray());
            double[] x0 = equationSystem.getStateVector().get().clone();
            // Device-side state-vector scaling: NONE = undamped Newton; LINE_SEARCH backtracks on the
            // device (mirrors LineSearchStateVectorScaling); MAX_VOLTAGE_CHANGE clamps each step so the
            // largest |Δv|/|Δφ| stays within maxDv/maxDphi (mirrors MaxVoltageChangeStateVectorScaling).
            int lsMode = switch (parameters.getStateVectorScalingMode()) {
                case NONE -> 0;
                case LINE_SEARCH -> 1;
                case MAX_VOLTAGE_CHANGE -> 2;
            };
            long t1 = System.nanoTime();
            double[] out = GpuAcNewtonSolver.solveContext(context.handle.get(),
                    x0, target, activation.rowMode(), parameters.getMaxIterations(), GPU_TOLERANCE,
                    lsMode, parameters.getLineSearchStateVectorScalingMaxIteration(),
                    parameters.getLineSearchStateVectorScalingStepFold(),
                    parameters.getMaxVoltageChangeStateVectorScalingMaxDv(),
                    parameters.getMaxVoltageChangeStateVectorScalingMaxDphi(),
                    varType);
            long t2 = System.nanoTime();
            LOGGER.debug("GPU Newton-Raphson timing: prepare {} ms (context {}), solve {} ms",
                    (t1 - t0) / 1_000_000, rebuilt ? "rebuilt" : "reused", (t2 - t1) / 1_000_000);
            iterations = (int) out[n];
            // write the device solution back; the equation vector re-evaluates from it
            equationSystem.getStateVector().set(Arrays.copyOf(out, n));
            equationVector.minus(targetVector);
            NewtonRaphsonStoppingCriteria.TestResult result =
                    parameters.getStoppingCriteria().test(equationVector.getArray(), equationSystem);
            status = result.isStop() ? AcSolverStatus.CONVERGED : AcSolverStatus.MAX_ITERATION_REACHED;
            LOGGER.debug("GPU Newton-Raphson: {} iterations, |f(x)|={}, {}", iterations, result.getNorm(), status);
        } catch (RuntimeException e) {
            LOGGER.error("GPU Newton-Raphson failed: {}", e.toString());
            // the native context may be mid-iteration inconsistent — rebuild next run
            GpuAcNewtonSolver.destroyContext(context.handle.getAndSet(0));
            cachedData = null;
            invalidation.structureDirty = true;
            status = AcSolverStatus.SOLVER_FAILED;
        }

        if (status == AcSolverStatus.CONVERGED || parameters.isAlwaysUpdateNetwork()) {
            AcSolverUtil.updateNetwork(network, equationSystem);
        }

        double slackBusActivePowerMismatch = network.getSlackBuses().stream().mapToDouble(LfBus::getMismatchP).sum();
        return new AcSolverResult(status, iterations, slackBusActivePowerMismatch);
    }

    /**
     * The event-driven invalidation state, a SEPARATE listener object (the Cleaner action
     * references it for deregistration — referencing the solver itself would keep it
     * strongly reachable forever and the cleanup would never run).
     */
    private static final class Invalidation
            implements EquationSystemIndexListener<AcVariableType, AcEquationType>, LfNetworkListener {

        private boolean structureDirty = true;

        private boolean activationDirty = true;

        // ---------------------------------------------------------------------------------
        // Event classification. STRUCTURE = the extracted element packs / variable rows may
        // have changed -> full re-extract + validation. run() then splits STRUCTURE into two
        // levels by comparing the fresh data: a changed CSR pattern (index packs) rebuilds +
        // re-analyzes, while an unchanged pattern with only changed element PARAMETERS (a tap or
        // shunt-b move) takes the cheap VALUES re-upload (refreshContextValues, analysis reused).
        // ACTIVATION = only which equations are active changed -> refresh rowMode/targetMap.
        // Target-value events need nothing: targets are re-read from the TargetVector (which
        // maintains itself through its own listeners) on every solve.
        // ---------------------------------------------------------------------------------

        private void structureChanged() {
            structureDirty = true;
        }

        private void activationChanged() {
            activationDirty = true;
        }

        // --- EquationSystemIndexListener ---

        @Override
        public void onVariableChange(Variable<AcVariableType> variable, ChangeType changeType) {
            structureChanged();                                  // variable rows shift
        }

        @Override
        public void onEquationChange(SingleEquation<AcVariableType, AcEquationType> equation, ChangeType changeType) {
            activationChanged();                                 // single equation (de)activated
        }

        @Override
        public void onEquationTermChange(SingleEquationTerm<AcVariableType, AcEquationType> term) {
            if (term instanceof com.powsybl.openloadflow.equations.VariableEquationTerm) {
                activationChanged();                             // identity-term toggle = PV→PQ side effect
            } else {
                structureChanged();                              // any other term toggling (open/closed switching)
            }
        }

        @Override
        public void onEquationArrayChange(EquationArray<AcVariableType, AcEquationType> equationArray, ChangeType changeType) {
            activationChanged();                                 // array element (de)activated (PV→PQ)
        }

        @Override
        public void onEquationTermArrayChange(EquationTermArray<AcVariableType, AcEquationType> equationTermArray,
                                              int termNum, ChangeType changeType) {
            structureChanged();                                  // array term toggling
        }

        @Override
        public void onEquationIndexOrderChanged() {
            activationChanged();                                 // OLF equation columns renumbered:
        }                                                        // structure is column-free, targetMap is not

        // --- LfNetworkListener ---

        @Override
        public void onGeneratorVoltageControlChange(LfBus controllerBus, boolean newVoltageControllerEnabled) {
            activationChanged();                                 // PV↔PQ
        }

        @Override
        public void onGeneratorVoltageControlTargetChange(GeneratorVoltageControl control, double newTargetVoltage) {
            // target values are re-read every solve
        }

        @Override
        public void onGeneratorReactivePowerControlChange(LfBus controllerBus, boolean newReactiveControllerEnabled) {
            structureChanged();
        }

        @Override
        public void onTransformerPhaseControlChange(LfBranch controllerBranch, boolean newPhaseControlEnabled) {
            structureChanged();
        }

        @Override
        public void onTransformerVoltageControlChange(LfBranch controllerBranch, boolean newVoltageControllerEnabled) {
            structureChanged();
        }

        @Override
        public void onTransformerVoltageControlTargetChange(TransformerVoltageControl transformerVoltageControl,
                                                            double newTargetVoltage) {
            // target values are re-read every solve
        }

        @Override
        public void onShuntVoltageControlChange(LfShunt controllerShunt, boolean newVoltageControllerEnabled) {
            structureChanged();
        }

        @Override
        public void onLoadActivePowerTargetChange(LfLoad load, double oldTargetP, double newTargetP) {
            // target values are re-read every solve
        }

        @Override
        public void onLoadReactivePowerTargetChange(LfLoad load, double oldTargetQ, double newTargetQ) {
            // target values are re-read every solve
        }

        @Override
        public void onGenerationActivePowerTargetChange(LfGenerator generator, double oldGenerationTargetP,
                                                        double newGenerationTargetP) {
            // target values are re-read every solve
        }

        @Override
        public void onGenerationReactivePowerTargetChange(LfBus bus, double oldGenerationTargetQ,
                                                          double newGenerationTargetQ) {
            // target values are re-read every solve
        }

        @Override
        public void onDisableChange(LfElement element, boolean disabled) {
            structureChanged();
        }

        @Override
        public void onTapPositionChange(LfBranch branch, int oldPosition, int newPosition) {
            structureChanged();                                  // r1/a1 in cbIn; run() downgrades to VALUES
        }

        @Override
        public void onShuntSusceptanceChange(LfShunt shunt, double b) {
            structureChanged();                                  // b in shIn; run() downgrades to VALUES
        }

        @Override
        public void onZeroImpedanceNetworkSpanningTreeChange(LfBranch branch, LoadFlowModel loadFlowModel,
                                                             boolean spanningTree) {
            structureChanged();
        }

        @Override
        public void onZeroImpedanceNetworkSplit(LfZeroImpedanceNetwork initialNetwork,
                                                List<LfZeroImpedanceNetwork> splitNetworks, LoadFlowModel loadFlowModel) {
            structureChanged();
        }

        @Override
        public void onZeroImpedanceNetworkMerge(LfZeroImpedanceNetwork network1, LfZeroImpedanceNetwork network2,
                                                LfZeroImpedanceNetwork mergedNetwork, LoadFlowModel loadFlowModel) {
            structureChanged();
        }

        @Override
        public void onBranchConnectionStatusChange(LfBranch branch, TwoSides side, boolean connected) {
            structureChanged();
        }

        @Override
        public void onSlackBusChange(LfBus bus, boolean slack) {
            activationChanged();                                 // PHI identity moves
        }

        @Override
        public void onReferenceBusChange(LfBus bus, boolean reference) {
            activationChanged();
        }

        @Override
        public void onHvdcAcEmulationStatusChange(LfHvdc hvdc, LfHvdc.AcEmulationControl.AcEmulationStatus acEmulationStatus) {
            structureChanged();
        }
    }
}
