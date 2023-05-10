/**
 * Copyright (c) 2023, Jean-Baptiste Heyberger <jbheyberger at gmail.com> ,
 *                     Geoffroy Jamgotchian <geoffroy.jamgotchian at gmail.com>
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.iidm.network.*;
import com.powsybl.iidm.network.extensions.GeneratorFortescueAdder;
import com.powsybl.iidm.network.extensions.LineFortescueAdder;
import com.powsybl.iidm.network.extensions.TwoWindingsTransformerFortescueAdder;
import com.powsybl.iidm.network.extensions.WindingConnectionType;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.ac.equations.*;
import com.powsybl.openloadflow.ac.equations.asym.*;
import com.powsybl.openloadflow.ac.nr.NewtonRaphson;
import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.equations.EquationTerm;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.extensions.AsymThreePhaseTransfo;
import com.powsybl.openloadflow.network.extensions.LegConnectionType;
import com.powsybl.openloadflow.network.extensions.iidm.LineAsymmetrical;
import com.powsybl.openloadflow.network.extensions.iidm.LineAsymmetricalAdder;
import com.powsybl.openloadflow.network.extensions.iidm.LoadUnbalancedAdder;
import com.powsybl.openloadflow.network.impl.Networks;
import com.powsybl.openloadflow.network.util.UniformValueVoltageInitializer;
import com.powsybl.openloadflow.util.ComplexMatrix;
import com.powsybl.openloadflow.util.Fortescue;
import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.geometry.euclidean.twod.Vector2D;
import org.apache.commons.math3.util.Pair;
import org.joda.time.DateTime;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionException;

import static com.powsybl.openloadflow.util.LoadFlowAssert.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class DisymTest {

    private Network network;
    private Bus bus0;
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
        assertEquals("ac_ixiy_coupled_closed", coupledEquTerm.getName());

        AsymmetricalClosedBranchCoupledPowerEquationTerm coupledPowerEquTerm;
        if (eqTerm12 instanceof AsymmetricalClosedBranchCoupledPowerEquationTerm) {
            coupledPowerEquTerm = (AsymmetricalClosedBranchCoupledPowerEquationTerm) eqTerm12;
        } else if (eqTerm13 instanceof AsymmetricalClosedBranchCoupledPowerEquationTerm) {
            coupledPowerEquTerm = (AsymmetricalClosedBranchCoupledPowerEquationTerm) eqTerm13;
        } else {
            coupledPowerEquTerm = (AsymmetricalClosedBranchCoupledPowerEquationTerm) eqTerm14;
        }
        assertEquals(2, coupledPowerEquTerm.getElementNum());
        assertEquals("ac_pq_coupled_closed", coupledPowerEquTerm.getName());
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

    @Test
    void fiveNodeTest() {
        network = fiveNodescreate();
        bus0 = network.getBusBreakerView().getBus("B0");
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

        assertVoltageEquals(10., bus0);
        assertAngleEquals(0, bus0);
        assertVoltageEquals(109.4843854548432, bus1);
        assertAngleEquals(-0.018721076877077133, bus1);
        assertVoltageEquals(109.29944440302664, bus2); // balanced = 99.79736062173895
        assertVoltageEquals(108.99189289657092, bus3); // balanced = 99.54462759204546
        assertVoltageEquals(108.7617902085402, bus4); // balanced = 99.29252809145005
    }

    @Test
    void ieee4noadesFeederTest() {
        network = ieee4YgYgStepUpBalanced();
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

        assertVoltageEquals(12.47, bus1);
        assertAngleEquals(0., bus1);
        assertVoltageEquals(12.438818115032886, bus2); // balanced = 99.79736062173895
        assertVoltageEquals(24.75916902673125, bus3); // balanced = 99.54462759204546
        assertVoltageEquals(24.739649041513385, bus4); // balanced = 99.29252809145005
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

    public static Network fiveNodescreate() {
        // Proposed network to be tested
        // The grid is balanced except l23_fault which has phase C disconnected in parallel to line l23 which stays connected
        // We use a parallel line because in this use case we would like to avoid issues linked to the loss of connexity
        //
        //       0          1         2        3        4
        //       |---(())---|---------|========|--------|
        //  (~)--|---(())---|---------|========|--------|--[X]
        //       |---(())---|---------|==----==|--------|
        //                               \  /
        //

        Network network = Network.create("4n", "test");
        network.setCaseDate(DateTime.parse("2018-03-05T13:30:30.486+01:00"));

        Substation substation01 = network.newSubstation()
                .setId("S1")
                .setCountry(Country.FR)
                .add();

        // Bus 0
        VoltageLevel vl0 = substation01.newVoltageLevel()
                .setId("VL_0")
                .setNominalV(10.0)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(30)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus0 = vl0.getBusBreakerView().newBus()
                .setId("B0")
                .add();
        bus0.setV(10.0).setAngle(0.);

        Generator gen0 = vl0.newGenerator()
                .setId("G0")
                .setBus(bus0.getId())
                .setMinP(-10.0)
                .setMaxP(200)
                .setTargetP(10)
                .setTargetV(10.0)
                .setVoltageRegulatorOn(true)
                .add();

        // Bus 1
        VoltageLevel vl1 = substation01.newVoltageLevel()
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

        double ratedU0 = 10.5;
        double ratedU1 = 115;
        double rho01 = ratedU1 * ratedU1 / (ratedU0 * ratedU0);
        double rT01 = 2.046454 / rho01;
        double xT01 = 49.072241 / rho01;
        var t01 = substation01.newTwoWindingsTransformer()
                .setId("T2W_B0_B1")
                .setVoltageLevel1(vl0.getId())
                .setBus1(bus0.getId())
                .setConnectableBus1(bus0.getId())
                .setRatedU1(10.5)
                .setVoltageLevel2(vl1.getId())
                .setBus2(bus1.getId())
                .setConnectableBus2(bus1.getId())
                .setRatedU2(115)
                .setR(rT01)
                .setX(xT01)
                .setG(0.0D)
                .setB(0.0D)
                .setRatedS(31.5)
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

        gen0.newExtension(GeneratorFortescueAdder.class)
                .withRz(0.)
                .withXz(0.1)
                .withRn(0.)
                .withXn(0.1)
                .add();

        t01.newExtension(TwoWindingsTransformerFortescueAdder.class)
                .withRz(rT01 / 3)
                .withXz(xT01 / 3)
                .withConnectionType1(WindingConnectionType.Y_GROUNDED)
                .withConnectionType2(WindingConnectionType.Y_GROUNDED)
                .withGroundingX1(0.001)
                .withGroundingX2(0.002)
                .withFreeFluxes(false)
                .add();

        return network;
    }

    public static Network ieee4YgYgStepUpBalanced() {

        Network network = Network.create("4n", "test");
        network.setCaseDate(DateTime.parse("2018-03-05T13:30:30.486+01:00"));

        // step up case, we use Vbase of transformer = Vnom
        double v1nom = 12.47;
        double v3nom = 24.9;

        Substation substation1 = network.newSubstation()
                .setId("S1")
                .setCountry(Country.FR)
                .add();

        // Bus 1
        VoltageLevel vl1 = substation1.newVoltageLevel()
                .setId("VL_1")
                .setNominalV(v1nom)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(20)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus1 = vl1.getBusBreakerView().newBus()
                .setId("B1")
                .add();
        bus1.setV(v1nom).setAngle(0.);

        // Generator modeling infinite feeder
        Generator gen1 = vl1.newGenerator()
                .setId("G1")
                .setBus(bus1.getId())
                .setMinP(-100.0)
                .setMaxP(200)
                .setTargetP(0)
                .setTargetV(v1nom)
                .setVoltageRegulatorOn(true)
                .add();

        gen1.newExtension(GeneratorFortescueAdder.class)
                .withRz(0.)
                .withXz(0.0001)
                .withRn(0.)
                .withXn(0.0001)
                .add();

        // Bus2
        Substation substation23 = network.newSubstation()
                .setId("S23")
                .setCountry(Country.FR)
                .add();

        VoltageLevel vl2 = substation23.newVoltageLevel()
                .setId("VL_2")
                .setNominalV(v1nom)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(20)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus2 = vl2.getBusBreakerView().newBus()
                .setId("B2")
                .add();
        bus2.setV(v1nom).setAngle(0.);

        // Bus3
        VoltageLevel vl3 = substation23.newVoltageLevel()
                .setId("VL_3")
                .setNominalV(v3nom)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(40)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus3 = vl3.getBusBreakerView().newBus()
                .setId("B3")
                .add();
        bus3.setV(v3nom).setAngle(0.);

        // Bus4
        Substation substation4 = network.newSubstation()
                .setId("S4")
                .setCountry(Country.FR)
                .add();

        VoltageLevel vl4 = substation4.newVoltageLevel()
                .setId("VL_4")
                .setNominalV(v3nom)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(40)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus4 = vl4.getBusBreakerView().newBus()
                .setId("B4")
                .add();
        bus4.setV(v3nom).setAngle(0.);

        double p = 1.2;
        double q = p * 0.9;

        // balanced load
        vl4.newLoad()
                .setId("LOAD_4")
                .setBus(bus4.getId())
                .setP0(p)
                .setQ0(q)
                .add();

        double feetInMile = 5280;
        double ry = 0.3061;
        double xy = 0.627;
        double r0y = 0.7735;
        double x0y = 1.9373;
        double length1InFeet = 2000;
        double length2InFeet = 2500;

        Line line12 = network.newLine()
                .setId("B1_B2")
                .setVoltageLevel1(vl1.getId())
                .setBus1(bus1.getId())
                .setConnectableBus1(bus1.getId())
                .setVoltageLevel2(vl2.getId())
                .setBus2(bus2.getId())
                .setConnectableBus2(bus2.getId())
                .setR(ry * length1InFeet / feetInMile)
                .setX(xy * length1InFeet / feetInMile)
                .setG1(0.0)
                .setB1(0.0)
                .setG2(0.0)
                .setB2(0.0)
                .add();

        // addition of asymmetrical extensions
        line12.newExtension(LineAsymmetricalAdder.class)
                .withIsOpenA(false)
                .withIsOpenB(false)
                .withIsOpenC(false)
                .add();

        line12.newExtension(LineFortescueAdder.class)
                .withRz(r0y * length1InFeet / feetInMile)
                .withXz(x0y * length1InFeet / feetInMile)
                .add();

        Line line34 = network.newLine()
                .setId("B3_B4")
                .setVoltageLevel1(vl3.getId())
                .setBus1(bus3.getId())
                .setConnectableBus1(bus3.getId())
                .setVoltageLevel2(vl4.getId())
                .setBus2(bus4.getId())
                .setConnectableBus2(bus4.getId())
                .setR(ry * length2InFeet / feetInMile)
                .setX(xy * length2InFeet / feetInMile)
                .setG1(0.0)
                .setB1(0.0)
                .setG2(0.0)
                .setB2(0.0)
                .add();

        // addition of asymmetrical extensions
        line34.newExtension(LineAsymmetricalAdder.class)
                .withIsOpenA(false)
                .withIsOpenB(false)
                .withIsOpenC(false)
                .add();

        line34.newExtension(LineFortescueAdder.class)
                .withRz(r0y * length2InFeet / feetInMile)
                .withXz(x0y * length2InFeet / feetInMile)
                .add();

        double ratedU2 = v1nom;
        double ratedU3 = v3nom;
        double sBase = 6;
        double rT23 = ratedU2 * ratedU2 / sBase / 100;
        double xT23 = 6 * ratedU2 * ratedU2 / sBase / 100;
        var t23 = substation23.newTwoWindingsTransformer()
                .setId("T2W_B2_B3")
                .setVoltageLevel1(vl2.getId())
                .setBus1(bus2.getId())
                .setConnectableBus1(bus2.getId())
                .setRatedU1(ratedU2)
                .setVoltageLevel2(vl3.getId())
                .setBus2(bus3.getId())
                .setConnectableBus2(bus3.getId())
                .setRatedU2(ratedU3)
                .setR(rT23)
                .setX(xT23)
                .setG(0.0D)
                .setB(0.0D)
                .setRatedS(sBase)
                .add();

        t23.newExtension(TwoWindingsTransformerFortescueAdder.class)
                .withRz(rT23) // TODO : check that again
                .withXz(xT23) // TODO : check that
                .withConnectionType1(WindingConnectionType.Y_GROUNDED)
                .withConnectionType2(WindingConnectionType.Y_GROUNDED)
                .withGroundingX1(0.000001)
                .withGroundingX2(0.000001)
                .withFreeFluxes(true)
                .add();

        return network;
    }

    @Test
    void asym3phaseTransfoTest() {
        // test of an asymmetric three phase transformer
        Complex za = new Complex(0.1, 1.);
        Complex y1a = new Complex(0.001, 0.01);
        Complex y2a = new Complex(0.002, 0.02);

        Complex zb = new Complex(0.1, 1.);
        Complex y1b = new Complex(0.001, 0.01);
        Complex y2b = new Complex(0.002, 0.02);

        Complex zc = new Complex(0.1, 1.);
        Complex y1c = new Complex(0.001, 0.01);
        Complex y2c = new Complex(0.002, 0.02);

        Complex rho = new Complex(0.9, 0.);

        LegConnectionType leg1ConnectionType = LegConnectionType.Y;
        LegConnectionType leg2ConnectionType = LegConnectionType.Y;

        Complex zG1 = new Complex(0.01, 0.01);
        Complex zG2 = new Complex(0.03, 0.03);

        List<Boolean> connectionList = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            connectionList.add(true);
        }
        connectionList.set(2, false);

        ComplexMatrix ya = buildSinglePhaseAdmittanceMatrix(za, y1a, y2a);
        ComplexMatrix yb = buildSinglePhaseAdmittanceMatrix(zb, y1b, y2b);
        ComplexMatrix yc = buildSinglePhaseAdmittanceMatrix(zc, y1c, y2c);

        AsymThreePhaseTransfo asym3phaseTfo = new AsymThreePhaseTransfo(leg1ConnectionType, leg2ConnectionType,
                ya, yb, yc, rho, zG1, zG2, connectionList);

    }

    public static ComplexMatrix buildSinglePhaseAdmittanceMatrix(Complex z, Complex y1, Complex y2) {
        ComplexMatrix cm = new ComplexMatrix(2, 2);
        cm.set(1, 1, y1.add(z.reciprocal()));
        cm.set(1, 2, z.reciprocal().multiply(-1.));
        cm.set(2, 1, z.reciprocal().multiply(-1.));
        cm.set(2, 2, y2.add(z.reciprocal()));

        return cm;
    }

    @Test
    void asym3phase4busFeederTransfoTest() {
        // test of an asymmetric three phase transformer
        double vBase2 = 12.47;
        double vBase3 = 4.16;
        double sBase = 2;

        Complex zBase = new Complex(1., 6.).multiply(vBase3 * vBase3 / 100. / 2. / 3); // seem to be the closest solution... which means its more 6 MVA than 2 MVA for Sbase
        Complex yBase = new Complex(0., 0.);
        Complex za = zBase;
        Complex y1a = yBase;
        Complex y2a = yBase;

        Complex zb = zBase;
        Complex y1b = yBase;
        Complex y2b = yBase;

        Complex zc = zBase;
        Complex y1c = yBase;
        Complex y2c = yBase;

        Complex rho = new Complex(1., 0.).multiply(vBase3 / vBase2);

        LegConnectionType leg1ConnectionType = LegConnectionType.Y_GROUNDED;
        LegConnectionType leg2ConnectionType = LegConnectionType.Y_GROUNDED;

        Complex zG1 = new Complex(0., 0.);
        Complex zG2 = new Complex(0., 0.);

        List<Boolean> connectionList = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            connectionList.add(true);
        }
        //connectionList.set(2, false);

        ComplexMatrix ya = buildSinglePhaseAdmittanceMatrix(za, y1a, y2a);
        ComplexMatrix yb = buildSinglePhaseAdmittanceMatrix(zb, y1b, y2b);
        ComplexMatrix yc = buildSinglePhaseAdmittanceMatrix(zc, y1c, y2c);

        AsymThreePhaseTransfo asym3phaseTfo = new AsymThreePhaseTransfo(leg1ConnectionType, leg2ConnectionType,
                ya, yb, yc, rho, zG1, zG2, connectionList);

        DenseMatrix yabc = asym3phaseTfo.getYabc();

        double va2 = 7.107;
        double tha2 = Math.toRadians(-0.3);
        double vb2 = 7.140;
        double thb2 = Math.toRadians(-120.3);
        double vc2 = 7.121;
        double thc2 = Math.toRadians(119.6);

        double va3 = 2.2476;
        double tha3 = Math.toRadians(-3.7);
        double vb3 = 2.269;
        double thb3 = Math.toRadians(-123.5);
        double vc3 = 2.256;
        double thc3 = Math.toRadians(116.4);

        Vector2D va2Cart = Fortescue.getCartesianFromPolar(va2, tha2);
        Vector2D vb2Cart = Fortescue.getCartesianFromPolar(vb2, thb2);
        Vector2D vc2Cart = Fortescue.getCartesianFromPolar(vc2, thc2);

        Vector2D va3Cart = Fortescue.getCartesianFromPolar(va3, tha3);
        Vector2D vb3Cart = Fortescue.getCartesianFromPolar(vb3, thb3);
        Vector2D vc3Cart = Fortescue.getCartesianFromPolar(vc3, thc3);

        DenseMatrix vabc2Vabc3 = new DenseMatrix(12, 1);
        vabc2Vabc3.add(0, 0, va2Cart.getX());
        vabc2Vabc3.add(1, 0, va2Cart.getY());
        vabc2Vabc3.add(2, 0, vb2Cart.getX());
        vabc2Vabc3.add(3, 0, vb2Cart.getY());
        vabc2Vabc3.add(4, 0, vc2Cart.getX());
        vabc2Vabc3.add(5, 0, vc2Cart.getY());

        vabc2Vabc3.add(6, 0, va3Cart.getX());
        vabc2Vabc3.add(7, 0, va3Cart.getY());
        vabc2Vabc3.add(8, 0, vb3Cart.getX());
        vabc2Vabc3.add(9, 0, vb3Cart.getY());
        vabc2Vabc3.add(10, 0, vc3Cart.getX());
        vabc2Vabc3.add(11, 0, vc3Cart.getY());

        DenseMatrix iabc2Iabc3 = yabc.times(vabc2Vabc3);
        Pair<Double, Double> ia2Polar = getPolarFromCartesian(iabc2Iabc3.get(0, 0), iabc2Iabc3.get(1, 0));
        Pair<Double, Double> ib2Polar = getPolarFromCartesian(iabc2Iabc3.get(2, 0), iabc2Iabc3.get(3, 0));
        Pair<Double, Double> ic2Polar = getPolarFromCartesian(iabc2Iabc3.get(4, 0), iabc2Iabc3.get(5, 0));

        Pair<Double, Double> ia3Polar = getPolarFromCartesian(iabc2Iabc3.get(6, 0), iabc2Iabc3.get(7, 0));
        Pair<Double, Double> ib3Polar = getPolarFromCartesian(iabc2Iabc3.get(8, 0), iabc2Iabc3.get(9, 0));
        Pair<Double, Double> ic3Polar = getPolarFromCartesian(iabc2Iabc3.get(10, 0), iabc2Iabc3.get(11, 0));

        System.out.println("Ia2 = " + ia2Polar.getFirst() + " ( " + Math.toDegrees(ia2Polar.getSecond()));
        System.out.println("Ib2 = " + ib2Polar.getFirst() + " ( " + Math.toDegrees(ib2Polar.getSecond()));
        System.out.println("Ic2 = " + ic2Polar.getFirst() + " ( " + Math.toDegrees(ic2Polar.getSecond()));

        System.out.println("Ia3 = " + ia3Polar.getFirst() + " ( " + Math.toDegrees(ia3Polar.getSecond()));
        System.out.println("Ib3 = " + ib3Polar.getFirst() + " ( " + Math.toDegrees(ib3Polar.getSecond()));
        System.out.println("Ic3 = " + ic3Polar.getFirst() + " ( " + Math.toDegrees(ic3Polar.getSecond()));

    }

    @Test
    void asym3phase4busFeederYgDeltaTransfoTest() {
        // test of an asymmetric three phase transformer
        double vBase2 = 12.47;
        double vBase3 = 4.16;
        double sBase = 2.;

        Complex zBase = new Complex(1., 6.).multiply(vBase3 * vBase3 / 100. / 2.); // seem to be the closest solution... which means its more 6 MVA than 2 MVA for Sbase combined with a sqrt3 * sqrt3 for per uniting
        Complex yBase = new Complex(0., 0.);
        Complex za = zBase;
        Complex y1a = yBase;
        Complex y2a = yBase;

        Complex zb = zBase;
        Complex y1b = yBase;
        Complex y2b = yBase;

        Complex zc = zBase;
        Complex y1c = yBase;
        Complex y2c = yBase;

        Complex rho = new Complex(1., 0.).multiply(vBase3 / vBase2 * Math.sqrt(3.)); // ratio of I is 3 and V is not 3...

        LegConnectionType leg1ConnectionType = LegConnectionType.Y_GROUNDED;
        LegConnectionType leg2ConnectionType = LegConnectionType.DELTA;

        Complex zG1 = new Complex(0., 0.);
        Complex zG2 = new Complex(0., 0.);

        List<Boolean> connectionList = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            connectionList.add(true);
        }
        //connectionList.set(2, false);

        ComplexMatrix ya = buildSinglePhaseAdmittanceMatrix(za, y1a, y2a);
        ComplexMatrix yb = buildSinglePhaseAdmittanceMatrix(zb, y1b, y2b);
        ComplexMatrix yc = buildSinglePhaseAdmittanceMatrix(zc, y1c, y2c);

        AsymThreePhaseTransfo asym3phaseTfo = new AsymThreePhaseTransfo(leg1ConnectionType, leg2ConnectionType,
                ya, yb, yc, rho, zG1, zG2, connectionList);

        DenseMatrix yabc = asym3phaseTfo.getYabc();

        double va2 = 7.113;
        double tha2 = Math.toRadians(-0.3);
        double vb2 = 7.132;
        double thb2 = Math.toRadians(-120.3);
        double vc2 = 7.123;
        double thc2 = Math.toRadians(119.6);

        double vab3 = 3.906;
        double thab3 = Math.toRadians(-3.5);
        double vbc3 = 3.915;
        double thbc3 = Math.toRadians(-123.6);
        double vca3 = 3.909;
        double thca3 = Math.toRadians(116.3);

        Vector2D va2Cart = Fortescue.getCartesianFromPolar(va2, tha2);
        Vector2D vb2Cart = Fortescue.getCartesianFromPolar(vb2, thb2);
        Vector2D vc2Cart = Fortescue.getCartesianFromPolar(vc2, thc2);

        Vector2D vab3Cart = Fortescue.getCartesianFromPolar(vab3, thab3);
        Vector2D vbc3Cart = Fortescue.getCartesianFromPolar(vbc3, thbc3);
        Vector2D vca3Cart = Fortescue.getCartesianFromPolar(vca3, thca3);

        Complex vab3Complex = new Complex(vab3Cart.getX(), vab3Cart.getY());
        Complex vca3Complex = new Complex(vca3Cart.getX(), vca3Cart.getY());
        // we suppose Va3 = 1/sqrt(3).Vab3 (arbitrary)
        Complex va3 = new Complex(vab3Cart.getX(), vab3Cart.getY()).multiply(1. / Math.sqrt(3.));

        Complex vb3 = va3.add(vab3Complex.multiply(-1.));
        Complex vc3 = vca3Complex.add(va3);

        Pair<Double, Double> va3Polar = getPolarFromCartesian(va3.getReal(), va3.getImaginary());
        Pair<Double, Double> vb3Polar = getPolarFromCartesian(vb3.getReal(), vb3.getImaginary());
        Pair<Double, Double> vc3Polar = getPolarFromCartesian(vc3.getReal(), vc3.getImaginary());

        System.out.println("Va3 = " + va3Polar.getFirst() + " ( " + Math.toDegrees(va3Polar.getSecond()));
        System.out.println("Vb3 = " + vb3Polar.getFirst() + " ( " + Math.toDegrees(vb3Polar.getSecond()));
        System.out.println("Vc3 = " + vc3Polar.getFirst() + " ( " + Math.toDegrees(vc3Polar.getSecond()));

        Complex vabCalc = va3.add(vb3.multiply(-1.));
        Complex vbcCalc = vb3.add(vc3.multiply(-1.));
        Complex vcaCalc = vc3.add(va3.multiply(-1.));

        Pair<Double, Double> vabPolar = getPolarFromCartesian(vabCalc.getReal(), vabCalc.getImaginary());
        Pair<Double, Double> vbcPolar = getPolarFromCartesian(vbcCalc.getReal(), vbcCalc.getImaginary());
        Pair<Double, Double> vcaPolar = getPolarFromCartesian(vcaCalc.getReal(), vcaCalc.getImaginary());

        System.out.println("VabCalc = " + vabPolar.getFirst() + " ( " + Math.toDegrees(vabPolar.getSecond()));
        System.out.println("VbcCalc = " + vbcPolar.getFirst() + " ( " + Math.toDegrees(vbcPolar.getSecond()));
        System.out.println("VcaCalc = " + vcaPolar.getFirst() + " ( " + Math.toDegrees(vcaPolar.getSecond()));

        DenseMatrix vabc2Vabc3 = new DenseMatrix(12, 1);
        vabc2Vabc3.add(0, 0, va2Cart.getX());
        vabc2Vabc3.add(1, 0, va2Cart.getY());
        vabc2Vabc3.add(2, 0, vb2Cart.getX());
        vabc2Vabc3.add(3, 0, vb2Cart.getY());
        vabc2Vabc3.add(4, 0, vc2Cart.getX());
        vabc2Vabc3.add(5, 0, vc2Cart.getY());

        vabc2Vabc3.add(6, 0, va3.getReal());
        vabc2Vabc3.add(7, 0, va3.getImaginary());
        vabc2Vabc3.add(8, 0, vb3.getReal());
        vabc2Vabc3.add(9, 0, vb3.getImaginary());
        vabc2Vabc3.add(10, 0, vc3.getReal());
        vabc2Vabc3.add(11, 0, vc3.getImaginary());

        DenseMatrix iabc2Iabc3 = yabc.times(vabc2Vabc3);
        Pair<Double, Double> ia2Polar = getPolarFromCartesian(iabc2Iabc3.get(0, 0), iabc2Iabc3.get(1, 0));
        Pair<Double, Double> ib2Polar = getPolarFromCartesian(iabc2Iabc3.get(2, 0), iabc2Iabc3.get(3, 0));
        Pair<Double, Double> ic2Polar = getPolarFromCartesian(iabc2Iabc3.get(4, 0), iabc2Iabc3.get(5, 0));

        Pair<Double, Double> ia3Polar = getPolarFromCartesian(iabc2Iabc3.get(6, 0), iabc2Iabc3.get(7, 0));
        Pair<Double, Double> ib3Polar = getPolarFromCartesian(iabc2Iabc3.get(8, 0), iabc2Iabc3.get(9, 0));
        Pair<Double, Double> ic3Polar = getPolarFromCartesian(iabc2Iabc3.get(10, 0), iabc2Iabc3.get(11, 0));

        System.out.println("Ia2 = " + ia2Polar.getFirst() + " ( " + Math.toDegrees(ia2Polar.getSecond()));
        System.out.println("Ib2 = " + ib2Polar.getFirst() + " ( " + Math.toDegrees(ib2Polar.getSecond()));
        System.out.println("Ic2 = " + ic2Polar.getFirst() + " ( " + Math.toDegrees(ic2Polar.getSecond()));

        System.out.println("Ia3 = " + ia3Polar.getFirst() + " ( " + Math.toDegrees(ia3Polar.getSecond()));
        System.out.println("Ib3 = " + ib3Polar.getFirst() + " ( " + Math.toDegrees(ib3Polar.getSecond()));
        System.out.println("Ic3 = " + ic3Polar.getFirst() + " ( " + Math.toDegrees(ic3Polar.getSecond()));

    }

    public org.apache.commons.math3.util.Pair<Double, Double> getPolarFromCartesian(double xValue, double yValue) {
        double magnitude = Math.sqrt(xValue * xValue + yValue * yValue);
        double phase = Math.atan2(yValue, xValue); // TODO : check radians and degrees
        return new org.apache.commons.math3.util.Pair<>(magnitude, phase);
    }

    @Test
    void asym3phase4busFeederOpenYgDeltaTransfoTest() {
        // test of an asymmetric three phase transformer
        double vBase2 = 12.47;
        double vBase3 = 4.16;
        double sBase = 2.;

        Complex zBase = new Complex(1., 6.).multiply(vBase3 * vBase3 / 100. / 2.); // seem to be the closest solution... which means its more 6 MVA than 2 MVA for Sbase combined with a sqrt3 * sqrt3 for per uniting
        Complex yBase = new Complex(0., 0.);
        Complex za = zBase;
        Complex y1a = yBase;
        Complex y2a = yBase;

        Complex zb = zBase;
        Complex y1b = yBase;
        Complex y2b = yBase;

        Complex zc = zBase;
        Complex y1c = yBase;
        Complex y2c = yBase;

        Complex rho = new Complex(1., 0.).multiply(vBase3 / vBase2 * Math.sqrt(3.)); // ratio of I is 3 and V is not 3...

        LegConnectionType leg1ConnectionType = LegConnectionType.Y_GROUNDED;
        LegConnectionType leg2ConnectionType = LegConnectionType.DELTA;

        Complex zG1 = new Complex(0., 0.);
        Complex zG2 = new Complex(0., 0.);

        List<Boolean> connectionList = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            connectionList.add(true);
        }
        connectionList.set(2, false);

        ComplexMatrix ya = buildSinglePhaseAdmittanceMatrix(za, y1a, y2a);
        ComplexMatrix yb = buildSinglePhaseAdmittanceMatrix(zb, y1b, y2b);
        ComplexMatrix yc = buildSinglePhaseAdmittanceMatrix(zc, y1c, y2c);

        AsymThreePhaseTransfo asym3phaseTfo = new AsymThreePhaseTransfo(leg1ConnectionType, leg2ConnectionType,
                ya, yb, yc, rho, zG1, zG2, connectionList);

        DenseMatrix yabc = asym3phaseTfo.getYabc();

        double va2 = 6.984;
        double tha2 = Math.toRadians(0.4);
        double vb2 = 7.167;
        double thb2 = Math.toRadians(-121.7);
        double vc2 = 7.293;
        double thc2 = Math.toRadians(120.5);

        double vab3 = 3.701;
        double thab3 = Math.toRadians(-0.9);
        double vbc3 = 4.076;
        double thbc3 = Math.toRadians(-126.5);
        double vca3 = 3.572;
        double thca3 = Math.toRadians(110.9);

        Vector2D va2Cart = Fortescue.getCartesianFromPolar(va2, tha2);
        Vector2D vb2Cart = Fortescue.getCartesianFromPolar(vb2, thb2);
        Vector2D vc2Cart = Fortescue.getCartesianFromPolar(vc2, thc2);

        Vector2D vab3Cart = Fortescue.getCartesianFromPolar(vab3, thab3);
        Vector2D vbc3Cart = Fortescue.getCartesianFromPolar(vbc3, thbc3);
        Vector2D vca3Cart = Fortescue.getCartesianFromPolar(vca3, thca3);

        Complex vab3Complex = new Complex(vab3Cart.getX(), vab3Cart.getY());
        Complex vca3Complex = new Complex(vca3Cart.getX(), vca3Cart.getY());
        // we suppose Va3 = 1/sqrt(3).Vab3 (arbitrary)
        Complex va3 = new Complex(vab3Cart.getX(), vab3Cart.getY()).multiply(1. / Math.sqrt(3.));

        Complex vb3 = va3.add(vab3Complex.multiply(-1.));
        Complex vc3 = vca3Complex.add(va3);

        Pair<Double, Double> va3Polar = getPolarFromCartesian(va3.getReal(), va3.getImaginary());
        Pair<Double, Double> vb3Polar = getPolarFromCartesian(vb3.getReal(), vb3.getImaginary());
        Pair<Double, Double> vc3Polar = getPolarFromCartesian(vc3.getReal(), vc3.getImaginary());

        System.out.println("Va3 = " + va3Polar.getFirst() + " ( " + Math.toDegrees(va3Polar.getSecond()));
        System.out.println("Vb3 = " + vb3Polar.getFirst() + " ( " + Math.toDegrees(vb3Polar.getSecond()));
        System.out.println("Vc3 = " + vc3Polar.getFirst() + " ( " + Math.toDegrees(vc3Polar.getSecond()));

        Complex vabCalc = va3.add(vb3.multiply(-1.));
        Complex vbcCalc = vb3.add(vc3.multiply(-1.));
        Complex vcaCalc = vc3.add(va3.multiply(-1.));

        Pair<Double, Double> vabPolar = getPolarFromCartesian(vabCalc.getReal(), vabCalc.getImaginary());
        Pair<Double, Double> vbcPolar = getPolarFromCartesian(vbcCalc.getReal(), vbcCalc.getImaginary());
        Pair<Double, Double> vcaPolar = getPolarFromCartesian(vcaCalc.getReal(), vcaCalc.getImaginary());

        System.out.println("VabCalc = " + vabPolar.getFirst() + " ( " + Math.toDegrees(vabPolar.getSecond()));
        System.out.println("VbcCalc = " + vbcPolar.getFirst() + " ( " + Math.toDegrees(vbcPolar.getSecond()));
        System.out.println("VcaCalc = " + vcaPolar.getFirst() + " ( " + Math.toDegrees(vcaPolar.getSecond()));

        DenseMatrix vabc2Vabc3 = new DenseMatrix(12, 1);
        vabc2Vabc3.add(0, 0, va2Cart.getX());
        vabc2Vabc3.add(1, 0, va2Cart.getY());
        vabc2Vabc3.add(2, 0, vb2Cart.getX());
        vabc2Vabc3.add(3, 0, vb2Cart.getY());
        vabc2Vabc3.add(4, 0, vc2Cart.getX());
        vabc2Vabc3.add(5, 0, vc2Cart.getY());

        vabc2Vabc3.add(6, 0, va3.getReal());
        vabc2Vabc3.add(7, 0, va3.getImaginary());
        vabc2Vabc3.add(8, 0, vb3.getReal());
        vabc2Vabc3.add(9, 0, vb3.getImaginary());
        vabc2Vabc3.add(10, 0, vc3.getReal());
        vabc2Vabc3.add(11, 0, vc3.getImaginary());

        DenseMatrix iabc2Iabc3 = yabc.times(vabc2Vabc3);
        Pair<Double, Double> ia2Polar = getPolarFromCartesian(iabc2Iabc3.get(0, 0), iabc2Iabc3.get(1, 0));
        Pair<Double, Double> ib2Polar = getPolarFromCartesian(iabc2Iabc3.get(2, 0), iabc2Iabc3.get(3, 0));
        Pair<Double, Double> ic2Polar = getPolarFromCartesian(iabc2Iabc3.get(4, 0), iabc2Iabc3.get(5, 0));

        Pair<Double, Double> ia3Polar = getPolarFromCartesian(iabc2Iabc3.get(6, 0), iabc2Iabc3.get(7, 0));
        Pair<Double, Double> ib3Polar = getPolarFromCartesian(iabc2Iabc3.get(8, 0), iabc2Iabc3.get(9, 0));
        Pair<Double, Double> ic3Polar = getPolarFromCartesian(iabc2Iabc3.get(10, 0), iabc2Iabc3.get(11, 0));

        System.out.println("Ia2 = " + ia2Polar.getFirst() + " ( " + Math.toDegrees(ia2Polar.getSecond()));
        System.out.println("Ib2 = " + ib2Polar.getFirst() + " ( " + Math.toDegrees(ib2Polar.getSecond()));
        System.out.println("Ic2 = " + ic2Polar.getFirst() + " ( " + Math.toDegrees(ic2Polar.getSecond()));

        System.out.println("Ia3 = " + ia3Polar.getFirst() + " ( " + Math.toDegrees(ia3Polar.getSecond()));
        System.out.println("Ib3 = " + ib3Polar.getFirst() + " ( " + Math.toDegrees(ib3Polar.getSecond()));
        System.out.println("Ic3 = " + ic3Polar.getFirst() + " ( " + Math.toDegrees(ic3Polar.getSecond()));

    }

}
