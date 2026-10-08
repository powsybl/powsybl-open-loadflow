/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.commons.test.PowsyblTestReportResourceBundle;
import com.powsybl.iidm.network.*;
import com.powsybl.iidm.network.extensions.StandbyAutomatonAdder;
import com.powsybl.iidm.network.regulation.RegulationMode;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.loadflow.LoadFlowRunParameters;
import com.powsybl.openloadflow.CommonTestConfig;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.ServiceParameterResolver;
import com.powsybl.openloadflow.graph.NaiveGraphConnectivityFactory;
import com.powsybl.openloadflow.network.BusState;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfElement;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.LfShunt;
import com.powsybl.openloadflow.network.SlackBusSelectionMode;
import com.powsybl.openloadflow.network.VoltageControlNetworkFactory;
import com.powsybl.openloadflow.network.impl.Networks;
import com.powsybl.openloadflow.util.PerUnit;
import com.powsybl.openloadflow.util.report.PowsyblOpenLoadFlowReportResourceBundle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;

import static com.powsybl.openloadflow.util.LoadFlowAssert.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * SVC test case.
 *<pre>
 * g1        ld1
 * |          |
 * b1---------b2
 *      l1    |
 *           svc1
 *</pre>
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
@ExtendWith(ServiceParameterResolver.class)
class AcLoadFlowSvcTest {

    private final CommonTestConfig commonTestConfig;

    AcLoadFlowSvcTest(CommonTestConfig commonTestConfig) {
        this.commonTestConfig = commonTestConfig;
    }

    private Network network;
    private Bus bus1;
    private Bus bus2;
    private Line l1;
    private Generator g1;
    private StaticVarCompensator svc1;

    private LoadFlow.Runner loadFlowRunner;

    private LoadFlowParameters parameters;

    private OpenLoadFlowParameters parametersExt;

    private Network createNetwork() {
        Network net = VoltageControlNetworkFactory.createWithStaticVarCompensator();
        bus1 = net.getBusBreakerView().getBus("b1");
        bus2 = net.getBusBreakerView().getBus("b2");
        svc1 = net.getStaticVarCompensator("svc1");
        l1 = net.getLine("l1");
        g1 = net.getGenerator("g1");
        return net;
    }

