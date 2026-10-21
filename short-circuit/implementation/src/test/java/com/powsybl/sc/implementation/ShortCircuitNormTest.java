/**
 * Copyright (c) 2022, Jean-Baptiste Heyberger & Geoffroy Jamgotchian
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.implementation;

import com.powsybl.iidm.network.*;
import com.powsybl.iidm.network.extensions.*;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.sc.extensions.*;
import com.powsybl.sc.util.extensions.GeneratorNorm;
import com.powsybl.sc.util.extensions.ThreeWindingsTransformerNorm;
import com.powsybl.sc.util.extensions.TwoWindingsTransformerNorm;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class ShortCircuitNormTest {

    @Test
    void shortCircuitNormTest() {

        LoadFlowParameters loadFlowParameters = LoadFlowParameters.load();
        loadFlowParameters.setTwtSplitShuntAdmittance(true);

        Network network = createNormNetwork();

        MatrixFactory matrixFactory = new DenseMatrixFactory();

        ShortCircuitNormIec shortCircuitNormIec = new ShortCircuitNormIec();

        TwoWindingsTransformer t2w = network.getTwoWindingsTransformer("T12");

        Generator g1T2w = shortCircuitNormIec.getAssociatedGenerator(network, t2w);

        Generator g1 = network.getGenerator("G1");
        assertEquals(g1, g1T2w);

        double ks = shortCircuitNormIec.getKs(t2w, g1);
        assertEquals(0.1950430724873738, ks, 0.000001);

        shortCircuitNormIec.applyNormToNetwork(network);
        TwoWindingsTransformerNorm extensionT2wNorm = shortCircuitNormIec.getNormExtensions().getNormExtension(t2w);
        double knormT2w = extensionT2wNorm.getkNorm();
        assertEquals(0.1950430724873738, knormT2w, 0.000001);

        GeneratorNorm extensionGenNorm = shortCircuitNormIec.getNormExtensions().getNormExtension(g1);
        double kg = extensionGenNorm.getkG();
        assertEquals(0.1950430724873738, kg, 0.000001);

        t2w.setProperty("isPartOfGeneratorUnit", String.valueOf(false));
        shortCircuitNormIec.applyNormToT2W(network);
        shortCircuitNormIec.applyNormToGenerators(network);
        kg = extensionGenNorm.getkG();
        assertEquals(0.1899770186565329, kg, 0.000001);

        ShortCircuitNormNone shortCircuitNormNone = new ShortCircuitNormNone();
        shortCircuitNormNone.applyNormToNetwork(network);
        extensionGenNorm = shortCircuitNormNone.getNormExtensions().getNormExtension(g1);
        kg = extensionGenNorm.getkG();
        assertEquals(1.0, kg, 0.000001);

        double roOverR = 0.15;
        double xoOverX = 3.;
        double rOverX = 0.1;
        double voltageFactor = 1.1;
        double ikQmax = 38.;

        g1.newExtension(GeneratorShortCircuitAdder.class)
                .withStepUpTransformerX(0.)
                .withDirectTransX(0.)
                .withDirectSubtransX(0.)
                .add();
        g1.newExtension(GeneratorShortCircuitAdder2.class)
                .withSubTransRd(0.)
                .withTransRd(0.)
                .withRatedU(0.)
                .withCosPhi(0.)
                .withMaxR1ToX1Ratio(rOverX) // extensions for feeder type
                .withCq(voltageFactor)
                .withIkQmax(ikQmax)
                .add();

        g1.newExtension(GeneratorFortescueAdder.class)
                .withGrounded(true)
                .add();

        g1.newExtension(GeneratorFortescueTypeAdder.class)
                .withGeneratorType(GeneratorFortescueType.GeneratorType.FEEDER)
                .add();

        shortCircuitNormNone.applyNormToNetwork(network);
        GeneratorShortCircuit extension1Gen = g1.getExtension(GeneratorShortCircuit.class);
        double subtransX = extension1Gen.getDirectSubtransX();
        //assertEquals(332.59657293872044, subtransX, 0.000001);

        double rhoB2 = 1. / (120. * 120.);
        double rT3a = 0.045714 * rhoB2;
        double xT3a = 8.0969989 * rhoB2;
        double rT3b = 0.053563 * rhoB2;
        double xT3b = -0.079062 * rhoB2;
        double rT3c = 0.408560 * rhoB2;
        double xT3c = 20.292035 * rhoB2;

        ThreeWindingsTransformer t3 = network.getSubstation("S1").newThreeWindingsTransformer()
                .setId("T3")
                .setRatedU0(1.0D)
                .newLeg1()
                .setR(rT3a)
                .setX(xT3a)
                .setG(0.)
                .setB(0.)
                .setRatedU(400.0D)
                .setVoltageLevel(network.getVoltageLevel("VL_1").getId())
                .setBus(network.getBusBreakerView().getBus("B1").getId())
                .add()
                .newLeg2()
                .setR(rT3b)
                .setX(xT3b)
                .setG(0.0D)
                .setB(0.0D)
                .setRatedU(120.0D)
                .setVoltageLevel(network.getVoltageLevel("VL_2").getId())
                .setBus(network.getBusBreakerView().getBus("B2").getId())
                .add()
                .newLeg3()
                .setR(rT3c)
                .setX(xT3c)
                .setG(0.0D)
                .setB(0.0D)
                .setRatedU(30.)
                .setVoltageLevel(network.getVoltageLevel("VL_3").getId())
                .setBus(network.getBusBreakerView().getBus("B3").getId())
                .add()
                .add();

        shortCircuitNormNone.applyNormToNetwork(network);
        ThreeWindingsTransformerNorm extensionT3wNorm = shortCircuitNormNone.getNormExtensions().getNormExtension(t3);
        double ktRa = extensionT3wNorm.getLeg1().getKtR();
        double ktXoc = extensionT3wNorm.getLeg3().getKtXo();
        assertEquals(1., ktRa, 0.000001);
        assertEquals(1., ktXoc, 0.000001);

        Generator g1bis = network.getVoltageLevel("VL_1").newGenerator()
                .setId("G1_BIS")
                .setBus(network.getBusBreakerView().getBus("B1").getId())
                .setMinP(0.0)
                .setMaxP(100.)
                .setTargetP(0.)
                .setTargetQ(0.)
                .setTargetV(100.)
                .setVoltageRegulatorOn(false)
                .setRatedS(150.)
                .add();

        shortCircuitNormIec.setKg(g1bis, 12.);
        extensionGenNorm = shortCircuitNormIec.getNormExtensions().getNormExtension(g1bis); //g1bis.getExtension(GeneratorNorm.class);
        kg = extensionGenNorm.getkG();
        assertEquals(12., kg, 0.000001);

        var t12bis = network.getSubstation("S1").newTwoWindingsTransformer()
                .setId("T12_BIS")
                .setVoltageLevel1(network.getVoltageLevel("VL_1").getId())
                .setBus1(network.getBusBreakerView().getBus("B1").getId())
                .setConnectableBus1(network.getBusBreakerView().getBus("B1").getId())
                .setRatedU1(21.)
                .setRatedS(150.)
                .setVoltageLevel2(network.getVoltageLevel("VL_2").getId())
                .setBus2(network.getBusBreakerView().getBus("B2").getId())
                .setConnectableBus2(network.getBusBreakerView().getBus("B2").getId())
                .setRatedU2(225.)
                .setR(0.1)
                .setX(1.)
                .setG(0.0D)
                .setB(0.0D)
                .setRatedS(31.5)
                .add();

        shortCircuitNormNone.applyNormToT2W(network);
        extensionT2wNorm = shortCircuitNormNone.getNormExtensions().getNormExtension(t12bis);
        knormT2w = extensionT2wNorm.getkNorm();
        assertEquals(1.0, knormT2w, 0.000001);

    }

    public static Network createNormNetwork() {

        Network network = Network.create("ShortCircuit_Norm", "IEC_Norm");

        double vLv = 20;
        double vMv = 100.;
        double vHv = 220.;
        double pEquivalentFeeder2 = 100.;
        double qEquivalentFeeder2 = 50.;
        double xG1 = 20.;

        Substation substation1 = network.newSubstation()
                .setId("S1")
                .setCountry(Country.FR)
                .add();
        VoltageLevel vl1 = substation1.newVoltageLevel()
                .setId("VL_1")
                .setNominalV(vLv)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(2 * vLv)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus1 = vl1.getBusBreakerView().newBus()
                .setId("B1")
                .add();
        bus1.setV(vLv).setAngle(0.);

        VoltageLevel vl2 = substation1.newVoltageLevel()
                .setId("VL_2")
                .setNominalV(vHv)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(2 * vHv)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus2 = vl2.getBusBreakerView().newBus()
                .setId("B2")
                .add();
        bus2.setV(vHv).setAngle(0.);

        VoltageLevel vl3 = substation1.newVoltageLevel()
                .setId("VL_3")
                .setNominalV(vMv)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(2 * vMv)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus3 = vl3.getBusBreakerView().newBus()
                .setId("B3")
                .add();
        bus3.setV(vMv).setAngle(0.);

        Load load = vl2.newLoad()
                .setId("LOAD_FEEDER2") // we try to model the feeder through a load that will be transformed into an impedance
                .setBus(bus2.getId())
                .setP0(pEquivalentFeeder2)
                .setQ0(qEquivalentFeeder2)
                .add();

        load.newExtension(LoadShortCircuitAdder.class)
                .withLoadShortCircuitType(LoadShortCircuit.LoadShortCircuitType.ASYNCHRONOUS_MACHINE)
                .withAsynchronousMachineLoadData(new LoadShortCircuit.AsynchronousMachineLoadData(100., 0.88, 5.828, 220., 97.5, 0.5, 1, 0.1))
                .add();

        Generator g1 = vl1.newGenerator()
                .setId("G1")
                .setBus(bus1.getId())
                .setMinP(0.0)
                .setMaxP(100.)
                .setTargetP(0.)
                .setTargetQ(0.)
                .setTargetV(vHv)
                .setVoltageRegulatorOn(false)
                .setRatedS(150.)
                .add();

        g1.newExtension(GeneratorShortCircuitAdder.class)
                .withDirectSubtransX(xG1) // TODO : add table to store coeffs for homopolar values
                .withDirectTransX(xG1)
                .withStepUpTransformerX(0.)
                .add();

        g1.newExtension(GeneratorShortCircuitAdder2.class)
                .withTransRd(0.1)
                .add();

        g1.newExtension(GeneratorFortescueAdder.class)
                .withGrounded(true)
                .add();

        g1.newExtension(GeneratorFortescueTypeAdder.class)
                .add();

        var t12 = substation1.newTwoWindingsTransformer()
                .setId("T12")
                .setVoltageLevel1(vl1.getId())
                .setBus1(bus1.getId())
                .setConnectableBus1(bus1.getId())
                .setRatedU1(21.)
                .setRatedS(150.)
                .setVoltageLevel2(vl2.getId())
                .setBus2(bus2.getId())
                .setConnectableBus2(bus2.getId())
                .setRatedU2(225.)
                .setR(0.1)
                .setX(1.)
                .setG(0.0D)
                .setB(0.0D)
                .setRatedS(31.5)
                .add();
        t12.newExtension(TwoWindingsTransformerFortescueAdder.class)
                .withConnectionType1(WindingConnectionType.Y)
                .withConnectionType2(WindingConnectionType.Y)
                .add();
        t12.setProperty("isPartOfGeneratorUnit", String.valueOf(true));

        return network;
    }
}
