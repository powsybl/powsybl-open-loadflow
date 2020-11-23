/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac;

import static com.powsybl.openloadflow.util.LoadFlowAssert.assertActivePowerEquals;
import static com.powsybl.openloadflow.util.LoadFlowAssert.assertLoadFlowResultsEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.powsybl.iidm.network.Load;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.extensions.LoadDetail;
import com.powsybl.iidm.network.extensions.LoadDetailAdder;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.network.DistributedSlackNetworkFactory;
import com.powsybl.openloadflow.network.MostMeshedSlackBusSelector;
import com.powsybl.openloadflow.util.LoadFlowResultBuilder;
import java.util.Iterator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


/**
 * @author Anne Tilloy <anne.tilloy at rte-france.com>
 */
class DistributedSlackOnLoadTest {

    private Network network;
    private Load l1;
    private Load l2;
    private Load l3;
    private Load l4;
    private Load l5;
    private Load l6;
    private LoadFlow.Runner loadFlowRunner;
    private LoadFlowParameters parameters;
    private OpenLoadFlowParameters parametersExt;
    private static final double ONE_BILLION = 1000000000;

    @BeforeEach
    void setUp() {
        network = DistributedSlackNetworkFactory.createNetworkWithLoads();
        l1 = network.getLoad("l1");
        l2 = network.getLoad("l2");
        l3 = network.getLoad("l3");
        l4 = network.getLoad("l4");
        l5 = network.getLoad("l5");
        l6 = network.getLoad("l6");
        loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        parameters = new LoadFlowParameters().setDistributedSlack(true)
                .setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD);
        parametersExt = new OpenLoadFlowParameters()
                .setSlackBusSelector(new MostMeshedSlackBusSelector());
        parameters.addExtension(OpenLoadFlowParameters.class, parametersExt);
    }

    @Test
    void test() {
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertActivePowerEquals(37.5, l1.getTerminal());
        assertActivePowerEquals(75, l2.getTerminal());
        assertActivePowerEquals(62.5, l3.getTerminal());
        assertActivePowerEquals(175, l4.getTerminal());
        assertActivePowerEquals(12.5, l5.getTerminal());
        assertActivePowerEquals(-50, l6.getTerminal()); // same as p0 because p0 < 0
        LoadFlowResult loadFlowResultExpected = new LoadFlowResultBuilder(true).addMetrics("3", "CONVERGED").addComponentResult(0, LoadFlowResult.ComponentResult.Status.CONVERGED, 3, "b4_vl_0", 1.6895598253796607E-7).build();
        assertLoadFlowResultsEquals(loadFlowResultExpected, result);
    }

    @Test
    void testWithLoadDetail() {
        l2.newExtension(LoadDetailAdder.class)
                .withVariableActivePower(40)
                .withFixedActivePower(20)
                .add();
        parameters.setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_CONFORM_LOAD);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertActivePowerEquals(38.182, l1.getTerminal());
        assertActivePowerEquals(70.909, l2.getTerminal());
        assertActivePowerEquals(63.636, l3.getTerminal());
        assertActivePowerEquals(178.182, l4.getTerminal());
        assertActivePowerEquals(12.727, l5.getTerminal());
        assertActivePowerEquals(-50, l6.getTerminal()); // same as p0 because p0 < 0
        LoadFlowResult loadFlowResultExpected = new LoadFlowResultBuilder(true).addMetrics("3", "CONVERGED").addComponentResult(0, LoadFlowResult.ComponentResult.Status.CONVERGED, 3, "b4_vl_0", 9.726437433243973E-8).build();
        assertLoadFlowResultsEquals(loadFlowResultExpected, result);
    }

    private void assertPowerFactor(Network network) {
        Iterator<Load> loads = network.getLoads().iterator();
        while (loads.hasNext()) {
            Load load = loads.next();
            if (parametersExt.isPowerFactorConstant()
                    && (parameters.getBalanceType() == LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD
                    || parameters.getBalanceType() == LoadFlowParameters.BalanceType.PROPORTIONAL_TO_CONFORM_LOAD)) {
                if (parameters.getBalanceType() == LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD) {
                    // there is precision loss here, use round on value previously multiplied by one million
                    assertEquals(Math.round(ONE_BILLION * load.getP0() / load.getQ0()),
                            Math.round(ONE_BILLION * load.getTerminal().getP() / load.getTerminal().getQ()),
                            "power factor should be a constant value");
                } else {
                    LoadDetail loadDetail = load.getExtension(LoadDetail.class);
                    double fixedLoadTargetP = 0;
                    double fixedLoadTargetQ = 0;
                    if (loadDetail != null) {
                        fixedLoadTargetP = loadDetail.getFixedActivePower();
                        fixedLoadTargetQ = loadDetail.getFixedReactivePower();
                    }
                    assertEquals(Math.round(ONE_BILLION * ((load.getP0() - fixedLoadTargetP) / (load.getQ0() - fixedLoadTargetQ))),
                            Math.round(ONE_BILLION * (load.getTerminal().getP() - fixedLoadTargetP) / (load.getTerminal().getQ() - fixedLoadTargetQ)),
                            "power factor should be a constant value");
                }
            } else {
                assertNotEquals(Math.round(ONE_BILLION * load.getP0() / load.getQ0()),
                        Math.round(ONE_BILLION * load.getTerminal().getP() / load.getTerminal().getQ()),
                        "power factor should not be a constant value");
            }
        }

    }

    @Test
    void testPowerFactorConstant() {
        // 1a -> test case 1a : PROPORTIONAL_TO_LOAD + not remain power factor constant
        // given
        parameters.setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD);
        parametersExt.setPowerFactorConstant(false);
        Network network1a = EurostagTutorialExample1Factory.create();

        // when
        LoadFlowResult loadFlowResult1a = loadFlowRunner.run(network1a, parameters);

        // then
        assertPowerFactor(network1a);
        LoadFlowResult loadFlowResultExpected1a = new LoadFlowResultBuilder(true).addMetrics("5", "CONVERGED").addComponentResult(0, LoadFlowResult.ComponentResult.Status.CONVERGED, 5, "VLHV1_0", -1.241543164276493E-4).build();
        assertLoadFlowResultsEquals(loadFlowResultExpected1a, loadFlowResult1a);

        // 1b -> test case 1b : PROPORTIONAL_TO_LOAD + not remain power factor constant
        // given
        parameters.setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_CONFORM_LOAD);
        parametersExt.setPowerFactorConstant(false);
        Network network1b = EurostagTutorialExample1Factory.create();
        network1b.getLoad("LOAD").newExtension(LoadDetailAdder.class)
                .withFixedActivePower(400).withVariableActivePower(200)
                .withFixedReactivePower(150).withVariableReactivePower(50)
                .add();

        // when
        LoadFlowResult loadFlowResult1b = loadFlowRunner.run(network1b, parameters);

        // then
        assertPowerFactor(network1b);
        LoadFlowResult loadFlowResultExpected1b = new LoadFlowResultBuilder(true).addMetrics("5", "CONVERGED").addComponentResult(0, LoadFlowResult.ComponentResult.Status.CONVERGED, 5, "VLHV1_0", -1.241543164276493E-4).build();
        assertLoadFlowResultsEquals(loadFlowResultExpected1b, loadFlowResult1b);

        // 2a -> test case 2a : PROPORTIONAL_TO_LOAD + remain power factor constant
        // given
        parameters.setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD);
        parametersExt.setPowerFactorConstant(true);
        Network network2a = EurostagTutorialExample1Factory.create();

        //when
        LoadFlowResult loadFlowResult2a = loadFlowRunner.run(network2a, parameters);

        // then
        assertPowerFactor(network2a);
        LoadFlowResult loadFlowResultExpected2a = new LoadFlowResultBuilder(true).addMetrics("5", "CONVERGED").addComponentResult(0, LoadFlowResult.ComponentResult.Status.CONVERGED, 5, "VLHV1_0", -3.06844963660069E-5).build();
        assertLoadFlowResultsEquals(loadFlowResultExpected2a, loadFlowResult2a);

        // 2b -> test case 2b : PROPORTIONAL_TO_CONFORM_LOAD + remain power factor constant
        // given
        parameters.setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_CONFORM_LOAD);
        parametersExt.setPowerFactorConstant(true);
        Network network2b = EurostagTutorialExample1Factory.create();
        // fixedActivePower and FixedReactivePower are unbalanced
        network2b.getLoad("LOAD").newExtension(LoadDetailAdder.class)
                .withFixedActivePower(500).withVariableActivePower(100)
                .withFixedReactivePower(150).withVariableReactivePower(50)
                .add();

        //when
        LoadFlowResult loadFlowResult2b = loadFlowRunner.run(network2b, parameters);

        // then
        assertPowerFactor(network2b);
        LoadFlowResult loadFlowResultExpected2b = new LoadFlowResultBuilder(true).addMetrics("5", "CONVERGED").addComponentResult(0, LoadFlowResult.ComponentResult.Status.CONVERGED, 5, "VLHV1_0", 1.340823176931849E-5).build();
        assertLoadFlowResultsEquals(loadFlowResultExpected2b, loadFlowResult2b);
    }
}