    @BeforeEach
    void setUp() {
        network = createNetwork();
        loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(commonTestConfig.matrixFactory()));
        parameters = new LoadFlowParameters().setUseReactiveLimits(true)
                .setDistributedSlack(false);
        parametersExt = OpenLoadFlowParameters.create(parameters)
                .setSlackBusSelectionMode(SlackBusSelectionMode.MOST_MESHED)
                .setVoltagePerReactivePowerControl(false)
                .setSvcVoltageMonitoring(false);
    }

    @Test
    void test() {
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());

        assertVoltageEquals(390, bus1);
        assertAngleEquals(0, bus1);
        assertVoltageEquals(388.581824, bus2);
        assertAngleEquals(-0.057845, bus2);
        assertActivePowerEquals(101.216, l1.getTerminal1());
        assertReactivePowerEquals(150.649, l1.getTerminal1());
        assertActivePowerEquals(-101, l1.getTerminal2());
        assertReactivePowerEquals(-150, l1.getTerminal2());
        // SVC is OFF
        assertActivePowerEquals(0, svc1.getTerminal());
        assertReactivePowerEquals(0, svc1.getTerminal());

        svc1.setVoltageSetpoint(385)
                .setRegulationMode(RegulationMode.VOLTAGE)
                .setRegulating(true);

        result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());

        assertVoltageEquals(390, bus1);
        assertAngleEquals(0, bus1);
        assertVoltageEquals(385, bus2);
        assertAngleEquals(0.116345, bus2);
        assertActivePowerEquals(103.562, l1.getTerminal1());
        assertReactivePowerEquals(615.582, l1.getTerminal1());
        assertActivePowerEquals(-101, l1.getTerminal2());
        assertReactivePowerEquals(-607.897, l1.getTerminal2());
        assertActivePowerEquals(0, svc1.getTerminal());
        assertReactivePowerEquals(457.896, svc1.getTerminal());
    }

    @Test
    void shouldReachReactiveMaxLimit() throws IOException {
        svc1.setBmin(-0.002)
                .setVoltageSetpoint(385)
                .setRegulationMode(RegulationMode.VOLTAGE)
                .setRegulating(true);
        ReportNode reportNode = ReportNode.newRootReportNode()
                .withResourceBundles(PowsyblOpenLoadFlowReportResourceBundle.BASE_NAME, PowsyblTestReportResourceBundle.TEST_BASE_NAME)
                .withMessageTemplate("testReport")
                .build();
        LoadFlowResult result = loadFlowRunner.run(network, new LoadFlowRunParameters().setParameters(parameters).setReportNode(reportNode));
        assertTrue(result.isFullyConverged());
        Bus bus = svc1.getTerminal().getBusView().getBus();
        assertVoltageEquals(386.256, bus);
        assertReactivePowerEquals(-svc1.getBmin() * bus.getV() * bus.getV(), svc1.getTerminal()); // min reactive limit has been correctly reached

        assertTxtReportEquals("""
                + Test Report
                   + Load flow on network 'svc'
                      + Network CC0 SC0
                         + Network info
                            Network has 2 buses and 1 branches
                            Network balance: active generation=101.3664 MW, active load=101 MW, reactive generation=0 MVar, reactive load=150 MVar
                            Angle reference bus: vl1_0
                            Slack bus: vl1_0
                         Voltage initialization with method Uniform Values
                         + Outer loop ReactiveLimits
                            + Outer loop iteration 1
                               + 1 buses switched PV -> PQ (1 buses remain PV)
                                  Switch bus 'vl2_0' PV -> PQ, q=-457.896814 < minQ=-296.45
                         AC load flow completed successfully (solverStatus=CONVERGED, outerloopStatus=STABLE)
                """, reportNode);
    }

    @Test
    void shouldReachVoltageDependentReactiveLimitInOneOuterLoopIteration() throws IOException {
        // SVC reactive limits are B * V^2: with a frozen Q at limit, each voltage change would change the limit and,
        // on a weak network (high X), require many outer loop iterations. Modeled as a fixed susceptance at limit, one is enough.
        l1.setX(100);
        svc1.setBmax(0.002)
                .setVoltageSetpoint(450)
                .setRegulationMode(RegulationMode.VOLTAGE)
                .setRegulating(true);
        ReportNode reportNode = ReportNode.newRootReportNode()
                .withResourceBundles(PowsyblOpenLoadFlowReportResourceBundle.BASE_NAME, PowsyblTestReportResourceBundle.TEST_BASE_NAME)
                .withMessageTemplate("testReport")
                .build();
        LoadFlowResult result = loadFlowRunner.run(network, new LoadFlowRunParameters().setParameters(parameters).setReportNode(reportNode));
        assertTrue(result.isFullyConverged());
        Bus bus = svc1.getTerminal().getBusView().getBus();
        assertVoltageEquals(444.130, bus);
        assertReactivePowerEquals(-svc1.getBmax() * bus.getV() * bus.getV(), svc1.getTerminal());
        assertTxtReportEquals("""
                + Test Report
                   + Load flow on network 'svc'
                      + Network CC0 SC0
                         + Network info
                            Network has 2 buses and 1 branches
                            Network balance: active generation=101.3664 MW, active load=101 MW, reactive generation=0 MVar, reactive load=150 MVar
                            Angle reference bus: vl1_0
                            Slack bus: vl1_0
                         Voltage initialization with method Uniform Values
                         + Outer loop ReactiveLimits
                            + Outer loop iteration 1
                               + 1 buses switched PV -> PQ (1 buses remain PV)
                                  Switch bus 'vl2_0' PV -> PQ, q=424.07869 > maxQ=405
                         AC load flow completed successfully (solverStatus=CONVERGED, outerloopStatus=STABLE)
                """, reportNode);
    }

    @Test
    void shouldSwitchBackToPvAfterReachingLimitAsSusceptance() throws IOException {
        // g1 and svc1 both reach their limit at first outer loop iteration: g1 can no longer hold voltage, so that voltage
        // at svc1 bus decreases below its target and svc1 has to leave its susceptance at limit model to switch back PV
        Substation s3 = network.newSubstation().setId("S3").add();
        VoltageLevel vl3 = s3.newVoltageLevel().setId("vl3").setNominalV(400).setTopologyKind(TopologyKind.BUS_BREAKER).add();
        vl3.getBusBreakerView().newBus().setId("b3").add();
        vl3.newGenerator().setId("g3").setBus("b3").setConnectableBus("b3").setTargetP(0).setTargetV(400)
                .setMinP(0).setMaxP(500).setVoltageRegulatorOn(true).add();
        network.newLine().setId("l3").setBus1("b3").setBus2("b1").setR(1).setX(30).add();
        g1.setTargetV(400);
        g1.newMinMaxReactiveLimits().setMinQ(-100).setMaxQ(100).add();
        svc1.setBmin(-0.002)
                .setVoltageSetpoint(395)
                .setRegulationMode(RegulationMode.VOLTAGE)
                .setRegulating(true);
        ReportNode reportNode = ReportNode.newRootReportNode()
                .withResourceBundles(PowsyblOpenLoadFlowReportResourceBundle.BASE_NAME, PowsyblTestReportResourceBundle.TEST_BASE_NAME)
                .withMessageTemplate("testReport")
                .build();
        LoadFlowResult result = loadFlowRunner.run(network, new LoadFlowRunParameters().setParameters(parameters).setReportNode(reportNode));
        assertTrue(result.isFullyConverged());
        assertVoltageEquals(395, bus2);
        assertReactivePowerEquals(-2.711, svc1.getTerminal());
        assertReactivePowerEquals(-100, g1.getTerminal());
        assertTxtReportEquals("""
                + Test Report
                   + Load flow on network 'svc'
                      + Network CC0 SC0
                         + Network info
                            Network has 3 buses and 2 branches
                            Network balance: active generation=101.3664 MW, active load=101 MW, reactive generation=0 MVar, reactive load=150 MVar
                            Angle reference bus: vl1_0
                            Slack bus: vl1_0
                         Voltage initialization with method Uniform Values
                         + Outer loop ReactiveLimits
                            + Outer loop iteration 1
                               + 2 buses switched PV -> PQ (1 buses remain PV)
                                  Switch bus 'vl1_0' PV -> PQ, q=632.253926 > maxQ=100
                                  Switch bus 'vl2_0' PV -> PQ, q=-474.557598 < minQ=-312.05
                            + Outer loop iteration 2
                               + 1 buses switched PQ -> PV (0 buses blocked PQ due to the max number of switches)
                                  Switch bus 'vl2_0' PQ -> PV, q=minQ and v=369.965982kV < targetV=395kV
                         AC load flow completed successfully (solverStatus=CONVERGED, outerloopStatus=STABLE)
                """, reportNode);

        // a remaining susceptance at limit would not change the solution but would skew next reactive limits checks
        AcLoadFlowParameters acParameters = OpenLoadFlowParameters.createAcParameters(network, parameters, parametersExt,
                commonTestConfig.matrixFactory(), new NaiveGraphConnectivityFactory<>(LfElement::getNum));
        LfNetwork lfNetwork = Networks.load(network, acParameters.getNetworkParameters()).getFirst();
        try (AcLoadFlowContext context = new AcLoadFlowContext(lfNetwork, acParameters)) {
            new AcloadFlowEngine(context).run();
        }
        LfBus lfBus2 = lfNetwork.getBusById("vl2_0");
        assertTrue(lfBus2.isGeneratorVoltageControlEnabled());
        assertEquals(0, lfBus2.getSvcShunt().orElseThrow().getB());
    }

    @Test
    void shouldUpdateSusceptanceAtLimitWithBusState() {
        l1.setX(100);
        svc1.setBmax(0.002)
                .setVoltageSetpoint(450)
                .setRegulationMode(RegulationMode.VOLTAGE)
                .setRegulating(true);
        AcLoadFlowParameters acParameters = OpenLoadFlowParameters.createAcParameters(network, parameters, parametersExt,
                commonTestConfig.matrixFactory(), new NaiveGraphConnectivityFactory<>(LfElement::getNum));
        LfNetwork lfNetwork = Networks.load(network, acParameters.getNetworkParameters()).getFirst();
        try (AcLoadFlowContext context = new AcLoadFlowContext(lfNetwork, acParameters)) {
            new AcloadFlowEngine(context).run();
        }
        LfBus lfBus2 = lfNetwork.getBusById("vl2_0");
        LfShunt svcShunt = lfBus2.getSvcShunt().orElseThrow();
        double bmax = 0.002 * 400 * 400 / PerUnit.SB;
        assertEquals(bmax, svcShunt.getB(), 1e-12);
        BusState busState = BusState.save(lfBus2);

        // whatever the path re-enabling voltage control, the susceptance at limit is released
        lfBus2.setGeneratorVoltageControlEnabledAndRecomputeTargetQ(true);
        assertTrue(lfBus2.getQLimitType().isEmpty());
        assertEquals(0, svcShunt.getB());
        assertStaticVarCompensatorsConsistent(lfNetwork);

        busState.restore();
        assertEquals(bmax, svcShunt.getB(), 1e-12);
        assertStaticVarCompensatorsConsistent(lfNetwork);
    }

    @Test
    void shouldReachVoltageDependentReactiveLimitWhenSvcSharesBusWithGenerator() throws IOException {
        // the generator part of the bus limit is frozen, the SVC part follows the voltage through its susceptance
        l1.setX(100);
        svc1.setBmax(0.002)
                .setVoltageSetpoint(450)
                .setRegulationMode(RegulationMode.VOLTAGE)
                .setRegulating(true);
        Generator g2 = bus2.getVoltageLevel().newGenerator().setId("g2").setBus("b2").setConnectableBus("b2").setTargetP(1).setTargetV(450)
                .setMinP(0).setMaxP(10).setVoltageRegulatorOn(true)
                .add();
        g2.newMinMaxReactiveLimits().setMinQ(-10).setMaxQ(10).add();
        ReportNode reportNode = ReportNode.newRootReportNode()
                .withResourceBundles(PowsyblOpenLoadFlowReportResourceBundle.BASE_NAME, PowsyblTestReportResourceBundle.TEST_BASE_NAME)
                .withMessageTemplate("testReport")
                .build();
        LoadFlowResult result = loadFlowRunner.run(network, new LoadFlowRunParameters().setParameters(parameters).setReportNode(reportNode));
        assertTrue(result.isFullyConverged());
        assertVoltageEquals(447.250, bus2);
        assertReactivePowerEquals(-svc1.getBmax() * bus2.getV() * bus2.getV(), svc1.getTerminal());
        assertReactivePowerEquals(-10, g2.getTerminal());
        assertTxtReportEquals("""
                + Test Report
                   + Load flow on network 'svc'
                      + Network CC0 SC0
                         + Network info
                            Network has 2 buses and 1 branches
                            Network balance: active generation=102.3664 MW, active load=101 MW, reactive generation=0 MVar, reactive load=150 MVar
                            Angle reference bus: vl1_0
                            Slack bus: vl1_0
                         Voltage initialization with method Uniform Values
                         + Outer loop ReactiveLimits
                            + Outer loop iteration 1
                               + 1 buses switched PV -> PQ (1 buses remain PV)
                                  Switch bus 'vl2_0' PV -> PQ, q=424.009726 > maxQ=415
                         AC load flow completed successfully (solverStatus=CONVERGED, outerloopStatus=STABLE)
                """, reportNode);
    }

    @Test
    void shouldReachVoltageDependentReactiveLimitWithSeveralSvcsOnSameBus() throws IOException {
        l1.setX(100);
        svc1.setBmax(0.0015)
                .setVoltageSetpoint(450)
                .setRegulationMode(RegulationMode.VOLTAGE)
                .setRegulating(true);
        StaticVarCompensator svc2 = bus2.getVoltageLevel().newStaticVarCompensator()
                .setId("svc2")
                .setConnectableBus("b2")
                .setBus("b2")
                .setRegulationMode(RegulationMode.VOLTAGE)
                .setRegulating(true)
                .setVoltageSetpoint(450)
                .setBmin(-0.002)
                .setBmax(0.0005)
                .add();
        ReportNode reportNode = ReportNode.newRootReportNode()
                .withResourceBundles(PowsyblOpenLoadFlowReportResourceBundle.BASE_NAME, PowsyblTestReportResourceBundle.TEST_BASE_NAME)
                .withMessageTemplate("testReport")
                .build();
        LoadFlowResult result = loadFlowRunner.run(network, new LoadFlowRunParameters().setParameters(parameters).setReportNode(reportNode));
        assertTrue(result.isFullyConverged());
        // same as a single SVC with Bmax = 0.002, each SVC at its own limit
        assertVoltageEquals(444.130, bus2);
        assertReactivePowerEquals(-svc1.getBmax() * bus2.getV() * bus2.getV(), svc1.getTerminal());
        assertReactivePowerEquals(-svc2.getBmax() * bus2.getV() * bus2.getV(), svc2.getTerminal());
        assertTxtReportEquals("""
                + Test Report
                   + Load flow on network 'svc'
                      + Network CC0 SC0
                         + Network info
                            Network has 2 buses and 1 branches
                            Network balance: active generation=101.3664 MW, active load=101 MW, reactive generation=0 MVar, reactive load=150 MVar
                            Angle reference bus: vl1_0
                            Slack bus: vl1_0
                         Voltage initialization with method Uniform Values
                         + Outer loop ReactiveLimits
                            + Outer loop iteration 1
                               + 1 buses switched PV -> PQ (1 buses remain PV)
                                  Switch bus 'vl2_0' PV -> PQ, q=424.07869 > maxQ=405
                         AC load flow completed successfully (solverStatus=CONVERGED, outerloopStatus=STABLE)
                """, reportNode);
    }

    @Test
    void shouldReachVoltageDependentReactiveLimitWithStandbyAutomatonB0() throws IOException {
        // not in standby, so SVC is controlling voltage with B0 as susceptance offset: at limit, total susceptance is B0 + Bmax
        l1.setX(100);
        svc1.setBmax(0.003)
                .setVoltageSetpoint(450)
                .setRegulationMode(RegulationMode.VOLTAGE)
                .setRegulating(true);
        svc1.newExtension(StandbyAutomatonAdder.class)
                .withHighVoltageThreshold(460)
                .withLowVoltageThreshold(380)
                .withLowVoltageSetpoint(400)
                .withHighVoltageSetpoint(450)
                .withB0(-0.001f)
                .withStandbyStatus(false)
                .add();
        parametersExt.setSvcVoltageMonitoring(true);
        ReportNode reportNode = ReportNode.newRootReportNode()
                .withResourceBundles(PowsyblOpenLoadFlowReportResourceBundle.BASE_NAME, PowsyblTestReportResourceBundle.TEST_BASE_NAME)
                .withMessageTemplate("testReport")
                .build();
        LoadFlowResult result = loadFlowRunner.run(network, new LoadFlowRunParameters().setParameters(parameters).setReportNode(reportNode));
        assertTrue(result.isFullyConverged());
        // same as a single SVC with Bmax = 0.002
        assertVoltageEquals(444.130, bus2);
        assertReactivePowerEquals(-(svc1.getBmax() - 0.001) * bus2.getV() * bus2.getV(), svc1.getTerminal());
        String report = reportToString(reportNode);
        assertTrue(report.contains("Switch bus 'vl2_0' PV -> PQ"));
        assertFalse(report.contains("reactive limit changed"));
    }

    @Test
    void shouldReachVoltageDependentReactiveLimitWithSlope() throws IOException {
        l1.setX(100);
        svc1.setBmax(0.002).setVoltageSetpoint(450);
        svc1.newVoltageRegulation()
                .withMode(RegulationMode.VOLTAGE_PER_REACTIVE_POWER)
                .withSlope(0.01)
                .withRegulating(true)
                .build();
        parametersExt.setVoltagePerReactivePowerControl(true);
        ReportNode reportNode = ReportNode.newRootReportNode()
                .withResourceBundles(PowsyblOpenLoadFlowReportResourceBundle.BASE_NAME, PowsyblTestReportResourceBundle.TEST_BASE_NAME)
                .withMessageTemplate("testReport")
                .build();
        LoadFlowResult result = loadFlowRunner.run(network, new LoadFlowRunParameters().setParameters(parameters).setReportNode(reportNode));
        assertTrue(result.isFullyConverged());
        // at limit, the slope no longer applies
        assertVoltageEquals(444.128, bus2);
        assertReactivePowerEquals(-svc1.getBmax() * bus2.getV() * bus2.getV(), svc1.getTerminal());
        String report = reportToString(reportNode);
        assertTrue(report.contains("Switch bus 'vl2_0' PV -> PQ, q=403.671565 > maxQ=397.766905"));
        assertFalse(report.contains("reactive limit changed"));
    }

    @Test
    void testSvcWithSlope() {
        svc1.setVoltageSetpoint(385);
        svc1.newVoltageRegulation()
                .withMode(RegulationMode.VOLTAGE_PER_REACTIVE_POWER)
                .withSlope(0.03)
                .withRegulating(true)
                .build();

        parametersExt.setVoltagePerReactivePowerControl(true);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());

        assertVoltageEquals(390, bus1);
        assertAngleEquals(0, bus1);
        assertVoltageEquals(387.845, bus2);
        assertAngleEquals(-0.022026, bus2);
        assertActivePowerEquals(101.466, l1.getTerminal1());
        assertReactivePowerEquals(246.252, l1.getTerminal1());
        assertActivePowerEquals(-101, l1.getTerminal2());
        assertReactivePowerEquals(-244.853, l1.getTerminal2());
        assertActivePowerEquals(0, svc1.getTerminal());
        assertReactivePowerEquals(94.853, svc1.getTerminal());
    }

    @Test
    void testSvcWithSlope2() {
        // Test switch PV to PQ
        svc1.setVoltageSetpoint(440);
        svc1.newVoltageRegulation()
                .withMode(RegulationMode.VOLTAGE_PER_REACTIVE_POWER)
                .withSlope(0.03)
                .withRegulating(true)
                .build();

        parametersExt.setVoltagePerReactivePowerControl(true);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());

        assertVoltageEquals(390, bus1);
        assertAngleEquals(0, bus1);
        assertVoltageEquals(398.155, bus2);
        assertAngleEquals(-0.524413, bus2);
        assertActivePowerEquals(108.952, l1.getTerminal1());
        assertReactivePowerEquals(-1094.367, l1.getTerminal1());
        assertActivePowerEquals(-101, l1.getTerminal2());
        assertReactivePowerEquals(1118.223, l1.getTerminal2());
        assertActivePowerEquals(0, svc1.getTerminal());
        assertReactivePowerEquals(-1268.223, svc1.getTerminal());
    }

    @Test
    void testSvcWithSlope3() {
        Generator gen = network.getVoltageLevel("vl2").newGenerator()
                .setId("gen")
                .setBus("b2")
                .setConnectableBus("b2")
                .setTargetP(0)
                .setTargetV(385)
                .setTargetQ(100)
                .setMaxP(100)
                .setMinP(0)
                .setVoltageRegulatorOn(true)
                .add();

        svc1.setVoltageSetpoint(385)
                .setRegulationMode(RegulationMode.VOLTAGE)
                .setRegulating(true);

        parametersExt.setVoltagePerReactivePowerControl(true);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());

        assertVoltageEquals(390, bus1);
        assertAngleEquals(0, bus1);
        assertVoltageEquals(385, bus2);
        assertAngleEquals(0.116345, bus2);
        assertReactivePowerEquals(228.948, svc1.getTerminal()); // same behaviour as slope = 0
        assertReactivePowerEquals(228.948, gen.getTerminal()); // same behaviour as slope = 0
    }

    @Test
    void testSvcWithSlope4() {

        StaticVarCompensator svc2 = network.getVoltageLevel("vl2").newStaticVarCompensator()
                .setId("svc2")
                .setConnectableBus("b2")
                .setBus("b2")
                // .setVoltageSetpoint(385) // TODO MSA use setVoltageSetpoint instead of setLocalTargetV
                .setLocalTargetV(385)
                .newVoltageRegulation()
                    .withMode(RegulationMode.VOLTAGE_PER_REACTIVE_POWER)
                    .withSlope(0.03)
                    .withRegulating(true)
                    .add()
                .setBmin(-0.008)
                .setBmax(0.008)
                .add();

        svc1.setVoltageSetpoint(385);
        svc1.newVoltageRegulation()
                .withMode(RegulationMode.VOLTAGE_PER_REACTIVE_POWER)
                .withSlope(0.03)
                .withRegulating(true)
                .build();

        parametersExt.setVoltagePerReactivePowerControl(true);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());

        assertVoltageEquals(390, bus1);
        assertAngleEquals(0, bus1);
        assertVoltageEquals(385, bus2);
        assertAngleEquals(0.116345, bus2);
        assertReactivePowerEquals(228.948, svc1.getTerminal()); // same behaviour as slope = 0
        assertReactivePowerEquals(228.948, svc2.getTerminal()); // same behaviour as slope = 0
    }

    @Test
    void testSvcWithSlope5() {
        // With a generator at bus2 not controlling voltage
        svc1.setVoltageSetpoint(385);
        svc1.newVoltageRegulation()
                .withMode(RegulationMode.VOLTAGE_PER_REACTIVE_POWER)
                .withSlope(0.03)
                .withRegulating(true)
                .build();

        network.getVoltageLevel("vl2").newGenerator()
                .setId("gen")
                .setBus("b2")
                .setConnectableBus("b2")
                .setTargetP(0)
                .setTargetV(385)
                .setTargetQ(100)
                .setMaxP(100)
                .setMinP(0)
                .setVoltageRegulatorOn(false)
                .add();

        parametersExt.setVoltagePerReactivePowerControl(true);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());

        assertVoltageEquals(390, bus1);
        assertAngleEquals(0, bus1);
        assertVoltageEquals(388.462, bus2);
        assertAngleEquals(-0.052034, bus2);
        assertActivePowerEquals(101.249, l1.getTerminal1());
        assertReactivePowerEquals(166.160, l1.getTerminal1());
        assertActivePowerEquals(-101, l1.getTerminal2());
        assertReactivePowerEquals(-165.413, l1.getTerminal2());
        assertActivePowerEquals(0, svc1.getTerminal());
        assertReactivePowerEquals(115.413, svc1.getTerminal());
    }

    @Test
    void testRegulationModeReactivePower() {
        svc1.setReactivePowerSetpoint(100)
                .setRegulationMode(RegulationMode.REACTIVE_POWER)
                .setRegulating(true);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());
        assertReactivePowerEquals(100, svc1.getTerminal());
    }

    @Test
    void testStandByAutomaton() {
        svc1.setVoltageSetpoint(385)
                .setRegulationMode(RegulationMode.VOLTAGE)
                .setRegulating(true);
        svc1.newExtension(StandbyAutomatonAdder.class)
                .withHighVoltageThreshold(400)
                .withLowVoltageThreshold(380)
                .withLowVoltageSetpoint(385)
                .withHighVoltageSetpoint(395)
                .withB0(-0.001f)
                .withStandbyStatus(true)
                .add();

        parametersExt.setSvcVoltageMonitoring(true);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());

        assertReactivePowerEquals(150.091, svc1.getTerminal());
        assertVoltageEquals(387.415, bus2);
    }

    @Test
    void testStandByAutomatonAndSlope() {
        svc1.setVoltageSetpoint(385);
        svc1.newVoltageRegulation()
                .withMode(RegulationMode.VOLTAGE_PER_REACTIVE_POWER)
                .withSlope(0.03)
                .withRegulating(true)
                .build();
        svc1.newExtension(StandbyAutomatonAdder.class)
                .withHighVoltageThreshold(400)
                .withLowVoltageThreshold(380)
                .withLowVoltageSetpoint(385)
                .withHighVoltageSetpoint(395)
                .withB0(-0.001f)
                .withStandbyStatus(true)
                .add();

        parametersExt
                .setSvcVoltageMonitoring(true)
                .setVoltagePerReactivePowerControl(true);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());

        assertReactivePowerEquals(150.091, svc1.getTerminal()); // same as testStandByAutomaton
        assertVoltageEquals(387.415, bus2); // same as testStandByAutomaton
    }

    @Test
    void testStandByAutomaton2() {
        svc1.setVoltageSetpoint(385)
                .setRegulationMode(RegulationMode.VOLTAGE)
                .setRegulating(true);
        svc1.newExtension(StandbyAutomatonAdder.class)
                .withHighVoltageThreshold(397)
                .withLowVoltageThreshold(383)
                .withLowVoltageSetpoint(384)
                .withHighVoltageSetpoint(395)
                .withB0(-0.005)
                .withStandbyStatus(true)
                .add();

        parametersExt.setSvcVoltageMonitoring(true);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());

        assertReactivePowerEquals(584.129, svc1.getTerminal());
        assertVoltageEquals(384.0, bus2);
    }

    @Test
    void testStandByAutomaton3() {
        svc1.setVoltageSetpoint(385)
                .setRegulationMode(RegulationMode.VOLTAGE)
                .setRegulating(true);
        g1.setTargetV(405);

        svc1.newExtension(StandbyAutomatonAdder.class)
                .withHighVoltageThreshold(397)
                .withLowVoltageThreshold(383)
                .withLowVoltageSetpoint(384)
                .withHighVoltageSetpoint(395)
                .withB0(-0.005f)
                .withStandbyStatus(true)
                .add();

        parametersExt.setSvcVoltageMonitoring(true);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());

        assertReactivePowerEquals(1132.001, svc1.getTerminal());
        assertVoltageEquals(395.0, bus2);
    }

    @Test
    void testStandByAutomaton4() {
        // Test a voltage controller and a voltage monitor connected to the same bus.
        // Voltage monitor is discarded.
        svc1.setVoltageSetpoint(385)
                .setRegulationMode(RegulationMode.VOLTAGE)
                .setRegulating(true);
        svc1.newExtension(StandbyAutomatonAdder.class)
                .withHighVoltageThreshold(397)
                .withLowVoltageThreshold(383)
                .withLowVoltageSetpoint(384)
                .withHighVoltageSetpoint(395)
                .withB0(-0.005f)
                .withStandbyStatus(true)
                .add();
        network.getVoltageLevel("vl2").newGenerator()
                .setMinP(-100)
                .setMaxP(100)
                .setTargetP(0.0)
                .setTargetV(392)
                .setVoltageRegulatorOn(true)
                .setId("g3")
                .setBus(bus2.getId())
                .add();

        parametersExt.setSvcVoltageMonitoring(true);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());

        assertReactivePowerEquals(768.320, svc1.getTerminal());
        assertVoltageEquals(392.0, bus2);
    }

    @Test
    void testStandByAutomaton5() {

        StaticVarCompensator svc2 = network.getVoltageLevel("vl2").newStaticVarCompensator()
                .setId("svc2")
                .setConnectableBus("b2")
                .setBus("b2")
                .setRegulationMode(RegulationMode.VOLTAGE)
                .setRegulating(true)
                .setVoltageSetpoint(385)
                .setBmin(-0.008)
                .setBmax(0.008)
                .add();
        svc2.newExtension(StandbyAutomatonAdder.class)
                .withHighVoltageThreshold(397)
                .withLowVoltageThreshold(383)
                .withLowVoltageSetpoint(384)
                .withHighVoltageSetpoint(395)
                .withB0(-0.005f)
                .withStandbyStatus(true)
                .add();

        svc1.setVoltageSetpoint(385)
                .setRegulationMode(RegulationMode.VOLTAGE)
                .setRegulating(true);
        svc1.newExtension(StandbyAutomatonAdder.class)
                .withHighVoltageThreshold(397)
                .withLowVoltageThreshold(383)
                .withLowVoltageSetpoint(384)
                .withHighVoltageSetpoint(395)
                .withB0(-0.005f)
                .withStandbyStatus(true)
                .add();

        parametersExt.setSvcVoltageMonitoring(true);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());

        assertVoltageEquals(390, bus1);
        assertAngleEquals(0, bus1);
        assertVoltageEquals(385, bus2);
        assertAngleEquals(0.116346, bus2);
        // same behaviour as classical voltage control: the 457.896 MVar of the single SVC case shared by both SVCs
        assertReactivePowerEquals(228.948, svc1.getTerminal());
        assertReactivePowerEquals(228.948, svc2.getTerminal());
    }

    @Test
    void testSharedVoltageControl() {
        // setup plausible reactive limits on g1
        g1.newMinMaxReactiveLimits().setMinQ(-100.).setMaxQ(100.).add();

        // SVC regulating voltage with g1
        svc1.setVoltageSetpoint(g1.getTargetV())
                .setRegulationMode(RegulationMode.VOLTAGE)
                .setRegulatingTerminal(g1.getRegulatingTerminal())
                .setRegulating(true);

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());

        assertVoltageEquals(390, bus1);
        assertAngleEquals(0, bus1);
        assertVoltageEquals(389.657, bus2);
        assertAngleEquals(-0.1102126, bus2);
        assertReactivePowerEquals(-10.884, g1.getTerminal());
        assertReactivePowerEquals(-139.319, svc1.getTerminal());

        // Verify reactive power keys distribution - keys are reactive power range of g1 and svc1
        double rangeQg1 = g1.getReactiveLimits().getMaxQ(0) - g1.getReactiveLimits().getMinQ(0);
        // Reactive keys for SVCs are always from nominal voltage, not solved voltage.
        double vBus2 = bus2.getVoltageLevel().getNominalV();
        double rangeQSvc1 = (svc1.getBmax() - svc1.getBmin()) * vBus2 * vBus2;
        double rangeQTotal = rangeQg1 + rangeQSvc1;
        double qG1 = g1.getTerminal().getQ();
        double qSvc1 = svc1.getTerminal().getQ();
        double qTot = qG1 + qSvc1;
        assertEquals(rangeQg1 / rangeQTotal, qG1 / qTot, 1e-3);
        assertEquals(rangeQSvc1 / rangeQTotal, qSvc1 / qTot, 1e-3);
    }
}
