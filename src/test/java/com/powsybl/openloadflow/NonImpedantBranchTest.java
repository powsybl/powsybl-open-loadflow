/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow;

import com.powsybl.commons.reporter.Reporter;
import com.powsybl.computation.local.LocalComputationManager;
import com.powsybl.contingency.BranchContingency;
import com.powsybl.contingency.ContingenciesProvider;
import com.powsybl.contingency.Contingency;
import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.Line;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.TwoWindingsTransformer;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.graph.EvenShiloachGraphDecrementalConnectivityFactory;
import com.powsybl.openloadflow.network.AbstractLoadFlowNetworkFactory;
import com.powsybl.openloadflow.network.SlackBusSelectionMode;
import com.powsybl.openloadflow.sa.OpenSecurityAnalysisProvider;
import com.powsybl.security.*;
import com.powsybl.security.detectors.DefaultLimitViolationDetector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static com.powsybl.openloadflow.util.LoadFlowAssert.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
class NonImpedantBranchTest extends AbstractLoadFlowNetworkFactory {

    private LoadFlow.Runner loadFlowRunner;

    private LoadFlowParameters parameters;

    private OpenLoadFlowParameters parametersExt;

    @BeforeEach
    void setUp() {
        loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        parameters = new LoadFlowParameters();
        parametersExt = OpenLoadFlowParameters.create(parameters);
    }

    @Test
    void threeBusesTest() {
        Network network = Network.create("ThreeBusesWithNonImpedantLine", "code");
        Bus b1 = createBus(network, "b1");
        Bus b2 = createBus(network, "b2");
        Bus b3 = createBus(network, "b3");
        createGenerator(b1, "g1", 2, 1);
        createLoad(b3, "l1", 1.99, 1);
        createLine(network, b1, b2, "l12", 0.1);
        Line l23 = createLine(network, b2, b3, "l23", 0); // non impedant branch

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertVoltageEquals(1, b1);
        assertVoltageEquals(0.858, b2);
        assertVoltageEquals(0.858, b3);
        assertAngleEquals(13.36967, b1);
        assertAngleEquals(0, b2);
        assertAngleEquals(0, b3);

        assertActivePowerEquals(1.99, l23.getTerminal1());
        assertActivePowerEquals(-1.99, l23.getTerminal2());
        assertReactivePowerEquals(1, l23.getTerminal1());
        assertReactivePowerEquals(-1, l23.getTerminal2());

        // use low impedance cut strategy (state is changed a little bit)
        parametersExt.setLowImpedanceBranchMode(OpenLoadFlowParameters.LowImpedanceBranchMode.REPLACE_BY_MIN_IMPEDANCE_LINE);
        result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertVoltageEquals(1, b1);
        assertVoltageEquals(0.856, b2);
        assertVoltageEquals(0.856, b3);
        assertAngleEquals(13.520904, b1);
        assertAngleEquals(0, b2);
        assertAngleEquals(0, b3);

        // test in DC mode
        parameters.setDc(true);
        parametersExt.setSlackBusSelectionMode(SlackBusSelectionMode.FIRST);
        result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertTrue(Double.isNaN(b1.getV()));
        assertTrue(Double.isNaN(b2.getV()));
        assertTrue(Double.isNaN(b3.getV()));
        assertAngleEquals(0, b1);
        assertAngleEquals(-11.40186, b2);
        assertAngleEquals(-11.40186, b3);
    }

    @Test
    void fourBusesTest() {
        Network network = Network.create("FourBusesWithNonImpedantLine", "code");
        Bus b1 = createBus(network, "b1");
        Bus b2 = createBus(network, "b2");
        Bus b3 = createBus(network, "b3");
        Bus b4 = createBus(network, "b4");
        createGenerator(b1, "g1", 2, 1);
        createLoad(b4, "l1", 1.99, 1);
        createLine(network, b1, b2, "l12", 0.05);
        createLine(network, b2, b3, "l23", 0); // non impedant branch
        createLine(network, b3, b4, "l34", 0.05);

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertVoltageEquals(1, b1);
        assertVoltageEquals(0.921, b2);
        assertVoltageEquals(0.921, b3);
        assertVoltageEquals(0.855, b4);
        assertAngleEquals(6.2301, b1);
        assertAngleEquals(0, b2);
        assertAngleEquals(0, b3);
        assertAngleEquals(-7.248787, b4);

        parameters.setDc(true);
        parametersExt.setSlackBusSelectionMode(SlackBusSelectionMode.FIRST);
        result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertTrue(Double.isNaN(b1.getV()));
        assertTrue(Double.isNaN(b2.getV()));
        assertTrue(Double.isNaN(b3.getV()));
        assertTrue(Double.isNaN(b4.getV()));
        assertAngleEquals(0, b1);
        assertAngleEquals(-5.70093, b2);
        assertAngleEquals(-5.70093, b3);
        assertAngleEquals(-11.40186, b4);
    }

