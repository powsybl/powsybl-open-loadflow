/**
 * Copyright (c) 2026, Kickstage (https://kickstage.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.se;

import com.powsybl.ieeecdf.converter.IeeeCdfNetworkFactory;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.ShuntCompensator;
import com.powsybl.iidm.network.ShuntCompensatorNonLinearModel;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.network.FirstSlackBusSelector;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.ShuntNetworkFactory;
import com.powsybl.openloadflow.network.impl.Networks;
import com.powsybl.openloadflow.se.equations.MeasuredQuantity;
import com.powsybl.openloadflow.se.network.LfMeasurementProjection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        Network network = IeeeCdfNetworkFactory.create14();
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
        List<Measurement> measurements = withNoiseFreeValues(describeRedundantMeterSet());

        WlsStateEstimator estimator = new WlsStateEstimator(lfNetwork, measurements);
        estimator.initFlat();
        WlsResult result = estimator.estimate();

        assertTrue(result.converged(), "the estimate must converge from a flat start");
        assertTrue(result.iterations() <= 6, "expected a handful of iterations, got " + result.iterations());

        for (int i = 0; i < trueV.length; i++) {
            assertEquals(trueV[i], result.busV()[i], 1e-8, "voltage magnitude at bus " + i);
            assertEquals(trueAngle[i], result.busAngleRadian()[i], 1e-8, "voltage angle at bus " + i);
        }
    }

    @Test
    void leavesTheStateUntouchedWhenStartedAtTheAnswer() {
        List<Measurement> measurements = withNoiseFreeValues(describeRedundantMeterSet());

        WlsStateEstimator estimator = new WlsStateEstimator(lfNetwork, measurements);
        estimator.init(trueV, trueAngle);
        WlsResult result = estimator.estimate();

        assertTrue(result.converged());
        assertEquals(1, result.iterations(), "a converged start needs a single confirming iteration");
    }

    @Test
    void weighsAPreciseMeterMoreThanACrudeOne() {
        LfBus bus = lfNetwork.getBuses().get(1);
        // two readings of the same voltage disagreeing by 0.1 pu, the second ten times
        // more precise: the estimate must land near the second
        List<Measurement> measurements = new ArrayList<>(withNoiseFreeValues(describeRedundantMeterSet()));
        measurements.add(Measurement.busVoltage(bus, trueV[bus.getNum()] - 0.05, 0.1));
        measurements.add(Measurement.busVoltage(bus, trueV[bus.getNum()] + 0.05, 0.01));

        WlsStateEstimator estimator = new WlsStateEstimator(lfNetwork, measurements);
        estimator.initFlat();
        WlsResult result = estimator.estimate();

        assertTrue(result.converged());
        assertTrue(result.busV()[bus.getNum()] > trueV[bus.getNum()],
                "the estimate must be pulled towards the more precise of the two readings");
    }

    @Test
    void resolvesBranchSidesAgainstTheBranchOrientation() {
        LfMeasurementProjection projection = new LfMeasurementProjection(lfNetwork);
        LfBranch branch = lfNetwork.getBranches().get(0);

        assertTrue(projection.findBranch(branch.getId()).isPresent());
        assertEquals(1, LfMeasurementProjection.sideOf(branch, branch.getBus1()));
        assertEquals(2, LfMeasurementProjection.sideOf(branch, branch.getBus2()));

        LfBus foreign = lfNetwork.getBuses().stream()
                .filter(b -> b.getNum() != branch.getBus1().getNum() && b.getNum() != branch.getBus2().getNum())
                .findFirst()
                .orElseThrow();
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> LfMeasurementProjection.sideOf(branch, foreign));
        assertTrue(thrown.getMessage().contains(foreign.getId()),
                "the message should name the bus that is not an end of the branch: " + thrown.getMessage());
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

        double[] v = new double[lf.getBuses().size()];
        double[] angle = new double[lf.getBuses().size()];
        for (LfBus bus : lf.getBuses()) {
            v[bus.getNum()] = bus.getV();
            angle[bus.getNum()] = bus.getAngle();
        }

        // the bus carries a shunt and a line and nothing else, so its device injection is
        // zero: the line flow and the shunt must cancel on both axes
        List<Measurement> injections = List.of(
                Measurement.busInjection(shuntBus, true, 0, 0.01),
                Measurement.busInjection(shuntBus, false, 0, 0.01));
        WlsStateEstimator probe = new WlsStateEstimator(lf, injections);
        probe.init(v, angle);
        double[] predicted = probe.evaluate();

        assertEquals(0, predicted[0], 1e-9, "active injection at a bus carrying only a shunt and a line");
        assertEquals(0, predicted[1], 1e-9, "reactive injection at the same bus");
    }

    /**
     * Voltage at every bus, injections everywhere, and flows on both sides of every
     * branch: comfortably more readings than the 2n - 1 unknowns.
     */
    private List<Measurement> describeRedundantMeterSet() {
        List<Measurement> measurements = new ArrayList<>();
        for (LfBus bus : lfNetwork.getBuses()) {
            measurements.add(Measurement.busVoltage(bus, 0, 0.004));
            measurements.add(Measurement.busInjection(bus, true, 0, 0.01));
            measurements.add(Measurement.busInjection(bus, false, 0, 0.01));
        }
        for (LfBranch branch : lfNetwork.getBranches()) {
            if (branch.getBus1() == null || branch.getBus2() == null) {
                continue;
            }
            for (int side = 1; side <= 2; side++) {
                measurements.add(Measurement.branchFlow(branch, MeasuredQuantity.BRANCH_P, side, 0, 0.01));
                measurements.add(Measurement.branchFlow(branch, MeasuredQuantity.BRANCH_Q, side, 0, 0.01));
            }
        }
        return measurements;
    }

    /**
     * Replaces the placeholder values of a meter set with what those meters would read
     * in the load-flow state, so the readings carry no error.
     */
    private List<Measurement> withNoiseFreeValues(List<Measurement> described) {
        WlsStateEstimator probe = new WlsStateEstimator(lfNetwork, described);
        probe.init(trueV, trueAngle);
        double[] exact = probe.evaluate();

        List<Measurement> measurements = new ArrayList<>(described.size());
        for (int i = 0; i < described.size(); i++) {
            Measurement m = described.get(i);
            measurements.add(new Measurement(m.quantity(), m.bus(), m.branch(), m.side(), exact[i], m.sigmaPu()));
        }
        return measurements;
    }
}
