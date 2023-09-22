/**
 * Copyright (c) 2023, Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 * Copyright (c) 2023, Geoffroy Jamgotchian <geoffroy.jamgotchian at gmail.com>
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.iidm.network.*;
import com.powsybl.iidm.network.extensions.*;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.network.SlackBusSelectionMode;
import com.powsybl.openloadflow.network.extensions.AsymThreePhaseTransfo;
import com.powsybl.openloadflow.network.extensions.iidm.*;
import com.powsybl.openloadflow.util.ComplexMatrix;
import org.apache.commons.math3.complex.Complex;
import org.joda.time.DateTime;
import org.junit.jupiter.api.Test;

import static com.powsybl.openloadflow.util.LoadFlowAssert.assertAngleEquals;
import static com.powsybl.openloadflow.util.LoadFlowAssert.assertVoltageEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class Asym4nodesFeederTest {

    private Network network;
    private Bus bus1;
    private Bus bus2;
    private Bus bus3;
    private Bus bus4;

    private LoadFlow.Runner loadFlowRunner;
    private LoadFlowParameters parameters;

    @Test
    void ygYgUnbalancedTest() {

        Complex zz = new Complex(0.0001, 0.0001); // 0.0001 , 0.001
        Complex zn = new Complex(0.0001, 0.0001); // 0.001 , 0.01
        Boolean isLoadBalanced = false;
        WindingConnectionType w1 = WindingConnectionType.Y_GROUNDED;
        WindingConnectionType w2 = WindingConnectionType.Y_GROUNDED;
        int numDisconnectedPhase = 0;
        StepWindingConnectionType stepWindingConnectionType = StepWindingConnectionType.NONE;

        network = Asym4nodesFeederTest.ieee4Feeder(zz, zn, isLoadBalanced, WindingConnectionType.Y_GROUNDED, w1, w2);

        bus1 = network.getBusBreakerView().getBus("B1");
        bus2 = network.getBusBreakerView().getBus("B2");
        bus3 = network.getBusBreakerView().getBus("B3");
        bus4 = network.getBusBreakerView().getBus("B4");

        loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        parameters = new LoadFlowParameters().setNoGeneratorReactiveLimits(true)
                .setDistributedSlack(false);
        OpenLoadFlowParameters.create(parameters)
                .setMaxNewtonRaphsonIterations(100)
                .setSlackBusSelectionMode(SlackBusSelectionMode.FIRST)
                .setAsymmetrical(true);

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());

        assertVoltageEquals(7.199557856794634, bus1);
        assertAngleEquals(0., bus1);
        assertVoltageEquals(7.121540042954291, bus2);
        assertVoltageEquals(2.2440043558129816, bus3);
        assertVoltageEquals(1.9562695924418882, bus4);

        // addition of an extension to have a 3 phase transformer and new load flow:
        addTfo3PhaseExtension(network, w2, stepWindingConnectionType, numDisconnectedPhase);
        result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());

        assertVoltageEquals(7.199557856794634, bus1);
        assertAngleEquals(0., bus1);
        assertVoltageEquals(7.121244336543118, bus2);
        assertVoltageEquals(2.243330561718073, bus3);
        assertVoltageEquals(1.9537456912534272, bus4);
    }

    @Test
    void ygYgTest() {

        Complex zz = new Complex(0.00001, 0.00001); // 0.0001 , 0.001
        Complex zn = new Complex(0.00001, 0.00001); // 0.001 , 0.01
        Boolean isLoadBalanced = true;
        WindingConnectionType w1 = WindingConnectionType.Y_GROUNDED;
        WindingConnectionType w2 = WindingConnectionType.Y_GROUNDED;
        int numDisconnectedPhase = 0;
        StepWindingConnectionType stepWindingConnectionType = StepWindingConnectionType.NONE;

        network = Asym4nodesFeederTest.ieee4Feeder(zz, zn, isLoadBalanced, WindingConnectionType.Y_GROUNDED, w1, w2);

        bus1 = network.getBusBreakerView().getBus("B1");
        bus2 = network.getBusBreakerView().getBus("B2");
        bus3 = network.getBusBreakerView().getBus("B3");
        bus4 = network.getBusBreakerView().getBus("B4");

        loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        parameters = new LoadFlowParameters().setNoGeneratorReactiveLimits(true)
                .setDistributedSlack(false);
        OpenLoadFlowParameters.create(parameters)
                .setMaxNewtonRaphsonIterations(100)
                .setSlackBusSelectionMode(SlackBusSelectionMode.FIRST)
                .setAsymmetrical(true);

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());

        assertVoltageEquals(7.199557856794634, bus1);
        assertAngleEquals(0., bus1);
        assertVoltageEquals(7.132278619390253, bus2);
        assertVoltageEquals(2.2643149471277386, bus3);
        assertVoltageEquals(2.017173369480019, bus4);

        // addition of an extension to have a 3 phase transformer and new load flow:
        addTfo3PhaseExtension(network, w2, stepWindingConnectionType, numDisconnectedPhase);
        result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());

        assertVoltageEquals(7.199557856794634, bus1);
        assertAngleEquals(0., bus1);
        assertVoltageEquals(7.132278619390253, bus2);
        assertVoltageEquals(2.2643149471277386, bus3);
        assertVoltageEquals(2.017173369480019, bus4);

    }

    @Test
    void ygDeltaTest() {

        Complex zz = new Complex(0.0001, 0.0001); // 0.0001 , 0.001
        Complex zn = new Complex(0.0001, 0.0001); // 0.001 , 0.01
        Boolean isLoadBalanced = true;
        WindingConnectionType w1 = WindingConnectionType.Y_GROUNDED;
        WindingConnectionType w2 = WindingConnectionType.DELTA;
        int numDisconnectedPhase = 0;
        StepWindingConnectionType stepWindingConnectionType = StepWindingConnectionType.STEP_DOWN;

        network = Asym4nodesFeederTest.ieee4Feeder(zz, zn, isLoadBalanced, WindingConnectionType.DELTA, w1, w2);

        bus1 = network.getBusBreakerView().getBus("B1");
        bus2 = network.getBusBreakerView().getBus("B2");
        bus3 = network.getBusBreakerView().getBus("B3");
        bus4 = network.getBusBreakerView().getBus("B4");

        loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        parameters = new LoadFlowParameters().setNoGeneratorReactiveLimits(true)
                .setDistributedSlack(false);
        OpenLoadFlowParameters.create(parameters)
                .setSlackBusSelectionMode(SlackBusSelectionMode.FIRST)
                .setMaxNewtonRaphsonIterations(100)
                .setMaxActivePowerMismatch(0.000001)
                .setMaxReactivePowerMismatch(0.000001)
                .setNewtonRaphsonConvEpsPerEq(0.000001)
                .setMaxVoltageMismatch(0.000001)
                .setMaxSusceptanceMismatch(0.000001)
                .setMaxAngleMismatch(0.000001)
                .setMaxRatioMismatch(0.000001)
                .setAsymmetrical(true);

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());

        assertVoltageEquals(7.199557856794634, bus1);
        assertAngleEquals(0., bus1);
        assertVoltageEquals(6.966398471893453, bus2);
        assertVoltageEquals(3.222022038533233, bus3);
        assertVoltageEquals(2.716189992104315, bus4);

        // addition of an extension to have a 3 phase transformer and new load flow:
        addTfo3PhaseExtension(network, w2, stepWindingConnectionType, numDisconnectedPhase);
        result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());

        assertVoltageEquals(7.199557856794634, bus1);
        assertAngleEquals(0., bus1);
        assertVoltageEquals(7.096954458010889, bus2);
        assertVoltageEquals(3.8041760321458704, bus3);
        assertVoltageEquals(3.400344917162819, bus4);
    }

    @Test
    void deltaDeltaTest() {

        Complex zz = new Complex(0.001, 0.001); // 0.0001 , 0.001
        Complex zn = new Complex(0.001, 0.001); // 0.001 , 0.01
        Boolean isLoadBalanced = true;
        WindingConnectionType w1 = WindingConnectionType.DELTA;
        WindingConnectionType w2 = WindingConnectionType.DELTA;
        int numDisconnectedPhase = 0;
        StepWindingConnectionType stepWindingConnectionType = StepWindingConnectionType.STEP_DOWN;

        network = Asym4nodesFeederTest.ieee4Feeder(zz, zn, isLoadBalanced, WindingConnectionType.DELTA, w1, w2);

        bus1 = network.getBusBreakerView().getBus("B1");
        bus2 = network.getBusBreakerView().getBus("B2");
        bus3 = network.getBusBreakerView().getBus("B3");
        bus4 = network.getBusBreakerView().getBus("B4");

        loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        parameters = new LoadFlowParameters().setNoGeneratorReactiveLimits(true)
                .setDistributedSlack(false);
        OpenLoadFlowParameters.create(parameters)
                .setSlackBusSelectionMode(SlackBusSelectionMode.FIRST)
                .setMaxNewtonRaphsonIterations(100)
                .setMaxActivePowerMismatch(0.001)
                .setMaxReactivePowerMismatch(0.001)
                .setNewtonRaphsonConvEpsPerEq(0.001)
                .setMaxVoltageMismatch(0.001)
                .setMaxSusceptanceMismatch(0.001)
                .setMaxAngleMismatch(0.001)
                .setMaxRatioMismatch(0.001)
                .setAsymmetrical(true);

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());

        assertVoltageEquals(12.47, bus1);
        assertAngleEquals(0., bus1);
        assertVoltageEquals(12.343319573062105, bus2);
        assertVoltageEquals(3.3615589948468156, bus3);
        assertVoltageEquals(2.885485615952693, bus4);

        // addition of an extension to have a 3 phase transformer and new load flow:
        addTfo3PhaseExtension(network, w2, stepWindingConnectionType, numDisconnectedPhase);
        result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());

        assertVoltageEquals(12.47, bus1);
        assertAngleEquals(0., bus1);
        assertVoltageEquals(12.363327843118185, bus2);
        assertVoltageEquals(3.8305407701093968, bus3);
        assertVoltageEquals(3.430495590081445, bus4);
    }

    @Test
    void ygDeltaUnbalancedTest() {

        Complex zz = new Complex(0.00001, 0.00001); // 0.0001 , 0.001
        Complex zn = new Complex(0.00001, 0.00001); // 0.001 , 0.01
        Boolean isLoadBalanced = false;
        WindingConnectionType w1 = WindingConnectionType.Y_GROUNDED;
        WindingConnectionType w2 = WindingConnectionType.DELTA;
        int numDisconnectedPhase = 0;
        StepWindingConnectionType stepWindingConnectionType = StepWindingConnectionType.STEP_DOWN;

        network = Asym4nodesFeederTest.ieee4Feeder(zz, zn, isLoadBalanced, WindingConnectionType.DELTA, w1, w2);

        bus1 = network.getBusBreakerView().getBus("B1");
        bus2 = network.getBusBreakerView().getBus("B2");
        bus3 = network.getBusBreakerView().getBus("B3");
        bus4 = network.getBusBreakerView().getBus("B4");

        loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        parameters = new LoadFlowParameters().setNoGeneratorReactiveLimits(true)
                .setDistributedSlack(false);
        OpenLoadFlowParameters.create(parameters)
                .setSlackBusSelectionMode(SlackBusSelectionMode.FIRST)
                .setMaxNewtonRaphsonIterations(100)
                .setMaxActivePowerMismatch(0.000001)
                .setMaxReactivePowerMismatch(0.000001)
                .setNewtonRaphsonConvEpsPerEq(0.000001)
                .setMaxVoltageMismatch(0.000001)
                .setMaxSusceptanceMismatch(0.000001)
                .setMaxAngleMismatch(0.000001)
                .setMaxRatioMismatch(0.000001)
                .setAsymmetrical(true);

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());

        assertVoltageEquals(7.199557856794634, bus1);
        assertAngleEquals(0., bus1);
        assertVoltageEquals(6.937021164658664, bus2);
        assertVoltageEquals(3.1166742441520894, bus3);
        assertVoltageEquals(2.547669019028284, bus4);

        // addition of an extension to have a 3 phase transformer and new load flow:
        addTfo3PhaseExtension(network, w2, stepWindingConnectionType, numDisconnectedPhase);
        result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());

        assertVoltageEquals(7.199557856794634, bus1);
        assertAngleEquals(0., bus1);
        assertVoltageEquals(7.090474310111878, bus2);
        assertVoltageEquals(3.7865891875643336, bus3);
        assertVoltageEquals(3.36128591375635, bus4);

    }

    public static Network ieee4Feeder(Complex zz, Complex zn, boolean isLoadBalanced, WindingConnectionType loadConnectionType, WindingConnectionType w1, WindingConnectionType w2) {

        Network network = Network.create("4n", "test");
        network.setCaseDate(DateTime.parse("2018-03-05T13:30:30.486+01:00"));

        // step up case, we use Vbase of transformer = Vnom
        double vBase1 = 12.47;
        double vBase3 = 4.16;

        double v1nom = vBase1 / Math.sqrt(3.); // Vnom at generating unit (line to ground)
        double v3nom = vBase3 / Math.sqrt(3.); // Vnom at side 2 of transformer  (line to ground)

        BusVariableType side1VariableType = BusVariableType.WYE;
        if (w1 == WindingConnectionType.DELTA) {
            side1VariableType = BusVariableType.DELTA;
            // test
            v1nom = vBase1;
        }

        BusVariableType side2VariableType = BusVariableType.WYE;
        if (w2 == WindingConnectionType.DELTA) {
            side2VariableType = BusVariableType.DELTA;
            // test
            v3nom = vBase3;

        }

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

        bus1.newExtension(BusAsymmetricalAdder.class)
                .withBusVariableType(side1VariableType)
                .add();

        // Generator modeling infinite feeder
        Generator gen1 = vl1.newGenerator()
                .setId("G1")
                .setBus(bus1.getId())
                .setMinP(-100.0)
                .setMaxP(200)
                .setTargetP(2)
                .setTargetV(v1nom)
                .setVoltageRegulatorOn(true)
                .add();

        gen1.newExtension(GeneratorFortescueAdder.class)
                .withRz(zz.getReal())
                .withXz(zz.getImaginary())
                .withRn(zn.getReal())
                .withXn(zn.getImaginary())
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

        bus2.newExtension(BusAsymmetricalAdder.class)
                .withBusVariableType(side1VariableType)
                .withPositiveSequenceAsCurrent(true)
                .add();

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

        bus3.newExtension(BusAsymmetricalAdder.class)
                .withBusVariableType(side2VariableType)
                .withPositiveSequenceAsCurrent(true)
                .add();

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

        bus4.newExtension(BusAsymmetricalAdder.class)
                .withPositiveSequenceAsCurrent(true)
                .withBusVariableType(side2VariableType)
                .add();

        double p;
        double q;
        if (loadConnectionType == WindingConnectionType.Y_GROUNDED) {
            p = 1.2;
            q = p * 0.9;

            // balanced load
            Load load4 = vl4.newLoad()
                    .setId("LOAD_4")
                    .setBus(bus4.getId())
                    .setP0(p)
                    .setQ0(q)
                    .add();

            if (!isLoadBalanced) {
                double pa = 0.85;
                double qa = pa * 0.85;
                double pc = 1.58333;
                double qc = 0.95 * pc;
                load4.newExtension(LoadAsymmetricalAdder.class)
                        .withDeltaPa(pa - p)
                        .withDeltaQa(qa - q)
                        .withDeltaPb(-0.)
                        .withDeltaQb(-0.)
                        .withDeltaPc(pc - p)
                        .withDeltaQc(qc - q)
                        .withConnectionType(LoadConnectionType.Y)
                        .add();

            } else {
                load4.newExtension(LoadAsymmetricalAdder.class)
                        .withConnectionType(LoadConnectionType.Y)
                        .add();
            }
        } else if (loadConnectionType == WindingConnectionType.DELTA) {
            p = 1.8;
            q = p * 0.9;

            // balanced load
            Load load4 = vl4.newLoad()
                    .setId("LOAD_4")
                    .setBus(bus4.getId())
                    .setP0(p)
                    .setQ0(q)
                    .add();

            if (!isLoadBalanced) {

                double pa = 1.275;
                double qa = pa * 0.85;
                double pc = 2.375;
                double qc = 0.95 * pc;
                load4.newExtension(LoadAsymmetricalAdder.class)
                        .withDeltaPa(pa - p)
                        .withDeltaQa(qa - q)
                        .withDeltaPb(-0.)
                        .withDeltaQb(-0.)
                        .withDeltaPc(pc - p)
                        .withDeltaQc(qc - q)
                        .withConnectionType(LoadConnectionType.DELTA)
                        .add();
            } else {
                load4.newExtension(LoadAsymmetricalAdder.class)
                        .withConnectionType(LoadConnectionType.DELTA)
                        .add();
            }
        }

        double feetInMile = 5280;
        double ry = 0.3061;
        double xy = 0.627;
        double r0y = 0.7735;
        double x0y = 1.9373;
        double length1InFeet = 2000;
        double length2InFeet = 2500;

        // building of YWyeabc from given Y impedance matrix Zy
        ComplexMatrix zy = new ComplexMatrix(3, 3);
        zy.set(1, 1, new Complex(0.4576, 1.078));
        zy.set(1, 2, new Complex(0.1559, 0.5017));
        zy.set(1, 3, new Complex(0.1535, 0.3849));
        zy.set(2, 1, new Complex(0.1559, 0.5017));
        zy.set(2, 2, new Complex(0.4666, 1.0482));
        zy.set(2, 3, new Complex(0.158, 0.4236));
        zy.set(3, 1, new Complex(0.1535, 0.3849));
        zy.set(3, 2, new Complex(0.158, 0.4236));
        zy.set(3, 3, new Complex(0.4615, 1.0651));

        DenseMatrix bwye3 = ComplexMatrix.complexMatrixIdentity(3).getRealCartesianMatrix();
        DenseMatrix minusId3 = ComplexMatrix.getMatrixScaled(ComplexMatrix.complexMatrixIdentity(3), -1.).getRealCartesianMatrix();
        DenseMatrix zWye = zy.getRealCartesianMatrix();
        zWye.decomposeLU().solve(bwye3);

        DenseMatrix minusBwye3 = bwye3.times(minusId3);
        DenseMatrix realYwyeabc = AsymThreePhaseTransfo.buildFromBlocs(bwye3, minusBwye3, minusBwye3, bwye3);
        ComplexMatrix ywyeabc = ComplexMatrix.getComplexMatrixFromRealCartesian(realYwyeabc);

        // building of YDeltaabc from given Y impedance matrix Zd
        ComplexMatrix zd = new ComplexMatrix(3, 3);
        zd.set(1, 1, new Complex(0.4013, 1.4133));
        zd.set(1, 2, new Complex(0.0953, 0.8515));
        zd.set(1, 3, new Complex(0.0953, 0.7266));
        zd.set(2, 1, new Complex(0.0953, 0.8515));
        zd.set(2, 2, new Complex(0.4013, 1.4133));
        zd.set(2, 3, new Complex(0.0953, 0.7802));
        zd.set(3, 1, new Complex(0.0953, 0.7266));
        zd.set(3, 2, new Complex(0.0953, 0.7802));
        zd.set(3, 3, new Complex(0.4013, 1.4133));

        DenseMatrix bdelta3 = ComplexMatrix.complexMatrixIdentity(3).getRealCartesianMatrix();
        DenseMatrix zDelta = zd.getRealCartesianMatrix();
        zDelta.decomposeLU().solve(bdelta3);

        DenseMatrix minusBdelta3 = bdelta3.times(minusId3);
        DenseMatrix realYdeltaabc = AsymThreePhaseTransfo.buildFromBlocs(bdelta3, minusBdelta3, minusBdelta3, bdelta3);
        ComplexMatrix yDeltaabc = ComplexMatrix.getComplexMatrixFromRealCartesian(realYdeltaabc);

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
        ComplexMatrix yabc12 = ComplexMatrix.getMatrixScaled(ywyeabc, feetInMile / length1InFeet);
        if (side1VariableType == BusVariableType.DELTA) {
            yabc12 = ComplexMatrix.getMatrixScaled(yDeltaabc, feetInMile / length1InFeet);
        }
        // addition of asymmetrical extensions
        line12.newExtension(LineAsymmetricalAdder.class)
                .withYabc(yabc12)
                .add();

        line12.newExtension(LineFortescueAdder.class)
                .withOpenPhaseA(false)
                .withOpenPhaseB(false)
                .withOpenPhaseC(false)
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
        ComplexMatrix yabc34 = ComplexMatrix.getMatrixScaled(ywyeabc, feetInMile / length2InFeet);
        if (side2VariableType == BusVariableType.DELTA) {
            yabc34 = ComplexMatrix.getMatrixScaled(yDeltaabc, feetInMile / length2InFeet);
        }
        line34.newExtension(LineAsymmetricalAdder.class)
                .withYabc(yabc34)
                .add();

        line34.newExtension(LineFortescueAdder.class)
                .withOpenPhaseA(false)
                .withOpenPhaseB(false)
                .withOpenPhaseC(false)
                .withRz(r0y * length2InFeet / feetInMile)
                .withXz(x0y * length2InFeet / feetInMile)
                .add();

        double ratedU2 = vBase1;
        double ratedU3 = vBase3;
        double sBase = 2.;

        if (w1 == WindingConnectionType.Y_GROUNDED) {
            ratedU2 = vBase1 / Math.sqrt(3);
        }
        if (w2 == WindingConnectionType.Y_GROUNDED) {
            ratedU3 = vBase3 / Math.sqrt(3.);
        }

        double zBase = ratedU3 * ratedU3 / sBase;
        double rT23 = zBase / 100;
        double xT23 = 6. * zBase / 100;
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
                .withRz(rT23)
                .withXz(xT23)
                .withConnectionType1(w1)
                .withConnectionType2(w2)
                .withGroundingX1(0.0000)
                .withGroundingX2(0.0000)
                .withFreeFluxes(true)
                .add();

        return network;
    }

    public static void addTfo3PhaseExtension(Network network, WindingConnectionType w2, StepWindingConnectionType stepWindingConnectionType, int numDisconnectedPhase) {

        TwoWindingsTransformer t2w = network.getTwoWindingsTransformer("T2W_B2_B3");
        // step up case, we use Vbase of transformer = Vnom
        double vBase3 = 4.16;
        double ratedU3 = vBase3;
        double sBase = 2.;

        if (w2 == WindingConnectionType.Y_GROUNDED) {
            ratedU3 = vBase3 / Math.sqrt(3.);
        }

        double zBase = ratedU3 * ratedU3 / sBase;

        Complex zPhase = new Complex(1., 6.).multiply(zBase / 100.);
        Complex yPhase = new Complex(0., 0.);

        // test 3 phase tfo
        boolean openPhase1 = false;
        boolean openPhase2 = false;
        boolean openPhase3 = false;
        if (numDisconnectedPhase == 1) {
            openPhase1 = true;
        }
        if (numDisconnectedPhase == 2) {
            openPhase2 = true;
        }
        if (numDisconnectedPhase == 3) {
            openPhase3 = true;
        }
        t2w.newExtension(Tfo3PhasesAdder.class)
                .withIsOpenPhaseA1(openPhase1)
                .withIsOpenPhaseB1(openPhase2)
                .withIsOpenPhaseC1(openPhase3)
                .withYa(buildSinglePhaseAdmittanceMatrix(zPhase, yPhase, yPhase))
                .withYb(buildSinglePhaseAdmittanceMatrix(zPhase, yPhase, yPhase))
                .withYc(buildSinglePhaseAdmittanceMatrix(zPhase, yPhase, yPhase))
                .withStepWindingConnectionType(stepWindingConnectionType)
                .add();
    }

    public static ComplexMatrix buildSinglePhaseAdmittanceMatrix(Complex z, Complex y1, Complex y2) {
        ComplexMatrix cm = new ComplexMatrix(2, 2);
        cm.set(1, 1, y1.add(z.reciprocal()));
        cm.set(1, 2, z.reciprocal().multiply(-1.));
        cm.set(2, 1, z.reciprocal().multiply(-1.));
        cm.set(2, 2, y2.add(z.reciprocal()));

        return cm;
    }
}
