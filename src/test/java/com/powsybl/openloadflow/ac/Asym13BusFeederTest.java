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
import com.powsybl.openloadflow.network.extensions.iidm.*;
import com.powsybl.openloadflow.util.ComplexMatrix;
import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.complex.ComplexUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;

import static com.powsybl.openloadflow.util.LoadFlowAssert.assertVoltageEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class Asym13BusFeederTest {

    private Network network;
    private Bus bus650;
    private Bus bus632;
    private Bus bus645;
    private Bus bus646;
    private Bus bus652;
    private Bus bus684;
    private Bus bus611;

    private LoadFlow.Runner loadFlowRunner;
    private LoadFlowParameters parameters;

    @BeforeEach
    void setUp() {
        network = ieee13LoadFeeder();

        bus650 = network.getBusBreakerView().getBus("B650");
        bus632 = network.getBusBreakerView().getBus("B632");
        bus645 = network.getBusBreakerView().getBus("B645");
        bus646 = network.getBusBreakerView().getBus("B646");
        bus652 = network.getBusBreakerView().getBus("B652");
        bus684 = network.getBusBreakerView().getBus("B684");
        bus611 = network.getBusBreakerView().getBus("B611");

        loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        parameters = new LoadFlowParameters()
                .setUseReactiveLimits(false)
                .setDistributedSlack(false);
    }

    @Test
    void test601() {

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

        double length650y632InFeet = 2000.;
        double feetInMile = 5280;
        ComplexMatrix yabc601 = LineAsymmetrical.getAdmittanceMatrixFromImpedanceAndBmatrix(zy601, b601, true, true, true).scale(feetInMile / length650y632InFeet / Math.sqrt(3));

        double vBase = 4.16;
        ComplexMatrix v = new ComplexMatrix(6, 1);
        v.set(1, 1, ComplexUtils.polar2Complex(vBase * 1.0625, Math.toRadians(0.)));
        v.set(2, 1, ComplexUtils.polar2Complex(vBase * 1.05, Math.toRadians(-120.)));
        v.set(3, 1, ComplexUtils.polar2Complex(vBase * 1.0687, Math.toRadians(120.)));
        v.set(4, 1, ComplexUtils.polar2Complex(vBase * 1.0210, Math.toRadians(-2.49)));
        v.set(5, 1, ComplexUtils.polar2Complex(vBase * 1.042, Math.toRadians(-121.72)));
        v.set(6, 1, ComplexUtils.polar2Complex(vBase * 1.0174, Math.toRadians(117.83)));

        DenseMatrix i601Real = yabc601.getRealCartesianMatrix().times(v.getRealCartesianMatrix());
        ComplexMatrix i601 = ComplexMatrix.getComplexMatrixFromRealCartesian(i601Real);

        assertEquals(0.5586746694365669, i601.getTerm(1, 1).abs(), 0.00001);
        assertEquals(0.41499019362590045, i601.getTerm(5, 1).abs(), 0.00001);

    }

    @Test
    void ieee13LoadTest() {

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
        assertVoltageEquals(4.2762101139626765, bus632);
        assertVoltageEquals(4.230636258403482, bus645);
        assertVoltageEquals(4.220762522173593, bus646);
        assertVoltageEquals(4.09426812981166, bus652);
    }

    @Test
    void ieee13LoadWithConstantCurrentTest() {

        // addition of constant loads at busses
        Load load645Current = network.getVoltageLevel("VL_645").newLoad()
                .setId("LOAD_645_CURRENT")
                .setBus(bus645.getId())
                .setP0(0.)
                .setQ0(0.)
                .newZipModel()
                    .setC0p(0.)
                    .setC1p(1.)
                    .setC2p(0.)
                    .setC0q(0.)
                    .setC1q(1.)
                    .setC2q(0.)
                    .add()
                .add();

        load645Current.newExtension(LoadAsymmetricalAdder.class)
                .withDeltaPa(0.)
                .withDeltaQa(0.)
                .withDeltaPb(0.01)
                .withDeltaQb(0.02)
                .withDeltaPc(0.015)
                .withDeltaQc(0.025)
                .withConnectionType(LoadConnectionType.Y)
                .add();

        Load load684Current = network.getVoltageLevel("VL_684").newLoad()
                .setId("LOAD_684_CURRENT")
                .setBus(bus684.getId())
                .setP0(0.)
                .setQ0(0.)
                .newZipModel()
                    .setC0p(0.)
                    .setC1p(1.)
                    .setC2p(0.)
                    .setC0q(0.)
                    .setC1q(1.)
                    .setC2q(0.)
                    .add()
                .add();

        load684Current.newExtension(LoadAsymmetricalAdder.class)
                .withDeltaPa(0.01)
                .withDeltaQa(0.02)
                .withDeltaPb(0.)
                .withDeltaQb(0.)
                .withDeltaPc(0.015)
                .withDeltaQc(0.025)
                .withConnectionType(LoadConnectionType.Y)
                .add();

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

        assertVoltageEquals(4.2762101139626765, bus632);
        assertVoltageEquals(4.211289127155611, bus645);
        assertVoltageEquals(4.202478360461728, bus646);
        assertVoltageEquals(4.09426812981166, bus652);
    }

    @Test
    void ieee13LoadWithConstantImpedanceTest() {

        // addition of constant loads at busses
        Load load684Impedance = network.getVoltageLevel("VL_684").newLoad()
                .setId("LOAD_684_IMPEDANCE")
                .setBus(bus684.getId())
                .setP0(0.)
                .setQ0(0.)
                .newZipModel()
                    .setC0p(0.)
                    .setC1p(0.)
                    .setC2p(1.)
                    .setC0q(0.)
                    .setC1q(0.)
                    .setC2q(1.)
                    .add()
                .add();

        load684Impedance.newExtension(LoadAsymmetricalAdder.class)
                .withDeltaPa(0.)
                .withDeltaQa(0.)
                .withDeltaPb(0.)
                .withDeltaQb(0.)
                .withDeltaPc(0.15)
                .withDeltaQc(0.25)
                .withConnectionType(LoadConnectionType.Y)
                .add();

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

        assertVoltageEquals(4.2581057409678005, bus632);
        assertVoltageEquals(4.147549288446873, bus645);
        assertVoltageEquals(4.138729902805793, bus646);
        assertVoltageEquals(4.154403168622116, bus652);
    }

    @Test
    void ieee13LoadWithConstantImpedanceDeltaTest() {

        // addition of constant loads at busses
        Load load684Impedance = network.getVoltageLevel("VL_684").newLoad()
                .setId("LOAD_684_IMPEDANCE")
                .setBus(bus684.getId())
                .setP0(0.)
                .setQ0(0.)
                .newZipModel()
                    .setC0p(0.)
                    .setC1p(0.)
                    .setC2p(1.)
                    .setC0q(0.)
                    .setC1q(0.)
                    .setC2q(1.)
                    .add()
                .add();

        load684Impedance.newExtension(LoadAsymmetricalAdder.class)
                .withDeltaPa(0.)
                .withDeltaQa(0.)
                .withDeltaPb(0.)
                .withDeltaQb(0.)
                .withDeltaPc(0.15)
                .withDeltaQc(0.25)
                .withConnectionType(LoadConnectionType.DELTA)
                .add();

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

        assertVoltageEquals(4.290533459921324, bus632);
        assertVoltageEquals(4.2520625000874075, bus645);
        assertVoltageEquals(4.243258426216129, bus646);
        assertVoltageEquals(4.154403168622116, bus652);
    }

    @Test
    void ieee13LoadWithAbcPowerLoadTest() {

        // addition of constant loads at busses
        Load load684Impedance = network.getVoltageLevel("VL_684").newLoad()
                .setId("LOAD_684_POWER")
                .setBus(bus684.getId())
                .setP0(0.)
                .setQ0(0.)
                .add();

        load684Impedance.newExtension(LoadAsymmetricalAdder.class)
                .withDeltaPa(0.)
                .withDeltaQa(0.)
                .withDeltaPb(0.)
                .withDeltaQb(0.)
                .withDeltaPc(0.15)
                .withDeltaQc(0.25)
                .withConnectionType(LoadConnectionType.Y)
                .add();

        // addition of constant loads at busses
        Load load611Power = network.getVoltageLevel("VL_611").newLoad()
                .setId("LOAD_611_POWER")
                .setBus(bus611.getId())
                .setP0(0.)
                .setQ0(0.)
                .add();

        load611Power.newExtension(LoadAsymmetricalAdder.class)
                .withDeltaPa(0.)
                .withDeltaQa(0.)
                .withDeltaPb(0.)
                .withDeltaQb(0.)
                .withDeltaPc(0.15)
                .withDeltaQc(0.25)
                .withConnectionType(LoadConnectionType.Y)
                .add();

        AsymmetricalBranchConnector c646 = new AsymmetricalBranchConnector(BusVariableType.WYE,
                false, true, true, false, true);

        Line line645y646 = network.getLine("645y646");
        var extension645y646 = line645y646.getExtension(LineAsymmetrical.class);
        if (extension645y646 != null) {
            extension645y646.setAsymConnectorBus2(c646);
        }

        Load load646New = network.getVoltageLevel("VL_646").newLoad()
                .setId("LOAD_646_NEW")
                .setBus(bus646.getId())
                .setP0(0.)
                .setQ0(0.)
                .newZipModel()
                    .setC0p(0.)
                    .setC1p(0.)
                    .setC2p(1.)
                    .setC0q(0.)
                    .setC1q(0.)
                    .setC2q(1.)
                    .add()
                .add();

        load646New.newExtension(LoadAsymmetricalAdder.class)
                .withDeltaPa(0.)
                .withDeltaQa(0.)
                .withDeltaPb(0.230)
                .withDeltaQb(0.132)
                .withDeltaPc(0.08)
                .withDeltaQc(0.07)
                .withConnectionType(LoadConnectionType.Y)
                .add();

        // old 646 replaced by new
        Load load646Old = network.getLoad("LOAD_646");
        load646Old.remove();

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

        assertVoltageEquals(4.254515745736239, bus632);
        assertVoltageEquals(4.172825723037055, bus645);
        assertVoltageEquals(4.169908057051565, bus646);
        assertVoltageEquals(4.153827817198066, bus652);
    }

    @Test
    void ieee13LoadWithAbLoadTest() {

        // addition of constant loads at busses
        Load load645Impedance = network.getVoltageLevel("VL_645").newLoad()
                .setId("LOAD_645_IMPEDANCE")
                .setBus(bus645.getId())
                .setP0(0.)
                .setQ0(0.)
                .newZipModel()
                    .setC0p(0.)
                    .setC1p(0.)
                    .setC2p(1.)
                    .setC0q(0.)
                    .setC1q(0.)
                    .setC2q(1.)
                    .add()
                .add();

        load645Impedance.newExtension(LoadAsymmetricalAdder.class)
                .withDeltaPa(0.02)
                .withDeltaQa(0.03)
                .withDeltaPb(0.04)
                .withDeltaQb(0.05)
                .withDeltaPc(0.)
                .withDeltaQc(0.)
                .withConnectionType(LoadConnectionType.DELTA)
                .add();

        AsymmetricalBranchConnector c645 = new AsymmetricalBranchConnector(BusVariableType.WYE,
                true, true, false, false, true);

        Load load645 = network.getLoad("LOAD_645");

        load645.newExtension(LoadAsymmetricalAdder.class)
                .withDeltaPa(0.08)
                .withDeltaQa(0.07)
                .withDeltaPb(0.230)
                .withDeltaQb(0.132)
                .withDeltaPc(0.)
                .withDeltaQc(0.)
                .withConnectionType(LoadConnectionType.Y)
                .add();

        AsymmetricalBranchConnector c646 = new AsymmetricalBranchConnector(BusVariableType.WYE,
                true, true, false, false, true);

        Load load646New = network.getVoltageLevel("VL_646").newLoad()
                .setId("LOAD_646_NEW")
                .setBus(bus646.getId())
                .setP0(0.)
                .setQ0(0.)
                .newZipModel()
                    .setC0p(0.)
                    .setC1p(0.)
                    .setC2p(1.)
                    .setC0q(0.)
                    .setC1q(0.)
                    .setC2q(1.)
                    .add()
                .add();

        load646New.newExtension(LoadAsymmetricalAdder.class)
                .withDeltaPa(0.08)
                .withDeltaQa(0.07)
                .withDeltaPb(0.230)
                .withDeltaQb(0.132)
                .withDeltaPc(0.)
                .withDeltaQc(0.)
                .withConnectionType(LoadConnectionType.Y)
                .add();

        // old 646 replaced by new
        Load load646Old = network.getLoad("LOAD_646");
        load646Old.remove();

        Load load646Current = network.getVoltageLevel("VL_646").newLoad()
                .setId("LOAD_646_CURRENT")
                .setBus(bus646.getId())
                .setP0(0.)
                .setQ0(0.)
                .newZipModel()
                    .setC0p(0.)
                    .setC1p(1.)
                    .setC2p(0.)
                    .setC0q(0.)
                    .setC1q(1.)
                    .setC2q(0.)
                    .add()
                .add();

        load646Current.newExtension(LoadAsymmetricalAdder.class)
                .withDeltaPa(0.02)
                .withDeltaQa(0.03)
                .withDeltaPb(0.04)
                .withDeltaQb(0.05)
                .withDeltaPc(0.)
                .withDeltaQc(0.)
                .withConnectionType(LoadConnectionType.Y)
                .add();

        Load load652New = network.getVoltageLevel("VL_652").newLoad()
                .setId("LOAD_652_NEW")
                .setBus(bus652.getId())
                .setP0(0.)
                .setQ0(0.)
                .newZipModel()
                    .setC0p(0.)
                    .setC1p(1.)
                    .setC2p(0.)
                    .setC0q(0.)
                    .setC1q(1.)
                    .setC2q(0.)
                    .add()
                .add();

        load652New.newExtension(LoadAsymmetricalAdder.class)
                .withDeltaPa(0.1)
                .withDeltaQa(0.05)
                .withDeltaPb(0.)
                .withDeltaQb(0.)
                .withDeltaPc(0.)
                .withDeltaQc(0.)
                .withConnectionType(LoadConnectionType.Y)
                .add();

        Load load652Old = network.getLoad("LOAD_652");
        load652Old.remove();

        double micro = 0.000001;
        double yCoef = 1. / 3.;
        double feetInMile = 5280;
        double length632y645InFeet = 500.;
        double length645y646InFeet = 300.;

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
        // line 632y645
        Line line632y645 = network.getLine("632y645");

        ComplexMatrix yabc632y645 = LineAsymmetrical.getAdmittanceMatrixFromImpedanceAndBmatrix(zy601, b601, true, true, false, length632y645InFeet / feetInMile);
        line632y645.newExtension(LineAsymmetricalAdder.class)
                .withYabc(yabc632y645.scale(yCoef))
                .withAsymConnector2(c645)
                .add();

        // line 645y646
        Line line645y646 = network.getLine("645y646");

        ComplexMatrix yabc645y646 = LineAsymmetrical.getAdmittanceMatrixFromImpedanceAndBmatrix(zy601, b601, true, true, false, length645y646InFeet / feetInMile);
        line645y646.newExtension(LineAsymmetricalAdder.class)
                .withYabc(yabc645y646.scale(yCoef))
                .withAsymConnector1(c645)
                .withAsymConnector2(c646)
                .add();

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

        assertVoltageEquals(4.254515745736239, bus632);
        assertVoltageEquals(4.256717054142234, bus645);
        assertVoltageEquals(4.248706828201556, bus646);
        assertVoltageEquals(4.042857013587517, bus652);
    }

    public static Network ieee13LoadFeeder() {
        Network network = Network.create("13n", "test");
        network.setCaseDate(ZonedDateTime.parse("2018-03-05T13:30:30.486+01:00"));

        double vBase = 4.16;
        double vBaseLow = 0.48;

        double yCoef = 1. / 3.;

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

        AsymmetricalBranchConnector c650 = new AsymmetricalBranchConnector(BusVariableType.WYE,
                true, true, true, true, false);

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

        AsymmetricalBranchConnector c632 = new AsymmetricalBranchConnector(BusVariableType.WYE,
                true, true, true, true, true);

        double p0 = 0.017;
        double q0 = 0.01;
        Load load632 = vl632.newLoad()
                .setId("LOAD_632")
                .setBus(bus632.getId())
                .setP0(p0)
                .setQ0(q0)
                .add();

        load632.newExtension(LoadAsymmetricalAdder.class)
                .withDeltaPa(0.)
                .withDeltaQa(0.)
                .withDeltaPb(0.066 - p0)
                .withDeltaQb(0.038 - q0)
                .withDeltaPc(0.117 - p0)
                .withDeltaQc(0.068 - q0)
                .withConnectionType(LoadConnectionType.Y)
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

        AsymmetricalBranchConnector c645 = new AsymmetricalBranchConnector(BusVariableType.WYE,
                false, true, true, true, true);

        double p645 = 0.0;
        double q645 = 0.0;
        Load load645 = vl645.newLoad()
                .setId("LOAD_645")
                .setBus(bus645.getId())
                .setP0(p645)
                .setQ0(q645)
                .add();

        load645.newExtension(LoadAsymmetricalAdder.class)
                .withDeltaPa(0.)
                .withDeltaQa(0.)
                .withDeltaPb(0.17)
                .withDeltaQb(0.125)
                .withDeltaPc(0.)
                .withDeltaQc(0.)
                .withConnectionType(LoadConnectionType.Y)
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

        AsymmetricalBranchConnector c646 = new AsymmetricalBranchConnector(BusVariableType.WYE,
                false, true, true, true, true);

        Load load646 = vl646.newLoad()
                .setId("LOAD_646")
                .setBus(bus646.getId())
                .setP0(0.)
                .setQ0(0.)
                .newZipModel()
                    .setC0p(0.)
                    .setC1p(0.)
                    .setC2p(1.)
                    .setC0q(0.)
                    .setC1q(0.)
                    .setC2q(1.)
                    .add()
                .add();

        load646.newExtension(LoadAsymmetricalAdder.class)
                .withDeltaPa(0.)
                .withDeltaQa(0.)
                .withDeltaPb(0.230)
                .withDeltaQb(0.132)
                .withDeltaPc(0.)
                .withDeltaQc(0.)
                .withConnectionType(LoadConnectionType.DELTA)
                .add();

        // bus 671
        Substation substation671 = network.newSubstation()
                .setId("S671")
                .setCountry(Country.FR)
                .add();

        VoltageLevel vl671 = substation671.newVoltageLevel()
                .setId("VL_671")
                .setNominalV(vBase)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(20)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus671 = vl671.getBusBreakerView().newBus()
                .setId("B671")
                .add();
        bus671.setV(vBase).setAngle(0.);

        AsymmetricalBranchConnector c671 = new AsymmetricalBranchConnector(BusVariableType.WYE,
                true, true, true, true, true);

        double p671 = 0.385;
        double q671 = 0.220;
        Load load671 = vl671.newLoad()
                .setId("LOAD_671")
                .setBus(bus671.getId())
                .setP0(p671)
                .setQ0(q671)
                .add();

        load671.newExtension(LoadAsymmetricalAdder.class)
                .withDeltaPa(0.)
                .withDeltaQa(0.)
                .withDeltaPb(0.)
                .withDeltaQb(0.)
                .withDeltaPc(0.170) // equivalent load at 692
                .withDeltaQc(0.151)
                .withConnectionType(LoadConnectionType.DELTA)
                .add();

        // bus 684
        Substation substation684 = network.newSubstation()
                .setId("S684")
                .setCountry(Country.FR)
                .add();

        VoltageLevel vl684 = substation684.newVoltageLevel()
                .setId("VL_684")
                .setNominalV(vBase)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(20)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus684 = vl684.getBusBreakerView().newBus()
                .setId("B684")
                .add();
        bus684.setV(vBase).setAngle(0.);

        AsymmetricalBranchConnector c684 = new AsymmetricalBranchConnector(BusVariableType.WYE,
                true, false, true, true, true);

        // bus 611
        Substation substation611 = network.newSubstation()
                .setId("S611")
                .setCountry(Country.FR)
                .add();

        VoltageLevel vl611 = substation611.newVoltageLevel()
                .setId("VL_611")
                .setNominalV(vBase)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(20)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus611 = vl611.getBusBreakerView().newBus()
                .setId("B611")
                .add();
        bus611.setV(vBase).setAngle(0.);

        AsymmetricalBranchConnector c611 = new AsymmetricalBranchConnector(BusVariableType.WYE,
                false, false, true, true, true);

        Load load611 = vl611.newLoad()
                .setId("LOAD_611")
                .setBus(bus611.getId())
                .setP0(0.)
                .setQ0(0.)
                .newZipModel()
                    .setC0p(0.)
                    .setC1p(1.)
                    .setC2p(0.)
                    .setC0q(0.)
                    .setC1q(1.)
                    .setC2q(0.)
                    .add()
                .add();

        load611.newExtension(LoadAsymmetricalAdder.class)
                .withDeltaPa(0.)
                .withDeltaQa(0.)
                .withDeltaPb(0.)
                .withDeltaQb(0.)
                .withDeltaPc(0.170)
                .withDeltaQc(0.080)
                .withConnectionType(LoadConnectionType.Y)
                .add();

        Load compens611 = vl611.newLoad()
                .setId("COMPENS_611")
                .setBus(bus611.getId())
                .setP0(0.)
                .setQ0(0.)
                .newZipModel()
                    .setC0p(0.)
                    .setC1p(0.)
                    .setC2p(1.)
                    .setC0q(0.)
                    .setC1q(0.)
                    .setC2q(1.)
                    .add()
                .add();

        compens611.newExtension(LoadAsymmetricalAdder.class)
                .withDeltaPa(0.)
                .withDeltaQa(0.)
                .withDeltaPb(0.)
                .withDeltaQb(0.)
                .withDeltaPc(0.)
                .withDeltaQc(-0.1)
                .withConnectionType(LoadConnectionType.Y)
                .add();

        // bus 652
        Substation substation652 = network.newSubstation()
                .setId("S652")
                .setCountry(Country.FR)
                .add();

        VoltageLevel vl652 = substation652.newVoltageLevel()
                .setId("VL_652")
                .setNominalV(vBase)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(20)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus652 = vl652.getBusBreakerView().newBus()
                .setId("B652")
                .add();
        bus652.setV(vBase).setAngle(0.);

        AsymmetricalBranchConnector c652 = new AsymmetricalBranchConnector(BusVariableType.WYE,
                true, false, false, true, true);

        Load load652 = vl652.newLoad()
                .setId("LOAD_652")
                .setBus(bus652.getId())
                .setP0(0.)
                .setQ0(0.)
                .newZipModel()
                    .setC0p(0.)
                    .setC1p(0.)
                    .setC2p(1.)
                    .setC0q(0.)
                    .setC1q(0.)
                    .setC2q(1.)
                    .add()
                .add();

        load652.newExtension(LoadAsymmetricalAdder.class)
                .withDeltaPa(0.128)
                .withDeltaQa(0.086)
                .withDeltaPb(0.)
                .withDeltaQb(0.)
                .withDeltaPc(0.)
                .withDeltaQc(0.)
                .withConnectionType(LoadConnectionType.Y)
                .add();

        // bus 680
        Substation substation680 = network.newSubstation()
                .setId("S680")
                .setCountry(Country.FR)
                .add();

        VoltageLevel vl680 = substation680.newVoltageLevel()
                .setId("VL_680")
                .setNominalV(vBase)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(20)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus680 = vl680.getBusBreakerView().newBus()
                .setId("B680")
                .add();
        bus680.setV(vBase).setAngle(0.);

        AsymmetricalBranchConnector c680 = new AsymmetricalBranchConnector(BusVariableType.WYE,
                true, true, true, true, true);

        // Bus 633
        Substation substation633 = network.newSubstation()
                .setId("S633")
                .setCountry(Country.FR)
                .add();

        VoltageLevel vl633 = substation633.newVoltageLevel()
                .setId("VL_633")
                .setNominalV(vBase)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(20)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus633 = vl633.getBusBreakerView().newBus()
                .setId("B633")
                .add();
        bus633.setV(vBase).setAngle(0.);

        AsymmetricalBranchConnector c633 = new AsymmetricalBranchConnector(BusVariableType.WYE,
                true, true, true, true, true);

        // Bus 634
        VoltageLevel vl634 = substation633.newVoltageLevel()
                .setId("VL_634")
                .setNominalV(vBaseLow)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(20)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus634 = vl634.getBusBreakerView().newBus()
                .setId("B634")
                .add();
        bus634.setV(vBase).setAngle(0.);

        AsymmetricalBranchConnector c634 = new AsymmetricalBranchConnector(BusVariableType.WYE,
                true, true, true, true, true);

        double p634 = 0.120;
        double q634 = 0.090;
        Load load634 = vl634.newLoad()
                .setId("LOAD_634")
                .setBus(bus634.getId())
                .setP0(p634)
                .setQ0(q634)
                .add();

        load634.newExtension(LoadAsymmetricalAdder.class)
                .withDeltaPa(0.160 - p634)
                .withDeltaQa(0.110 - q634)
                .withDeltaPb(0.)
                .withDeltaQb(0.)
                .withDeltaPc(0.)
                .withDeltaQc(0.)
                .withConnectionType(LoadConnectionType.Y)
                .add();

        // bus 675
        Substation substation675 = network.newSubstation()
                .setId("S675")
                .setCountry(Country.FR)
                .add();

        VoltageLevel vl675 = substation675.newVoltageLevel()
                .setId("VL_675")
                .setNominalV(vBase)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(20)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus675 = vl675.getBusBreakerView().newBus()
                .setId("B675")
                .add();
        bus675.setV(vBase).setAngle(0.);

        AsymmetricalBranchConnector c675 = new AsymmetricalBranchConnector(BusVariableType.WYE,
                true, true, true, false, true);

        double p675 = 0.;
        double q675 = 0.;
        Load load675 = vl675.newLoad()
                .setId("LOAD_675")
                .setBus(bus675.getId())
                .setP0(p675)
                .setQ0(q675)
                .add();

        load675.newExtension(LoadAsymmetricalAdder.class)
                .withDeltaPa(0.485)
                .withDeltaQa(0.190)
                .withDeltaPb(0.068)
                .withDeltaQb(0.060)
                .withDeltaPc(0.)
                .withDeltaQc(0.)
                .withConnectionType(LoadConnectionType.Y)
                .add();

        // we split the load into 2 parts to test the addition of loads
        Load load675bis = vl675.newLoad()
                .setId("LOAD_675_bis")
                .setBus(bus675.getId())
                .setP0(p675)
                .setQ0(q675)
                .add();

        load675bis.newExtension(LoadAsymmetricalAdder.class)
                .withDeltaPa(0.)
                .withDeltaQa(0.)
                .withDeltaPb(0.)
                .withDeltaQb(0.)
                .withDeltaPc(0.290)
                .withDeltaQc(0.212)
                .withConnectionType(LoadConnectionType.Y)
                .add();

        Load compens675 = vl675.newLoad()
                .setId("COMPENS_675")
                .setBus(bus675.getId())
                .setP0(0.)
                .setQ0(-0.2)
                .newZipModel()
                    .setC0p(0.)
                    .setC1p(0.)
                    .setC2p(1.)
                    .setC0q(0.)
                    .setC1q(0.)
                    .setC2q(1.)
                    .add()
                .add();

        compens675.newExtension(LoadAsymmetricalAdder.class)
                .withDeltaPa(0.)
                .withDeltaQa(0.)
                .withDeltaPb(0.)
                .withDeltaQb(0.)
                .withDeltaPc(0.)
                .withDeltaQc(0.)
                .withConnectionType(LoadConnectionType.Y)
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

        // config 602 :
        // building of Yabc from given Y impedance matrix Zy
        ComplexMatrix zy602 = new ComplexMatrix(3, 3);
        zy602.set(1, 1, new Complex(0.7526, 1.1814));
        zy602.set(1, 2, new Complex(0.1580, 0.4236));
        zy602.set(1, 3, new Complex(0.1560, 0.5017));
        zy602.set(2, 1, new Complex(0.1580, 0.4236));
        zy602.set(2, 2, new Complex(0.7475, 1.1983));
        zy602.set(2, 3, new Complex(0.1535, 0.3849));
        zy602.set(3, 1, new Complex(0.1560, 0.5017));
        zy602.set(3, 2, new Complex(0.1535, 0.3849));
        zy602.set(3, 3, new Complex(0.7436, 1.2112));

        ComplexMatrix b602 = new ComplexMatrix(3, 3);

        b602.set(1, 1, new Complex(0, micro * 5.6990));
        b602.set(1, 2, new Complex(0, micro * -1.0817));
        b602.set(1, 3, new Complex(0, micro * -1.6905));
        b602.set(2, 1, new Complex(0, micro * -1.0817));
        b602.set(2, 2, new Complex(0, micro * 5.1795));
        b602.set(2, 3, new Complex(0, micro * -0.6588));
        b602.set(3, 1, new Complex(0, micro * -1.6905));
        b602.set(3, 2, new Complex(0, micro * -0.6588));
        b602.set(3, 3, new Complex(0, micro * 5.4246));

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

        // config 604 :
        // building of Yabc from given Y impedance matrix Zy
        ComplexMatrix zy604 = new ComplexMatrix(3, 3);
        zy604.set(1, 1, new Complex(1.3238, 1.3569));
        zy604.set(1, 2, new Complex(0., 0.));
        zy604.set(1, 3, new Complex(0.2066, 0.4591));
        zy604.set(2, 1, new Complex(0., 0.));
        zy604.set(2, 2, new Complex(0., 0.));
        zy604.set(2, 3, new Complex(0., 0.));
        zy604.set(3, 1, new Complex(0.2066, 0.4591));
        zy604.set(3, 2, new Complex(0., 0.));
        zy604.set(3, 3, new Complex(1.3294, 1.3471));

        ComplexMatrix b604 = new ComplexMatrix(3, 3);

        b604.set(1, 1, new Complex(0, micro * 4.6658));
        b604.set(1, 2, new Complex(0, micro * 0.));
        b604.set(1, 3, new Complex(0, micro * -0.8999));
        b604.set(2, 1, new Complex(0, micro * 0.));
        b604.set(2, 2, new Complex(0, micro * 0.));
        b604.set(2, 3, new Complex(0, micro * 0.));
        b604.set(3, 1, new Complex(0, micro * -0.8999));
        b604.set(3, 2, new Complex(0, micro * 0.));
        b604.set(3, 3, new Complex(0, micro * 4.7097));

        // config 605 :
        // building of Yabc from given Y impedance matrix Zy
        ComplexMatrix zy605 = new ComplexMatrix(3, 3);
        zy605.set(1, 1, new Complex(0., 0.));
        zy605.set(1, 2, new Complex(0., 0.));
        zy605.set(1, 3, new Complex(0., .0));
        zy605.set(2, 1, new Complex(0., 0.));
        zy605.set(2, 2, new Complex(0., 0.));
        zy605.set(2, 3, new Complex(0., 0.));
        zy605.set(3, 1, new Complex(0., 0.));
        zy605.set(3, 2, new Complex(0., 0.));
        zy605.set(3, 3, new Complex(1.3292, 1.3475));

        ComplexMatrix b605 = new ComplexMatrix(3, 3);

        b605.set(1, 1, new Complex(0, micro * 0.));
        b605.set(1, 2, new Complex(0, micro * 0.));
        b605.set(1, 3, new Complex(0, micro * 0.));
        b605.set(2, 1, new Complex(0, micro * 0.));
        b605.set(2, 2, new Complex(0, micro * 0.));
        b605.set(2, 3, new Complex(0, micro * 0.));
        b605.set(3, 1, new Complex(0, micro * 0.));
        b605.set(3, 2, new Complex(0, micro * 0.));
        b605.set(3, 3, new Complex(0, micro * 4.5193));

        // config 606 :
        // building of Yabc from given Y impedance matrix Zy
        ComplexMatrix zy606 = new ComplexMatrix(3, 3);
        zy606.set(1, 1, new Complex(0.7982, 0.4463));
        zy606.set(1, 2, new Complex(0.3192, 0.0328));
        zy606.set(1, 3, new Complex(0.2849, -0.0143));
        zy606.set(2, 1, new Complex(0.3192, 0.0328));
        zy606.set(2, 2, new Complex(0.7891, 0.4041));
        zy606.set(2, 3, new Complex(0.3192, 0.0328));
        zy606.set(3, 1, new Complex(0.2849, -0.0143));
        zy606.set(3, 2, new Complex(0.3192, 0.0328));
        zy606.set(3, 3, new Complex(0.7982, 0.4463));

        ComplexMatrix b606 = new ComplexMatrix(3, 3);

        b606.set(1, 1, new Complex(0, micro * 96.8897));
        b606.set(1, 2, new Complex(0, micro * 0.));
        b606.set(1, 3, new Complex(0, micro * 0.));
        b606.set(2, 1, new Complex(0, micro * 0.));
        b606.set(2, 2, new Complex(0, micro * 96.8897));
        b606.set(2, 3, new Complex(0, micro * 0.));
        b606.set(3, 1, new Complex(0, micro * 0.));
        b606.set(3, 2, new Complex(0, micro * 0.));
        b606.set(3, 3, new Complex(0, micro * 96.8897));

        // config 607 :
        // building of Yabc from given Y impedance matrix Zy
        ComplexMatrix zy607 = new ComplexMatrix(3, 3);
        zy607.set(1, 1, new Complex(1.3425, 0.5124));
        zy607.set(1, 2, new Complex(0., 0.));
        zy607.set(1, 3, new Complex(0., 0.));
        zy607.set(2, 1, new Complex(0., 0.));
        zy607.set(2, 2, new Complex(0., 0.));
        zy607.set(2, 3, new Complex(0., 0.));
        zy607.set(3, 1, new Complex(0., 0.));
        zy607.set(3, 2, new Complex(0., 0.));
        zy607.set(3, 3, new Complex(0., 0.));

        ComplexMatrix b607 = new ComplexMatrix(3, 3);

        b607.set(1, 1, new Complex(0, micro * 88.9912));
        b607.set(1, 2, new Complex(0, micro * 0.));
        b607.set(1, 3, new Complex(0, micro * 0.));
        b607.set(2, 1, new Complex(0, micro * 0.));
        b607.set(2, 2, new Complex(0, micro * 0.));
        b607.set(2, 3, new Complex(0, micro * 0.));
        b607.set(3, 1, new Complex(0, micro * 0.));
        b607.set(3, 2, new Complex(0, micro * 0.));
        b607.set(3, 3, new Complex(0, micro * 0.));

        double feetInMile = 5280;
        double ry = 0.3061;
        double xy = 0.627;
        double r0y = 0.7735;
        double x0y = 1.9373;
        double length650y632InFeet = 2000.;
        double length632y645InFeet = 500.;
        double length645y646InFeet = 300.;
        double length632y671InFeet = 2000.;
        double length671y684InFeet = 300.;
        double length684y611InFeet = 300.;
        double length684y652InFeet = 800.;
        double length671y680InFeet = 1000.;
        double length632y633InFeet = 500.;
        double length671y675InFeet = 500.;

        // line 650y632
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

        // modification of yabc601 to take into account the voltage regulator
        // [VRG60] = [rho] * [V650]
        ComplexMatrix rho = new ComplexMatrix(6, 6);
        rho.set(1, 1, new Complex(1.0625, 0.));
        rho.set(2, 2, new Complex(1.05, 0.));
        rho.set(3, 3, new Complex(1.0687, 0.));
        rho.set(4, 4, new Complex(1., 0.));
        rho.set(5, 5, new Complex(1., 0.));
        rho.set(6, 6, new Complex(1., 0.));
        ComplexMatrix yabc650y632 = LineAsymmetrical.getAdmittanceMatrixFromImpedanceAndBmatrix(zy601, b601, true, true, true, length650y632InFeet / feetInMile);
        DenseMatrix yabcRg60Real = rho.getRealCartesianMatrix().times(yabc650y632.getRealCartesianMatrix().times(rho.getRealCartesianMatrix()));
        ComplexMatrix yabcRg60 = ComplexMatrix.getComplexMatrixFromRealCartesian(yabcRg60Real);

        line650y632.newExtension(LineAsymmetricalAdder.class)
                .withYabc(yabcRg60.scale(yCoef))
                .withAsymConnector1(c650)
                .withAsymConnector2(c632)
                .add();

        line650y632.newExtension(LineFortescueAdder.class)
                .withOpenPhaseA(false)
                .withOpenPhaseB(false)
                .withOpenPhaseC(false)
                .withRz(r0y * length650y632InFeet / feetInMile)
                .withXz(x0y * length650y632InFeet / feetInMile)
                .add();

        // line 632y645
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

        ComplexMatrix yabc632y645 = LineAsymmetrical.getAdmittanceMatrixFromImpedanceAndBmatrix(zy603, b603, false, true, true, length632y645InFeet / feetInMile);
        line632y645.newExtension(LineAsymmetricalAdder.class)
                .withYabc(yabc632y645.scale(yCoef))
                .withAsymConnector1(c632)
                .withAsymConnector2(c645)
                .add();

        line632y645.newExtension(LineFortescueAdder.class)
                .withOpenPhaseA(false)
                .withOpenPhaseB(false)
                .withOpenPhaseC(false)
                .withRz(r0y * length632y645InFeet / feetInMile)
                .withXz(x0y * length632y645InFeet / feetInMile)
                .add();

        // line 645y646
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

        ComplexMatrix yabc645y646 = LineAsymmetrical.getAdmittanceMatrixFromImpedanceAndBmatrix(zy603, b603, false, true, true, length645y646InFeet / feetInMile);
        line645y646.newExtension(LineAsymmetricalAdder.class)
                .withYabc(yabc645y646.scale(yCoef))
                .withAsymConnector1(c645)
                .withAsymConnector2(c646)
                .add();
        line645y646.newExtension(LineFortescueAdder.class)
                .withOpenPhaseA(false)
                .withOpenPhaseB(false)
                .withOpenPhaseC(false)
                .add();

        line645y646.newExtension(LineFortescueAdder.class)
                .withRz(r0y * length645y646InFeet / feetInMile)
                .withXz(x0y * length645y646InFeet / feetInMile)
                .add();

        // line 632y671
        Line line632y671 = network.newLine()
                .setId("632y671")
                .setVoltageLevel1(vl632.getId())
                .setBus1(bus632.getId())
                .setConnectableBus1(bus632.getId())
                .setVoltageLevel2(vl671.getId())
                .setBus2(bus671.getId())
                .setConnectableBus2(bus671.getId())
                .setR(ry * length632y671InFeet / feetInMile)
                .setX(xy * length632y671InFeet / feetInMile)
                .setG1(0.0)
                .setB1(0.0)
                .setG2(0.0)
                .setB2(0.0)
                .add();

        ComplexMatrix yabc632y671 = LineAsymmetrical.getAdmittanceMatrixFromImpedanceAndBmatrix(zy601, b601, true, true, true, length632y671InFeet / feetInMile);
        line632y671.newExtension(LineAsymmetricalAdder.class)
                .withYabc(yabc632y671.scale(yCoef))
                .withAsymConnector1(c632)
                .withAsymConnector2(c671)
                .add();

        line632y671.newExtension(LineFortescueAdder.class)
                .withOpenPhaseA(false)
                .withOpenPhaseB(false)
                .withOpenPhaseC(false)
                .add();

        line632y671.newExtension(LineFortescueAdder.class)
                .withRz(r0y * length632y671InFeet / feetInMile)
                .withXz(x0y * length632y671InFeet / feetInMile)
                .add();

        // line 671y684
        Line line671y684 = network.newLine()
                .setId("671y684")
                .setVoltageLevel1(vl671.getId())
                .setBus1(bus671.getId())
                .setConnectableBus1(bus671.getId())
                .setVoltageLevel2(vl684.getId())
                .setBus2(bus684.getId())
                .setConnectableBus2(bus684.getId())
                .setR(ry * length671y684InFeet / feetInMile)
                .setX(xy * length671y684InFeet / feetInMile)
                .setG1(0.0)
                .setB1(0.0)
                .setG2(0.0)
                .setB2(0.0)
                .add();

        ComplexMatrix yabc671y684 = LineAsymmetrical.getAdmittanceMatrixFromImpedanceAndBmatrix(zy604, b604, true, false, true, length671y684InFeet / feetInMile);
        line671y684.newExtension(LineAsymmetricalAdder.class)
                .withYabc(yabc671y684.scale(yCoef))
                .withAsymConnector1(c671)
                .withAsymConnector2(c684)
                .add();

        line671y684.newExtension(LineFortescueAdder.class)
                .withOpenPhaseA(false)
                .withOpenPhaseB(false)
                .withOpenPhaseC(false)
                .add();

        line671y684.newExtension(LineFortescueAdder.class)
                .withRz(r0y * length671y684InFeet / feetInMile)
                .withXz(x0y * length671y684InFeet / feetInMile)
                .add();

        // line 684y611
        Line line684y611 = network.newLine()
                .setId("684y611")
                .setVoltageLevel1(vl684.getId())
                .setBus1(bus684.getId())
                .setConnectableBus1(bus684.getId())
                .setVoltageLevel2(vl611.getId())
                .setBus2(bus611.getId())
                .setConnectableBus2(bus611.getId())
                .setR(ry * length684y611InFeet / feetInMile)
                .setX(xy * length684y611InFeet / feetInMile)
                .setG1(0.0)
                .setB1(0.0)
                .setG2(0.0)
                .setB2(0.0)
                .add();

        ComplexMatrix yabc684y611 = LineAsymmetrical.getAdmittanceMatrixFromImpedanceAndBmatrix(zy605, b605, false, false, true, length684y611InFeet / feetInMile);
        line684y611.newExtension(LineAsymmetricalAdder.class)
                .withYabc(yabc684y611.scale(yCoef))
                .withAsymConnector1(c684)
                .withAsymConnector2(c611)
                .add();

        line684y611.newExtension(LineFortescueAdder.class)
                .withOpenPhaseA(false)
                .withOpenPhaseB(false)
                .withOpenPhaseC(false)
                .add();

        line684y611.newExtension(LineFortescueAdder.class)
                .withRz(r0y * length684y611InFeet / feetInMile)
                .withXz(x0y * length684y611InFeet / feetInMile)
                .add();

        // line 684y652
        Line line684y652 = network.newLine()
                .setId("684y652")
                .setVoltageLevel1(vl684.getId())
                .setBus1(bus684.getId())
                .setConnectableBus1(bus684.getId())
                .setVoltageLevel2(vl652.getId())
                .setBus2(bus652.getId())
                .setConnectableBus2(bus652.getId())
                .setR(ry * length684y652InFeet / feetInMile)
                .setX(xy * length684y652InFeet / feetInMile)
                .setG1(0.0)
                .setB1(0.0)
                .setG2(0.0)
                .setB2(0.0)
                .add();

        ComplexMatrix yabc684y652 = LineAsymmetrical.getAdmittanceMatrixFromImpedanceAndBmatrix(zy607, b607, true, false, false, length684y652InFeet / feetInMile);
        line684y652.newExtension(LineAsymmetricalAdder.class)
                .withYabc(yabc684y652.scale(yCoef))
                .withAsymConnector1(c684)
                .withAsymConnector2(c652)
                .add();

        line684y652.newExtension(LineFortescueAdder.class)
                .withOpenPhaseA(false)
                .withOpenPhaseB(false)
                .withOpenPhaseC(false)
                .add();

        line684y652.newExtension(LineFortescueAdder.class)
                .withRz(r0y * length684y652InFeet / feetInMile)
                .withXz(x0y * length684y652InFeet / feetInMile)
                .add();

        // line 671y680
        Line line671y680 = network.newLine()
                .setId("671y680")
                .setVoltageLevel1(vl671.getId())
                .setBus1(bus671.getId())
                .setConnectableBus1(bus671.getId())
                .setVoltageLevel2(vl680.getId())
                .setBus2(bus680.getId())
                .setConnectableBus2(bus680.getId())
                .setR(ry * length671y680InFeet / feetInMile)
                .setX(xy * length671y680InFeet / feetInMile)
                .setG1(0.0)
                .setB1(0.0)
                .setG2(0.0)
                .setB2(0.0)
                .add();

        ComplexMatrix yabc671y680 = LineAsymmetrical.getAdmittanceMatrixFromImpedanceAndBmatrix(zy601, b601, true, true, true, length671y680InFeet / feetInMile);
        line671y680.newExtension(LineAsymmetricalAdder.class)
                .withYabc(yabc671y680.scale(yCoef))
                .withAsymConnector1(c671)
                .withAsymConnector2(c680)
                .add();

        line671y680.newExtension(LineFortescueAdder.class)
                .withOpenPhaseA(false)
                .withOpenPhaseB(false)
                .withOpenPhaseC(false)
                .add();

        line671y680.newExtension(LineFortescueAdder.class)
                .withRz(r0y * length671y680InFeet / feetInMile)
                .withXz(x0y * length671y680InFeet / feetInMile)
                .add();

        // line 632y633
        Line line632y633 = network.newLine()
                .setId("632y633")
                .setVoltageLevel1(vl632.getId())
                .setBus1(bus632.getId())
                .setConnectableBus1(bus632.getId())
                .setVoltageLevel2(vl633.getId())
                .setBus2(bus633.getId())
                .setConnectableBus2(bus633.getId())
                .setR(ry * length632y633InFeet / feetInMile)
                .setX(xy * length632y633InFeet / feetInMile)
                .setG1(0.0)
                .setB1(0.0)
                .setG2(0.0)
                .setB2(0.0)
                .add();

        ComplexMatrix yabc632y633 = LineAsymmetrical.getAdmittanceMatrixFromImpedanceAndBmatrix(zy602, b602, true, true, true, length632y633InFeet / feetInMile);
        line632y633.newExtension(LineAsymmetricalAdder.class)
                .withYabc(yabc632y633.scale(yCoef))
                .withAsymConnector1(c632)
                .withAsymConnector2(c633)
                .add();

        line632y633.newExtension(LineFortescueAdder.class)
                .withOpenPhaseA(false)
                .withOpenPhaseB(false)
                .withOpenPhaseC(false)
                .add();

        line632y633.newExtension(LineFortescueAdder.class)
                .withRz(r0y * length632y633InFeet / feetInMile)
                .withXz(x0y * length632y633InFeet / feetInMile)
                .add();

        // line 671y675
        Line line671y675 = network.newLine()
                .setId("671y675")
                .setVoltageLevel1(vl671.getId())
                .setBus1(bus671.getId())
                .setConnectableBus1(bus671.getId())
                .setVoltageLevel2(vl675.getId())
                .setBus2(bus675.getId())
                .setConnectableBus2(bus675.getId())
                .setR(ry * length671y675InFeet / feetInMile)
                .setX(xy * length671y675InFeet / feetInMile)
                .setG1(0.0)
                .setB1(0.0)
                .setG2(0.0)
                .setB2(0.0)
                .add();

        ComplexMatrix yabc671y675 = LineAsymmetrical.getAdmittanceMatrixFromImpedanceAndBmatrix(zy606, b606, true, true, true, length671y675InFeet / feetInMile);
        line671y675.newExtension(LineAsymmetricalAdder.class)
                .withYabc(yabc671y675.scale(yCoef))
                .withAsymConnector1(c671)
                .withAsymConnector2(c675)
                .add();

        line671y675.newExtension(LineFortescueAdder.class)
                .withOpenPhaseA(false)
                .withOpenPhaseB(false)
                .withOpenPhaseC(false)
                .add();

        line671y675.newExtension(LineFortescueAdder.class)
                .withRz(r0y * length671y675InFeet / feetInMile)
                .withXz(x0y * length671y675InFeet / feetInMile)
                .add();

        // tfo 633y634
        double ratedU633 = vBase;
        double ratedU634 = vBaseLow;
        double sBase = 0.5;

        double rTpc = 1.1;
        double xTpc = 2.;
        double zBase = ratedU634 * ratedU633 / sBase;
        double rT = rTpc * zBase / 100; // TODO : sBase = 2. for single phase and 6. for three phase
        double xT = xTpc * zBase / 100;

        var t633y634 = substation633.newTwoWindingsTransformer()
                .setId("t633y634")
                .setVoltageLevel1(vl633.getId())
                .setBus1(bus633.getId())
                .setConnectableBus1(bus633.getId())
                .setRatedU1(ratedU633)
                .setVoltageLevel2(vl634.getId())
                .setBus2(bus634.getId())
                .setConnectableBus2(bus634.getId())
                .setRatedU2(ratedU634) // TODO : check OK for Fortescue modeling
                .setR(rT)
                .setX(xT)
                .setG(0.0D)
                .setB(0.0D)
                .setRatedS(sBase)
                .add();

        t633y634.newExtension(TwoWindingsTransformerFortescueAdder.class)
                .withRz(rT) // TODO : check that again
                .withXz(xT) // TODO : check that
                .withConnectionType1(WindingConnectionType.Y_GROUNDED)
                .withConnectionType2(WindingConnectionType.Y_GROUNDED)
                .withGroundingX1(0.0000)
                .withGroundingX2(0.0000)
                .withFreeFluxes(true)
                .add();

        Complex zPhase = new Complex(rTpc, xTpc).multiply(zBase / 3. / 100.);
        Complex yPhase = new Complex(0., 0.);

        t633y634.newExtension(Tfo3PhasesAdder.class)
                .withIsOpenPhaseA1(false)
                .withIsOpenPhaseB1(false)
                .withIsOpenPhaseC1(false)
                .withYa(Asym4nodesFeederTest.buildSinglePhaseAdmittanceMatrix(zPhase, yPhase, yPhase))
                .withYb(Asym4nodesFeederTest.buildSinglePhaseAdmittanceMatrix(zPhase, yPhase, yPhase))
                .withYc(Asym4nodesFeederTest.buildSinglePhaseAdmittanceMatrix(zPhase, yPhase, yPhase))
                .add();

        return network;
    }
}
