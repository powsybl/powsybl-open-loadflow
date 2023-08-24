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
import com.powsybl.openloadflow.network.extensions.iidm.LoadAsymmetrical2Adder;
import com.powsybl.openloadflow.network.extensions.iidm.*;
import com.powsybl.openloadflow.network.extensions.iidm.LoadType;
import com.powsybl.openloadflow.util.ComplexMatrix;
import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.complex.ComplexUtils;
import org.joda.time.DateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.powsybl.openloadflow.util.LoadFlowAssert.assertVoltageEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class Asym34BusFeederTest {

    private Network network;
    private Bus bus860;
    private Bus bus850;
    private Bus bus810;

    private LoadFlow.Runner loadFlowRunner;
    private LoadFlowParameters parameters;

    @BeforeEach
    void setUp() {
        network = ieee34LoadFeeder();

        bus860 = network.getBusBreakerView().getBus("B860");
        bus850 = network.getBusBreakerView().getBus("B850");
        bus810 = network.getBusBreakerView().getBus("B810");

        loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        parameters = new LoadFlowParameters().setNoGeneratorReactiveLimits(true)
                .setDistributedSlack(false);
    }

    @Test
    void ieee34LoadTest() {

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

        assertVoltageEquals(25.74576766760779, bus860);
        assertVoltageEquals(25.480362952505075, bus850);

    }

    @Test
    void ieee34LoadAbcPowerTest() {

        // addition of constant loads at busses
        Load load810Power = network.getVoltageLevel("VL_810").newLoad()
                .setId("LOAD_810_POWER")
                .setBus(bus810.getId())
                .setP0(0.)
                .setQ0(0.)
                .add();

        load810Power.newExtension(LoadAsymmetricalAdder.class)
                .withDeltaPa(0.)
                .withDeltaQa(0.)
                .withDeltaPb(0.1)
                .withDeltaQb(0.15)
                .withDeltaPc(0.)
                .withDeltaQc(0.)
                .withConnectionType(LoadConnectionType.Y)
                .add();

        load810Power.newExtension(LoadAsymmetrical2Adder.class)
                .withLoadType(LoadType.CONSTANT_POWER)
                .add();

        Load load810Impedant = network.getVoltageLevel("VL_810").newLoad()
                .setId("LOAD_810_IMPEDANT")
                .setBus(bus810.getId())
                .setP0(0.)
                .setQ0(0.)
                .add();

        load810Impedant.newExtension(LoadAsymmetricalAdder.class)
                .withDeltaPa(0.)
                .withDeltaQa(0.)
                .withDeltaPb(0.2)
                .withDeltaQb(0.25)
                .withDeltaPc(0.)
                .withDeltaQc(0.)
                .withConnectionType(LoadConnectionType.Y)
                .add();

        load810Impedant.newExtension(LoadAsymmetrical2Adder.class)
                .withLoadType(LoadType.CONSTANT_IMPEDANCE)
                .add();

        Load load810Current = network.getVoltageLevel("VL_810").newLoad()
                .setId("LOAD_810_CURRENT")
                .setBus(bus810.getId())
                .setP0(0.)
                .setQ0(0.)
                .add();

        load810Current.newExtension(LoadAsymmetricalAdder.class)
                .withDeltaPa(0.)
                .withDeltaQa(0.)
                .withDeltaPb(0.02)
                .withDeltaQb(0.01)
                .withDeltaPc(0.)
                .withDeltaQc(0.)
                .withConnectionType(LoadConnectionType.Y)
                .add();

        load810Current.newExtension(LoadAsymmetrical2Adder.class)
                .withLoadType(LoadType.CONSTANT_CURRENT)
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

        assertVoltageEquals(25.520692860319357, bus860);
        assertVoltageEquals(25.27020825194182, bus850);

    }

    public static Network ieee34LoadFeeder() {
        Network network = Network.create("13n", "test");
        network.setCaseDate(DateTime.parse("2018-03-05T13:30:30.486+01:00"));

        double vBase = 24.9;
        double vBaseLow = 4.16;

        double yCoef = 1. / 3.;

        Complex zz = new Complex(0.0001, 0.0001); // 0.0001 , 0.001
        Complex zn = new Complex(0.0001, 0.0001); // 0.001 , 0.01

        // Bus 800
        Substation substation800 = network.newSubstation()
                .setId("S800")
                .setCountry(Country.FR)
                .add();

        VoltageLevel vl800 = substation800.newVoltageLevel()
                .setId("VL_800")
                .setNominalV(vBase)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(2. * vBase)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus800 = vl800.getBusBreakerView().newBus()
                .setId("B800")
                .add();
        bus800.setV(vBase).setAngle(0.);

        bus800.newExtension(BusAsymmetricalAdder.class)
                .withBusVariableType(BusVariableType.WYE)
                .add();

        Generator gen800 = vl800.newGenerator()
                .setId("G800")
                .setBus(bus800.getId())
                .setMinP(-100.0)
                .setMaxP(200)
                .setTargetP(0)
                .setTargetV(vBase * 1.05)
                .setVoltageRegulatorOn(true)
                .add();

        gen800.newExtension(GeneratorFortescueAdder.class)
                .withRz(zz.getReal())
                .withXz(zz.getImaginary())
                .withRn(zn.getReal())
                .withXn(zn.getImaginary())
                .add();

        // Bus 802
        Substation substation802 = network.newSubstation()
                .setId("S802")
                .setCountry(Country.FR)
                .add();

        VoltageLevel vl802 = substation802.newVoltageLevel()
                .setId("VL_802")
                .setNominalV(vBase)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(2. * vBase)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus802 = vl802.getBusBreakerView().newBus()
                .setId("B802")
                .add();
        bus802.setV(vBase).setAngle(0.);

        bus802.newExtension(BusAsymmetricalAdder.class)
                .withBusVariableType(BusVariableType.WYE)
                .withPositiveSequenceAsCurrent(true)
                .add();

        double p802 = 0.;
        double q802 = 0.;
        Load load802 = vl802.newLoad()
                .setId("LOAD_802")
                .setBus(bus802.getId())
                .setP0(p802)
                .setQ0(q802)
                .add();

        load802.newExtension(LoadAsymmetricalAdder.class)
                .withDeltaPa(0.)
                .withDeltaQa(0.)
                .withDeltaPb(0.030)
                .withDeltaQb(0.015)
                .withDeltaPc(0.025)
                .withDeltaQc(0.014)
                .withConnectionType(LoadConnectionType.Y)
                .add();

        load802.newExtension(LoadAsymmetrical2Adder.class)
                .add();

        // Bus 806
        Substation substation806 = network.newSubstation()
                .setId("S806")
                .setCountry(Country.FR)
                .add();

        VoltageLevel vl806 = substation806.newVoltageLevel()
                .setId("VL_806")
                .setNominalV(vBase)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(2. * vBase)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus806 = vl806.getBusBreakerView().newBus()
                .setId("B806")
                .add();
        bus806.setV(vBase).setAngle(0.);

        bus806.newExtension(BusAsymmetricalAdder.class)
                .withBusVariableType(BusVariableType.WYE)
                .withPositiveSequenceAsCurrent(true)
                .add();

        // Bus 808
        Substation substation808 = network.newSubstation()
                .setId("S808")
                .setCountry(Country.FR)
                .add();

        VoltageLevel vl808 = substation808.newVoltageLevel()
                .setId("VL_808")
                .setNominalV(vBase)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(2. * vBase)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus808 = vl808.getBusBreakerView().newBus()
                .setId("B808")
                .add();
        bus808.setV(vBase).setAngle(0.);

        bus808.newExtension(BusAsymmetricalAdder.class)
                .withBusVariableType(BusVariableType.WYE)
                .withPositiveSequenceAsCurrent(true)
                .withFortescueRepresentation(false) // if not, then load other tham power not handled
                .add();

        double p808 = 0.;
        double q808 = 0.;
        Load load808 = vl808.newLoad()
                .setId("LOAD_808")
                .setBus(bus808.getId())
                .setP0(p808)
                .setQ0(q808)
                .add();

        load808.newExtension(LoadAsymmetricalAdder.class)
                .withDeltaPa(0.)
                .withDeltaQa(0.)
                .withDeltaPb(0.016)
                .withDeltaQb(0.008)
                .withDeltaPc(0.0)
                .withDeltaQc(0.0)
                .withConnectionType(LoadConnectionType.Y)
                .add();

        load808.newExtension(LoadAsymmetrical2Adder.class)
                .withLoadType(LoadType.CONSTANT_CURRENT)
                .add();

        // Bus 810
        Substation substation810 = network.newSubstation()
                .setId("S810")
                .setCountry(Country.FR)
                .add();

        VoltageLevel vl810 = substation810.newVoltageLevel()
                .setId("VL_810")
                .setNominalV(vBase)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(2. * vBase)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus810 = vl810.getBusBreakerView().newBus()
                .setId("B810")
                .add();
        bus810.setV(vBase).setAngle(0.);

        bus810.newExtension(BusAsymmetricalAdder.class)
                .withBusVariableType(BusVariableType.WYE)
                .withPositiveSequenceAsCurrent(true)
                .withFortescueRepresentation(false) // if not, then load other tham power not handled
                .withHasPhaseA(false)
                .withHasPhaseC(false)
                .add();

        // Bus 812
        Substation substation812 = network.newSubstation()
                .setId("S812")
                .setCountry(Country.FR)
                .add();

        VoltageLevel vl812 = substation812.newVoltageLevel()
                .setId("VL_812")
                .setNominalV(vBase)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(2. * vBase)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus812 = vl812.getBusBreakerView().newBus()
                .setId("B812")
                .add();
        bus812.setV(vBase).setAngle(0.);

        bus812.newExtension(BusAsymmetricalAdder.class)
                .withBusVariableType(BusVariableType.WYE)
                .withPositiveSequenceAsCurrent(true)
                .add();

        // Bus 814
        Substation substation814 = network.newSubstation()
                .setId("S814")
                .setCountry(Country.FR)
                .add();

        VoltageLevel vl814 = substation814.newVoltageLevel()
                .setId("VL_814")
                .setNominalV(vBase)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(2. * vBase)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus814 = vl814.getBusBreakerView().newBus()
                .setId("B814")
                .add();
        bus814.setV(vBase).setAngle(0.);

        bus814.newExtension(BusAsymmetricalAdder.class)
                .withBusVariableType(BusVariableType.WYE)
                .withPositiveSequenceAsCurrent(true)
                .add();

        // Bus 850
        Substation substation850 = network.newSubstation()
                .setId("S850")
                .setCountry(Country.FR)
                .add();

        VoltageLevel vl850 = substation850.newVoltageLevel()
                .setId("VL_850")
                .setNominalV(vBase)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(2. * vBase)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus850 = vl850.getBusBreakerView().newBus()
                .setId("B850")
                .add();
        bus850.setV(vBase).setAngle(0.);

        bus850.newExtension(BusAsymmetricalAdder.class)
                .withBusVariableType(BusVariableType.WYE)
                .withPositiveSequenceAsCurrent(true)
                .add();

        // Bus 816
        Substation substation816 = network.newSubstation()
                .setId("S816")
                .setCountry(Country.FR)
                .add();

        VoltageLevel vl816 = substation816.newVoltageLevel()
                .setId("VL_816")
                .setNominalV(vBase)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(2. * vBase)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus816 = vl816.getBusBreakerView().newBus()
                .setId("B816")
                .add();
        bus816.setV(vBase).setAngle(0.);

        bus816.newExtension(BusAsymmetricalAdder.class)
                .withBusVariableType(BusVariableType.WYE)
                .withPositiveSequenceAsCurrent(true)
                .withFortescueRepresentation(false)
                .add();

        double p816 = 0.;
        double q816 = 0.;
        Load load816 = vl816.newLoad()
                .setId("LOAD_816")
                .setBus(bus816.getId())
                .setP0(p816)
                .setQ0(q816)
                .add();

        load816.newExtension(LoadAsymmetricalAdder.class)
                .withDeltaPa(0.)
                .withDeltaQa(0.)
                .withDeltaPb(0.005)
                .withDeltaQb(0.002)
                .withDeltaPc(0.0)
                .withDeltaQc(0.0)
                .withConnectionType(LoadConnectionType.DELTA)
                .add();

        load816.newExtension(LoadAsymmetrical2Adder.class)
                .withLoadType(LoadType.CONSTANT_CURRENT)
                .add();

        // Bus 818
        Substation substation818 = network.newSubstation()
                .setId("S818")
                .setCountry(Country.FR)
                .add();

        VoltageLevel vl818 = substation818.newVoltageLevel()
                .setId("VL_818")
                .setNominalV(vBase)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(2. * vBase)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus818 = vl818.getBusBreakerView().newBus()
                .setId("B818")
                .add();
        bus818.setV(vBase).setAngle(0.);

        bus818.newExtension(BusAsymmetricalAdder.class)
                .withBusVariableType(BusVariableType.WYE)
                .withPositiveSequenceAsCurrent(true)
                .withFortescueRepresentation(false) // if not, then load other than power not handled
                .withHasPhaseB(false)
                .withHasPhaseC(false)
                .add();

        double p818 = 0.;
        double q818 = 0.;
        Load load818 = vl818.newLoad()
                .setId("LOAD_818")
                .setBus(bus818.getId())
                .setP0(p818)
                .setQ0(q818)
                .add();

        load818.newExtension(LoadAsymmetricalAdder.class)
                .withDeltaPa(0.034)
                .withDeltaQa(0.017)
                .withDeltaPb(0.0)
                .withDeltaQb(0.0)
                .withDeltaPc(0.0)
                .withDeltaQc(0.0)
                .withConnectionType(LoadConnectionType.Y)
                .add();

        load818.newExtension(LoadAsymmetrical2Adder.class)
                .withLoadType(LoadType.CONSTANT_IMPEDANCE)
                .add();

        // Bus 820
        Substation substation820 = network.newSubstation()
                .setId("S820")
                .setCountry(Country.FR)
                .add();

        VoltageLevel vl820 = substation820.newVoltageLevel()
                .setId("VL_820")
                .setNominalV(vBase)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(2. * vBase)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus820 = vl820.getBusBreakerView().newBus()
                .setId("B820")
                .add();
        bus820.setV(vBase).setAngle(0.);

        bus820.newExtension(BusAsymmetricalAdder.class)
                .withBusVariableType(BusVariableType.WYE)
                .withPositiveSequenceAsCurrent(true)
                .withFortescueRepresentation(false) // if not, then load other than power not handled
                .withHasPhaseB(false)
                .withHasPhaseC(false)
                .add();

        double p820 = 0.;
        double q820 = 0.;
        Load load820 = vl820.newLoad()
                .setId("LOAD_820")
                .setBus(bus820.getId())
                .setP0(p820)
                .setQ0(q820)
                .add();

        load820.newExtension(LoadAsymmetricalAdder.class)
                .withDeltaPa(0.135)
                .withDeltaQa(0.070)
                .withDeltaPb(0.0)
                .withDeltaQb(0.0)
                .withDeltaPc(0.0)
                .withDeltaQc(0.0)
                .withConnectionType(LoadConnectionType.Y)
                .add();

        load820.newExtension(LoadAsymmetrical2Adder.class)
                .withLoadType(LoadType.CONSTANT_POWER)
                .add();

        // Bus 822
        Substation substation822 = network.newSubstation()
                .setId("S822")
                .setCountry(Country.FR)
                .add();

        VoltageLevel vl822 = substation822.newVoltageLevel()
                .setId("VL_822")
                .setNominalV(vBase)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(2. * vBase)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus822 = vl822.getBusBreakerView().newBus()
                .setId("B822")
                .add();
        bus822.setV(vBase).setAngle(0.);

        bus822.newExtension(BusAsymmetricalAdder.class)
                .withBusVariableType(BusVariableType.WYE)
                .withPositiveSequenceAsCurrent(true)
                .withFortescueRepresentation(false) // if not, then load other than power not handled
                .withHasPhaseB(false)
                .withHasPhaseC(false)
                .add();

        // Bus 824
        Substation substation824 = network.newSubstation()
                .setId("S824")
                .setCountry(Country.FR)
                .add();

        VoltageLevel vl824 = substation824.newVoltageLevel()
                .setId("VL_824")
                .setNominalV(vBase)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(2. * vBase)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus824 = vl824.getBusBreakerView().newBus()
                .setId("B824")
                .add();
        bus824.setV(vBase).setAngle(0.);

        bus824.newExtension(BusAsymmetricalAdder.class)
                .withBusVariableType(BusVariableType.WYE)
                .withPositiveSequenceAsCurrent(true)
                .withFortescueRepresentation(false)
                .add();

        double p824 = 0.;
        double q824 = 0.;
        Load load824 = vl824.newLoad()
                .setId("LOAD_824")
                .setBus(bus824.getId())
                .setP0(p824)
                .setQ0(q824)
                .add();

        load824.newExtension(LoadAsymmetricalAdder.class)
                .withDeltaPa(0.)
                .withDeltaQa(0.)
                .withDeltaPb(0.04)
                .withDeltaQb(0.02)
                .withDeltaPc(0.004) // TODO :  adapt equivalent 824-828 distributed load Y-PQ
                .withDeltaQc(0.002)
                .withConnectionType(LoadConnectionType.Y)
                .add();

        load824.newExtension(LoadAsymmetrical2Adder.class)
                .withLoadType(LoadType.CONSTANT_CURRENT)
                .add();

        // Bus 826
        Substation substation826 = network.newSubstation()
                .setId("S826")
                .setCountry(Country.FR)
                .add();

        VoltageLevel vl826 = substation826.newVoltageLevel()
                .setId("VL_826")
                .setNominalV(vBase)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(2. * vBase)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus826 = vl826.getBusBreakerView().newBus()
                .setId("B826")
                .add();
        bus826.setV(vBase).setAngle(0.);

        bus826.newExtension(BusAsymmetricalAdder.class)
                .withBusVariableType(BusVariableType.WYE)
                .withPositiveSequenceAsCurrent(true)
                .withFortescueRepresentation(false) // if not, then load other tham power not handled
                .withHasPhaseA(false)
                .withHasPhaseC(false)
                .add();

        // Bus 828
        Substation substation828 = network.newSubstation()
                .setId("S828")
                .setCountry(Country.FR)
                .add();

        VoltageLevel vl828 = substation828.newVoltageLevel()
                .setId("VL_828")
                .setNominalV(vBase)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(2. * vBase)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus828 = vl828.getBusBreakerView().newBus()
                .setId("B828")
                .add();
        bus828.setV(vBase).setAngle(0.);

        bus828.newExtension(BusAsymmetricalAdder.class)
                .withBusVariableType(BusVariableType.WYE)
                .withPositiveSequenceAsCurrent(true)
                .withFortescueRepresentation(false)
                .add();

        double p828 = 0.;
        double q828 = 0.;
        Load load828 = vl828.newLoad()
                .setId("LOAD_828")
                .setBus(bus828.getId())
                .setP0(p828)
                .setQ0(q828)
                .add();

        load828.newExtension(LoadAsymmetricalAdder.class)
                .withDeltaPa(0.007)
                .withDeltaQa(0.003)
                .withDeltaPb(0.)
                .withDeltaQb(0.)
                .withDeltaPc(0.)
                .withDeltaQc(0.)
                .withConnectionType(LoadConnectionType.Y)
                .add();

        load828.newExtension(LoadAsymmetrical2Adder.class)
                .withLoadType(LoadType.CONSTANT_POWER)
                .add();

        // Bus 830
        Substation substation830 = network.newSubstation()
                .setId("S830")
                .setCountry(Country.FR)
                .add();

        VoltageLevel vl830 = substation830.newVoltageLevel()
                .setId("VL_830")
                .setNominalV(vBase)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(2. * vBase)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus830 = vl830.getBusBreakerView().newBus()
                .setId("B830")
                .add();
        bus830.setV(vBase).setAngle(0.);

        bus830.newExtension(BusAsymmetricalAdder.class)
                .withBusVariableType(BusVariableType.WYE)
                .withPositiveSequenceAsCurrent(true)
                .withFortescueRepresentation(false)
                .add();

        double p830 = 0.;
        double q830 = 0.;
        Load load830 = vl830.newLoad()
                .setId("LOAD_830")
                .setBus(bus830.getId())
                .setP0(p830)
                .setQ0(q830)
                .add();

        load830.newExtension(LoadAsymmetricalAdder.class)
                .withDeltaPa(0.010)
                .withDeltaQa(0.005)
                .withDeltaPb(0.01)
                .withDeltaQb(0.005)
                .withDeltaPc(0.025)
                .withDeltaQc(0.01)
                .withConnectionType(LoadConnectionType.DELTA)
                .add();

        load830.newExtension(LoadAsymmetrical2Adder.class)
                .withLoadType(LoadType.CONSTANT_IMPEDANCE)
                .add();

        // Bus 854
        Substation substation854 = network.newSubstation()
                .setId("S854")
                .setCountry(Country.FR)
                .add();

        VoltageLevel vl854 = substation854.newVoltageLevel()
                .setId("VL_854")
                .setNominalV(vBase)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(2. * vBase)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus854 = vl854.getBusBreakerView().newBus()
                .setId("B854")
                .add();
        bus854.setV(vBase).setAngle(0.);

        bus854.newExtension(BusAsymmetricalAdder.class)
                .withBusVariableType(BusVariableType.WYE)
                .withPositiveSequenceAsCurrent(true)
                .withFortescueRepresentation(false)
                .add();

        double p854 = 0.;
        double q854 = 0.;
        Load load854 = vl854.newLoad()
                .setId("LOAD_854")
                .setBus(bus854.getId())
                .setP0(p854)
                .setQ0(q854)
                .add();

        load854.newExtension(LoadAsymmetricalAdder.class)
                .withDeltaPa(0.)
                .withDeltaQa(0.)
                .withDeltaPb(0.004)
                .withDeltaQb(0.002)
                .withDeltaPc(0.)
                .withDeltaQc(0.)
                .withConnectionType(LoadConnectionType.Y)
                .add();

        load854.newExtension(LoadAsymmetrical2Adder.class)
                .withLoadType(LoadType.CONSTANT_POWER)
                .add();

        // Bus 856
        Substation substation856 = network.newSubstation()
                .setId("S856")
                .setCountry(Country.FR)
                .add();

        VoltageLevel vl856 = substation856.newVoltageLevel()
                .setId("VL_856")
                .setNominalV(vBase)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(2. * vBase)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus856 = vl856.getBusBreakerView().newBus()
                .setId("B856")
                .add();
        bus856.setV(vBase).setAngle(0.);

        bus856.newExtension(BusAsymmetricalAdder.class)
                .withBusVariableType(BusVariableType.WYE)
                .withPositiveSequenceAsCurrent(true)
                .withFortescueRepresentation(false) // if not, then load other tham power not handled
                .withHasPhaseA(false)
                .withHasPhaseC(false)
                .add();

        // Bus 852
        Substation substation852 = network.newSubstation()
                .setId("S852")
                .setCountry(Country.FR)
                .add();

        VoltageLevel vl852 = substation852.newVoltageLevel()
                .setId("VL_852")
                .setNominalV(vBase)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(2. * vBase)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus852 = vl852.getBusBreakerView().newBus()
                .setId("B852")
                .add();
        bus852.setV(vBase).setAngle(0.);

        bus852.newExtension(BusAsymmetricalAdder.class)
                .withBusVariableType(BusVariableType.WYE)
                .withPositiveSequenceAsCurrent(true)
                .add();

        // Bus 832
        Substation substation832 = network.newSubstation()
                .setId("S832")
                .setCountry(Country.FR)
                .add();

        VoltageLevel vl832 = substation832.newVoltageLevel()
                .setId("VL_832")
                .setNominalV(vBase)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(2. * vBase)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus832 = vl832.getBusBreakerView().newBus()
                .setId("B832")
                .add();
        bus832.setV(vBase).setAngle(0.);

        bus832.newExtension(BusAsymmetricalAdder.class)
                .withBusVariableType(BusVariableType.WYE)
                .withPositiveSequenceAsCurrent(true)
                .withFortescueRepresentation(true)
                .add();

        double p832 = 0.;
        double q832 = 0.;
        Load load832 = vl832.newLoad()
                .setId("LOAD_832")
                .setBus(bus832.getId())
                .setP0(p832)
                .setQ0(q832)
                .add();

        load832.newExtension(LoadAsymmetricalAdder.class)
                .withDeltaPa(0.007)
                .withDeltaQa(0.003)
                .withDeltaPb(0.002)
                .withDeltaQb(0.001)
                .withDeltaPc(0.006)
                .withDeltaQc(0.003)
                .withConnectionType(LoadConnectionType.DELTA)
                .add();

        load832.newExtension(LoadAsymmetrical2Adder.class)
                .withLoadType(LoadType.CONSTANT_POWER) // modified because with the transformer the bus must be fortescue and CONSTANT IMPEDANCE DELTA not compatible
                .add();

        // Bus 888
        VoltageLevel vl888 = substation832.newVoltageLevel()
                .setId("VL_888")
                .setNominalV(vBaseLow)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(2. * vBaseLow)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus888 = vl888.getBusBreakerView().newBus()
                .setId("B888")
                .add();
        bus888.setV(vBase).setAngle(0.);

        bus888.newExtension(BusAsymmetricalAdder.class)
                .withBusVariableType(BusVariableType.WYE)
                .withPositiveSequenceAsCurrent(true)
                .withFortescueRepresentation(true)
                .add();

        // Bus 890
        Substation substation890 = network.newSubstation()
                .setId("S890")
                .setCountry(Country.FR)
                .add();

        VoltageLevel vl890 = substation890.newVoltageLevel()
                .setId("VL_890")
                .setNominalV(vBaseLow)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(2. * vBaseLow)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus890 = vl890.getBusBreakerView().newBus()
                .setId("B890")
                .add();
        bus890.setV(vBase).setAngle(0.);

        bus890.newExtension(BusAsymmetricalAdder.class)
                .withBusVariableType(BusVariableType.WYE)
                .withPositiveSequenceAsCurrent(true)
                .withFortescueRepresentation(false)
                .add();

        double p890 = 0.;
        double q890 = 0.;
        Load load890 = vl890.newLoad()
                .setId("LOAD_890")
                .setBus(bus890.getId())
                .setP0(p890)
                .setQ0(q890)
                .add();

        load890.newExtension(LoadAsymmetricalAdder.class)
                .withDeltaPa(0.15)
                .withDeltaQa(0.075)
                .withDeltaPb(0.15)
                .withDeltaQb(0.075)
                .withDeltaPc(0.15)
                .withDeltaQc(0.075)
                .withConnectionType(LoadConnectionType.DELTA)
                .add();

        load890.newExtension(LoadAsymmetrical2Adder.class)
                .withLoadType(LoadType.CONSTANT_CURRENT)
                .add();

        // Bus 858
        Substation substation858 = network.newSubstation()
                .setId("S858")
                .setCountry(Country.FR)
                .add();

        VoltageLevel vl858 = substation858.newVoltageLevel()
                .setId("VL_858")
                .setNominalV(vBase)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(2. * vBase)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus858 = vl858.getBusBreakerView().newBus()
                .setId("B858")
                .add();
        bus858.setV(vBase).setAngle(0.);

        bus858.newExtension(BusAsymmetricalAdder.class)
                .withBusVariableType(BusVariableType.WYE)
                .withPositiveSequenceAsCurrent(true)
                .withFortescueRepresentation(true)
                .add();

        double p858 = 0.;
        double q858 = 0.;
        Load load858 = vl858.newLoad()
                .setId("LOAD_858")
                .setBus(bus858.getId())
                .setP0(p858)
                .setQ0(q858)
                .add();

        load858.newExtension(LoadAsymmetricalAdder.class)
                .withDeltaPa(0.004)
                .withDeltaQa(0.002)
                .withDeltaPb(0.015)
                .withDeltaQb(0.008)
                .withDeltaPc(0.013)
                .withDeltaQc(0.007)
                .withConnectionType(LoadConnectionType.DELTA)
                .add();

        load858.newExtension(LoadAsymmetrical2Adder.class)
                .withLoadType(LoadType.CONSTANT_POWER)
                .add();

        // Bus 864
        Substation substation864 = network.newSubstation()
                .setId("S864")
                .setCountry(Country.FR)
                .add();

        VoltageLevel vl864 = substation864.newVoltageLevel()
                .setId("VL_864")
                .setNominalV(vBase)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(2. * vBase)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus864 = vl864.getBusBreakerView().newBus()
                .setId("B864")
                .add();
        bus864.setV(vBase).setAngle(0.);

        bus864.newExtension(BusAsymmetricalAdder.class)
                .withBusVariableType(BusVariableType.WYE)
                .withPositiveSequenceAsCurrent(true)
                .withFortescueRepresentation(false) // if not, then load other than power not handled
                .withHasPhaseB(false)
                .withHasPhaseC(false)
                .add();

        // Bus 834
        Substation substation834 = network.newSubstation()
                .setId("S834")
                .setCountry(Country.FR)
                .add();

        VoltageLevel vl834 = substation834.newVoltageLevel()
                .setId("VL_834")
                .setNominalV(vBase)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(2. * vBase)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus834 = vl834.getBusBreakerView().newBus()
                .setId("B834")
                .add();
        bus834.setV(vBase).setAngle(0.);

        bus834.newExtension(BusAsymmetricalAdder.class)
                .withBusVariableType(BusVariableType.WYE)
                .withPositiveSequenceAsCurrent(true)
                .withFortescueRepresentation(false)
                .add();

        double p834 = 0.;
        double q834 = 0.;
        Load load834 = vl834.newLoad()
                .setId("LOAD_834")
                .setBus(bus834.getId())
                .setP0(p834)
                .setQ0(q834)
                .add();

        load834.newExtension(LoadAsymmetricalAdder.class)
                .withDeltaPa(0.016)
                .withDeltaQa(0.008)
                .withDeltaPb(0.020)
                .withDeltaQb(0.010)
                .withDeltaPc(0.110)
                .withDeltaQc(0.055)
                .withConnectionType(LoadConnectionType.DELTA)
                .add();

        load834.newExtension(LoadAsymmetrical2Adder.class)
                .withLoadType(LoadType.CONSTANT_IMPEDANCE)
                .add();

        // Bus 842
        Substation substation842 = network.newSubstation()
                .setId("S842")
                .setCountry(Country.FR)
                .add();

        VoltageLevel vl842 = substation842.newVoltageLevel()
                .setId("VL_842")
                .setNominalV(vBase)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(2. * vBase)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus842 = vl842.getBusBreakerView().newBus()
                .setId("B842")
                .add();
        bus842.setV(vBase).setAngle(0.);

        bus842.newExtension(BusAsymmetricalAdder.class)
                .withBusVariableType(BusVariableType.WYE)
                .withPositiveSequenceAsCurrent(true)
                .add();

        double p842 = 0.;
        double q842 = 0.;
        Load load842 = vl842.newLoad()
                .setId("LOAD_842")
                .setBus(bus842.getId())
                .setP0(p842)
                .setQ0(q842)
                .add();

        load842.newExtension(LoadAsymmetricalAdder.class)
                .withDeltaPa(0.009)
                .withDeltaQa(0.005)
                .withDeltaPb(0.)
                .withDeltaQb(0.)
                .withDeltaPc(0.)
                .withDeltaQc(0.)
                .withConnectionType(LoadConnectionType.Y)
                .add();

        load842.newExtension(LoadAsymmetrical2Adder.class)
                .add();

        // Bus 844
        Substation substation844 = network.newSubstation()
                .setId("S844")
                .setCountry(Country.FR)
                .add();

        VoltageLevel vl844 = substation844.newVoltageLevel()
                .setId("VL_844")
                .setNominalV(vBase)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(2. * vBase)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus844 = vl844.getBusBreakerView().newBus()
                .setId("B844")
                .add();
        bus844.setV(vBase).setAngle(0.);

        bus844.newExtension(BusAsymmetricalAdder.class)
                .withBusVariableType(BusVariableType.WYE)
                .withPositiveSequenceAsCurrent(true)
                .withFortescueRepresentation(false)
                .add();

        double p844 = 0.;
        double q844 = 0.;
        Load load844 = vl844.newLoad()
                .setId("LOAD_844")
                .setBus(bus844.getId())
                .setP0(p844)
                .setQ0(q844)
                .add();

        load844.newExtension(LoadAsymmetricalAdder.class)
                .withDeltaPa(0.135)
                .withDeltaQa(0.105 - 0.1) // approx shunt capacitor
                .withDeltaPb(0.135)
                .withDeltaQb(0.105 - 0.1)
                .withDeltaPc(0.135)
                .withDeltaQc(0.105 - 0.1)
                .withConnectionType(LoadConnectionType.Y)
                .add();

        load844.newExtension(LoadAsymmetrical2Adder.class)
                .withLoadType(LoadType.CONSTANT_IMPEDANCE)
                .add();

        Load distrload844 = vl844.newLoad()
                .setId("DISTR_LOAD_844")
                .setBus(bus844.getId())
                .setP0(p844)
                .setQ0(q844)
                .add();

        distrload844.newExtension(LoadAsymmetricalAdder.class)
                .withDeltaPa(0.)
                .withDeltaQa(0.)
                .withDeltaPb(0.025)
                .withDeltaQb(0.012)
                .withDeltaPc(0.020)
                .withDeltaQc(0.011)
                .withConnectionType(LoadConnectionType.Y)
                .add();

        distrload844.newExtension(LoadAsymmetrical2Adder.class)
                .withLoadType(LoadType.CONSTANT_POWER)
                .add();

        // Bus 846
        Substation substation846 = network.newSubstation()
                .setId("S846")
                .setCountry(Country.FR)
                .add();

        VoltageLevel vl846 = substation846.newVoltageLevel()
                .setId("VL_846")
                .setNominalV(vBase)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(2. * vBase)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus846 = vl846.getBusBreakerView().newBus()
                .setId("B846")
                .add();
        bus846.setV(vBase).setAngle(0.);

        bus846.newExtension(BusAsymmetricalAdder.class)
                .withBusVariableType(BusVariableType.WYE)
                .withPositiveSequenceAsCurrent(true)
                .withFortescueRepresentation(false)
                .add();

        double p846 = 0.;
        double q846 = 0.;
        Load load846 = vl846.newLoad()
                .setId("LOAD_846")
                .setBus(bus846.getId())
                .setP0(p846)
                .setQ0(q846)
                .add();

        load846.newExtension(LoadAsymmetricalAdder.class)
                .withDeltaPa(0.)
                .withDeltaQa(0.)
                .withDeltaPb(0.023)
                .withDeltaQb(0.011)
                .withDeltaPc(0.)
                .withDeltaQc(0.)
                .withConnectionType(LoadConnectionType.Y)
                .add();

        load846.newExtension(LoadAsymmetrical2Adder.class)
                .withLoadType(LoadType.CONSTANT_POWER)
                .add();

        // Bus 848
        Substation substation848 = network.newSubstation()
                .setId("S848")
                .setCountry(Country.FR)
                .add();

        VoltageLevel vl848 = substation848.newVoltageLevel()
                .setId("VL_848")
                .setNominalV(vBase)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(2. * vBase)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus848 = vl848.getBusBreakerView().newBus()
                .setId("B848")
                .add();
        bus848.setV(vBase).setAngle(0.);

        bus848.newExtension(BusAsymmetricalAdder.class)
                .withBusVariableType(BusVariableType.WYE)
                .withPositiveSequenceAsCurrent(true)
                .withFortescueRepresentation(true)
                .add();

        double p848 = 0.;
        double q848 = 0.;
        Load load848 = vl848.newLoad()
                .setId("LOAD_848")
                .setBus(bus848.getId())
                .setP0(p848)
                .setQ0(q848)
                .add();

        load848.newExtension(LoadAsymmetricalAdder.class)
                .withDeltaPa(0.02)
                .withDeltaQa(0.016 - 0.15) // for now combination of Constant power delta load and shunt not possible
                .withDeltaPb(0.02)
                .withDeltaQb(0.016 - 0.15)
                .withDeltaPc(0.02)
                .withDeltaQc(0.016 - 0.15)
                .withConnectionType(LoadConnectionType.DELTA)
                .add();

        load848.newExtension(LoadAsymmetrical2Adder.class)
                .withLoadType(LoadType.CONSTANT_POWER)
                .add();
/*
        Load compensload848 = vl848.newLoad()
                .setId("COMPENSE_LOAD_848")
                .setBus(bus848.getId())
                .setP0(0.)
                .setQ0(0.)
                .add();

        compensload848.newExtension(LoadUnbalancedAdder.class)
                .withDeltaPa(0.)
                .withDeltaQa(-0.15)
                .withDeltaPb(0.)
                .withDeltaQb(-0.15)
                .withDeltaPc(0.)
                .withDeltaQc(-0.15)
                .withLoadType(LoadType.CONSTANT_IMPEDANCE)
                .withConnectionType(WindingConnectionType.Y_GROUNDED) // TODO : put in argument
                .add();
*/
        // Bus 860
        Substation substation860 = network.newSubstation()
                .setId("S860")
                .setCountry(Country.FR)
                .add();

        VoltageLevel vl860 = substation860.newVoltageLevel()
                .setId("VL_860")
                .setNominalV(vBase)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(2. * vBase)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus860 = vl860.getBusBreakerView().newBus()
                .setId("B860")
                .add();
        bus860.setV(vBase).setAngle(0.);

        bus860.newExtension(BusAsymmetricalAdder.class)
                .withBusVariableType(BusVariableType.WYE)
                .withPositiveSequenceAsCurrent(true)
                .withFortescueRepresentation(true)
                .add();

        double p860 = 0.;
        double q860 = 0.;
        Load load860 = vl860.newLoad()
                .setId("LOAD_860")
                .setBus(bus860.getId())
                .setP0(p860)
                .setQ0(q860)
                .add();

        load860.newExtension(LoadAsymmetricalAdder.class)
                .withDeltaPa(0.02)
                .withDeltaQa(0.016)
                .withDeltaPb(0.02)
                .withDeltaQb(0.016)
                .withDeltaPc(0.02)
                .withDeltaQc(0.016)
                .withConnectionType(LoadConnectionType.Y)
                .add();

        load860.newExtension(LoadAsymmetrical2Adder.class)
                .withLoadType(LoadType.CONSTANT_POWER)
                .add();

        Load distriload860 = vl860.newLoad()
                .setId("DISTRI_LOAD_860")
                .setBus(bus860.getId())
                .setP0(p860)
                .setQ0(q860)
                .add();

        distriload860.newExtension(LoadAsymmetricalAdder.class)
                .withDeltaPa(0.03)
                .withDeltaQa(0.015)
                .withDeltaPb(0.01)
                .withDeltaQb(0.006)
                .withDeltaPc(0.042)
                .withDeltaQc(0.022)
                .withConnectionType(LoadConnectionType.DELTA)
                .add();

        distriload860.newExtension(LoadAsymmetrical2Adder.class)
                .withLoadType(LoadType.CONSTANT_POWER)
                .add();

        // Bus 836
        Substation substation836 = network.newSubstation()
                .setId("S836")
                .setCountry(Country.FR)
                .add();

        VoltageLevel vl836 = substation836.newVoltageLevel()
                .setId("VL_836")
                .setNominalV(vBase)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(2. * vBase)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus836 = vl836.getBusBreakerView().newBus()
                .setId("B836")
                .add();
        bus836.setV(vBase).setAngle(0.);

        bus836.newExtension(BusAsymmetricalAdder.class)
                .withBusVariableType(BusVariableType.WYE)
                .withPositiveSequenceAsCurrent(true)
                .withFortescueRepresentation(false)
                .add();

        double p836 = 0.;
        double q836 = 0.;
        Load load836 = vl836.newLoad()
                .setId("LOAD_836")
                .setBus(bus836.getId())
                .setP0(p836)
                .setQ0(q836)
                .add();

        load836.newExtension(LoadAsymmetricalAdder.class)
                .withDeltaPa(0.018)
                .withDeltaQa(0.009)
                .withDeltaPb(0.022)
                .withDeltaQb(0.011)
                .withDeltaPc(0.)
                .withDeltaQc(0.)
                .withConnectionType(LoadConnectionType.DELTA)
                .add();

        load836.newExtension(LoadAsymmetrical2Adder.class)
                .withLoadType(LoadType.CONSTANT_CURRENT)
                .add();

        // Bus 840
        Substation substation840 = network.newSubstation()
                .setId("S840")
                .setCountry(Country.FR)
                .add();

        VoltageLevel vl840 = substation840.newVoltageLevel()
                .setId("VL_840")
                .setNominalV(vBase)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(2. * vBase)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus840 = vl840.getBusBreakerView().newBus()
                .setId("B840")
                .add();
        bus840.setV(vBase).setAngle(0.);

        bus840.newExtension(BusAsymmetricalAdder.class)
                .withBusVariableType(BusVariableType.WYE)
                .withPositiveSequenceAsCurrent(true)
                .withFortescueRepresentation(false)
                .add();

        double p840 = 0.;
        double q840 = 0.;
        Load load840 = vl840.newLoad()
                .setId("LOAD_840")
                .setBus(bus840.getId())
                .setP0(p840)
                .setQ0(q840)
                .add();

        load840.newExtension(LoadAsymmetricalAdder.class)
                .withDeltaPa(0.009)
                .withDeltaQa(0.007)
                .withDeltaPb(0.009)
                .withDeltaQb(0.007)
                .withDeltaPc(0.009)
                .withDeltaQc(0.007)
                .withConnectionType(LoadConnectionType.Y)
                .add();

        load840.newExtension(LoadAsymmetrical2Adder.class)
                .withLoadType(LoadType.CONSTANT_CURRENT)
                .add();

        // Bus 862
        Substation substation862 = network.newSubstation()
                .setId("S862")
                .setCountry(Country.FR)
                .add();

        VoltageLevel vl862 = substation862.newVoltageLevel()
                .setId("VL_862")
                .setNominalV(vBase)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(2. * vBase)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus862 = vl862.getBusBreakerView().newBus()
                .setId("B862")
                .add();
        bus862.setV(vBase).setAngle(0.);

        bus862.newExtension(BusAsymmetricalAdder.class)
                .withBusVariableType(BusVariableType.WYE)
                .withPositiveSequenceAsCurrent(true)
                .withFortescueRepresentation(false)
                .add();

        double p862 = 0.;
        double q862 = 0.;
        Load load862 = vl862.newLoad()
                .setId("LOAD_862")
                .setBus(bus862.getId())
                .setP0(p862)
                .setQ0(q862)
                .add();

        load862.newExtension(LoadAsymmetricalAdder.class)
                .withDeltaPa(0.)
                .withDeltaQa(0.)
                .withDeltaPb(0.028)
                .withDeltaQb(0.014)
                .withDeltaPc(0.)
                .withDeltaQc(0.)
                .withConnectionType(LoadConnectionType.Y)
                .add();

        load862.newExtension(LoadAsymmetrical2Adder.class)
                .withLoadType(LoadType.CONSTANT_POWER)
                .add();

        // Bus 838
        Substation substation838 = network.newSubstation()
                .setId("S838")
                .setCountry(Country.FR)
                .add();

        VoltageLevel vl838 = substation838.newVoltageLevel()
                .setId("VL_838")
                .setNominalV(vBase)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(2. * vBase)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus838 = vl838.getBusBreakerView().newBus()
                .setId("B838")
                .add();
        bus838.setV(vBase).setAngle(0.);

        bus838.newExtension(BusAsymmetricalAdder.class)
                .withBusVariableType(BusVariableType.WYE)
                .withPositiveSequenceAsCurrent(true)
                .withFortescueRepresentation(false) // if not, then load other tham power not handled
                .withHasPhaseA(false)
                .withHasPhaseC(false)
                .add();

        double micro = 0.000001;

        // config 300 :
        // building of Yabc from given Y impedance matrix Zy
        ComplexMatrix zy300 = new ComplexMatrix(3, 3);
        zy300.set(1, 1, new Complex(1.3368, 1.3343));
        zy300.set(1, 2, new Complex(0.2101, 0.5779));
        zy300.set(1, 3, new Complex(0.2130, 0.5015));
        zy300.set(2, 1, new Complex(0.2101, 0.5779));
        zy300.set(2, 2, new Complex(1.3238, 1.3569));
        zy300.set(2, 3, new Complex(0.2066, 0.4591));
        zy300.set(3, 1, new Complex(0.2130, 0.5015));
        zy300.set(3, 2, new Complex(0.2066, 0.4591));
        zy300.set(3, 3, new Complex(1.3294, 1.3471));

        ComplexMatrix b300 = new ComplexMatrix(3, 3);

        b300.set(1, 1, new Complex(0, micro * 5.335));
        b300.set(1, 2, new Complex(0, micro * -1.5313));
        b300.set(1, 3, new Complex(0, micro * -0.9443));
        b300.set(2, 1, new Complex(0, micro * -1.5313));
        b300.set(2, 2, new Complex(0, micro * 5.0979));
        b300.set(2, 3, new Complex(0, micro * -0.6212));
        b300.set(3, 1, new Complex(0, micro * -0.9443));
        b300.set(3, 2, new Complex(0, micro * -0.6212));
        b300.set(3, 3, new Complex(0, micro * 4.888));

        // config 301 :
        // building of Yabc from given Y impedance matrix Zy
        ComplexMatrix zy301 = new ComplexMatrix(3, 3);
        zy301.set(1, 1, new Complex(1.93, 1.4115));
        zy301.set(1, 2, new Complex(0.2327, 0.6442));
        zy301.set(1, 3, new Complex(0.2359, 0.5691));
        zy301.set(2, 1, new Complex(0.2327, 0.6442));
        zy301.set(2, 2, new Complex(1.9157, 1.4281));
        zy301.set(2, 3, new Complex(0.2288, 0.5238));
        zy301.set(3, 1, new Complex(0.2359, 0.5691));
        zy301.set(3, 2, new Complex(0.2288, 0.5238));
        zy301.set(3, 3, new Complex(1.9219, 1.4209));

        ComplexMatrix b301 = new ComplexMatrix(3, 3);

        b301.set(1, 1, new Complex(0, micro * 5.1207));
        b301.set(1, 2, new Complex(0, micro * -1.4364));
        b301.set(1, 3, new Complex(0, micro * -0.9402));
        b301.set(2, 1, new Complex(0, micro * -1.4364));
        b301.set(2, 2, new Complex(0, micro * 4.9055));
        b301.set(2, 3, new Complex(0, micro * -0.5951));
        b301.set(3, 1, new Complex(0, micro * -0.9402));
        b301.set(3, 2, new Complex(0, micro * -0.5951));
        b301.set(3, 3, new Complex(0, micro * 4.7154));

        // config 302 :
        // building of Yabc from given Y impedance matrix Zy
        ComplexMatrix zy302 = new ComplexMatrix(3, 3);
        zy302.set(1, 1, new Complex(2.7995, 1.4855));
        zy302.set(1, 2, new Complex(0., 0.));
        zy302.set(1, 3, new Complex(0., 0.));
        zy302.set(2, 1, new Complex(0., 0.));
        zy302.set(2, 2, new Complex(0., 0.));
        zy302.set(2, 3, new Complex(0., 0.));
        zy302.set(3, 1, new Complex(0., 0.));
        zy302.set(3, 2, new Complex(0., 0.));
        zy302.set(3, 3, new Complex(0., 0.));

        ComplexMatrix b302 = new ComplexMatrix(3, 3);

        b302.set(1, 1, new Complex(0, micro * 4.2251));
        b302.set(1, 2, new Complex(0, micro * 0.));
        b302.set(1, 3, new Complex(0, micro * 0.));
        b302.set(2, 1, new Complex(0, micro * 0.));
        b302.set(2, 2, new Complex(0, micro * 0.));
        b302.set(2, 3, new Complex(0, micro * 0.));
        b302.set(3, 1, new Complex(0, micro * 0.));
        b302.set(3, 2, new Complex(0, micro * 0.));
        b302.set(3, 3, new Complex(0, micro * 0.));

        // config 303 :
        // building of Yabc from given Y impedance matrix Zy
        ComplexMatrix zy303 = new ComplexMatrix(3, 3);
        zy303.set(1, 1, new Complex(0., 0.));
        zy303.set(1, 2, new Complex(0., 0.));
        zy303.set(1, 3, new Complex(0., 0.));
        zy303.set(2, 1, new Complex(0., 0.));
        zy303.set(2, 2, new Complex(2.7995, 1.4855));
        zy303.set(2, 3, new Complex(0., 0.));
        zy303.set(3, 1, new Complex(0., 0.));
        zy303.set(3, 2, new Complex(0., 0.));
        zy303.set(3, 3, new Complex(0., 0.));

        ComplexMatrix b303 = new ComplexMatrix(3, 3);

        b303.set(1, 1, new Complex(0, micro * 0.));
        b303.set(1, 2, new Complex(0, micro * -0.));
        b303.set(1, 3, new Complex(0, micro * -0.));
        b303.set(2, 1, new Complex(0, micro * -0.));
        b303.set(2, 2, new Complex(0, micro * 4.2251));
        b303.set(2, 3, new Complex(0, micro * -0.));
        b303.set(3, 1, new Complex(0, micro * -0.));
        b303.set(3, 2, new Complex(0, micro * -0.));
        b303.set(3, 3, new Complex(0, micro * 0.));

        // config 304 :
        // building of Yabc from given Y impedance matrix Zy
        ComplexMatrix zy304 = new ComplexMatrix(3, 3);
        zy304.set(1, 1, new Complex(0., 0.));
        zy304.set(1, 2, new Complex(0., 0.));
        zy304.set(1, 3, new Complex(0., 0.));
        zy304.set(2, 1, new Complex(0., 0.));
        zy304.set(2, 2, new Complex(1.9217, 1.4212));
        zy304.set(2, 3, new Complex(0., 0.));
        zy304.set(3, 1, new Complex(0., 0.));
        zy304.set(3, 2, new Complex(0., 0.));
        zy304.set(3, 3, new Complex(0., 0.));

        ComplexMatrix b304 = new ComplexMatrix(3, 3);

        b304.set(1, 1, new Complex(0, micro * 0.));
        b304.set(1, 2, new Complex(0, micro * -0.));
        b304.set(1, 3, new Complex(0, micro * -0.));
        b304.set(2, 1, new Complex(0, micro * -0.));
        b304.set(2, 2, new Complex(0, micro * 4.3637));
        b304.set(2, 3, new Complex(0, micro * -0.));
        b304.set(3, 1, new Complex(0, micro * -0.));
        b304.set(3, 2, new Complex(0, micro * -0.));
        b304.set(3, 3, new Complex(0, micro * 0.));

        double feetInMile = 5280;

        double length800y802InFeet = 2580.;
        double length802y806InFeet = 1730.;
        double length806y808InFeet = 32230.;
        double length808y810InFeet = 5804.;
        double length808y812InFeet = 37500.;
        double length812y814InFeet = 29730.;
        double length814y850InFeet = 10.;
        double length850y816InFeet = 310.;
        double length816y818InFeet = 1710.;
        double length818y820InFeet = 48150.;
        double length820y822InFeet = 13740.;
        double length816y824InFeet = 10210.;
        double length824y826InFeet = 3030.;
        double length824y828InFeet = 840.;
        double length828y830InFeet = 20440.;
        double length830y854InFeet = 520.;
        double length854y856InFeet = 23330.;
        double length854y852InFeet = 36830.;
        double length852y832InFeet = 10.;
        double length888y890InFeet = 10560.;
        double length832y858InFeet = 4900.;
        double length858y864InFeet = 1620.;
        double length858y834InFeet = 5830.;
        double length834y842InFeet = 280.;
        double length842y844InFeet = 1350.;
        double length844y846InFeet = 3640.;
        double length846y848InFeet = 530.;
        double length834y860InFeet = 2020.;
        double length860y836InFeet = 2680.;
        double length836y840InFeet = 860.;
        double length836y862InFeet = 280.;
        double length862y838InFeet = 4860.;

        double ry = 0.1;
        double xy = 0.1;
        double r0y = 0.1;
        double x0y = 0.1;

        // line 800y802
        Line line800y802 = network.newLine()
                .setId("800y802")
                .setVoltageLevel1(vl800.getId())
                .setBus1(bus800.getId())
                .setConnectableBus1(bus800.getId())
                .setVoltageLevel2(vl802.getId())
                .setBus2(bus802.getId())
                .setConnectableBus2(bus802.getId())
                .setR(ry * length800y802InFeet / feetInMile)
                .setX(xy * length800y802InFeet / feetInMile)
                .setG1(0.0)
                .setB1(0.0)
                .setG2(0.0)
                .setB2(0.0)
                .add();

        ComplexMatrix yabc800y802 = LineAsymmetrical.getAdmittanceMatrixFromImpedanceAndBmatrix(zy300, b300, true, true, true, length800y802InFeet / feetInMile);
        line800y802.newExtension(LineAsymmetricalAdder.class)
                .withYabc(ComplexMatrix.getMatrixScaled(yabc800y802, yCoef))
                .add();

        line800y802.newExtension(LineFortescueAdder.class)
                .withOpenPhaseA(false)
                .withOpenPhaseB(false)
                .withOpenPhaseC(false)
                .add();

        line800y802.newExtension(LineFortescueAdder.class)
                .withRz(r0y * length800y802InFeet / feetInMile)
                .withXz(x0y * length800y802InFeet / feetInMile)
                .add();

        // line 802y806
        Line line802y806 = network.newLine()
                .setId("802y806")
                .setVoltageLevel1(vl802.getId())
                .setBus1(bus802.getId())
                .setConnectableBus1(bus802.getId())
                .setVoltageLevel2(vl806.getId())
                .setBus2(bus806.getId())
                .setConnectableBus2(bus806.getId())
                .setR(ry * length802y806InFeet / feetInMile)
                .setX(xy * length802y806InFeet / feetInMile)
                .setG1(0.0)
                .setB1(0.0)
                .setG2(0.0)
                .setB2(0.0)
                .add();

        ComplexMatrix yabc802y806 = LineAsymmetrical.getAdmittanceMatrixFromImpedanceAndBmatrix(zy300, b300, true, true, true, length802y806InFeet / feetInMile);
        line802y806.newExtension(LineAsymmetricalAdder.class)
                .withYabc(ComplexMatrix.getMatrixScaled(yabc802y806, yCoef))
                .add();

        line802y806.newExtension(LineFortescueAdder.class)
                .withOpenPhaseA(false)
                .withOpenPhaseB(false)
                .withOpenPhaseC(false)
                .add();

        line802y806.newExtension(LineFortescueAdder.class)
                .withRz(r0y * length802y806InFeet / feetInMile)
                .withXz(x0y * length802y806InFeet / feetInMile)
                .add();

        // line 806y808
        Line line806y808 = network.newLine()
                .setId("806y808")
                .setVoltageLevel1(vl806.getId())
                .setBus1(bus806.getId())
                .setConnectableBus1(bus806.getId())
                .setVoltageLevel2(vl808.getId())
                .setBus2(bus808.getId())
                .setConnectableBus2(bus808.getId())
                .setR(ry * length806y808InFeet / feetInMile)
                .setX(xy * length806y808InFeet / feetInMile)
                .setG1(0.0)
                .setB1(0.0)
                .setG2(0.0)
                .setB2(0.0)
                .add();

        ComplexMatrix yabc806y808 = LineAsymmetrical.getAdmittanceMatrixFromImpedanceAndBmatrix(zy300, b300, true, true, true, length806y808InFeet / feetInMile);
        line806y808.newExtension(LineAsymmetricalAdder.class)
                .withYabc(ComplexMatrix.getMatrixScaled(yabc806y808, yCoef))
                .add();

        line806y808.newExtension(LineFortescueAdder.class)
                .withOpenPhaseA(false)
                .withOpenPhaseB(false)
                .withOpenPhaseC(false)
                .add();

        line806y808.newExtension(LineFortescueAdder.class)
                .withRz(r0y * length806y808InFeet / feetInMile)
                .withXz(x0y * length806y808InFeet / feetInMile)
                .add();

        // line 808y810
        Line line808y810 = network.newLine()
                .setId("808y810")
                .setVoltageLevel1(vl808.getId())
                .setBus1(bus808.getId())
                .setConnectableBus1(bus808.getId())
                .setVoltageLevel2(vl810.getId())
                .setBus2(bus810.getId())
                .setConnectableBus2(bus810.getId())
                .setR(ry * length808y810InFeet / feetInMile)
                .setX(xy * length808y810InFeet / feetInMile)
                .setG1(0.0)
                .setB1(0.0)
                .setG2(0.0)
                .setB2(0.0)
                .add();

        ComplexMatrix yabc808y810 = LineAsymmetrical.getAdmittanceMatrixFromImpedanceAndBmatrix(zy303, b303, false, true, false, length808y810InFeet / feetInMile);
        line808y810.newExtension(LineAsymmetricalAdder.class)
                .withYabc(ComplexMatrix.getMatrixScaled(yabc808y810, yCoef))
                .add();

        line808y810.newExtension(LineFortescueAdder.class)
                .withOpenPhaseA(false)
                .withOpenPhaseB(false)
                .withOpenPhaseC(false)
                .add();

        line808y810.newExtension(LineFortescueAdder.class)
                .withRz(r0y * length808y810InFeet / feetInMile)
                .withXz(x0y * length808y810InFeet / feetInMile)
                .add();

        // line 808y812
        Line line808y812 = network.newLine()
                .setId("808y812")
                .setVoltageLevel1(vl808.getId())
                .setBus1(bus808.getId())
                .setConnectableBus1(bus808.getId())
                .setVoltageLevel2(vl812.getId())
                .setBus2(bus812.getId())
                .setConnectableBus2(bus812.getId())
                .setR(ry * length808y812InFeet / feetInMile)
                .setX(xy * length808y812InFeet / feetInMile)
                .setG1(0.0)
                .setB1(0.0)
                .setG2(0.0)
                .setB2(0.0)
                .add();

        ComplexMatrix yabc808y812 = LineAsymmetrical.getAdmittanceMatrixFromImpedanceAndBmatrix(zy300, b300, true, true, true, length808y812InFeet / feetInMile);
        line808y812.newExtension(LineAsymmetricalAdder.class)
                .withYabc(ComplexMatrix.getMatrixScaled(yabc808y812, yCoef))
                .add();

        line808y812.newExtension(LineFortescueAdder.class)
                .withOpenPhaseA(false)
                .withOpenPhaseB(false)
                .withOpenPhaseC(false)
                .add();

        line808y812.newExtension(LineFortescueAdder.class)
                .withRz(r0y * length808y812InFeet / feetInMile)
                .withXz(x0y * length808y812InFeet / feetInMile)
                .add();

        // line 812y814
        Line line812y814 = network.newLine()
                .setId("812y814")
                .setVoltageLevel1(vl812.getId())
                .setBus1(bus812.getId())
                .setConnectableBus1(bus812.getId())
                .setVoltageLevel2(vl814.getId())
                .setBus2(bus814.getId())
                .setConnectableBus2(bus814.getId())
                .setR(ry * length812y814InFeet / feetInMile)
                .setX(xy * length812y814InFeet / feetInMile)
                .setG1(0.0)
                .setB1(0.0)
                .setG2(0.0)
                .setB2(0.0)
                .add();

        ComplexMatrix yabc812y814 = LineAsymmetrical.getAdmittanceMatrixFromImpedanceAndBmatrix(zy300, b300, true, true, true, length812y814InFeet / feetInMile);
        line812y814.newExtension(LineAsymmetricalAdder.class)
                .withYabc(ComplexMatrix.getMatrixScaled(yabc812y814, yCoef))
                .add();

        line812y814.newExtension(LineFortescueAdder.class)
                .withOpenPhaseA(false)
                .withOpenPhaseB(false)
                .withOpenPhaseC(false)
                .add();

        line812y814.newExtension(LineFortescueAdder.class)
                .withRz(r0y * length812y814InFeet / feetInMile)
                .withXz(x0y * length812y814InFeet / feetInMile)
                .add();

        // line 814y850
        Line line814y850 = network.newLine()
                .setId("814y850")
                .setVoltageLevel1(vl814.getId())
                .setBus1(bus814.getId())
                .setConnectableBus1(bus814.getId())
                .setVoltageLevel2(vl850.getId())
                .setBus2(bus850.getId())
                .setConnectableBus2(bus850.getId())
                .setR(ry * length814y850InFeet / feetInMile)
                .setX(xy * length814y850InFeet / feetInMile)
                .setG1(0.0)
                .setB1(0.0)
                .setG2(0.0)
                .setB2(0.0)
                .add();

        ComplexMatrix yabc814y850 = LineAsymmetrical.getAdmittanceMatrixFromImpedanceAndBmatrix(zy301, b301, true, true, true, length814y850InFeet / feetInMile);
        // modification of yabc301 to take into account the voltage regulator
        // [VRG10] = [rho] * [V814]
        ComplexMatrix rho = new ComplexMatrix(6, 6);
        rho.set(1, 1, new Complex(1.0177 / 0.9467, 0.));
        rho.set(2, 2, new Complex(1.0255 / 0.9945, 0.));
        rho.set(3, 3, new Complex(1.0203 / 0.9893, 0.));
        rho.set(4, 4, new Complex(1., 0.));
        rho.set(5, 5, new Complex(1., 0.));
        rho.set(6, 6, new Complex(1., 0.));
        DenseMatrix yabcRg10Real = rho.getRealCartesianMatrix().times(yabc814y850.getRealCartesianMatrix().times(rho.getRealCartesianMatrix()));
        ComplexMatrix yabcRg10 = ComplexMatrix.getComplexMatrixFromRealCartesian(yabcRg10Real);

        line814y850.newExtension(LineAsymmetricalAdder.class)
                .withYabc(ComplexMatrix.getMatrixScaled(yabcRg10, yCoef))
                .add();

        line814y850.newExtension(LineFortescueAdder.class)
                .withOpenPhaseA(false)
                .withOpenPhaseB(false)
                .withOpenPhaseC(false)
                .add();

        line814y850.newExtension(LineFortescueAdder.class)
                .withRz(r0y * length814y850InFeet / feetInMile)
                .withXz(x0y * length814y850InFeet / feetInMile)
                .add();

        // line 850y816
        Line line850y816 = network.newLine()
                .setId("850y816")
                .setVoltageLevel1(vl850.getId())
                .setBus1(bus850.getId())
                .setConnectableBus1(bus850.getId())
                .setVoltageLevel2(vl816.getId())
                .setBus2(bus816.getId())
                .setConnectableBus2(bus816.getId())
                .setR(ry * length850y816InFeet / feetInMile)
                .setX(xy * length850y816InFeet / feetInMile)
                .setG1(0.0)
                .setB1(0.0)
                .setG2(0.0)
                .setB2(0.0)
                .add();

        ComplexMatrix yabc850y816 = LineAsymmetrical.getAdmittanceMatrixFromImpedanceAndBmatrix(zy301, b301, true, true, true, length850y816InFeet / feetInMile);
        line850y816.newExtension(LineAsymmetricalAdder.class)
                .withYabc(ComplexMatrix.getMatrixScaled(yabc850y816, yCoef))
                .add();

        line850y816.newExtension(LineFortescueAdder.class)
                .withOpenPhaseA(false)
                .withOpenPhaseB(false)
                .withOpenPhaseC(false)
                .add();

        line850y816.newExtension(LineFortescueAdder.class)
                .withRz(r0y * length850y816InFeet / feetInMile)
                .withXz(x0y * length850y816InFeet / feetInMile)
                .add();

        // line 816y818
        Line line816y818 = network.newLine()
                .setId("816y818")
                .setVoltageLevel1(vl816.getId())
                .setBus1(bus816.getId())
                .setConnectableBus1(bus816.getId())
                .setVoltageLevel2(vl818.getId())
                .setBus2(bus818.getId())
                .setConnectableBus2(bus818.getId())
                .setR(ry * length816y818InFeet / feetInMile)
                .setX(xy * length816y818InFeet / feetInMile)
                .setG1(0.0)
                .setB1(0.0)
                .setG2(0.0)
                .setB2(0.0)
                .add();

        ComplexMatrix yabc816y818 = LineAsymmetrical.getAdmittanceMatrixFromImpedanceAndBmatrix(zy302, b302, true, false, false, length816y818InFeet / feetInMile);
        line816y818.newExtension(LineAsymmetricalAdder.class)
                .withYabc(ComplexMatrix.getMatrixScaled(yabc816y818, yCoef))
                .add();

        line816y818.newExtension(LineFortescueAdder.class)
                .withOpenPhaseA(false)
                .withOpenPhaseB(false)
                .withOpenPhaseC(false)
                .add();

        line816y818.newExtension(LineFortescueAdder.class)
                .withRz(r0y * length816y818InFeet / feetInMile)
                .withXz(x0y * length816y818InFeet / feetInMile)
                .add();

        // line 818y820
        Line line818y820 = network.newLine()
                .setId("818y820")
                .setVoltageLevel1(vl818.getId())
                .setBus1(bus818.getId())
                .setConnectableBus1(bus818.getId())
                .setVoltageLevel2(vl820.getId())
                .setBus2(bus820.getId())
                .setConnectableBus2(bus820.getId())
                .setR(ry * length818y820InFeet / feetInMile)
                .setX(xy * length818y820InFeet / feetInMile)
                .setG1(0.0)
                .setB1(0.0)
                .setG2(0.0)
                .setB2(0.0)
                .add();

        ComplexMatrix yabc818y820 = LineAsymmetrical.getAdmittanceMatrixFromImpedanceAndBmatrix(zy302, b302, true, false, false, length818y820InFeet / feetInMile);
        line818y820.newExtension(LineAsymmetricalAdder.class)
                .withYabc(ComplexMatrix.getMatrixScaled(yabc818y820, yCoef))
                .add();

        line818y820.newExtension(LineFortescueAdder.class)
                .withOpenPhaseA(false)
                .withOpenPhaseB(false)
                .withOpenPhaseC(false)
                .add();

        line818y820.newExtension(LineFortescueAdder.class)
                .withRz(r0y * length818y820InFeet / feetInMile)
                .withXz(x0y * length818y820InFeet / feetInMile)
                .add();

        // line 820y822
        Line line820y822 = network.newLine()
                .setId("820y822")
                .setVoltageLevel1(vl820.getId())
                .setBus1(bus820.getId())
                .setConnectableBus1(bus820.getId())
                .setVoltageLevel2(vl822.getId())
                .setBus2(bus822.getId())
                .setConnectableBus2(bus822.getId())
                .setR(ry * length820y822InFeet / feetInMile)
                .setX(xy * length820y822InFeet / feetInMile)
                .setG1(0.0)
                .setB1(0.0)
                .setG2(0.0)
                .setB2(0.0)
                .add();

        ComplexMatrix yabc820y822 = LineAsymmetrical.getAdmittanceMatrixFromImpedanceAndBmatrix(zy302, b302, true, false, false, length820y822InFeet / feetInMile);
        line820y822.newExtension(LineAsymmetricalAdder.class)
                .withYabc(ComplexMatrix.getMatrixScaled(yabc820y822, yCoef))
                .add();

        line820y822.newExtension(LineFortescueAdder.class)
                .withOpenPhaseA(false)
                .withOpenPhaseB(false)
                .withOpenPhaseC(false)
                .add();

        line820y822.newExtension(LineFortescueAdder.class)
                .withRz(r0y * length820y822InFeet / feetInMile)
                .withXz(x0y * length820y822InFeet / feetInMile)
                .add();

        // line 816y824
        Line line816y824 = network.newLine()
                .setId("816y824")
                .setVoltageLevel1(vl816.getId())
                .setBus1(bus816.getId())
                .setConnectableBus1(bus816.getId())
                .setVoltageLevel2(vl824.getId())
                .setBus2(bus824.getId())
                .setConnectableBus2(bus824.getId())
                .setR(ry * length816y824InFeet / feetInMile)
                .setX(xy * length816y824InFeet / feetInMile)
                .setG1(0.0)
                .setB1(0.0)
                .setG2(0.0)
                .setB2(0.0)
                .add();

        ComplexMatrix yabc816y824 = LineAsymmetrical.getAdmittanceMatrixFromImpedanceAndBmatrix(zy301, b301, true, true, true, length816y824InFeet / feetInMile);
        line816y824.newExtension(LineAsymmetricalAdder.class)
                .withYabc(ComplexMatrix.getMatrixScaled(yabc816y824, yCoef))
                .add();

        line816y824.newExtension(LineFortescueAdder.class)
                .withOpenPhaseA(false)
                .withOpenPhaseB(false)
                .withOpenPhaseC(false)
                .add();

        line816y824.newExtension(LineFortescueAdder.class)
                .withRz(r0y * length816y824InFeet / feetInMile)
                .withXz(x0y * length816y824InFeet / feetInMile)
                .add();

        // line 824y826
        Line line824y826 = network.newLine()
                .setId("824y826")
                .setVoltageLevel1(vl824.getId())
                .setBus1(bus824.getId())
                .setConnectableBus1(bus824.getId())
                .setVoltageLevel2(vl826.getId())
                .setBus2(bus826.getId())
                .setConnectableBus2(bus826.getId())
                .setR(ry * length824y826InFeet / feetInMile)
                .setX(xy * length824y826InFeet / feetInMile)
                .setG1(0.0)
                .setB1(0.0)
                .setG2(0.0)
                .setB2(0.0)
                .add();

        ComplexMatrix yabc824y826 = LineAsymmetrical.getAdmittanceMatrixFromImpedanceAndBmatrix(zy303, b303, false, true, false, length824y826InFeet / feetInMile);
        line824y826.newExtension(LineAsymmetricalAdder.class)
                .withYabc(ComplexMatrix.getMatrixScaled(yabc824y826, yCoef))
                .add();

        line824y826.newExtension(LineFortescueAdder.class)
                .withOpenPhaseA(false)
                .withOpenPhaseB(false)
                .withOpenPhaseC(false)
                .add();

        line824y826.newExtension(LineFortescueAdder.class)
                .withRz(r0y * length824y826InFeet / feetInMile)
                .withXz(x0y * length824y826InFeet / feetInMile)
                .add();

        // line 824y828
        Line line824y828 = network.newLine()
                .setId("824y828")
                .setVoltageLevel1(vl824.getId())
                .setBus1(bus824.getId())
                .setConnectableBus1(bus824.getId())
                .setVoltageLevel2(vl828.getId())
                .setBus2(bus828.getId())
                .setConnectableBus2(bus828.getId())
                .setR(ry * length824y828InFeet / feetInMile)
                .setX(xy * length824y828InFeet / feetInMile)
                .setG1(0.0)
                .setB1(0.0)
                .setG2(0.0)
                .setB2(0.0)
                .add();

        ComplexMatrix yabc824y828 = LineAsymmetrical.getAdmittanceMatrixFromImpedanceAndBmatrix(zy301, b301, true, true, true, length824y828InFeet / feetInMile);
        line824y828.newExtension(LineAsymmetricalAdder.class)
                .withYabc(ComplexMatrix.getMatrixScaled(yabc824y828, yCoef))
                .add();

        line824y828.newExtension(LineFortescueAdder.class)
                .withOpenPhaseA(false)
                .withOpenPhaseB(false)
                .withOpenPhaseC(false)
                .add();

        line824y828.newExtension(LineFortescueAdder.class)
                .withRz(r0y * length824y828InFeet / feetInMile)
                .withXz(x0y * length824y828InFeet / feetInMile)
                .add();

        // line 828y830
        Line line828y830 = network.newLine()
                .setId("828y830")
                .setVoltageLevel1(vl828.getId())
                .setBus1(bus828.getId())
                .setConnectableBus1(bus828.getId())
                .setVoltageLevel2(vl830.getId())
                .setBus2(bus830.getId())
                .setConnectableBus2(bus830.getId())
                .setR(ry * length828y830InFeet / feetInMile)
                .setX(xy * length828y830InFeet / feetInMile)
                .setG1(0.0)
                .setB1(0.0)
                .setG2(0.0)
                .setB2(0.0)
                .add();

        ComplexMatrix yabc828y830 = LineAsymmetrical.getAdmittanceMatrixFromImpedanceAndBmatrix(zy301, b301, true, true, true, length828y830InFeet / feetInMile);
        line828y830.newExtension(LineAsymmetricalAdder.class)
                .withYabc(ComplexMatrix.getMatrixScaled(yabc828y830, yCoef))
                .add();

        line828y830.newExtension(LineFortescueAdder.class)
                .withOpenPhaseA(false)
                .withOpenPhaseB(false)
                .withOpenPhaseC(false)
                .add();

        line828y830.newExtension(LineFortescueAdder.class)
                .withRz(r0y * length828y830InFeet / feetInMile)
                .withXz(x0y * length828y830InFeet / feetInMile)
                .add();

        // line 830y854
        Line line830y854 = network.newLine()
                .setId("830y854")
                .setVoltageLevel1(vl830.getId())
                .setBus1(bus830.getId())
                .setConnectableBus1(bus830.getId())
                .setVoltageLevel2(vl854.getId())
                .setBus2(bus854.getId())
                .setConnectableBus2(bus854.getId())
                .setR(ry * length830y854InFeet / feetInMile)
                .setX(xy * length830y854InFeet / feetInMile)
                .setG1(0.0)
                .setB1(0.0)
                .setG2(0.0)
                .setB2(0.0)
                .add();

        ComplexMatrix yabc830y854 = LineAsymmetrical.getAdmittanceMatrixFromImpedanceAndBmatrix(zy301, b301, true, true, true, length830y854InFeet / feetInMile);
        line830y854.newExtension(LineAsymmetricalAdder.class)
                .withYabc(ComplexMatrix.getMatrixScaled(yabc830y854, yCoef))
                .add();

        line830y854.newExtension(LineFortescueAdder.class)
                .withOpenPhaseA(false)
                .withOpenPhaseB(false)
                .withOpenPhaseC(false)
                .add();

        line830y854.newExtension(LineFortescueAdder.class)
                .withRz(r0y * length830y854InFeet / feetInMile)
                .withXz(x0y * length830y854InFeet / feetInMile)
                .add();

        // line 854y856
        Line line854y856 = network.newLine()
                .setId("854y856")
                .setVoltageLevel1(vl854.getId())
                .setBus1(bus854.getId())
                .setConnectableBus1(bus854.getId())
                .setVoltageLevel2(vl856.getId())
                .setBus2(bus856.getId())
                .setConnectableBus2(bus856.getId())
                .setR(ry * length854y856InFeet / feetInMile)
                .setX(xy * length854y856InFeet / feetInMile)
                .setG1(0.0)
                .setB1(0.0)
                .setG2(0.0)
                .setB2(0.0)
                .add();

        ComplexMatrix yabc854y856 = LineAsymmetrical.getAdmittanceMatrixFromImpedanceAndBmatrix(zy303, b303, false, true, false, length854y856InFeet / feetInMile);
        line854y856.newExtension(LineAsymmetricalAdder.class)
                .withYabc(ComplexMatrix.getMatrixScaled(yabc854y856, yCoef))
                .add();
        line854y856.newExtension(LineFortescueAdder.class)
                .withOpenPhaseA(false)
                .withOpenPhaseB(false)
                .withOpenPhaseC(false)
                .add();

        line854y856.newExtension(LineFortescueAdder.class)
                .withRz(r0y * length854y856InFeet / feetInMile)
                .withXz(x0y * length854y856InFeet / feetInMile)
                .add();

        // line 854y852
        Line line854y852 = network.newLine()
                .setId("854y852")
                .setVoltageLevel1(vl854.getId())
                .setBus1(bus854.getId())
                .setConnectableBus1(bus854.getId())
                .setVoltageLevel2(vl852.getId())
                .setBus2(bus852.getId())
                .setConnectableBus2(bus852.getId())
                .setR(ry * length854y852InFeet / feetInMile)
                .setX(xy * length854y852InFeet / feetInMile)
                .setG1(0.0)
                .setB1(0.0)
                .setG2(0.0)
                .setB2(0.0)
                .add();

        ComplexMatrix yabc854y852 = LineAsymmetrical.getAdmittanceMatrixFromImpedanceAndBmatrix(zy301, b301, true, true, true, length854y852InFeet / feetInMile);
        line854y852.newExtension(LineAsymmetricalAdder.class)
                .withYabc(ComplexMatrix.getMatrixScaled(yabc854y852, yCoef))
                .add();
        line854y852.newExtension(LineFortescueAdder.class)
                .withOpenPhaseA(false)
                .withOpenPhaseB(false)
                .withOpenPhaseC(false)
                .add();

        line854y852.newExtension(LineFortescueAdder.class)
                .withRz(r0y * length854y852InFeet / feetInMile)
                .withXz(x0y * length854y852InFeet / feetInMile)
                .add();

        // line 852y832
        Line line852y832 = network.newLine()
                .setId("852y832")
                .setVoltageLevel1(vl852.getId())
                .setBus1(bus852.getId())
                .setConnectableBus1(bus852.getId())
                .setVoltageLevel2(vl832.getId())
                .setBus2(bus832.getId())
                .setConnectableBus2(bus832.getId())
                .setR(ry * length852y832InFeet / feetInMile)
                .setX(xy * length852y832InFeet / feetInMile)
                .setG1(0.0)
                .setB1(0.0)
                .setG2(0.0)
                .setB2(0.0)
                .add();

        // modification of yabc301 to take into account the voltage regulator
        // [VRG11] = [rho] * [V852]
        ComplexMatrix rho11 = new ComplexMatrix(6, 6);
        rho11.set(1, 1, new Complex(1.0359 / 0.9581, 0.));
        rho11.set(2, 2, new Complex(1.0345 / 0.9680, 0.));
        rho11.set(3, 3, new Complex(1.0360 / 0.9637, 0.));
        rho11.set(4, 4, new Complex(1., 0.));
        rho11.set(5, 5, new Complex(1., 0.));
        rho11.set(6, 6, new Complex(1., 0.));
        ComplexMatrix yabc852y832 = LineAsymmetrical.getAdmittanceMatrixFromImpedanceAndBmatrix(zy301, b301, true, true, true, length852y832InFeet / feetInMile);
        DenseMatrix yabcRg11Real = rho11.getRealCartesianMatrix().times(yabc852y832.getRealCartesianMatrix().times(rho11.getRealCartesianMatrix()));
        ComplexMatrix yabcRg11 = ComplexMatrix.getComplexMatrixFromRealCartesian(yabcRg11Real);

        line852y832.newExtension(LineAsymmetricalAdder.class)
                .withYabc(ComplexMatrix.getMatrixScaled(yabcRg11, yCoef))
                .add();

        line852y832.newExtension(LineFortescueAdder.class)
                .withOpenPhaseA(false)
                .withOpenPhaseB(false)
                .withOpenPhaseC(false)
                .add();

        line852y832.newExtension(LineFortescueAdder.class)
                .withRz(r0y * length852y832InFeet / feetInMile)
                .withXz(x0y * length852y832InFeet / feetInMile)
                .add();

        // tfo 832y888
        double ratedU832 = vBase;
        double ratedU888 = vBaseLow;
        double sBase = 0.5;

        double rTpc = 1.9;
        double xTpc = 4.08;
        double zBase = ratedU832 * ratedU888 / sBase;
        double rT = rTpc * zBase / 100; // TODO : sBase = 2. for single phase and 6. for three phase
        double xT = xTpc * zBase / 100;

        var t832y888 = substation832.newTwoWindingsTransformer()
                .setId("t832y888")
                .setVoltageLevel1(vl832.getId())
                .setBus1(bus832.getId())
                .setConnectableBus1(bus832.getId())
                .setRatedU1(ratedU832)
                .setVoltageLevel2(vl888.getId())
                .setBus2(bus888.getId())
                .setConnectableBus2(bus888.getId())
                .setRatedU2(ratedU888) // TODO : check OK for Fortescue modeling
                .setR(rT)
                .setX(xT)
                .setG(0.0D)
                .setB(0.0D)
                .setRatedS(sBase)
                .add();

        t832y888.newExtension(TwoWindingsTransformerFortescueAdder.class)
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

        t832y888.newExtension(Tfo3PhasesAdder.class)
                .withIsOpenPhaseA1(false)
                .withIsOpenPhaseB1(false)
                .withIsOpenPhaseC1(false)
                .withYa(Asym4nodesFeederTest.buildSinglePhaseAdmittanceMatrix(zPhase, yPhase, yPhase))
                .withYb(Asym4nodesFeederTest.buildSinglePhaseAdmittanceMatrix(zPhase, yPhase, yPhase))
                .withYc(Asym4nodesFeederTest.buildSinglePhaseAdmittanceMatrix(zPhase, yPhase, yPhase))
                .add();

        // line 888y890
        Line line888y890 = network.newLine()
                .setId("888y890")
                .setVoltageLevel1(vl888.getId())
                .setBus1(bus888.getId())
                .setConnectableBus1(bus888.getId())
                .setVoltageLevel2(vl890.getId())
                .setBus2(bus890.getId())
                .setConnectableBus2(bus890.getId())
                .setR(ry * length888y890InFeet / feetInMile)
                .setX(xy * length888y890InFeet / feetInMile)
                .setG1(0.0)
                .setB1(0.0)
                .setG2(0.0)
                .setB2(0.0)
                .add();

        ComplexMatrix yabc888y890 = LineAsymmetrical.getAdmittanceMatrixFromImpedanceAndBmatrix(zy300, b300, true, true, true, length888y890InFeet / feetInMile);
        line888y890.newExtension(LineAsymmetricalAdder.class)
                .withYabc(ComplexMatrix.getMatrixScaled(yabc888y890, yCoef))
                .add();

        line888y890.newExtension(LineFortescueAdder.class)
                .withOpenPhaseA(false)
                .withOpenPhaseB(false)
                .withOpenPhaseC(false)
                .add();

        line888y890.newExtension(LineFortescueAdder.class)
                .withRz(r0y * length888y890InFeet / feetInMile)
                .withXz(x0y * length888y890InFeet / feetInMile)
                .add();

        // line 832y858
        Line line832y858 = network.newLine()
                .setId("832y858")
                .setVoltageLevel1(vl832.getId())
                .setBus1(bus832.getId())
                .setConnectableBus1(bus832.getId())
                .setVoltageLevel2(vl858.getId())
                .setBus2(bus858.getId())
                .setConnectableBus2(bus858.getId())
                .setR(ry * length832y858InFeet / feetInMile)
                .setX(xy * length832y858InFeet / feetInMile)
                .setG1(0.0)
                .setB1(0.0)
                .setG2(0.0)
                .setB2(0.0)
                .add();

        ComplexMatrix yabc832y858 = LineAsymmetrical.getAdmittanceMatrixFromImpedanceAndBmatrix(zy301, b301, true, true, true, length832y858InFeet / feetInMile);
        line832y858.newExtension(LineAsymmetricalAdder.class)
                .withYabc(ComplexMatrix.getMatrixScaled(yabc832y858, yCoef))
                .add();

        line832y858.newExtension(LineFortescueAdder.class)
                .withOpenPhaseA(false)
                .withOpenPhaseB(false)
                .withOpenPhaseC(false)
                .add();

        line832y858.newExtension(LineFortescueAdder.class)
                .withRz(r0y * length832y858InFeet / feetInMile)
                .withXz(x0y * length832y858InFeet / feetInMile)
                .add();

        // line 858y864
        Line line858y864 = network.newLine()
                .setId("858y864")
                .setVoltageLevel1(vl858.getId())
                .setBus1(bus858.getId())
                .setConnectableBus1(bus858.getId())
                .setVoltageLevel2(vl864.getId())
                .setBus2(bus864.getId())
                .setConnectableBus2(bus864.getId())
                .setR(ry * length858y864InFeet / feetInMile)
                .setX(xy * length858y864InFeet / feetInMile)
                .setG1(0.0)
                .setB1(0.0)
                .setG2(0.0)
                .setB2(0.0)
                .add();

        ComplexMatrix yabc858y864 = LineAsymmetrical.getAdmittanceMatrixFromImpedanceAndBmatrix(zy302, b302, true, false, false, length858y864InFeet / feetInMile);
        line858y864.newExtension(LineAsymmetricalAdder.class)
                .withYabc(ComplexMatrix.getMatrixScaled(yabc858y864, yCoef))
                .add();

        line858y864.newExtension(LineFortescueAdder.class)
                .withOpenPhaseA(false)
                .withOpenPhaseB(false)
                .withOpenPhaseC(false)
                .add();

        line858y864.newExtension(LineFortescueAdder.class)
                .withRz(r0y * length858y864InFeet / feetInMile)
                .withXz(x0y * length858y864InFeet / feetInMile)
                .add();

        // line 858y834
        Line line858y834 = network.newLine()
                .setId("858y834")
                .setVoltageLevel1(vl858.getId())
                .setBus1(bus858.getId())
                .setConnectableBus1(bus858.getId())
                .setVoltageLevel2(vl834.getId())
                .setBus2(bus834.getId())
                .setConnectableBus2(bus834.getId())
                .setR(ry * length858y834InFeet / feetInMile)
                .setX(xy * length858y834InFeet / feetInMile)
                .setG1(0.0)
                .setB1(0.0)
                .setG2(0.0)
                .setB2(0.0)
                .add();

        ComplexMatrix yabc858y834 = LineAsymmetrical.getAdmittanceMatrixFromImpedanceAndBmatrix(zy301, b301, true, true, true, length858y834InFeet / feetInMile);
        line858y834.newExtension(LineAsymmetricalAdder.class)
                .withYabc(ComplexMatrix.getMatrixScaled(yabc858y834, yCoef))
                .add();

        line858y834.newExtension(LineFortescueAdder.class)
                .withOpenPhaseA(false)
                .withOpenPhaseB(false)
                .withOpenPhaseC(false)
                .add();

        line858y834.newExtension(LineFortescueAdder.class)
                .withRz(r0y * length858y834InFeet / feetInMile)
                .withXz(x0y * length858y834InFeet / feetInMile)
                .add();

        // line 834y842
        Line line834y842 = network.newLine()
                .setId("834y842")
                .setVoltageLevel1(vl834.getId())
                .setBus1(bus834.getId())
                .setConnectableBus1(bus834.getId())
                .setVoltageLevel2(vl842.getId())
                .setBus2(bus842.getId())
                .setConnectableBus2(bus842.getId())
                .setR(ry * length834y842InFeet / feetInMile)
                .setX(xy * length834y842InFeet / feetInMile)
                .setG1(0.0)
                .setB1(0.0)
                .setG2(0.0)
                .setB2(0.0)
                .add();

        ComplexMatrix yabc834y842 = LineAsymmetrical.getAdmittanceMatrixFromImpedanceAndBmatrix(zy301, b301, true, true, true, length834y842InFeet / feetInMile);
        line834y842.newExtension(LineAsymmetricalAdder.class)
                .withYabc(ComplexMatrix.getMatrixScaled(yabc834y842, yCoef))
                .add();

        line834y842.newExtension(LineFortescueAdder.class)
                .withOpenPhaseA(false)
                .withOpenPhaseB(false)
                .withOpenPhaseC(false)
                .add();

        line834y842.newExtension(LineFortescueAdder.class)
                .withRz(r0y * length834y842InFeet / feetInMile)
                .withXz(x0y * length834y842InFeet / feetInMile)
                .add();

        // line 842y844
        Line line842y844 = network.newLine()
                .setId("842y844")
                .setVoltageLevel1(vl842.getId())
                .setBus1(bus842.getId())
                .setConnectableBus1(bus842.getId())
                .setVoltageLevel2(vl844.getId())
                .setBus2(bus844.getId())
                .setConnectableBus2(bus844.getId())
                .setR(ry * length842y844InFeet / feetInMile)
                .setX(xy * length842y844InFeet / feetInMile)
                .setG1(0.0)
                .setB1(0.0)
                .setG2(0.0)
                .setB2(0.0)
                .add();

        ComplexMatrix yabc842y844 = LineAsymmetrical.getAdmittanceMatrixFromImpedanceAndBmatrix(zy301, b301, true, true, true, length842y844InFeet / feetInMile);
        line842y844.newExtension(LineAsymmetricalAdder.class)
                .withYabc(ComplexMatrix.getMatrixScaled(yabc842y844, yCoef))
                .add();

        line842y844.newExtension(LineFortescueAdder.class)
                .withOpenPhaseA(false)
                .withOpenPhaseB(false)
                .withOpenPhaseC(false)
                .add();

        line842y844.newExtension(LineFortescueAdder.class)
                .withRz(r0y * length842y844InFeet / feetInMile)
                .withXz(x0y * length842y844InFeet / feetInMile)
                .add();

        // line 844y846
        Line line844y846 = network.newLine()
                .setId("844y846")
                .setVoltageLevel1(vl844.getId())
                .setBus1(bus844.getId())
                .setConnectableBus1(bus844.getId())
                .setVoltageLevel2(vl846.getId())
                .setBus2(bus846.getId())
                .setConnectableBus2(bus846.getId())
                .setR(ry * length844y846InFeet / feetInMile)
                .setX(xy * length844y846InFeet / feetInMile)
                .setG1(0.0)
                .setB1(0.0)
                .setG2(0.0)
                .setB2(0.0)
                .add();

        ComplexMatrix yabc844y846 = LineAsymmetrical.getAdmittanceMatrixFromImpedanceAndBmatrix(zy301, b301, true, true, true, length844y846InFeet / feetInMile);
        line844y846.newExtension(LineAsymmetricalAdder.class)
                .withYabc(ComplexMatrix.getMatrixScaled(yabc844y846, yCoef))
                .add();

        line844y846.newExtension(LineFortescueAdder.class)
                .withOpenPhaseA(false)
                .withOpenPhaseB(false)
                .withOpenPhaseC(false)
                .add();

        line844y846.newExtension(LineFortescueAdder.class)
                .withRz(r0y * length844y846InFeet / feetInMile)
                .withXz(x0y * length844y846InFeet / feetInMile)
                .add();

        // line 846y848
        Line line846y848 = network.newLine()
                .setId("846y848")
                .setVoltageLevel1(vl846.getId())
                .setBus1(bus846.getId())
                .setConnectableBus1(bus846.getId())
                .setVoltageLevel2(vl848.getId())
                .setBus2(bus848.getId())
                .setConnectableBus2(bus848.getId())
                .setR(ry * length846y848InFeet / feetInMile)
                .setX(xy * length846y848InFeet / feetInMile)
                .setG1(0.0)
                .setB1(0.0)
                .setG2(0.0)
                .setB2(0.0)
                .add();

        ComplexMatrix yabc846y848 = LineAsymmetrical.getAdmittanceMatrixFromImpedanceAndBmatrix(zy301, b301, true, true, true, length846y848InFeet / feetInMile);
        line846y848.newExtension(LineAsymmetricalAdder.class)
                .withYabc(ComplexMatrix.getMatrixScaled(yabc846y848, yCoef))
                .add();

        line846y848.newExtension(LineFortescueAdder.class)
                .withOpenPhaseA(false)
                .withOpenPhaseB(false)
                .withOpenPhaseC(false)
                .add();

        line846y848.newExtension(LineFortescueAdder.class)
                .withRz(r0y * length846y848InFeet / feetInMile)
                .withXz(x0y * length846y848InFeet / feetInMile)
                .add();

        // line 834y860
        Line line834y860 = network.newLine()
                .setId("834y860")
                .setVoltageLevel1(vl834.getId())
                .setBus1(bus834.getId())
                .setConnectableBus1(bus834.getId())
                .setVoltageLevel2(vl860.getId())
                .setBus2(bus860.getId())
                .setConnectableBus2(bus860.getId())
                .setR(ry * length834y860InFeet / feetInMile)
                .setX(xy * length834y860InFeet / feetInMile)
                .setG1(0.0)
                .setB1(0.0)
                .setG2(0.0)
                .setB2(0.0)
                .add();

        ComplexMatrix yabc834y860 = LineAsymmetrical.getAdmittanceMatrixFromImpedanceAndBmatrix(zy301, b301, true, true, true, length834y860InFeet / feetInMile);
        line834y860.newExtension(LineAsymmetricalAdder.class)
                .withYabc(ComplexMatrix.getMatrixScaled(yabc834y860, yCoef))
                .add();
        line834y860.newExtension(LineFortescueAdder.class)
                .withOpenPhaseA(false)
                .withOpenPhaseB(false)
                .withOpenPhaseC(false)
                .add();

        line834y860.newExtension(LineFortescueAdder.class)
                .withRz(r0y * length834y860InFeet / feetInMile)
                .withXz(x0y * length834y860InFeet / feetInMile)
                .add();

        // line 860y836
        Line line860y836 = network.newLine()
                .setId("860y836")
                .setVoltageLevel1(vl860.getId())
                .setBus1(bus860.getId())
                .setConnectableBus1(bus860.getId())
                .setVoltageLevel2(vl836.getId())
                .setBus2(bus836.getId())
                .setConnectableBus2(bus836.getId())
                .setR(ry * length860y836InFeet / feetInMile)
                .setX(xy * length860y836InFeet / feetInMile)
                .setG1(0.0)
                .setB1(0.0)
                .setG2(0.0)
                .setB2(0.0)
                .add();

        ComplexMatrix yabc860y836 = LineAsymmetrical.getAdmittanceMatrixFromImpedanceAndBmatrix(zy301, b301, true, true, true, length860y836InFeet / feetInMile);
        line860y836.newExtension(LineAsymmetricalAdder.class)
                .withYabc(ComplexMatrix.getMatrixScaled(yabc860y836, yCoef))
                .add();

        line860y836.newExtension(LineFortescueAdder.class)
                .withOpenPhaseA(false)
                .withOpenPhaseB(false)
                .withOpenPhaseC(false)
                .add();

        line860y836.newExtension(LineFortescueAdder.class)
                .withRz(r0y * length860y836InFeet / feetInMile)
                .withXz(x0y * length860y836InFeet / feetInMile)
                .add();

        // line 836y840
        Line line836y840 = network.newLine()
                .setId("836y840")
                .setVoltageLevel1(vl836.getId())
                .setBus1(bus836.getId())
                .setConnectableBus1(bus836.getId())
                .setVoltageLevel2(vl840.getId())
                .setBus2(bus840.getId())
                .setConnectableBus2(bus840.getId())
                .setR(ry * length836y840InFeet / feetInMile)
                .setX(xy * length836y840InFeet / feetInMile)
                .setG1(0.0)
                .setB1(0.0)
                .setG2(0.0)
                .setB2(0.0)
                .add();

        ComplexMatrix yabc836y840 = LineAsymmetrical.getAdmittanceMatrixFromImpedanceAndBmatrix(zy301, b301, true, true, true, length836y840InFeet / feetInMile);
        line836y840.newExtension(LineAsymmetricalAdder.class)
                .withYabc(ComplexMatrix.getMatrixScaled(yabc836y840, yCoef))
                .add();

        line836y840.newExtension(LineFortescueAdder.class)
                .withOpenPhaseA(false)
                .withOpenPhaseB(false)
                .withOpenPhaseC(false)
                .add();

        line836y840.newExtension(LineFortescueAdder.class)
                .withRz(r0y * length836y840InFeet / feetInMile)
                .withXz(x0y * length836y840InFeet / feetInMile)
                .add();

        // line 836y862
        Line line836y862 = network.newLine()
                .setId("836y862")
                .setVoltageLevel1(vl836.getId())
                .setBus1(bus836.getId())
                .setConnectableBus1(bus836.getId())
                .setVoltageLevel2(vl862.getId())
                .setBus2(bus862.getId())
                .setConnectableBus2(bus862.getId())
                .setR(ry * length836y862InFeet / feetInMile)
                .setX(xy * length836y862InFeet / feetInMile)
                .setG1(0.0)
                .setB1(0.0)
                .setG2(0.0)
                .setB2(0.0)
                .add();

        ComplexMatrix yabc836y862 = LineAsymmetrical.getAdmittanceMatrixFromImpedanceAndBmatrix(zy301, b301, true, true, true, length836y862InFeet / feetInMile);
        line836y862.newExtension(LineAsymmetricalAdder.class)
                .withYabc(ComplexMatrix.getMatrixScaled(yabc836y862, yCoef))
                .add();

        line836y862.newExtension(LineFortescueAdder.class)
                .withOpenPhaseA(false)
                .withOpenPhaseB(false)
                .withOpenPhaseC(false)
                .add();

        line836y862.newExtension(LineFortescueAdder.class)
                .withRz(r0y * length836y862InFeet / feetInMile)
                .withXz(x0y * length836y862InFeet / feetInMile)
                .add();

        // line 862y838
        Line line862y838 = network.newLine()
                .setId("862y838")
                .setVoltageLevel1(vl862.getId())
                .setBus1(bus862.getId())
                .setConnectableBus1(bus862.getId())
                .setVoltageLevel2(vl838.getId())
                .setBus2(bus838.getId())
                .setConnectableBus2(bus838.getId())
                .setR(ry * length862y838InFeet / feetInMile)
                .setX(xy * length862y838InFeet / feetInMile)
                .setG1(0.0)
                .setB1(0.0)
                .setG2(0.0)
                .setB2(0.0)
                .add();

        ComplexMatrix yabc862y838 = LineAsymmetrical.getAdmittanceMatrixFromImpedanceAndBmatrix(zy301, b301, true, true, true, length862y838InFeet / feetInMile);
        line862y838.newExtension(LineAsymmetricalAdder.class)
                .withYabc(ComplexMatrix.getMatrixScaled(yabc862y838, yCoef))
                .add();

        line862y838.newExtension(LineFortescueAdder.class)
                .withOpenPhaseA(false)
                .withOpenPhaseB(false)
                .withOpenPhaseC(false)
                .add();

        line862y838.newExtension(LineFortescueAdder.class)
                .withRz(r0y * length862y838InFeet / feetInMile)
                .withXz(x0y * length862y838InFeet / feetInMile)
                .add();

        return network;
    }

    @Test
    void test800802() {

        double micro = 0.000001;

        // config 300 :
        // building of Yabc from given Y impedance matrix Zy
        ComplexMatrix zy300 = new ComplexMatrix(3, 3);
        zy300.set(1, 1, new Complex(1.3368, 1.3343));
        zy300.set(1, 2, new Complex(0.2101, 0.5779));
        zy300.set(1, 3, new Complex(0.2130, 0.5015));
        zy300.set(2, 1, new Complex(0.2101, 0.5779));
        zy300.set(2, 2, new Complex(1.3238, 1.3569));
        zy300.set(2, 3, new Complex(0.2066, 0.4591));
        zy300.set(3, 1, new Complex(0.2130, 0.5015));
        zy300.set(3, 2, new Complex(0.2066, 0.4591));
        zy300.set(3, 3, new Complex(1.3294, 1.3471));

        ComplexMatrix b300 = new ComplexMatrix(3, 3);

        b300.set(1, 1, new Complex(0, micro * 5.335));
        b300.set(1, 2, new Complex(0, micro * -1.5313));
        b300.set(1, 3, new Complex(0, micro * -0.9443));
        b300.set(2, 1, new Complex(0, micro * -1.5313));
        b300.set(2, 2, new Complex(0, micro * 5.0979));
        b300.set(2, 3, new Complex(0, micro * -0.6212));
        b300.set(3, 1, new Complex(0, micro * -0.9443));
        b300.set(3, 2, new Complex(0, micro * -0.6212));
        b300.set(3, 3, new Complex(0, micro * 4.888));

        double length800y802InFeet = 2580.;
        double feetInMile = 5280;
        ComplexMatrix yabc300 = ComplexMatrix.getMatrixScaled(LineAsymmetrical.getAdmittanceMatrixFromImpedanceAndBmatrix(zy300, b300, true, true, true), feetInMile / length800y802InFeet / Math.sqrt(3));

        double vBase = 24.9;
        ComplexMatrix v = new ComplexMatrix(6, 1);
        v.set(1, 1, ComplexUtils.polar2Complex(vBase * 1.05, Math.toRadians(0.)));
        v.set(2, 1, ComplexUtils.polar2Complex(vBase * 1.05, Math.toRadians(-120.)));
        v.set(3, 1, ComplexUtils.polar2Complex(vBase * 1.05, Math.toRadians(120.)));
        v.set(4, 1, ComplexUtils.polar2Complex(vBase * 1.0475, Math.toRadians(-0.05)));
        v.set(5, 1, ComplexUtils.polar2Complex(vBase * 1.0484, Math.toRadians(-120.07)));
        v.set(6, 1, ComplexUtils.polar2Complex(vBase * 1.0484, Math.toRadians(119.95)));

        DenseMatrix i800Real = yabc300.getRealCartesianMatrix().times(v.getRealCartesianMatrix());
        ComplexMatrix i800 = ComplexMatrix.getComplexMatrixFromRealCartesian(i800Real);

        assertEquals(0.050698174712604675, i800.getTerm(1, 1).abs(), 0.00001);
        assertEquals(0.04553392610054142, i800.getTerm(5, 1).abs(), 0.00001);

    }
}
