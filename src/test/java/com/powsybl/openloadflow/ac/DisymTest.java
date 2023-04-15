package com.powsybl.openloadflow.ac;

import com.powsybl.iidm.network.*;
import com.powsybl.iidm.network.extensions.GeneratorFortescueAdder;
import com.powsybl.iidm.network.extensions.LineFortescueAdder;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.ac.equations.*;
import com.powsybl.openloadflow.ac.equations.asym.*;
import com.powsybl.openloadflow.ac.nr.NewtonRaphson;
import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.equations.EquationTerm;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.extensions.iidm.LineAsymmetrical;
import com.powsybl.openloadflow.network.extensions.iidm.LineAsymmetricalAdder;
import com.powsybl.openloadflow.network.extensions.iidm.LoadUnbalancedAdder;
import com.powsybl.openloadflow.network.impl.Networks;
import com.powsybl.openloadflow.network.util.UniformValueVoltageInitializer;
import org.joda.time.DateTime;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletionException;

import static com.powsybl.openloadflow.util.LoadFlowAssert.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class DisymTest {

    private Network network;
    private Bus bus1;
    private Bus bus2;
    private Bus bus3;
    private Bus bus4;
    private Line line1;

    private LoadFlow.Runner loadFlowRunner;
    private LoadFlowParameters parameters;

    @Test
    void asymmetricEquationSystemTest() {

        network = fourNodescreate();

        LfNetworkParameters lfNetworkParameters = new LfNetworkParameters()
                .setAsymmetrical(true);
        List<LfNetwork> lfNetworks = Networks.load(network, lfNetworkParameters);
        LfNetwork mainNetwork = lfNetworks.get(0);

        EquationSystem<AcVariableType, AcEquationType> equationSystem = new AsymmetricalAcEquationSystemCreator(mainNetwork, new AcEquationSystemCreationParameters()).create();
        NewtonRaphson.initStateVector(mainNetwork, equationSystem, new UniformValueVoltageInitializer());

        LfBranch branch = mainNetwork.getBranchById("B1_B2");
        assertEquals(2, equationSystem.getEquation(branch.getBus1().getNum(), AcEquationType.BUS_TARGET_IX_ZERO).get().getTerms().size());
        EquationTerm<AcVariableType, AcEquationType> eqTerm1 = equationSystem.getEquation(branch.getBus1().getNum(), AcEquationType.BUS_TARGET_IX_ZERO).get().getTerms().get(0);
        EquationTerm<AcVariableType, AcEquationType> eqTerm2 = equationSystem.getEquation(branch.getBus1().getNum(), AcEquationType.BUS_TARGET_IX_ZERO).get().getTerms().get(1);
        ClosedBranchI1xFlowEquationTerm ix1Branch;
        ShuntFortescueIxEquationTerm ix1Shunt;
        if (eqTerm1.getElementType() == ElementType.BUS) {
            ix1Shunt = (ShuntFortescueIxEquationTerm) eqTerm1;
            ix1Branch = (ClosedBranchI1xFlowEquationTerm) eqTerm2;
        } else {
            ix1Shunt = (ShuntFortescueIxEquationTerm) eqTerm2;
            ix1Branch = (ClosedBranchI1xFlowEquationTerm) eqTerm1;
        }
        assertEquals(ElementType.BUS, ix1Shunt.getElementType());
        assertEquals(ElementType.BRANCH, ix1Branch.getElementType());
        assertEquals(0, ix1Shunt.getElementNum());
        assertEquals(0, ix1Branch.getElementNum());
        assertEquals("ac_ix_fortescue_shunt", ix1Shunt.getName());
        assertEquals("ac_ix_closed_1", ix1Branch.getName());
        assertEquals(0, ix1Branch.calculateSensi(0, 0, 0, 0, 0, 0));

        assertEquals(2, equationSystem.getEquation(branch.getBus1().getNum(), AcEquationType.BUS_TARGET_IY_ZERO).get().getTerms().size());
        EquationTerm<AcVariableType, AcEquationType> eqTerm3 = equationSystem.getEquation(branch.getBus1().getNum(), AcEquationType.BUS_TARGET_IY_ZERO).get().getTerms().get(0);
        EquationTerm<AcVariableType, AcEquationType> eqTerm4 = equationSystem.getEquation(branch.getBus1().getNum(), AcEquationType.BUS_TARGET_IY_ZERO).get().getTerms().get(1);
        ClosedBranchI1yFlowEquationTerm iy1Branch;
        ShuntFortescueIyEquationTerm iy1Shunt;
        if (eqTerm3.getElementType() == ElementType.BUS) {
            iy1Shunt = (ShuntFortescueIyEquationTerm) eqTerm3;
            iy1Branch = (ClosedBranchI1yFlowEquationTerm) eqTerm4;
        } else {
            iy1Shunt = (ShuntFortescueIyEquationTerm) eqTerm4;
            iy1Branch = (ClosedBranchI1yFlowEquationTerm) eqTerm3;
        }
        assertEquals(ElementType.BUS, iy1Shunt.getElementType());
        assertEquals(ElementType.BRANCH, iy1Branch.getElementType());
        assertEquals(0, iy1Shunt.getElementNum());
        assertEquals(0, iy1Branch.getElementNum());
        assertEquals("ac_iy_fortescue_shunt", iy1Shunt.getName());
        assertEquals("ac_iy_closed_1", iy1Branch.getName());
        assertEquals(0, iy1Branch.calculateSensi(0, 0, 0, 0, 0, 0));

        LfBranch branch2 = mainNetwork.getBranchById("B3_B4");
        assertEquals(2, equationSystem.getEquation(branch2.getBus2().getNum(), AcEquationType.BUS_TARGET_IX_NEGATIVE).get().getTerms().size());
        EquationTerm<AcVariableType, AcEquationType> eqTerm5 = equationSystem.getEquation(branch2.getBus2().getNum(), AcEquationType.BUS_TARGET_IX_NEGATIVE).get().getTerms().get(0);
        EquationTerm<AcVariableType, AcEquationType> eqTerm6 = equationSystem.getEquation(branch2.getBus2().getNum(), AcEquationType.BUS_TARGET_IX_NEGATIVE).get().getTerms().get(1);
        ClosedBranchI2xFlowEquationTerm ix2Branch;
        LoadFortescuePowerEquationTerm ix2Load;
        if (eqTerm5.getElementType() == ElementType.BUS) {
            ix2Load = (LoadFortescuePowerEquationTerm) eqTerm5;
            ix2Branch = (ClosedBranchI2xFlowEquationTerm) eqTerm6;
        } else {
            ix2Load = (LoadFortescuePowerEquationTerm) eqTerm6;
            ix2Branch = (ClosedBranchI2xFlowEquationTerm) eqTerm5;
        }
        assertEquals(ElementType.BUS, ix2Load.getElementType());
        assertEquals(ElementType.BRANCH, ix2Branch.getElementType());
        assertEquals(3, ix2Load.getElementNum());
        assertEquals(3, ix2Branch.getElementNum());
        assertEquals("ac_pq_fortescue_load", ix2Load.getName());
        assertEquals("ac_ix_closed_2", ix2Branch.getName());
        assertEquals(0, ix2Branch.calculateSensi(0, 0, 0, 0, 0, 0));

        assertEquals(2, equationSystem.getEquation(branch2.getBus2().getNum(), AcEquationType.BUS_TARGET_IY_NEGATIVE).get().getTerms().size());
        EquationTerm<AcVariableType, AcEquationType> eqTerm7 = equationSystem.getEquation(branch2.getBus2().getNum(), AcEquationType.BUS_TARGET_IY_NEGATIVE).get().getTerms().get(0);
        EquationTerm<AcVariableType, AcEquationType> eqTerm8 = equationSystem.getEquation(branch2.getBus2().getNum(), AcEquationType.BUS_TARGET_IY_NEGATIVE).get().getTerms().get(1);
        ClosedBranchI2yFlowEquationTerm iy2Branch;
        LoadFortescuePowerEquationTerm iy2Load;
        if (eqTerm7.getElementType() == ElementType.BUS) {
            iy2Load = (LoadFortescuePowerEquationTerm) eqTerm7;
            iy2Branch = (ClosedBranchI2yFlowEquationTerm) eqTerm8;
        } else {
            iy2Load = (LoadFortescuePowerEquationTerm) eqTerm8;
            iy2Branch = (ClosedBranchI2yFlowEquationTerm) eqTerm7;
        }
        assertEquals(ElementType.BUS, iy2Load.getElementType());
        assertEquals(ElementType.BRANCH, iy2Branch.getElementType());
        assertEquals(3, iy2Load.getElementNum());
        assertEquals(3, iy2Branch.getElementNum());
        assertEquals("ac_pq_fortescue_load", iy2Load.getName());
        assertEquals("ac_iy_closed_2", iy2Branch.getName());
        assertEquals(0, iy2Branch.calculateSensi(0, 0, 0, 0, 0, 0));

        assertEquals(3, equationSystem.getEquation(branch2.getBus1().getNum(), AcEquationType.BUS_TARGET_IX_NEGATIVE).get().getTerms().size());
        EquationTerm<AcVariableType, AcEquationType> eqTerm9 = equationSystem.getEquation(branch2.getBus1().getNum(), AcEquationType.BUS_TARGET_IX_NEGATIVE).get().getTerms().get(0);
        EquationTerm<AcVariableType, AcEquationType> eqTerm10 = equationSystem.getEquation(branch2.getBus1().getNum(), AcEquationType.BUS_TARGET_IX_NEGATIVE).get().getTerms().get(1);
        EquationTerm<AcVariableType, AcEquationType> eqTerm11 = equationSystem.getEquation(branch2.getBus1().getNum(), AcEquationType.BUS_TARGET_IX_NEGATIVE).get().getTerms().get(2);
        EquationTerm<AcVariableType, AcEquationType> eqTerm12 = equationSystem.getEquation(branch2.getBus1().getNum(), AcEquationType.BUS_TARGET_P).get().getTerms().get(0);
        EquationTerm<AcVariableType, AcEquationType> eqTerm13 = equationSystem.getEquation(branch2.getBus1().getNum(), AcEquationType.BUS_TARGET_P).get().getTerms().get(1);
        EquationTerm<AcVariableType, AcEquationType> eqTerm14 = equationSystem.getEquation(branch2.getBus1().getNum(), AcEquationType.BUS_TARGET_P).get().getTerms().get(2);
        AsymmetricalClosedBranchCoupledCurrentEquationTerm coupledEquTerm;
        if (eqTerm11 instanceof AsymmetricalClosedBranchCoupledCurrentEquationTerm) {
            coupledEquTerm = (AsymmetricalClosedBranchCoupledCurrentEquationTerm) eqTerm11;
        } else if (eqTerm10 instanceof AsymmetricalClosedBranchCoupledCurrentEquationTerm) {
            coupledEquTerm = (AsymmetricalClosedBranchCoupledCurrentEquationTerm) eqTerm10;
        } else {
            coupledEquTerm = (AsymmetricalClosedBranchCoupledCurrentEquationTerm) eqTerm9;
        }
        assertEquals(2, coupledEquTerm.getElementNum());
        assertEquals("ac_ixiy_coupled_closed_1", coupledEquTerm.getName());

        AsymmetricalClosedBranchCoupledPowerEquationTerm coupledPowerEquTerm;
        if (eqTerm12 instanceof AsymmetricalClosedBranchCoupledPowerEquationTerm) {
            coupledPowerEquTerm = (AsymmetricalClosedBranchCoupledPowerEquationTerm) eqTerm12;
        } else if (eqTerm13 instanceof AsymmetricalClosedBranchCoupledPowerEquationTerm) {
            coupledPowerEquTerm = (AsymmetricalClosedBranchCoupledPowerEquationTerm) eqTerm13;
        } else {
            coupledPowerEquTerm = (AsymmetricalClosedBranchCoupledPowerEquationTerm) eqTerm14;
        }
        assertEquals(2, coupledPowerEquTerm.getElementNum());
        assertEquals("ac_pq_coupled_closed_1", coupledPowerEquTerm.getName());
    }

    @Test
    void baseCaseTest() {

        network = TwoBusNetworkFactory.create();
        bus1 = network.getBusBreakerView().getBus("b1");
        bus2 = network.getBusBreakerView().getBus("b2");
        line1 = network.getLine("l12");

        loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        parameters = new LoadFlowParameters().setNoGeneratorReactiveLimits(true)
                .setDistributedSlack(false);
        OpenLoadFlowParameters.create(parameters)
                .setSlackBusSelectionMode(SlackBusSelectionMode.FIRST);

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());

        assertVoltageEquals(1, bus1);
        assertAngleEquals(0, bus1);
        assertVoltageEquals(0.855, bus2);
        assertAngleEquals(-13.520904, bus2);
        assertActivePowerEquals(2, line1.getTerminal1());
        assertReactivePowerEquals(1.683, line1.getTerminal1());
        assertActivePowerEquals(-2, line1.getTerminal2());
        assertReactivePowerEquals(-1, line1.getTerminal2());
    }

    @Test
    void fourNodesBalancedTest() {

        network = fourNodescreate();
        bus1 = network.getBusBreakerView().getBus("B1");
        bus2 = network.getBusBreakerView().getBus("B2");
        bus3 = network.getBusBreakerView().getBus("B3");
        bus4 = network.getBusBreakerView().getBus("B4");
        line1 = network.getLine("B1_B2");

        loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        parameters = new LoadFlowParameters().setNoGeneratorReactiveLimits(true)
                .setDistributedSlack(false);
        OpenLoadFlowParameters.create(parameters)
                .setSlackBusSelectionMode(SlackBusSelectionMode.FIRST)
                .setAsymmetrical(false);

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());

        assertVoltageEquals(100., bus1);
        assertAngleEquals(0, bus1);
        assertVoltageEquals(99.79736062173895, bus2);
        assertAngleEquals(-0.11482430885268813, bus2);
        assertVoltageEquals(99.54462759204546, bus3);
        assertAngleEquals(-0.2590112700040258, bus3);
        assertVoltageEquals(99.29252809145005, bus4);
        assertAngleEquals(-0.40393118155914964, bus4);
        assertActivePowerEquals(9.999999463827846, line1.getTerminal1());
        assertReactivePowerEquals(10.141989227123105, line1.getTerminal1());
        assertActivePowerEquals(-9.999999463827846, line1.getTerminal2());
        assertReactivePowerEquals(-10.101417240171545, line1.getTerminal2());
    }

    @Test
    void fourNodesDissymTest() {

        network = fourNodescreate();
        bus1 = network.getBusBreakerView().getBus("B1");
        bus2 = network.getBusBreakerView().getBus("B2");
        bus3 = network.getBusBreakerView().getBus("B3");
        bus4 = network.getBusBreakerView().getBus("B4");
        line1 = network.getLine("B1_B2");

        loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        parameters = new LoadFlowParameters().setNoGeneratorReactiveLimits(true)
                .setDistributedSlack(false);
        OpenLoadFlowParameters.create(parameters)
                .setSlackBusSelectionMode(SlackBusSelectionMode.FIRST)
                .setAsymmetrical(true);

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());

        assertVoltageEquals(100., bus1);
        assertAngleEquals(0, bus1);
        assertVoltageEquals(99.7971047825933, bus2); // balanced = 99.79736062173895
        assertVoltageEquals(99.45937102112217, bus3); // balanced = 99.54462759204546
        assertVoltageEquals(99.2070528211056, bus4); // balanced = 99.29252809145005

        Line line23fault = network.getLine("B2_B3_fault");
        var extension = line23fault.getExtension(LineAsymmetrical.class);
        extension.setOpenPhaseA(false);
        extension.setOpenPhaseB(true);

        result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());

        assertVoltageEquals(100., bus1);
        assertAngleEquals(0, bus1);
        assertVoltageEquals(99.7971047825933, bus2); // balanced = 99.79736062173895
        assertVoltageEquals(99.45937102112217, bus3); // balanced = 99.54462759204546
        assertVoltageEquals(99.2070528211056, bus4); // balanced = 99.29252809145005

        extension.setOpenPhaseB(false);
        extension.setOpenPhaseC(true);

        result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());

        assertVoltageEquals(100., bus1);
        assertAngleEquals(0, bus1);
        assertVoltageEquals(99.7971047825933, bus2); // balanced = 99.79736062173895
        assertVoltageEquals(99.45937102112217, bus3); // balanced = 99.54462759204546
        assertVoltageEquals(99.2070528211056, bus4); // balanced = 99.29252809145005

        Line line23New = network.newLine()
                .setId("B2_B3_New")
                .setVoltageLevel1(network.getVoltageLevel("VL_2").getId())
                .setConnectableBus1(bus2.getId())
                .setVoltageLevel2(network.getVoltageLevel("VL_3").getId())
                .setBus2(bus3.getId())
                .setConnectableBus2(bus3.getId())
                .setR(0.0)
                .setX(1 / 0.2)
                .setG1(0.0)
                .setB1(0.0)
                .setG2(0.0)
                .setB2(0.0)
                .add();

        assertThrows(CompletionException.class, () -> loadFlowRunner.run(network, parameters));
        line23New.getTerminal1().connect();
        line23New.getTerminal2().disconnect();
        assertThrows(CompletionException.class, () -> loadFlowRunner.run(network, parameters));

    }

    @Test
    void fourNodesDissymUnbalancedLoadLineTest() {

        network = fourNodescreate();
        bus1 = network.getBusBreakerView().getBus("B1");
        bus2 = network.getBusBreakerView().getBus("B2");
        bus3 = network.getBusBreakerView().getBus("B3");
        bus4 = network.getBusBreakerView().getBus("B4");
        line1 = network.getLine("B1_B2");

        Line line23 = network.getLine("B2_B3");
        double coeff = 1.;
        line23.setX(coeff * 1 / 0.2);

        Load load4 = network.getLoad("LOAD_4");

        load4.newExtension(LoadUnbalancedAdder.class)
                .withPa(0.)
                .withQa(10.)
                .withPb(0.)
                .withQb(0.)
                .withPc(0.)
                .withQc(0.)
                .add();

        loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        parameters = new LoadFlowParameters().setNoGeneratorReactiveLimits(true)
                .setDistributedSlack(false);
        OpenLoadFlowParameters.create(parameters)
                .setSlackBusSelectionMode(SlackBusSelectionMode.FIRST)
                .setAsymmetrical(true);

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());

        assertVoltageEquals(100., bus1);
        assertAngleEquals(0, bus1);
        assertVoltageEquals(99.72834946229246, bus2); // balanced = 99.79736062173895
        assertVoltageEquals(99.2189311203843, bus3); // balanced = 99.54462759204546
        assertVoltageEquals(98.88078638550749, bus4); // balanced = 99.29252809145005
    }

    @Test
    void fourNodesDissymUnbalancedLoadTest() {

        network = fourNodescreate();
        bus1 = network.getBusBreakerView().getBus("B1");
        bus2 = network.getBusBreakerView().getBus("B2");
        bus3 = network.getBusBreakerView().getBus("B3");
        bus4 = network.getBusBreakerView().getBus("B4");
        line1 = network.getLine("B1_B2");

        Line line23 = network.getLine("B2_B3");
        double coeff = 1.;
        line23.setX(coeff * 1 / 0.2);

        Line line23fault = network.getLine("B2_B3_fault");
        var extension = line23fault.getExtension(LineAsymmetrical.class);
        extension.setOpenPhaseA(false);

        Load load4 = network.getLoad("LOAD_4");

        load4.newExtension(LoadUnbalancedAdder.class)
                .withPa(20.)
                .withQa(0.)
                .withPb(40.)
                .withQb(0.)
                .withPc(21)
                .withQc(0.)
                .add();

        loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        parameters = new LoadFlowParameters()
                .setUseReactiveLimits(false)
                .setDistributedSlack(false);
        OpenLoadFlowParameters.create(parameters)
                .setSlackBusSelectionMode(SlackBusSelectionMode.FIRST)
                .setAsymmetrical(true);

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());

        assertVoltageEquals(100., bus1);
        assertAngleEquals(0, bus1);
        assertVoltageEquals(99.78067026758131, bus2); // balanced = 99.79736062173895
        assertVoltageEquals(99.5142639108648, bus3); // balanced = 99.54462759204546
        assertVoltageEquals(99.2565397779297, bus4); // balanced = 99.29252809145005
    }

    public static Network fourNodescreate() {
        // Proposed network to be tested
        // The grid is balanced except l23_fault which has phase C disconnected in parallel to line l23 which stays connected
        // We use a parallel line because in this use case we would like to avoid issues linked to the loss of connexity
        //
        //       1         2        3        4
        //       |---------|========|--------|
        //  (~)--|---------|========|--------|--[X]
        //       |---------|==----==|--------|
        //                    \  /
        //

        Network network = Network.create("4n", "test");
        network.setCaseDate(DateTime.parse("2018-03-05T13:30:30.486+01:00"));

        // Bus 1
        Substation substation1 = network.newSubstation()
                .setId("S1")
                .setCountry(Country.FR)
                .add();
        VoltageLevel vl1 = substation1.newVoltageLevel()
                .setId("VL_1")
                .setNominalV(100.0)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(200)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus1 = vl1.getBusBreakerView().newBus()
                .setId("B1")
                .add();
        bus1.setV(100.0).setAngle(0.);

        Generator gen1 = vl1.newGenerator()
                .setId("G1")
                .setBus(bus1.getId())
                .setMinP(0.0)
                .setMaxP(200)
                .setTargetP(10)
                .setTargetV(100.0)
                .setVoltageRegulatorOn(true)
                .add();

        // Bus 2
        Substation substation2 = network.newSubstation()
                .setId("S2")
                .setCountry(Country.FR)
                .add();
        VoltageLevel vl2 = substation2.newVoltageLevel()
                .setId("VL_2")
                .setNominalV(100.0)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(200)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus2 = vl2.getBusBreakerView().newBus()
                .setId("B2")
                .add();
        bus2.setV(100.0).setAngle(0);

        // Bus 3
        Substation substation3 = network.newSubstation()
                .setId("S3")
                .setCountry(Country.FR)
                .add();
        VoltageLevel vl3 = substation3.newVoltageLevel()
                .setId("VL_3")
                .setNominalV(100.0)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(200)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus3 = vl3.getBusBreakerView().newBus()
                .setId("B3")
                .add();
        bus3.setV(100.0).setAngle(0.);

        // Bus 4
        Substation substation4 = network.newSubstation()
                .setId("S4")
                .setCountry(Country.FR)
                .add();
        VoltageLevel vl4 = substation4.newVoltageLevel()
                .setId("VL_4")
                .setNominalV(100.0)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(200)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus4 = vl4.getBusBreakerView().newBus()
                .setId("B4")
                .add();
        bus4.setV(100.0).setAngle(0.);
        vl4.newLoad()
                .setId("LOAD_4")
                .setBus(bus4.getId())
                .setP0(10.0)
                .setQ0(10.)
                .add();

        network.newLine()
                .setId("B1_B2")
                .setVoltageLevel1(vl1.getId())
                .setBus1(bus1.getId())
                .setConnectableBus1(bus1.getId())
                .setVoltageLevel2(vl2.getId())
                .setBus2(bus2.getId())
                .setConnectableBus2(bus2.getId())
                .setR(0.0)
                .setX(1 / 0.5)
                .setG1(0.0)
                .setB1(0.0)
                .setG2(0.0)
                .setB2(0.0)
                .add();

        Line line23 = network.newLine()
                .setId("B2_B3")
                .setVoltageLevel1(vl2.getId())
                .setBus1(bus2.getId())
                .setConnectableBus1(bus2.getId())
                .setVoltageLevel2(vl3.getId())
                .setBus2(bus3.getId())
                .setConnectableBus2(bus3.getId())
                .setR(0.0)
                .setX(1 / 0.2)
                .setG1(0.0)
                .setB1(0.0)
                .setG2(0.0)
                .setB2(0.0)
                .add();

        Line line23fault = network.newLine()
                .setId("B2_B3_fault")
                .setVoltageLevel1(vl2.getId())
                .setBus1(bus2.getId())
                .setConnectableBus1(bus2.getId())
                .setVoltageLevel2(vl3.getId())
                .setBus2(bus3.getId())
                .setConnectableBus2(bus3.getId())
                .setR(0.0)
                .setX(1 / 0.2)
                .setG1(0.0)
                .setB1(0.0)
                .setG2(0.0)
                .setB2(0.0)
                .add();

        network.newLine()
                .setId("B3_B4")
                .setVoltageLevel1(vl3.getId())
                .setBus1(bus3.getId())
                .setConnectableBus1(bus3.getId())
                .setVoltageLevel2(vl4.getId())
                .setBus2(bus4.getId())
                .setConnectableBus2(bus4.getId())
                .setR(0.0)
                .setX(1 / 0.4)
                .setG1(0.0)
                .setB1(0.0)
                .setG2(0.0)
                .setB2(0.0)
                .add();

        // addition of asymmetrical extensions
        line23fault.newExtension(LineAsymmetricalAdder.class)
                .withIsOpenA(true)
                .withIsOpenB(false)
                .withIsOpenC(false)
                .add();

        line23fault.newExtension(LineFortescueAdder.class)
                .withRz(0)
                .withXz(line23fault.getX())
                .add();

        // addition of asymmetrical extensions
        gen1.newExtension(GeneratorFortescueAdder.class)
                .withRz(0.)
                .withXz(0.1)
                .withRn(0.)
                .withXn(0.1)
                .add();

        return network;
    }

}
