/**
 * Copyright (c) 2026, Elia Group (https://www.eliagroup.eu)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.se;

import com.powsybl.commons.PowsyblException;
import com.powsybl.ieeecdf.converter.IeeeCdfNetworkFactory;
import com.powsybl.iidm.network.Line;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.ShuntCompensator;
import com.powsybl.iidm.network.ShuntCompensatorNonLinearModel;
import com.powsybl.iidm.network.TopologyKind;
import com.powsybl.iidm.network.TwoSides;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.math.matrix.LUDecomposition;
import com.powsybl.math.matrix.Matrix;
import com.powsybl.math.matrix.SparseMatrix;
import com.powsybl.math.matrix.SparseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.ac.equations.AcEquationType;
import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.dc.DcValueVoltageInitializer;
import com.powsybl.openloadflow.dc.equations.DcApproximationType;
import com.powsybl.openloadflow.equations.JacobianMatrix;
import com.powsybl.openloadflow.network.FirstSlackBusSelector;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.LfNetworkParameters;
import com.powsybl.openloadflow.network.impl.Networks;
import com.powsybl.openloadflow.network.util.PreviousValueVoltageInitializer;
import com.powsybl.openloadflow.network.util.UniformValueVoltageInitializer;
import com.powsybl.openloadflow.se.solver.GainFactorization;
import com.powsybl.openloadflow.se.solver.LuGainFactorization;
import com.powsybl.openloadflow.se.solver.WlsParameters;
import com.powsybl.openloadflow.se.solver.WlsProblem;
import com.powsybl.openloadflow.se.solver.WlsSolver;
import com.powsybl.openloadflow.se.solver.WlsSolverFactory;
import com.powsybl.openloadflow.se.solver.WlsSolverResult;
import com.powsybl.openloadflow.se.solver.WlsSolverStatus;
import com.powsybl.openloadflow.test.networks.ShuntNetworkFactory;
import com.powsybl.openloadflow.util.PerUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A converged load flow gives a state whose answer is known independently. Meters
 * reading it without error must lead the estimator back to it from a flat start: the
 * least-squares optimum of a consistent, noise-free measurement set is the state that
 * produced it.
 *
 * @author Šime Pavlić {@literal <sime.pavlic at kickstage.com>}
 */
class WlsStateEstimatorTest {

    private LfNetwork lfNetwork;

    private double[] trueV;

    private double[] trueAngle;

    @BeforeEach
    void setUp() {
        solveAndLoad(IeeeCdfNetworkFactory.create14());
    }

    /** Makes the load-flow state of this network the one the meter sets of this class read. */
    private void solveAndLoad(Network network) {
        LoadFlow.Runner runner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        LoadFlowResult result = runner.run(network, new LoadFlowParameters());
        assertTrue(result.isFullyConverged(), "the reference load flow must converge");

        lfNetwork = Networks.load(network, new FirstSlackBusSelector()).get(0);
        int busCount = lfNetwork.getBuses().size();
        trueV = new double[busCount];
        trueAngle = new double[busCount];
        for (LfBus bus : lfNetwork.getBuses()) {
            trueV[bus.getNum()] = bus.getV();
            trueAngle[bus.getNum()] = bus.getAngle();
        }
    }

    @Test
    void recoversTheLoadFlowStateFromAFlatStart() {
        List<LfMeasurement> measurements = withNoiseFreeValues(describeRedundantMeterSet());

        WlsStateEstimator estimator = new WlsStateEstimator(lfNetwork, measurements);
        estimator.init(new UniformValueVoltageInitializer());
        WlsResult result = estimator.estimate();

        assertTrue(result.isConverged(), "the estimate must converge from a flat start");
        assertTrue(result.getIterationCount() <= 6,
                "expected a handful of iterations, got " + result.getIterationCount());

        // the result is in IIDM units and keyed by identifier, so compare through the network
        for (LfBus b : lfNetwork.getBuses()) {
            WlsResult.BusResult busResult = busResult(result, b.getId());
            assertEquals(trueV[b.getNum()] * b.getNominalV(), busResult.v(), 1e-6,
                    "voltage magnitude at " + b.getId());
            assertEquals(Math.toDegrees(trueAngle[b.getNum()]), busResult.angle(), 1e-6,
                    "voltage angle at " + b.getId());
            assertEquals(b.getVoltageLevelId(), busResult.voltageLevelId());
        }
    }

    @Test
    void derivesTheBranchFlowsTheEstimatedStateImplies() {
        List<LfMeasurement> measurements = withNoiseFreeValues(describeRedundantMeterSet());

        WlsStateEstimator estimator = new WlsStateEstimator(lfNetwork, measurements);
        estimator.init(new UniformValueVoltageInitializer());
        WlsResult result = estimator.estimate();

        long connectedBranches = lfNetwork.getBranches().stream()
                .filter(b -> b.getBus1() != null && b.getBus2() != null)
                .count();
        assertEquals(connectedBranches, result.getBranchResults().size(),
                "one flow per branch connected at both ends");

        for (WlsResult.BranchResult branchResult : result.getBranchResults()) {
            assertTrue(Double.isFinite(branchResult.p1()) && Double.isFinite(branchResult.p2()));
            assertTrue(branchResult.p1() + branchResult.p2() >= -1e-9,
                    "active losses cannot be negative on branch " + branchResult.branchId());
        }
    }

