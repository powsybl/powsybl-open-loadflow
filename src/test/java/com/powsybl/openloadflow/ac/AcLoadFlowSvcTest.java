/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.StaticVarCompensator;
import com.powsybl.iidm.network.extensions.VoltagePerReactivePowerControl;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.ac.nr.DefaultAcLoadFlowObserver;
import com.powsybl.openloadflow.ac.util.LfNetworkAndEquationSystemCreationAcLoadFlowObserver;
import com.powsybl.openloadflow.ac.util.NetworkBuilder;
import com.powsybl.openloadflow.equations.Equation;
import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.equations.EquationType;
import com.powsybl.openloadflow.network.MostMeshedSlackBusSelector;
import com.powsybl.openloadflow.util.LoadFlowAssert;
import com.powsybl.openloadflow.util.LoadFlowRunResults;
import org.hamcrest.core.IsEqual;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.powsybl.openloadflow.util.LoadFlowAssert.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SVC test case.
 *
 * g1        ld1
 * |          |
 * b1---------b2
 *      l1    |
 *           svc1
 *
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
class AcLoadFlowSvcTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(AcLoadFlowSvcTest.class);

    private LoadFlow.Runner loadFlowRunner;

    private LoadFlowParameters parameters;
    private OpenLoadFlowParameters parametersExt;

    @BeforeEach
    void setUp() {
        loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        parameters = new LoadFlowParameters().setNoGeneratorReactiveLimits(false)
                .setDistributedSlack(false);
        this.parametersExt = new OpenLoadFlowParameters()
                .setSlackBusSelector(new MostMeshedSlackBusSelector());
        parameters.addExtension(OpenLoadFlowParameters.class, parametersExt);
    }

    @Test
    void test() {
        NetworkBuilder networkBuilder = new NetworkBuilder();
        Network network = networkBuilder.addNetworkBus1GenBus2Svc().addBus2Load().build();
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());

        assertVoltageEquals(390, networkBuilder.getBus1());
        assertAngleEquals(0, networkBuilder.getBus1());
        assertVoltageEquals(388.581824, networkBuilder.getBus2());
        assertAngleEquals(-0.057845, networkBuilder.getBus2());
        assertActivePowerEquals(101.216, networkBuilder.getBus1bus2line().getTerminal1());
        assertReactivePowerEquals(150.649, networkBuilder.getBus1bus2line().getTerminal1());
        assertActivePowerEquals(-101, networkBuilder.getBus1bus2line().getTerminal2());
        assertReactivePowerEquals(-150, networkBuilder.getBus1bus2line().getTerminal2());
        assertTrue(Double.isNaN(networkBuilder.getBus2svc().getTerminal().getP()));
        assertTrue(Double.isNaN(networkBuilder.getBus2svc().getTerminal().getQ()));

        networkBuilder.getBus2svc().setVoltageSetPoint(385)
                .setRegulationMode(StaticVarCompensator.RegulationMode.VOLTAGE);

        result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());

        assertVoltageEquals(390, networkBuilder.getBus1());
        assertAngleEquals(0, networkBuilder.getBus1());
        assertVoltageEquals(385, networkBuilder.getBus2());
        assertAngleEquals(0.116345, networkBuilder.getBus2());
        assertActivePowerEquals(103.562, networkBuilder.getBus1bus2line().getTerminal1());
        assertReactivePowerEquals(615.582, networkBuilder.getBus1bus2line().getTerminal1());
        assertActivePowerEquals(-101, networkBuilder.getBus1bus2line().getTerminal2());
        assertReactivePowerEquals(-607.897, networkBuilder.getBus1bus2line().getTerminal2());
        assertActivePowerEquals(0, networkBuilder.getBus2svc().getTerminal());
        assertReactivePowerEquals(457.896, networkBuilder.getBus2svc().getTerminal());
    }

    @Test
    void shouldReachReactiveMaxLimit() {
        NetworkBuilder networkBuilder = new NetworkBuilder();
        Network network = networkBuilder.addNetworkBus1GenBus2Svc().addBus2Load().build();
        networkBuilder.getBus2svc().setBmin(-0.002)
                .setVoltageSetpoint(385)
                .setRegulationMode(StaticVarCompensator.RegulationMode.VOLTAGE);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertReactivePowerEquals(-networkBuilder.getBus2svc().getBmin() * networkBuilder.getBus2svc().getVoltageSetpoint() * networkBuilder.getBus2svc().getVoltageSetpoint(), networkBuilder.getBus2svc().getTerminal()); // min reactive limit has been correctly reached
    }

    @FunctionalInterface
    private interface NetworkCreator {
        Network create();
    }

    private enum NetworkDescription { BUS1_GEN_BUS2_SVC, BUS1_GEN_BUS2_SVC_LOAD, BUS1_GEN_BUS2_SVC_LOAD_GEN, BUS1_GEN_BUS2_SVC_LOAD_GEN_SC, BUS1_GEN_OLINE_BUS2_SVC_LOAD_GEN_SC_OLINE }
    private enum RunningParameters { USE_BUS_PV, USE_BUS_PVLQ }

    private void runLoadFlowAndStoreReports(NetworkCreator networkCreator, NetworkDescription networkDescription, LoadFlowRunResults<NetworkDescription, RunningParameters> loadFlowRunResults) {
        // 1 - run with bus2 as bus PV
        parametersExt.setUseBusPVLQ(false);
        Network network = networkCreator.create();
        assertTrue(loadFlowRunner.run(network, parameters).isOk());
        loadFlowRunResults.addLoadFlowReport(networkDescription, RunningParameters.USE_BUS_PV, network);

        // 2 - run with bus2 as bus PVLQ
        parametersExt.setUseBusPVLQ(true);
        network = networkCreator.create();
        assertTrue(loadFlowRunner.run(network, parameters).isOk());
        loadFlowRunResults.addLoadFlowReport(networkDescription, RunningParameters.USE_BUS_PVLQ, network);
    }

    private void shouldIncreaseQsvc(String reason, LoadFlowRunResults<NetworkDescription, RunningParameters> loadFlowRunResults, NetworkDescription actualNetwork, NetworkDescription previousNetwork) {
        Network actualNetworkPV = loadFlowRunResults.getLoadFlowReport(actualNetwork, RunningParameters.USE_BUS_PV);
        Network previousNetworkPV = loadFlowRunResults.getLoadFlowReport(previousNetwork, RunningParameters.USE_BUS_PV);
        Network actualNetworkPVLQ = loadFlowRunResults.getLoadFlowReport(actualNetwork, RunningParameters.USE_BUS_PVLQ);
        Network previousNetworkPVLQ = loadFlowRunResults.getLoadFlowReport(previousNetwork, RunningParameters.USE_BUS_PVLQ);
        if (actualNetworkPV != null && previousNetworkPV != null) {
            assertThat("in case of PV bus : " + reason, -actualNetworkPV.getStaticVarCompensator("bus2svc").getTerminal().getQ(),
                    new LoadFlowAssert.GreaterThan(-previousNetworkPV.getStaticVarCompensator("bus2svc").getTerminal().getQ()));
        }
        if (actualNetworkPVLQ != null && previousNetworkPVLQ != null) {
            assertThat("in case of PVLQ bus : " + reason, -actualNetworkPVLQ.getStaticVarCompensator("bus2svc").getTerminal().getQ(),
                    new LoadFlowAssert.GreaterThan(-previousNetworkPVLQ.getStaticVarCompensator("bus2svc").getTerminal().getQ()));
        }
    }

    private void shouldLowerQsvc(String reason, LoadFlowRunResults<NetworkDescription, RunningParameters> loadFlowRunResults, NetworkDescription actualNetwork, NetworkDescription previousNetwork) {
        Network actualNetworkPV = loadFlowRunResults.getLoadFlowReport(actualNetwork, RunningParameters.USE_BUS_PV);
        Network previousNetworkPV = loadFlowRunResults.getLoadFlowReport(previousNetwork, RunningParameters.USE_BUS_PV);
        Network actualNetworkPVLQ = loadFlowRunResults.getLoadFlowReport(actualNetwork, RunningParameters.USE_BUS_PVLQ);
        Network previousNetworkPVLQ = loadFlowRunResults.getLoadFlowReport(previousNetwork, RunningParameters.USE_BUS_PVLQ);
        if (actualNetworkPV != null && previousNetworkPV != null) {
            assertThat("in case of PV bus : " + reason, -actualNetworkPV.getStaticVarCompensator("bus2svc").getTerminal().getQ(),
                    new LoadFlowAssert.LowerThan(-previousNetworkPV.getStaticVarCompensator("bus2svc").getTerminal().getQ()));
        }
        if (actualNetworkPVLQ != null && previousNetworkPVLQ != null) {
            assertThat("in case of PVLQ bus : " + reason, -actualNetworkPVLQ.getStaticVarCompensator("bus2svc").getTerminal().getQ(),
                    new LoadFlowAssert.LowerThan(-previousNetworkPVLQ.getStaticVarCompensator("bus2svc").getTerminal().getQ()));
        }
    }

    private void shouldMatchVoltageTerm(LoadFlowRunResults<NetworkDescription, RunningParameters> loadFlowRunResults) {
        Map<NetworkDescription, Map<RunningParameters, Network>> networkResultByRunningParametersAndNetworkDescription = loadFlowRunResults.getNetworkResultByRunningParametersAndNetworkDescription();
        for (NetworkDescription networkDescription : networkResultByRunningParametersAndNetworkDescription.keySet()) {
            Map<RunningParameters, Network> networkResultByRunningParameters = networkResultByRunningParametersAndNetworkDescription.get(networkDescription);
            Network networkPV = networkResultByRunningParameters.get(RunningParameters.USE_BUS_PV);
            Network networkPVLQ = networkResultByRunningParameters.get(RunningParameters.USE_BUS_PVLQ);
            // assertions
            Double slope = networkPVLQ.getStaticVarCompensator("bus2svc").getExtension(VoltagePerReactivePowerControl.class).getSlope();
            assertThat("Network " + networkDescription + " : with PV bus, V on bus2 should remains constant", networkPV.getBusView().getBus("vl2_0").getV(),
                    new IsEqual(networkPV.getStaticVarCompensator("bus2svc").getVoltageSetpoint()));
            assertThat("Network " + networkDescription + " : with PVLQ bus, voltageSetpoint should be equals to 'V on bus2 + slope * Qsvc'", networkPVLQ.getStaticVarCompensator("bus2svc").getVoltageSetpoint(),
                    new LoadFlowAssert.EqualsTo(networkPVLQ.getBusView().getBus("vl2_0").getV() +
                            slope * (-networkPVLQ.getStaticVarCompensator("bus2svc").getTerminal().getQ()), DELTA_V));
            assertThat("Network " + networkDescription + " : Qsvc should be greater with bus PVLQ than PV", -networkPVLQ.getStaticVarCompensator("bus2svc").getTerminal().getQ(),
                    new LoadFlowAssert.GreaterThan(-networkPV.getStaticVarCompensator("bus2svc").getTerminal().getQ()));
            assertThat("Network " + networkDescription + " : V on bus2 should be greater with bus PVLQ than PV", networkPVLQ.getBusView().getBus("vl2_0").getV(),
                    new LoadFlowAssert.GreaterThan(networkPV.getBusView().getBus("vl2_0").getV()));
        }
    }

    @Test
    void shouldRunLoadFlowWithBusVLQ() {
        this.parametersExt.getAdditionalObservers().add(new LfNetworkAndEquationSystemCreationAcLoadFlowObserver());

        // build loadflow results
        LoadFlowRunResults<NetworkDescription, RunningParameters> loadFlowRunResults = new LoadFlowRunResults<>();
//        this.runLoadFlowAndStoreReports(() -> new NetworkBuilder().addNetworkBus1GenBus2Svc().setBus2SvcVoltageAndSlope().build(), NetworkDescription.BUS1_GEN_BUS2_SVC, loadFlowRunResults);
//        this.runLoadFlowAndStoreReports(() -> new NetworkBuilder().addNetworkBus1GenBus2Svc().setBus2SvcVoltageAndSlope().addBus2Load().build(), NetworkDescription.BUS1_GEN_BUS2_SVC_LOAD, loadFlowRunResults);
//        this.runLoadFlowAndStoreReports(() -> new NetworkBuilder().addNetworkBus1GenBus2Svc().setBus2SvcVoltageAndSlope().addBus2Load().addBus2Gen().build(), NetworkDescription.BUS1_GEN_BUS2_SVC_LOAD_GEN, loadFlowRunResults);
//        this.runLoadFlowAndStoreReports(() -> new NetworkBuilder().addNetworkBus1GenBus2Svc().setBus2SvcVoltageAndSlope().addBus2Load().addBus2Gen().addBus2Sc().build(), NetworkDescription.BUS1_GEN_BUS2_SVC_LOAD_GEN_SC, loadFlowRunResults);
        this.runLoadFlowAndStoreReports(() -> new NetworkBuilder().addNetworkBus1GenBus2Svc().setBus2SvcVoltageAndSlope().addBus2Load().addBus2Gen().addBus2Sc().addBus1OpenLine().addBus2OpenLine().build(), NetworkDescription.BUS1_GEN_OLINE_BUS2_SVC_LOAD_GEN_SC_OLINE, loadFlowRunResults);

        // display results
        loadFlowRunResults.displayAll();

        // assertions
        shouldMatchVoltageTerm(loadFlowRunResults);
        shouldIncreaseQsvc("with a load addition, Qsvc should be greater", loadFlowRunResults, NetworkDescription.BUS1_GEN_BUS2_SVC_LOAD, NetworkDescription.BUS1_GEN_BUS2_SVC);
        shouldLowerQsvc("with a generator addition, Qsvc should be lower", loadFlowRunResults, NetworkDescription.BUS1_GEN_BUS2_SVC_LOAD_GEN, NetworkDescription.BUS1_GEN_BUS2_SVC_LOAD);
        shouldLowerQsvc("with a shunt addition, Qsvc should be lower", loadFlowRunResults, NetworkDescription.BUS1_GEN_BUS2_SVC_LOAD_GEN_SC, NetworkDescription.BUS1_GEN_BUS2_SVC_LOAD_GEN);
        loadFlowRunResults.shouldHaveValidSumOfQinLines();
    }

    @Test
    void shouldSwitchPvlqToPq() {
        // add an observer on loadflow process in order to trace switch between PV bus to PQ bus
        final List<Boolean> equationVLQisActives = new LinkedList<>();
        final boolean[] hasSwitchPvlqPq = new boolean[1];
        parametersExt.getAdditionalObservers().add(new DefaultAcLoadFlowObserver() {
            @Override
            public void afterEquationVectorCreation(double[] fx, EquationSystem equationSystem, int iteration) {
                Optional<Equation> equation = equationSystem.getEquation(1, EquationType.BUS_VLQ);
                if (equation.isPresent()) {
                    equationVLQisActives.add(equation.get().isActive());
                }
            }

            @Override
            public void afterOuterLoopBody(int outerLoopIteration, String outerLoopName) {
                if (outerLoopName.equals("Reactive limits")) {
                    hasSwitchPvlqPq[0] = true;
                }
            }
        });

        // network and parameters setup
        NetworkBuilder networkBuilder = new NetworkBuilder();
        Network network = networkBuilder.addNetworkBus1GenBus2Svc().setBus2SvcVoltageAndSlope().addBus2Load().build();
        networkBuilder.getBus2svc().setBmin(-0.001);
        parametersExt.setUseBusPVLQ(true);
        Double slope = networkBuilder.getBus2svc().getExtension(VoltagePerReactivePowerControl.class).getSlope();

        // assertions
        assertTrue(loadFlowRunner.run(network, parameters).isOk());
        assertTrue(hasSwitchPvlqPq[0] && equationVLQisActives.size() >= 2 && equationVLQisActives.get(0) && !equationVLQisActives.get(equationVLQisActives.size() - 1));
    }
}