    @Test
    void threeBusesAndNonImpTransfoTest() {
        Network network = Network.create("ThreeBusesWithNonImpedantTransfo", "code");
        Bus b1 = createBus(network, "s", "b1");
        Bus b2 = createBus(network, "s", "b2");
        Bus b3 = createBus(network, "s", "b3");
        createGenerator(b1, "g1", 2, 1);
        createLoad(b3, "l1", 1.99, 1);
        createLine(network, b1, b2, "l12", 0.1);
        TwoWindingsTransformer l23 = createTransformer(network, "s", b2, b3, "l23", 0, 1.1); // non impedant branch

        // TODO: low impedance transformer flow calculation not yet supported
        assertTrue(Double.isNaN(l23.getTerminal1().getP()));
        assertTrue(Double.isNaN(l23.getTerminal2().getP()));
        assertTrue(Double.isNaN(l23.getTerminal1().getQ()));
        assertTrue(Double.isNaN(l23.getTerminal2().getQ()));

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertVoltageEquals(1, b1);
        assertVoltageEquals(0.858, b2);
        assertVoltageEquals(0.944, b3);
        assertAngleEquals(13.36967, b1);
        assertAngleEquals(0, b2);
        assertAngleEquals(0, b3);
    }

    /**
     *  g1
     *  |
     *  b1 === b2 --- b3 --- b4
     *         |             |
     *         g2            l1
     */
    @Test
    void inconsistentTargetVoltagesTest() {
        Network network = Network.create("FourBusesWithNonImpedantLineAndInconsistentTargetVoltages", "code");
        Bus b1 = createBus(network, "b1");
        Bus b2 = createBus(network, "b2");
        Bus b3 = createBus(network, "b3");
        Bus b4 = createBus(network, "b4");
        createGenerator(b1, "g1", 1, 1);
        createGenerator(b2, "g2", 1, 1.01);
        createLoad(b4, "l1", 1.99, 1);
        createLine(network, b1, b2, "l12", 0); // non impedant branch
        createLine(network, b2, b3, "l23", 0.05);
        createLine(network, b3, b4, "l34", 0.05);

        loadFlowRunner.run(network, parameters);

        assertEquals(1.01, b1.getV(), DELTA_V);
        assertEquals(1.01, b2.getV(), DELTA_V);
    }

    @Test
    void parallelNonImpedantBranchTest() {
        Network network = Network.create("ParallelNonImpedantBranch", "code");
        Bus b1 = createBus(network, "b1");
        Bus b2 = createBus(network, "b2");
        Bus b3 = createBus(network, "b3");
        createGenerator(b1, "g1", 2, 1);
        createLoad(b3, "l1", 1.99, 1);
        createLine(network, b1, b2, "l12", 0.1);
        createLine(network, b2, b3, "l23", 0); // non impedant branch
        createLine(network, b2, b3, "l23bis", 0); // non impedant branch

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
    }

    @Test
    void parallelNonImpedantAndImpedantBranchTest() {
        Network network = Network.create("ParallelNonImpedantAndImpedantBranch", "code");
        Bus b1 = createBus(network, "b1");
        Bus b2 = createBus(network, "b2");
        Bus b3 = createBus(network, "b3");
        createGenerator(b1, "g1", 2, 1);
        createLoad(b3, "l1", 1.99, 1);
        createLine(network, b1, b2, "l12", 0.1);
        createLine(network, b2, b3, "l23", 0.1);
        createLine(network, b2, b3, "l23bis", 0); // non impedant branch

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
    }

    @Test
    void loopNonImpedantBranchTest() {
        Network network = Network.create("LoopNonImpedantBranch", "code");
        Bus b1 = createBus(network, "b1");
        Bus b2 = createBus(network, "b2");
        Bus b3 = createBus(network, "b3");
        createGenerator(b1, "g1", 2, 1);
        createLoad(b3, "l1", 1.99, 1);
        createLine(network, b1, b2, "l12", 0); // non impedant branch
        createLine(network, b2, b3, "l23", 0); // non impedant branch
        createLine(network, b1, b3, "l13", 0); // non impedant branch

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());

