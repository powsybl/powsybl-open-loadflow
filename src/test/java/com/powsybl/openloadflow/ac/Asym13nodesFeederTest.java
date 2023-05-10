package com.powsybl.openloadflow.ac;

import com.powsybl.iidm.network.*;
import com.powsybl.iidm.network.extensions.GeneratorFortescueAdder;
import com.powsybl.iidm.network.extensions.LineFortescueAdder;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.network.SlackBusSelectionMode;
import com.powsybl.openloadflow.network.extensions.AsymThreePhaseTransfo;
import com.powsybl.openloadflow.network.extensions.iidm.BusAsymmetricalAdder;
import com.powsybl.openloadflow.network.extensions.iidm.BusVariableType;
import com.powsybl.openloadflow.network.extensions.iidm.LineAsymmetricalAdder;
import com.powsybl.openloadflow.util.ComplexMatrix;
import org.apache.commons.math3.complex.Complex;
import org.joda.time.DateTime;
import org.junit.jupiter.api.Test;

import static com.powsybl.openloadflow.util.LoadFlowAssert.assertAngleEquals;
import static com.powsybl.openloadflow.util.LoadFlowAssert.assertVoltageEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Asym13nodesFeederTest {

    private Network network;
    private Bus bus650;
    private Bus bus632;
    private Bus bus645;
    private Bus bus646;

    private LoadFlow.Runner loadFlowRunner;
    private LoadFlowParameters parameters;

    @Test
    void ieee13Test() {

        /*Complex zz = new Complex(0.1, 0.01); // 0.0001 , 0.001
        Complex zn = new Complex(0.1, 0.01); // 0.001 , 0.01
        Boolean isLoadBalanced = true;
        Boolean is3PhaseTfo = true;
        WindingConnectionType w1 = WindingConnectionType.Y_GROUNDED;
        WindingConnectionType w2 = WindingConnectionType.Y_GROUNDED;*/

        network = ieee13Feeder();

        bus650 = network.getBusBreakerView().getBus("B650");
        bus632 = network.getBusBreakerView().getBus("B632");
        bus645 = network.getBusBreakerView().getBus("B645");
        bus646 = network.getBusBreakerView().getBus("B646");

        loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        parameters = new LoadFlowParameters().setNoGeneratorReactiveLimits(true)
                .setDistributedSlack(false);
        OpenLoadFlowParameters.create(parameters)
                .setSlackBusSelectionMode(SlackBusSelectionMode.FIRST)
                .setMaxNewtonRaphsonIterations(100)
                .setMaxActivePowerMismatch(0.0001)
                .setMaxReactivePowerMismatch(0.0001)
                .setNewtonRaphsonConvEpsPerEq(0.0001)
                .setMaxVoltageMismatch(0.0001)
                .setMaxSusceptanceMismatch(0.0001)
                .setMaxAngleMismatch(0.0001)
                .setMaxRatioMismatch(0.0001)
                .setAsymmetrical(true);

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());

        assertVoltageEquals(4.16, bus650);
        assertAngleEquals(0., bus650);
        assertVoltageEquals(4.16, bus632);
        assertVoltageEquals(4.160250008971789, bus645);
        assertVoltageEquals(4.160271884980543, bus646);
    }

    public static Network ieee13Feeder() {
        Network network = Network.create("13n", "test");
        network.setCaseDate(DateTime.parse("2018-03-05T13:30:30.486+01:00"));

        double vBase = 4.16;

        Complex zz = new Complex(0.00001, 0.00001); // 0.0001 , 0.001
        Complex zn = new Complex(0.00001, 0.00001); // 0.001 , 0.01

        // Bus 650
        Substation substation650 = network.newSubstation()
                .setId("S650")
                .setCountry(Country.FR)
                .add();

        VoltageLevel vl650 = substation650.newVoltageLevel()
                .setId("VL_650")
                .setNominalV(vBase)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(20)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus650 = vl650.getBusBreakerView().newBus()
                .setId("B650")
                .add();
        bus650.setV(vBase).setAngle(0.);

        bus650.newExtension(BusAsymmetricalAdder.class)
                .withBusVariableType(BusVariableType.WYE)
                .add();

        // Bus 632
        Substation substation632 = network.newSubstation()
                .setId("S632")
                .setCountry(Country.FR)
                .add();

        VoltageLevel vl632 = substation632.newVoltageLevel()
                .setId("VL_632")
                .setNominalV(vBase)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(20)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus632 = vl632.getBusBreakerView().newBus()
                .setId("B632")
                .add();
        bus632.setV(vBase).setAngle(0.);

        bus632.newExtension(BusAsymmetricalAdder.class)
                .withBusVariableType(BusVariableType.WYE)
                .withPositiveSequenceAsCurrent(true)
                .add();

        // Bus 645
        Substation substation645 = network.newSubstation()
                .setId("S645")
                .setCountry(Country.FR)
                .add();

        VoltageLevel vl645 = substation645.newVoltageLevel()
                .setId("VL_645")
                .setNominalV(vBase)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(20)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus645 = vl645.getBusBreakerView().newBus()
                .setId("B645")
                .add();
        bus645.setV(vBase).setAngle(0.);

        bus645.newExtension(BusAsymmetricalAdder.class)
                .withBusVariableType(BusVariableType.WYE)
                .withHasPhaseA(false)
                .withPositiveSequenceAsCurrent(true)
                .add();

        // Bus 646
        Substation substation646 = network.newSubstation()
                .setId("S646")
                .setCountry(Country.FR)
                .add();

        VoltageLevel vl646 = substation646.newVoltageLevel()
                .setId("VL_646")
                .setNominalV(vBase)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(20)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus646 = vl646.getBusBreakerView().newBus()
                .setId("B646")
                .add();
        bus646.setV(vBase).setAngle(0.);

        bus646.newExtension(BusAsymmetricalAdder.class)
                .withBusVariableType(BusVariableType.WYE)
                .withHasPhaseA(false)
                .withPositiveSequenceAsCurrent(true)
                .add();

        // Generator modeling infinite feeder
        Generator gen650 = vl650.newGenerator()
                .setId("G650")
                .setBus(bus650.getId())
                .setMinP(-100.0)
                .setMaxP(200)
                .setTargetP(0)
                .setTargetV(vBase)
                .setVoltageRegulatorOn(true)
                .add();

        gen650.newExtension(GeneratorFortescueAdder.class)
                .withRz(zz.getReal())
                .withXz(zz.getImaginary())
                .withRn(zn.getReal())
                .withXn(zn.getImaginary())
                .add();

        double micro = 0.000001;

        // config 601 :
        // building of Yabc from given Y impedance matrix Zy
        ComplexMatrix zy601 = new ComplexMatrix(3, 3);
        zy601.set(1, 1, new Complex(0.3465, 1.0179));
        zy601.set(1, 2, new Complex(0.1560, 0.5017));
        zy601.set(1, 3, new Complex(0.1580, 0.4236));
        zy601.set(2, 1, new Complex(0.1560, 0.5017));
        zy601.set(2, 2, new Complex(0.3375, 1.0478));
        zy601.set(2, 3, new Complex(0.1535, 0.3849));
        zy601.set(3, 1, new Complex(0.1580, 0.4236));
        zy601.set(3, 2, new Complex(0.1535, 0.3849));
        zy601.set(3, 3, new Complex(0.3414, 1.0348));

        ComplexMatrix b601 = new ComplexMatrix(3, 3);

        b601.set(1, 1, new Complex(0, micro * 6.2998));
        b601.set(1, 2, new Complex(0, micro * -1.9958));
        b601.set(1, 3, new Complex(0, micro * -1.2595));
        b601.set(2, 1, new Complex(0, micro * -1.9958));
        b601.set(2, 2, new Complex(0, micro * 5.9597));
        b601.set(2, 3, new Complex(0, micro * -0.7417));
        b601.set(3, 1, new Complex(0, micro * -1.2595));
        b601.set(3, 2, new Complex(0, micro * -0.7417));
        b601.set(3, 3, new Complex(0, micro * 5.6386));

        ComplexMatrix yabc601 = getAdmittanceMatrixFromImpedanceAndBmatrix(zy601, b601, true, true, true);

        // config 603 :
        // building of Yabc from given Y impedance matrix Zy
        ComplexMatrix zy603 = new ComplexMatrix(3, 3);
        zy603.set(1, 1, new Complex(0., 0.));
        zy603.set(1, 2, new Complex(0., 0.));
        zy603.set(1, 3, new Complex(0., 0.));
        zy603.set(2, 1, new Complex(0., 0.));
        zy603.set(2, 2, new Complex(1.3294, 1.3471));
        zy603.set(2, 3, new Complex(0.2066, 0.4591));
        zy603.set(3, 1, new Complex(0., 0.));
        zy603.set(3, 2, new Complex(0.2066, 0.4591));
        zy603.set(3, 3, new Complex(1.3238, 1.3569));

        ComplexMatrix b603 = new ComplexMatrix(3, 3);
        b603.set(1, 1, new Complex(0, micro * 0.));
        b603.set(1, 2, new Complex(0, micro * 0.));
        b603.set(1, 3, new Complex(0, micro * 0.));
        b603.set(2, 1, new Complex(0, micro * 0.));
        b603.set(2, 2, new Complex(0, micro * 4.7097));
        b603.set(2, 3, new Complex(0, micro * -0.8999));
        b603.set(3, 1, new Complex(0, micro * 0.));
        b603.set(3, 2, new Complex(0, micro * -0.8999));
        b603.set(3, 3, new Complex(0, micro * 4.6658));

        ComplexMatrix yabc603 = getAdmittanceMatrixFromImpedanceAndBmatrix(zy603, b603, false, true, true);

        System.out.println("---- Complex Yabc603 form branch -------------------------------");
        ComplexMatrix.printComplexMatrix(yabc603);
        System.out.println("---- End -------------------------------");

        double feetInMile = 5280;
        double ry = 0.3061;
        double xy = 0.627;
        double r0y = 0.7735;
        double x0y = 1.9373;
        double length650y632InFeet = 2000.;
        double length632y645InFeet = 500.;
        double length645y646InFeet = 300.;

        Line line650y632 = network.newLine()
                .setId("650y632")
                .setVoltageLevel1(vl650.getId())
                .setBus1(bus650.getId())
                .setConnectableBus1(bus650.getId())
                .setVoltageLevel2(vl632.getId())
                .setBus2(bus632.getId())
                .setConnectableBus2(bus632.getId())
                .setR(ry * length650y632InFeet / feetInMile)
                .setX(xy * length650y632InFeet / feetInMile)
                .setG1(0.0)
                .setB1(0.0)
                .setG2(0.0)
                .setB2(0.0)
                .add();

        // addition of asymmetrical extensions
        line650y632.newExtension(LineAsymmetricalAdder.class)
                .withIsOpenA(false)
                .withIsOpenB(false)
                .withIsOpenC(false)
                .withYabc(ComplexMatrix.getMatrixScaled(yabc601, feetInMile / length650y632InFeet))
                .add();

        line650y632.newExtension(LineFortescueAdder.class)
                .withRz(r0y * length650y632InFeet / feetInMile)
                .withXz(x0y * length650y632InFeet / feetInMile)
                .add();

        Line line632y645 = network.newLine()
                .setId("632y645")
                .setVoltageLevel1(vl632.getId())
                .setBus1(bus632.getId())
                .setConnectableBus1(bus632.getId())
                .setVoltageLevel2(vl645.getId())
                .setBus2(bus645.getId())
                .setConnectableBus2(bus645.getId())
                .setR(ry * length632y645InFeet / feetInMile)
                .setX(xy * length632y645InFeet / feetInMile)
                .setG1(0.0)
                .setB1(0.0)
                .setG2(0.0)
                .setB2(0.0)
                .add();

        // addition of asymmetrical extensions
        line632y645.newExtension(LineAsymmetricalAdder.class)
                .withIsOpenA(false)
                .withIsOpenB(false)
                .withIsOpenC(false)
                .withYabc(ComplexMatrix.getMatrixScaled(yabc603, feetInMile / length632y645InFeet))
                .add();

        line632y645.newExtension(LineFortescueAdder.class)
                .withRz(r0y * length632y645InFeet / feetInMile)
                .withXz(x0y * length632y645InFeet / feetInMile)
                .add();

        Line line645y646 = network.newLine()
                .setId("645y646")
                .setVoltageLevel1(vl645.getId())
                .setBus1(bus645.getId())
                .setConnectableBus1(bus645.getId())
                .setVoltageLevel2(vl646.getId())
                .setBus2(bus646.getId())
                .setConnectableBus2(bus646.getId())
                .setR(ry * length645y646InFeet / feetInMile)
                .setX(xy * length645y646InFeet / feetInMile)
                .setG1(0.0)
                .setB1(0.0)
                .setG2(0.0)
                .setB2(0.0)
                .add();

        // addition of asymmetrical extensions
        line645y646.newExtension(LineAsymmetricalAdder.class)
                .withIsOpenA(false)
                .withIsOpenB(false)
                .withIsOpenC(false)
                .withYabc(ComplexMatrix.getMatrixScaled(yabc603, feetInMile / length645y646InFeet))
                .add();

        line645y646.newExtension(LineFortescueAdder.class)
                .withRz(r0y * length645y646InFeet / feetInMile)
                .withXz(x0y * length645y646InFeet / feetInMile)
                .add();

        return network;
    }

    public static ComplexMatrix getAdmittanceMatrixFromImpedanceAndBmatrix(ComplexMatrix zabc, ComplexMatrix babc, boolean hasPhaseA, boolean hasPhaseB, boolean hasPhaseC) {

        // second member [b] used as [b] = inv([z]) * [Id]
        DenseMatrix b3 = ComplexMatrix.complexMatrixIdentity(3).getRealCartesianMatrix();
        DenseMatrix minusId3 = ComplexMatrix.getMatrixScaled(ComplexMatrix.complexMatrixIdentity(3), -1.).getRealCartesianMatrix();

        // At this stage, zabc is not necessarily invertible since phases might be missing and then equivalent to zero blocs
        Complex one = new Complex(1., 0.);
        Complex zero = new Complex(0., 0.);
        if (!hasPhaseA) {
            // cancel all lines and columns of phase A and put 1 in the diagonal bloc for invertibility
            zabc.set(1, 1, one);
            zabc.set(1, 2, zero);
            zabc.set(1, 3, zero);
            zabc.set(2, 1, zero);
            zabc.set(3, 1, zero);
        }
        if (!hasPhaseB) {
            zabc.set(2, 2, one);
            zabc.set(1, 2, zero);
            zabc.set(3, 2, zero);
            zabc.set(2, 3, zero);
            zabc.set(2, 1, zero);
        }
        if (!hasPhaseC) {
            zabc.set(3, 3, one);
            zabc.set(1, 3, zero);
            zabc.set(2, 3, zero);
            zabc.set(3, 2, zero);
            zabc.set(3, 1, zero);
        }

        DenseMatrix zReal = zabc.getRealCartesianMatrix();
        zReal.decomposeLU().solve(b3);

        // Then we set to zero blocs with no phase
        ComplexMatrix invZabc = ComplexMatrix.getComplexMatrixFromRealCartesian(b3);
        if (!hasPhaseA) {
            invZabc.set(1, 1, zero);
        }
        if (!hasPhaseB) {
            invZabc.set(2, 2, zero);
        }
        if (!hasPhaseC) {
            invZabc.set(3, 3, zero);
        }

        b3 = invZabc.getRealCartesianMatrix();

        DenseMatrix minusB3 = b3.times(minusId3);
        DenseMatrix realYabc = AsymThreePhaseTransfo.buildFromBlocs(b3, minusB3, minusB3, b3);
        ComplexMatrix yabc = ComplexMatrix.getComplexMatrixFromRealCartesian(realYabc);

        // taking into account susceptance matrix babc
        yabc.set(4, 4, babc.getTerm(1, 1).add(yabc.getTerm(4, 4)));
        yabc.set(4, 5, babc.getTerm(1, 2).add(yabc.getTerm(4, 5)));
        yabc.set(4, 6, babc.getTerm(1, 3).add(yabc.getTerm(4, 6)));

        yabc.set(5, 4, babc.getTerm(2, 1).add(yabc.getTerm(5, 4)));
        yabc.set(5, 5, babc.getTerm(2, 2).add(yabc.getTerm(5, 5)));
        yabc.set(5, 6, babc.getTerm(2, 3).add(yabc.getTerm(5, 6)));

        yabc.set(6, 4, babc.getTerm(3, 1).add(yabc.getTerm(6, 4)));
        yabc.set(6, 5, babc.getTerm(3, 2).add(yabc.getTerm(6, 5)));
        yabc.set(6, 6, babc.getTerm(3, 3).add(yabc.getTerm(6, 6)));

        return yabc;
    }

}