    @Test
    void reportsTheAngleReferenceAndTheObjectiveItMinimized() {
        List<LfMeasurement> measurements = withNoiseFreeValues(describeRedundantMeterSet());

        WlsStateEstimator estimator = new WlsStateEstimator(lfNetwork, measurements);
        estimator.init(new UniformValueVoltageInitializer());
        WlsResult result = estimator.estimate();

        assertEquals(WlsResult.Status.ESTIMATED, result.getStatus());
        assertEquals(lfNetwork.getBuses().stream().filter(LfBus::isReference).count(),
                result.getReferenceBusIds().size(),
                "every pinned angle must be reported, one per synchronous component");
        assertTrue(result.getReferenceBusIds().stream()
                .allMatch(id -> lfNetwork.getBusById(id).isReference()));
        assertEquals(0.0, result.getObjectiveFunctionValue(), 1e-12,
                "noise-free measurements leave no residual to minimize");
        assertTrue(result.getStatusText().isEmpty(), "an estimate that worked has nothing to explain");
    }

    private static WlsResult.BusResult busResult(WlsResult result, String busId) {
        return result.getBusResults().stream()
                .filter(b -> b.busId().equals(busId))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void leavesTheStateUntouchedWhenStartedAtTheAnswer() {
        List<LfMeasurement> measurements = withNoiseFreeValues(describeRedundantMeterSet());

        WlsStateEstimator estimator = new WlsStateEstimator(lfNetwork, measurements);
        estimator.init(new PreviousValueVoltageInitializer());
        WlsResult result = estimator.estimate();

        assertTrue(result.isConverged());
        assertEquals(1, result.getIterationCount(), "a converged start needs a single confirming iteration");
    }

    @Test
    void weighsAPreciseMeterMoreThanACrudeOne() {
        LfBus bus = lfNetwork.getBuses().get(1);
        // two readings of the same voltage disagreeing by 0.1 pu, the second ten times
        // more precise: the estimate must land near the second
        List<LfMeasurement> measurements = new ArrayList<>(withNoiseFreeValues(describeRedundantMeterSet()));
        measurements.add(LfMeasurement.of(AcEquationType.BUS_TARGET_V, bus, trueV[bus.getNum()] - 0.05, 0.1));
        measurements.add(LfMeasurement.of(AcEquationType.BUS_TARGET_V, bus, trueV[bus.getNum()] + 0.05, 0.01));

        WlsStateEstimator estimator = new WlsStateEstimator(lfNetwork, measurements);
        estimator.init(new UniformValueVoltageInitializer());
        WlsResult result = estimator.estimate();

        assertTrue(result.isConverged());
        assertTrue(busResult(result, bus.getId()).v() > trueV[bus.getNum()] * bus.getNominalV(),
                "the estimate must be pulled towards the more precise of the two readings");
    }

    @Test
    void refusesAReadingOfSomethingNoMeterReads() {
        LfBus bus = lfNetwork.getBuses().get(0);
        LfBranch branch = lfNetwork.getBranches().get(0);

        assertThrows(IllegalArgumentException.class,
                () -> LfMeasurement.of(AcEquationType.BUS_TARGET_PHI, bus, 0, 0.01));
        assertThrows(IllegalArgumentException.class,
                () -> LfMeasurement.of(AcEquationType.BRANCH_TARGET_P1, bus, 0, 0.01));
        assertThrows(IllegalArgumentException.class,
                () -> LfMeasurement.of(AcEquationType.BUS_TARGET_V, branch, 0, 0.01));
    }

    @Test
    void countsBothShuntContributionsInABusInjection() {
        Network network = ShuntNetworkFactory.create();
        ShuntCompensator shunt = network.getShuntCompensator("SHUNT");
        shunt.setSectionCount(1);
        // a lossy shunt, so the active contribution G*V^2 is separable from zero
        ((ShuntCompensatorNonLinearModel) shunt.getModel()).getAllSections().getFirst().setG(1e-5);

        LoadFlow.Runner runner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        assertTrue(runner.run(network, new LoadFlowParameters()).isFullyConverged());

        LfNetwork lf = Networks.load(network, new FirstSlackBusSelector()).get(0);
        LfBus shuntBus = lf.getBuses().stream()
                .filter(b -> b.getShunt().isPresent() || b.getControllerShunt().isPresent() || b.getSvcShunt().isPresent())
                .findFirst()
                .orElseThrow();

        // the bus carries a shunt and a line and nothing else, so its device injection is
        // zero: the line flow and the shunt must cancel on both axes
        List<LfMeasurement> injections = List.of(
                LfMeasurement.of(AcEquationType.BUS_TARGET_P, shuntBus, 0, 0.01),
                LfMeasurement.of(AcEquationType.BUS_TARGET_Q, shuntBus, 0, 0.01));
        WlsStateEstimator probe = new WlsStateEstimator(lf, injections);
        probe.init(new PreviousValueVoltageInitializer());
        double[] predicted = probe.evaluate();

        assertEquals(0, predicted[0], 1e-9, "active injection at a bus carrying only a shunt and a line");
        assertEquals(0, predicted[1], 1e-9, "reactive injection at the same bus");
    }

    /**
     * Voltage at every bus, injections everywhere, and flows on both sides of every
     * branch: comfortably more readings than the 2n - 1 unknowns.
     */
    private List<LfMeasurement> describeRedundantMeterSet() {
        List<LfMeasurement> measurements = new ArrayList<>();
        for (LfBus bus : lfNetwork.getBuses()) {
            measurements.add(LfMeasurement.of(AcEquationType.BUS_TARGET_V, bus, 0, 0.004));
            measurements.add(LfMeasurement.of(AcEquationType.BUS_TARGET_P, bus, 0, 0.01));
            measurements.add(LfMeasurement.of(AcEquationType.BUS_TARGET_Q, bus, 0, 0.01));
        }
        for (LfBranch branch : lfNetwork.getBranches()) {
            if (branch.getBus1() == null || branch.getBus2() == null) {
                continue;
            }
            for (TwoSides side : TwoSides.values()) {
                measurements.add(LfMeasurement.of(AcEquationType.branchTargetP(side), branch, 0, 0.01));
                measurements.add(LfMeasurement.of(AcEquationType.branchTargetQ(side), branch, 0, 0.01));
            }
        }
        return measurements;
    }

    /**
     * Replaces the placeholder values of a meter set with what those meters would read
     * in the load-flow state, so the readings carry no error.
     */
    private List<LfMeasurement> withNoiseFreeValues(List<LfMeasurement> described) {
        WlsStateEstimator probe = new WlsStateEstimator(lfNetwork, described);
        probe.init(new PreviousValueVoltageInitializer());
        double[] exact = probe.evaluate();

        List<LfMeasurement> measurements = new ArrayList<>(described.size());
        for (int i = 0; i < described.size(); i++) {
            LfMeasurement m = described.get(i);
            measurements.add(new LfMeasurement(m.type(), m.element(), exact[i], m.sigmaPu(),
                    m.sourceMeasurementIds()));
        }
        return measurements;
    }

    @Test
    void reportsOneResidualPerReadingInTheUnitsIidmUses() {
        List<LfMeasurement> measurements = withNoiseFreeValues(describeRedundantMeterSet());

        WlsResult result = estimateFromFlat(measurements);

        assertEquals(measurements.size(), result.getMeasurementResults().size(),
                "every reading must be reported on, whether or not it stood out");
        for (int i = 0; i < measurements.size(); i++) {
            LfMeasurement measurement = measurements.get(i);
            WlsResult.MeasurementResult reported = result.getMeasurementResults().get(i);
            assertEquals(measurement.type(), reported.type(), "reading " + i);
            double unit = measurement.type() == AcEquationType.BUS_TARGET_V
                    ? ((LfBus) measurement.element()).getNominalV()
                    : PerUnit.SB;
            assertEquals(measurement.valuePu() * unit, reported.value(), 1e-9,
                    "the reading must come back in the unit IIDM uses");
            assertEquals(0, reported.residual(), 1e-6,
                    "noise-free readings leave nothing for the estimate to disagree with");
        }
    }

    @Test
    void countsTheReadingsInExcessOfTheUnknowns() {
        List<LfMeasurement> measurements = withNoiseFreeValues(describeRedundantMeterSet());

        WlsResult result = estimateFromFlat(measurements);

        long references = lfNetwork.getBuses().stream().filter(LfBus::isReference).count();
        int unknowns = 2 * lfNetwork.getBuses().size() - (int) references;
        assertEquals(measurements.size() - unknowns, result.getDegreesOfFreedom());
    }

    @Test
    void addsTheWeightedResidualsUpIntoTheObjectiveFunction() {
        List<LfMeasurement> measurements = moveOneReading(
                withNoiseFreeValues(describeRedundantMeterSet()), 1, 20);

        WlsResult result = estimateFromFlat(measurements);

        double sum = result.getMeasurementResults().stream()
                .mapToDouble(WlsResult.MeasurementResult::weightedSquaredResidual)
                .sum();
        assertTrue(sum > 0, "a reading moved off the true state must leave a residual behind");
        assertEquals(sum, result.getObjectiveFunctionValue(), 1e-6 * sum,
                "the objective function is what a chi-square test reads, so it must be the sum of"
                        + " exactly the residuals reported");
    }

    @Test
    void makesABadReadingStandOutInTheNormalizedResiduals() {
        int corrupted = 1; // the active injection at the first bus
        List<LfMeasurement> clean = withNoiseFreeValues(describeRedundantMeterSet());
        assertEquals(AcEquationType.BUS_TARGET_P, clean.get(corrupted).type());
        List<LfMeasurement> measurements = moveOneReading(clean, corrupted, 20);

        WlsResult result = estimateFromFlat(measurements);

        List<WlsResult.MeasurementResult> reported = result.getMeasurementResults();
        int worst = 0;
        for (int i = 1; i < reported.size(); i++) {
            if (Math.abs(reported.get(i).normalizedResidual())
                    > Math.abs(reported.get(worst).normalizedResidual())) {
                worst = i;
            }
        }
        assertEquals(corrupted, worst, "the reading that was moved must be the one that stands out");
        assertTrue(Math.abs(reported.get(worst).normalizedResidual()) > 3,
                "a reading 20 standard deviations off must clear any sane detection threshold, was "
                        + reported.get(worst).normalizedResidual());
    }

    @Test
    void keepsEveryRedundancyBetweenNothingAndTheWholeReading() {
        List<LfMeasurement> measurements = withNoiseFreeValues(describeRedundantMeterSet());

        WlsResult result = estimateFromFlat(measurements);

        for (WlsResult.MeasurementResult reported : result.getMeasurementResults()) {
            assertTrue(reported.redundancy() >= 0 && reported.redundancy() <= 1 + 1e-9,
                    "a residual cannot keep more variance than its reading had: "
                            + reported.redundancy() + " on " + reported.elementId());
        }
    }

    @Test
    void leavesNoTestableResidualWhenEveryReadingIsCritical() {
        List<LfMeasurement> measurements = withNoiseFreeValues(describeMinimalMeterSet());

        WlsResult result = estimateFromFlat(measurements);

        assertEquals(WlsResult.Status.ESTIMATED, result.getStatus());
        assertEquals(0, result.getDegreesOfFreedom(), "a minimal meter set has no reading to spare");
        for (WlsResult.MeasurementResult reported : result.getMeasurementResults()) {
            assertTrue(reported.isCritical(),
                    "removing any of these readings loses observability: " + reported.elementId());
            assertEquals(0, reported.redundancy());
            assertTrue(Double.isNaN(reported.normalizedResidual()),
                    "the estimate reproduces a critical reading whatever it says, so there is no"
                            + " residual of it to test");
        }
    }

    @Test
    void solvesSparselyUnlessAskedForDenseMatrices() {
        List<LfMeasurement> measurements = withNoiseFreeValues(describeRedundantMeterSet());

        WlsStateEstimator sparse = new WlsStateEstimator(lfNetwork, measurements);
        sparse.init(new UniformValueVoltageInitializer());
        WlsResult sparseResult = sparse.estimate();

        WlsStateEstimator dense = new WlsStateEstimator(lfNetwork, measurements, new DenseMatrixFactory());
        dense.init(new UniformValueVoltageInitializer());
        WlsResult denseResult = dense.estimate();

        assertInstanceOf(SparseMatrix.class, sparse.getGainMatrix(),
                "the gain matrix must come from the factory the estimator was given");
        assertInstanceOf(DenseMatrix.class, dense.getGainMatrix());
        assertEquals(sparseResult.getIterationCount(), denseResult.getIterationCount(),
                "the two differ in storage, not in arithmetic");
        for (int i = 0; i < sparseResult.getBusResults().size(); i++) {
            WlsResult.BusResult fromSparse = sparseResult.getBusResults().get(i);
            WlsResult.BusResult fromDense = denseResult.getBusResults().get(i);
            assertEquals(fromDense.v(), fromSparse.v(), 1e-9, fromSparse.busId());
            assertEquals(fromDense.angle(), fromSparse.angle(), 1e-9, fromSparse.busId());
            assertEquals(fromDense.vStandardDeviation(), fromSparse.vStandardDeviation(), 1e-9,
                    fromSparse.busId());
        }
    }

    @Test
    void reportsBusAccuraciesThatMatchTheInverseOfTheGainMatrix() {
        WlsStateEstimator estimator = new WlsStateEstimator(lfNetwork,
                withNoiseFreeValues(describeRedundantMeterSet()));
        estimator.init(new UniformValueVoltageInitializer());
        WlsResult result = estimator.estimate();

        // invert the gain matrix the long way round, as a check on accuracies the estimator derives
        // one unknown at a time without ever forming the inverse
        DenseMatrix gain = estimator.getGainMatrix().toDense();
        DenseMatrix inverse = new DenseMatrix(gain.getRowCount(), gain.getColumnCount());
        for (int i = 0; i < gain.getRowCount(); i++) {
            inverse.set(i, i, 1);
        }
        try (LUDecomposition lu = gain.decomposeLU()) {
            lu.solve(inverse);
        }
        double[] expected = new double[gain.getRowCount()];
        for (int i = 0; i < expected.length; i++) {
            expected[i] = inverse.get(i, i);
        }
        Arrays.sort(expected);

        List<Double> reported = new ArrayList<>();
        for (WlsResult.BusResult busResult : result.getBusResults()) {
            double nominalV = lfNetwork.getBusById(busResult.busId()).getNominalV();
            reported.add(Math.pow(busResult.vStandardDeviation() / nominalV, 2));
            if (!Double.isNaN(busResult.angleStandardDeviation())) {
                reported.add(Math.pow(Math.toRadians(busResult.angleStandardDeviation()), 2));
            }
        }
        double[] actual = reported.stream().mapToDouble(Double::doubleValue).sorted().toArray();

        assertEquals(expected.length, actual.length,
                "every unknown must get an accuracy and nothing else may");
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], actual[i], 1e-12 + 1e-6 * Math.abs(expected[i]), "variance " + i);
        }
    }

    @Test
    void reportsHowTightlyTheMetersPinEachBusDown() {
        List<LfMeasurement> measurements = withNoiseFreeValues(describeRedundantMeterSet());

        WlsResult result = estimateFromFlat(measurements);

        for (WlsResult.BusResult busResult : result.getBusResults()) {
            assertTrue(busResult.vStandardDeviation() > 0 && busResult.vStandardDeviation() < 1,
                    "a metered bus voltage must come with a usable accuracy, was "
                            + busResult.vStandardDeviation() + " kV at " + busResult.busId());
            boolean reference = result.getReferenceBusIds().contains(busResult.busId());
            assertEquals(reference, Double.isNaN(busResult.angleStandardDeviation()),
                    "a pinned angle is not estimated, so it has no accuracy: " + busResult.busId());
        }
    }

    private WlsResult estimateFromFlat(List<LfMeasurement> measurements) {
        WlsStateEstimator estimator = new WlsStateEstimator(lfNetwork, measurements);
        estimator.init(new UniformValueVoltageInitializer());
        return estimator.estimate();
    }

    /** Moves one reading off the true state by a multiple of its own standard deviation. */
    private static List<LfMeasurement> moveOneReading(List<LfMeasurement> measurements, int index,
                                                      double standardDeviations) {
        List<LfMeasurement> moved = new ArrayList<>(measurements);
        LfMeasurement m = moved.get(index);
        moved.set(index, new LfMeasurement(m.type(), m.element(),
                m.valuePu() + standardDeviations * m.sigmaPu(), m.sigmaPu(), m.sourceMeasurementIds()));
        return moved;
    }

    /**
     * Voltage at every bus and one active flow per branch of a spanning tree: 2n - 1 readings for
     * 2n - 1 unknowns, which determines the state and leaves nothing over to check it with.
     */
    private List<LfMeasurement> describeMinimalMeterSet() {
        List<LfMeasurement> measurements = new ArrayList<>();
        for (LfBus bus : lfNetwork.getBuses()) {
            measurements.add(LfMeasurement.of(AcEquationType.BUS_TARGET_V, bus, 0, 0.004));
        }
        int[] component = new int[lfNetwork.getBuses().size()];
        for (int i = 0; i < component.length; i++) {
            component[i] = i;
        }
        for (LfBranch branch : lfNetwork.getBranches()) {
            if (branch.getBus1() == null || branch.getBus2() == null) {
                continue;
            }
            int end1 = rootOf(component, branch.getBus1().getNum());
            int end2 = rootOf(component, branch.getBus2().getNum());
            if (end1 == end2) {
                continue; // the branch closes a loop, and a loop flow adds no new angle
            }
            component[end1] = end2;
            measurements.add(LfMeasurement.of(AcEquationType.branchTargetP(TwoSides.ONE), branch, 0, 0.01));
        }
        return measurements;
    }

    private static int rootOf(int[] component, int bus) {
        int root = bus;
        while (component[root] != root) {
            root = component[root];
        }
        return root;
    }

    @Test
    void estimatesFromCurrentReadingsAlongsidePowerOnes() {
        List<LfMeasurement> measurements = withNoiseFreeValues(withCurrentReadings(describeRedundantMeterSet()));

        WlsStateEstimator estimator = new WlsStateEstimator(lfNetwork, measurements);
        estimator.init(new PreviousValueVoltageInitializer());
        WlsResult result = estimator.estimate();

        assertEquals(WlsResult.Status.ESTIMATED, result.getStatus());
        List<WlsResult.MeasurementResult> currents = result.getMeasurementResults().stream()
                .filter(m -> m.type() == AcEquationType.BRANCH_TARGET_I1 || m.type() == AcEquationType.BRANCH_TARGET_I2)
                .toList();
        assertFalse(currents.isEmpty(), "a current reading must reach the solver, not be refused by it");
        for (WlsResult.MeasurementResult current : currents) {
            assertEquals(0, current.residual(), 1e-6,
                    "a noise-free current reading leaves no residual on " + current.elementId());
            assertTrue(current.value() > 0, "a current comes back in amperes, so it is positive");
        }
    }

    @Test
    void leavesCurrentReadingsOutOfTheFirstIterationFromAFlatStart() {
        List<LfMeasurement> measurements = withNoiseFreeValues(withCurrentReadings(describeRedundantMeterSet()));

        WlsResult result = estimateFromFlat(measurements);

        assertEquals(WlsResult.Status.ESTIMATED, result.getStatus(), result.getStatusText());
        for (LfBus bus : lfNetwork.getBuses()) {
            assertEquals(trueV[bus.getNum()] * bus.getNominalV(), busResult(result, bus.getId()).v(), 1e-6);
        }
    }

    @Test
    void aCurrentReadingHasNoDerivativeAtAFlatStart() {
        List<LfMeasurement> measurements = withNoiseFreeValues(withCurrentReadings(describeRedundantMeterSet()));
        WlsStateEstimator estimator = new WlsStateEstimator(lfNetwork, measurements);

        WlsResult result = estimator.estimate(new WlsParameters(WlsParameters.DEFAULT_MAX_ITERATIONS,
                WlsParameters.DEFAULT_TOLERANCE, WlsParameters.DEFAULT_MAX_STEP, false));

        assertEquals(WlsResult.Status.FAILED, result.getStatus(),
                "which is why the first iteration leaves current readings out unless told otherwise");
        assertTrue(result.getStatusText().contains("derivative"), result.getStatusText());
    }

    private List<LfMeasurement> withCurrentReadings(List<LfMeasurement> described) {
        List<LfMeasurement> withCurrents = new ArrayList<>(described);
        for (LfBranch branch : lfNetwork.getBranches()) {
            if (branch.getBus1() != null && branch.getBus2() != null) {
                for (TwoSides side : TwoSides.values()) {
                    withCurrents.add(LfMeasurement.of(AcEquationType.branchTargetI(side), branch, 0, 0.05));
                }
            }
        }
        return withCurrents;
    }

    @Test
    void scalesAStepLongerThanTheLimitDownToIt() {
        WlsStateEstimator estimator = new WlsStateEstimator(lfNetwork,
                withNoiseFreeValues(describeRedundantMeterSet()));

        // from a flat start the first whole step moves some angle by more than 0.05 rad
        WlsResult result = estimator.estimate(new WlsParameters(1, WlsParameters.DEFAULT_TOLERANCE, 0.05, true));

        double largestChange = 0;
        for (LfBus bus : lfNetwork.getBuses()) {
            WlsResult.BusResult busResult = busResult(result, bus.getId());
            largestChange = Math.max(largestChange, Math.abs(busResult.v() / bus.getNominalV() - 1));
            largestChange = Math.max(largestChange, Math.abs(Math.toRadians(busResult.angle())));
        }
        assertEquals(0.05, largestChange, 1e-12, "the largest change is brought to the limit and the rest keep their proportion");
    }

    @Test
    void iteratesWithTheSolverItIsGiven() {
        // a solver that calls the start the answer, which no other could be mistaken for
        WlsSolverFactory standingStill = new WlsSolverFactory() {
            @Override
            public String getName() {
                return "STANDING_STILL";
            }

            @Override
            public WlsSolver create(WlsProblem problem, JacobianMatrix<AcVariableType, AcEquationType> jacobian,
                                    WlsParameters parameters) {
                return new WlsSolver() {
                    @Override
                    public String getName() {
                        return "STANDING_STILL";
                    }

                    @Override
                    public WlsSolverResult run() {
                        return new WlsSolverResult(WlsSolverStatus.CONVERGED, "", 0);
                    }

                    @Override
                    public GainFactorization factorizeGain(Matrix scaledJacobian) {
                        return new LuGainFactorization(scaledJacobian);
                    }
                };
            }
        };
        WlsStateEstimator estimator = new WlsStateEstimator(lfNetwork,
                withNoiseFreeValues(describeRedundantMeterSet()), new SparseMatrixFactory(), standingStill);

        WlsResult result = estimator.estimate();

        assertEquals(WlsResult.Status.ESTIMATED, result.getStatus());
        assertEquals(0, result.getIterationCount());
        LfBus bus = lfNetwork.getBuses().get(1);
        assertEquals(bus.getNominalV(), busResult(result, bus.getId()).v(), 1e-9, "still at the flat start");
        assertTrue(busResult(result, bus.getId()).vStandardDeviation() > 0,
                "the accuracy is worked out with the factorization the solver supplies");
    }

    @Test
    void callsAMeasurementSetThatDoesNotDetermineTheStateUnobservable() {
        LfBranch branch = branchAtTheReferenceBus();
        // one flow reading against three unknowns: a voltage at either end and the angle at the far one
        List<LfMeasurement> measurements = List.of(
                LfMeasurement.of(AcEquationType.branchTargetP(TwoSides.ONE), branch, 0.1, 0.01));

        WlsResult result = estimateFromFlat(measurements);

        assertEquals(WlsResult.Status.UNOBSERVABLE, result.getStatus(),
                "a rank deficient gain matrix is what an underdetermined measurement set looks like");
        assertTrue(result.getStatusText().contains("does not determine the state"), result.getStatusText());
        assertEquals(0, result.getIterationCount(), "no state update was applied before the run stopped");
        assertTrue(Double.isNaN(busResult(result, branch.getBus1().getId()).vStandardDeviation()),
                "without a decomposition there is no accuracy to report");
        WlsResult.MeasurementResult reading = result.getMeasurementResults().get(0);
        assertTrue(Double.isNaN(reading.normalizedResidual()), "nor any residual that can be tested");
        assertTrue(Double.isNaN(reading.redundancy()));
        assertFalse(reading.isCritical(), "a redundancy nobody could compute is unknown, not zero");
    }

    private LfBranch branchAtTheReferenceBus() {
        return lfNetwork.getBranches().stream()
                .filter(b -> b.getBus1() != null && b.getBus2() != null)
                .filter(b -> b.getBus1().isReference() || b.getBus2().isReference())
                .findFirst()
                .orElseThrow();
    }

    @Test
    void callsReadingsThatReachNoAngleReferenceUnobservable() {
        LfBranch branch = lfNetwork.getBranches().stream()
                .filter(b -> b.getBus1() != null && b.getBus2() != null)
                .filter(b -> !b.getBus1().isReference() && !b.getBus2().isReference())
                .findFirst()
                .orElseThrow();
        // enough to settle both voltages and the angle between them, with nothing to count the angles from
        List<LfMeasurement> measurements = withNoiseFreeValues(List.of(
                LfMeasurement.of(AcEquationType.BUS_TARGET_V, branch.getBus1(), 0, 0.004),
                LfMeasurement.of(AcEquationType.BUS_TARGET_V, branch.getBus2(), 0, 0.004),
                LfMeasurement.of(AcEquationType.BRANCH_TARGET_P1, branch, 0, 0.01),
                LfMeasurement.of(AcEquationType.BRANCH_TARGET_Q1, branch, 0, 0.01)));

        WlsResult result = estimateFromFlat(measurements);

        assertEquals(WlsResult.Status.UNOBSERVABLE, result.getStatus());
        assertTrue(result.getStatusText().contains("angle reference"), result.getStatusText());
    }

    @Test
    void callsAnEmptyMeasurementSetUnobservable() {
        WlsResult result = estimateFromFlat(List.of());

        assertEquals(WlsResult.Status.UNOBSERVABLE, result.getStatus());
        assertTrue(Double.isNaN(result.getBusResults().get(0).v()), "no reading, no estimate");
    }

    @Test
    void startsFromAFlatProfileWhenGivenNoStart() {
        WlsStateEstimator estimator = new WlsStateEstimator(lfNetwork,
                withNoiseFreeValues(describeRedundantMeterSet()));

        WlsResult result = estimator.estimate();

        assertEquals(WlsResult.Status.ESTIMATED, result.getStatus(), result.getStatusText());
    }

    @Test
    void startsFromADcLoadFlowWhenGivenThatInitializer() {
        List<LfMeasurement> measurements = withNoiseFreeValues(describeRedundantMeterSet());
        WlsStateEstimator estimator = new WlsStateEstimator(lfNetwork, measurements);

        estimator.init(new DcValueVoltageInitializer(new LfNetworkParameters(), false,
                LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX, true, DcApproximationType.IGNORE_R,
                new DenseMatrixFactory(), 1));
        WlsResult result = estimator.estimate();

        assertEquals(WlsResult.Status.ESTIMATED, result.getStatus(), result.getStatusText());
        for (LfBus bus : lfNetwork.getBuses()) {
            assertEquals(trueV[bus.getNum()] * bus.getNominalV(), busResult(result, bus.getId()).v(), 1e-6);
        }
    }

    @Test
    void refusesABranchWhoseImpedanceItCannotResolve() {
        Network network = IeeeCdfNetworkFactory.create14();
        network.getLine("L1-2-1").setR(0).setX(0);
        solveAndLoad(network);
        List<LfMeasurement> measurements = describeRedundantMeterSet();

        PowsyblException refused = assertThrows(PowsyblException.class, () -> new WlsStateEstimator(lfNetwork, measurements));

        assertTrue(refused.getMessage().contains("L1-2-1"), refused.getMessage());
        assertTrue(refused.getMessage().contains("setMinImpedance"), "the message says how the network can be estimated");
    }

    @Test
    void readsTheLiveEndOfABranchOpenAtTheOther() {
        Network network = IeeeCdfNetworkFactory.create14();
        Line line = network.getLine("L1-2-1");
        line.getTerminal2().disconnect();
        LoadFlow.Runner runner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        assertTrue(runner.run(network, new LoadFlowParameters()).isFullyConverged());

        LfNetwork lf = Networks.load(network, new FirstSlackBusSelector()).get(0);
        LfBranch halfOpen = lf.getBranchById("L1-2-1");

        // the line still charges from the end that is connected, and a meter there reads it
        WlsStateEstimator probe = new WlsStateEstimator(lf, List.of(
                LfMeasurement.of(AcEquationType.BRANCH_TARGET_P1, halfOpen, 0, 0.01),
                LfMeasurement.of(AcEquationType.BRANCH_TARGET_Q1, halfOpen, 0, 0.01)));
        probe.init(new PreviousValueVoltageInitializer());
        double[] predicted = probe.evaluate();

        assertEquals(line.getTerminal1().getP() / PerUnit.SB, predicted[0], 1e-6);
        assertEquals(line.getTerminal1().getQ() / PerUnit.SB, predicted[1], 1e-6);
        assertNotEquals(0, predicted[1], 1e-3, "the charging of an open line is not nothing");

        List<LfMeasurement> atTheOpenEnd = List.of(LfMeasurement.of(AcEquationType.BRANCH_TARGET_P2, halfOpen, 0, 0.01));
        assertThrows(IllegalArgumentException.class, () -> new WlsStateEstimator(lf, atTheOpenEnd));
    }

    @Test
    void readsTheChargingCurrentAtTheLiveEndOfAnOpenBranch() {
        Line line = openAtSide2(true);
        List<LfMeasurement> described = describeRedundantMeterSet();
        described.add(LfMeasurement.of(AcEquationType.BRANCH_TARGET_I1, lfNetwork.getBranchById(line.getId()), 0, 0.05));

        WlsResult result = estimateFromFlat(withNoiseFreeValues(described));

        assertEquals(WlsResult.Status.ESTIMATED, result.getStatus(), result.getStatusText());
        WlsResult.MeasurementResult current = result.getMeasurementResults().get(described.size() - 1);
        assertEquals(line.getTerminal1().getI(), current.estimatedValue(), 1e-3);
        assertTrue(current.estimatedValue() > 1, "the charging of an open line is a current a meter reads");
    }

    @Test
    void estimatesWithACurrentReadingOnAnOpenBranchThatCarriesNone() {
        Line line = openAtSide2(false);
        List<LfMeasurement> described = describeRedundantMeterSet();
        described.add(LfMeasurement.of(AcEquationType.BRANCH_TARGET_I1, lfNetwork.getBranchById(line.getId()), 0, 0.05));

        WlsResult result = estimateFromFlat(withNoiseFreeValues(described));

        assertEquals(WlsResult.Status.ESTIMATED, result.getStatus(), result.getStatusText());
        for (LfBus bus : lfNetwork.getBuses()) {
            assertEquals(trueV[bus.getNum()] * bus.getNominalV(), busResult(result, bus.getId()).v(), 1e-6);
        }
        WlsResult.MeasurementResult current = result.getMeasurementResults().get(described.size() - 1);
        assertEquals(0, current.estimatedValue(), 0, "a branch without shunt admittance carries nothing once it is open");
        assertEquals(0, current.residual(), 0);
    }

    @Test
    void leavesACurrentReadWhereNoneCanFlowWhollyInItsResidual() {
        Line line = openAtSide2(false);
        List<LfMeasurement> measurements = withNoiseFreeValues(describeRedundantMeterSet());
        measurements.add(LfMeasurement.of(AcEquationType.BRANCH_TARGET_I1, lfNetwork.getBranchById(line.getId()), 0.1, 0.05));

        WlsResult result = estimateFromFlat(measurements);

        assertEquals(WlsResult.Status.ESTIMATED, result.getStatus(), result.getStatusText());
        for (LfBus bus : lfNetwork.getBuses()) {
            assertEquals(trueV[bus.getNum()] * bus.getNominalV(), busResult(result, bus.getId()).v(), 1e-6,
                    "no state explains the reading any better, so it must not move one");
        }
        WlsResult.MeasurementResult current = result.getMeasurementResults().get(measurements.size() - 1);
        assertEquals(1, current.redundancy(), 1e-12);
        assertEquals(0.1 / 0.05, current.normalizedResidual(), 1e-9);
    }

    /** Opens a line of the 14 bus network at its second end, with or without its charging, and solves it. */
    private Line openAtSide2(boolean charging) {
        Network network = IeeeCdfNetworkFactory.create14();
        Line line = network.getLine("L1-2-1");
        if (!charging) {
            line.setG1(0).setB1(0).setG2(0).setB2(0);
        }
        line.getTerminal2().disconnect();
        solveAndLoad(network);
        return line;
    }

    @Test
    void callsADivergingIterationFailedRatherThanUnconverged() {
        // 500 MW into every branch at side 1 at once, which no state can produce: that violates
        // Kirchhoff at every bus, and Gauss-Newton walks away from it rather than settling
        List<LfMeasurement> measurements = new ArrayList<>();
        for (LfBus bus : lfNetwork.getBuses()) {
            measurements.add(LfMeasurement.of(AcEquationType.BUS_TARGET_V, bus, 1.0, 0.004));
        }
        for (LfBranch branch : lfNetwork.getBranches()) {
            if (branch.getBus1() == null || branch.getBus2() == null) {
                continue;
            }
            measurements.add(LfMeasurement.of(AcEquationType.branchTargetP(TwoSides.ONE), branch, 5, 0.01));
            measurements.add(LfMeasurement.of(AcEquationType.branchTargetQ(TwoSides.ONE), branch, 5, 0.01));
        }

        // every step applied whole: the step limit exists to keep a run like this one from diverging
        WlsStateEstimator estimator = new WlsStateEstimator(lfNetwork, measurements);
        WlsResult result = estimator.estimate(new WlsParameters(WlsParameters.DEFAULT_MAX_ITERATIONS,
                WlsParameters.DEFAULT_TOLERANCE, Double.POSITIVE_INFINITY, true));

        assertEquals(WlsResult.Status.FAILED, result.getStatus(),
                "a run walking away from the answer is a different outcome from one closing in slowly");
        assertTrue(result.getIterationCount() < WlsParameters.DEFAULT_MAX_ITERATIONS,
                "divergence must be caught rather than left to exhaust the iteration cap, was "
                        + result.getIterationCount());
        assertTrue(result.getStatusText().contains("diverging"), result.getStatusText());
    }

    @Test
    void stopsAtTheIterationCapWithoutCallingItAFailure() {
        WlsStateEstimator estimator = new WlsStateEstimator(lfNetwork,
                withNoiseFreeValues(describeRedundantMeterSet()));
        estimator.init(new UniformValueVoltageInitializer());

        WlsResult result = estimator.estimate(new WlsParameters(1, 1e-6));

        assertEquals(WlsResult.Status.DID_NOT_CONVERGE, result.getStatus());
        assertEquals(1, result.getIterationCount());
        assertTrue(result.getStatusText().isEmpty(), "running out of iterations needs no explaining");
        assertTrue(busResult(result, "VL2_0").vStandardDeviation() > 0,
                "the accuracy follows from where the meters are, so it survives a run that stopped"
                        + " short of the answer");
    }

    @Test
    void callsAnUnobservableSetUnobservableWithDenseMatricesToo() {
        LfBranch branch = branchAtTheReferenceBus();
        List<LfMeasurement> measurements = List.of(
                LfMeasurement.of(AcEquationType.branchTargetP(TwoSides.ONE), branch, 0.1, 0.01));

        // a dense decomposition is only carried out on the first solve, so the singular gain
        // matrix surfaces somewhere else than with a sparse one, and must end the same way
        WlsStateEstimator estimator = new WlsStateEstimator(lfNetwork, measurements, new DenseMatrixFactory());
        estimator.init(new UniformValueVoltageInitializer());
        WlsResult result = estimator.estimate();

        assertEquals(WlsResult.Status.UNOBSERVABLE, result.getStatus());
        assertTrue(Double.isNaN(busResult(result, branch.getBus1().getId()).vStandardDeviation()));
    }

    @Test
    void callsARunThatLeavesTheFiniteNumbersFailed() {
        List<LfMeasurement> measurements = moveOneReading(
                withNoiseFreeValues(describeRedundantMeterSet()), 1, 1e300);

        WlsResult result = estimateFromFlat(measurements);

        assertEquals(WlsResult.Status.FAILED, result.getStatus(),
                "an overflow must stop the run rather than carry a NaN state to the iteration cap");
        assertTrue(result.getStatusText().contains("not a finite number"), result.getStatusText());
        assertTrue(result.getIterationCount() < WlsParameters.DEFAULT_MAX_ITERATIONS);
    }

    @Test
    void neverMistakesRoundOffAtTheMinimumForDivergence() {
        // noisy readings leave a real minimum, and a tolerance no iteration can reach keeps the run
        // going after it, where the objective moves by round-off alone
        List<LfMeasurement> described = describeRedundantMeterSet();
        WlsStateEstimator probe = new WlsStateEstimator(lfNetwork, described);
        probe.init(new PreviousValueVoltageInitializer());
        double[] exact = probe.evaluate();
        for (int seed = 0; seed < 20; seed++) {
            Random random = new Random(seed);
            List<LfMeasurement> noisy = new ArrayList<>(described.size());
            for (int i = 0; i < described.size(); i++) {
                LfMeasurement m = described.get(i);
                noisy.add(new LfMeasurement(m.type(), m.element(),
                        exact[i] + random.nextGaussian() * m.sigmaPu(), m.sigmaPu(), m.sourceMeasurementIds()));
            }
            WlsStateEstimator estimator = new WlsStateEstimator(lfNetwork, noisy);
            estimator.init(new UniformValueVoltageInitializer());

            WlsResult result = estimator.estimate(new WlsParameters(60, 1e-15));

            assertNotEquals(WlsResult.Status.FAILED, result.getStatus(),
                    "seed " + seed + ": " + result.getStatusText());
        }
    }

    @Test
    void estimatesAlongsideAReadingNoUnknownCanMove() {
        Network network = Network.create("one-bus", "test");
        VoltageLevel vl = network.newSubstation().setId("S").add()
                .newVoltageLevel().setId("VL").setNominalV(100).setTopologyKind(TopologyKind.BUS_BREAKER).add();
        vl.getBusBreakerView().newBus().setId("B").add();
        vl.newLoad().setId("L").setBus("B").setConnectableBus("B").setP0(10).setQ0(0).add();
        LfNetwork oneBus = Networks.load(network, new FirstSlackBusSelector()).get(0);
        LfBus bus = oneBus.getBuses().get(0);
        // an injection is the sum of branch and shunt terms, and this bus has neither, so the
        // reading reaches no unknown and its column of the Jacobian is empty. It is ordered ahead of
        // the voltage reading's column, which is the gap a sparse matrix does not close by itself.
        List<LfMeasurement> measurements = List.of(
                LfMeasurement.of(AcEquationType.BUS_TARGET_P, bus, 0.1, 0.01),
                LfMeasurement.of(AcEquationType.BUS_TARGET_V, bus, 1.02, 0.004));

        WlsStateEstimator estimator = new WlsStateEstimator(oneBus, measurements);
        estimator.init(new UniformValueVoltageInitializer());
        WlsResult result = estimator.estimate();

        assertEquals(WlsResult.Status.ESTIMATED, result.getStatus(), result.getStatusText());
        assertEquals(102, result.getBusResults().get(0).v(), 1e-9);
        WlsResult.MeasurementResult injection = result.getMeasurementResults().get(0);
        assertEquals(1, injection.redundancy(), 1e-12,
                "the estimate cannot move a reading nothing depends on, so all of its variance survives");
    }
}