        // also test that it works in DC mode
        parameters.setDc(true);
        result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
    }

    @Test
    void twoLinkedPVBusesTest() {
        Network network = Network.create("TwoPVBusesWithNonImpLine", "code");
        Bus b1 = createBus(network, "b1");
        Bus b2 = createBus(network, "b2");
        createGenerator(b1, "g1", 2, 1);
        createGenerator(b2, "g2", 2, 1);
        Line l12 = createLine(network, b1, b2, "l12", 0);

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertVoltageEquals(1, b1);
        assertVoltageEquals(1, b2);
        assertAngleEquals(0, b1);
        assertAngleEquals(0, b2);
        assertActivePowerEquals(0, l12.getTerminal1());
        assertActivePowerEquals(0, l12.getTerminal2());
        assertReactivePowerEquals(0, l12.getTerminal1());
        assertReactivePowerEquals(0, l12.getTerminal2());
    }

    /**
     *
     * g1 (2MW)    g3 (2 MW)
     * |           |
     * b1 -- b2 -- b3
     *       |
     *       l2 (4 MW, 2 MVar)
     */
    @Test
    void nonImpedantNetworkWithTwoPVBusesTest() {
        Network network = Network.create("TwoPVBusesInNonImpNet", "code");
        Bus b1 = createBus(network, "b1");
        Bus b2 = createBus(network, "b2");
        Bus b3 = createBus(network, "b3");
        createGenerator(b1, "g1", 2, 1);
        createGenerator(b3, "g3", 2, 1);
        createLoad(b2, "l2", 4, 2);
        Line l12 = createLine(network, b1, b2, "l12", 0);
        Line l23 = createLine(network, b2, b3, "l23", 0);

        LoadFlowResult result = loadFlowRunner.run(network, parameters);

        assertTrue(result.isOk());
        assertVoltageEquals(1, b1);
        assertVoltageEquals(1, b2);
        assertVoltageEquals(1, b3);
        assertAngleEquals(0, b1);
        assertAngleEquals(0, b2);
        assertAngleEquals(0, b3);
        assertActivePowerEquals(2, l12.getTerminal1());
        assertActivePowerEquals(-2, l12.getTerminal2());
        assertActivePowerEquals(-2, l23.getTerminal1());
        assertActivePowerEquals(2, l23.getTerminal2());
        assertReactivePowerEquals(1, l12.getTerminal1());
        assertReactivePowerEquals(-1, l12.getTerminal2());
        assertReactivePowerEquals(-1, l23.getTerminal1());
        assertReactivePowerEquals(1, l23.getTerminal2());
    }

    @Test
    void nonImpedantNetworkWithCycleTest() {
        Network network = Network.create("ThreeBusesNetworkWithCycle", "code");
        Bus b1 = createBus(network, "b1");
        Bus b2 = createBus(network, "b2");
        Bus b3 = createBus(network, "b3");
        createGenerator(b1, "g1", 2, 1);
        createGenerator(b3, "g3", 2, 1);
        createLoad(b2, "l2", 4, 2);
        createLine(network, b1, b2, "l12", 0);
        createLine(network, b2, b3, "l23", 0);
        createLine(network, b3, b1, "l31", 0);

        LoadFlowResult result = loadFlowRunner.run(network, parameters);

        assertTrue(result.isOk());
        assertVoltageEquals(1, b1);
        assertVoltageEquals(1, b2);
        assertVoltageEquals(1, b3);
        assertAngleEquals(0, b1);
        assertAngleEquals(0, b2);
        assertAngleEquals(0, b3);
    }

    @Test
    void securityAnalysisTest() {
        Network network = Network.create("test", "code");
        Bus b0 = createBus(network, "b0");
        Bus b1 = createBus(network, "b1");
        Bus b2 = createBus(network, "b2");
        Bus b3 = createBus(network, "b3");
        Bus b5 = createBus(network, "b5");
        Bus b7 = createBus(network, "b7");
        Bus b9 = createBus(network, "b9");
        createGenerator(b1, "g1", 2);
        createGenerator(b9, "g9", 1);
        createLoad(b2, "d2", 1);
        createLoad(b7, "d7", 4);
        createLine(network, b0, b1, "l01", 0.0);
        createLine(network, b0, b2, "l02", 0.0);
        createLine(network, b1, b3, "l13", 0.0);
        createLine(network, b2, b3, "l23", 0.0);
        createLine(network, b2, b5, "l25", 0.0);
        createLine(network, b5, b9, "l59", 0.1);
        createLine(network, b7, b9, "l79", 0.1);

        List<Contingency> contingencies = List.of(new Contingency("contingency1", List.of(new BranchContingency("l01"), new BranchContingency("l02"))),
                                                  new Contingency("contingency2", List.of(new BranchContingency("l01"), new BranchContingency("l13"))));

        ContingenciesProvider provider = n -> contingencies;
        SecurityAnalysisProvider securityAnalysisProvider = new OpenSecurityAnalysisProvider(new DenseMatrixFactory(), new EvenShiloachGraphDecrementalConnectivityFactory<>());
        SecurityAnalysisReport report = securityAnalysisProvider.run(network, network.getVariantManager().getWorkingVariantId(), new DefaultLimitViolationDetector(),
                new LimitViolationFilter(), LocalComputationManager.getDefault(), new SecurityAnalysisParameters(), provider, Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), Reporter.NO_OP).join();
        assertTrue(report.getResult().getPostContingencyResults().get(0).getStatus().equals(PostContingencyComputationStatus.CONVERGED));
        assertTrue(report.getResult().getPostContingencyResults().get(1).getStatus().equals(PostContingencyComputationStatus.CONVERGED));
    }
}
