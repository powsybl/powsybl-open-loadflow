/**
 * Copyright (c) 2022, Jean-Baptiste Heyberger & Geoffroy Jamgotchian
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.implementation;

import com.powsybl.computation.ComputationManager;
import com.powsybl.computation.local.LocalComputationManager;
import com.powsybl.iidm.network.*;
import com.powsybl.iidm.network.extensions.GeneratorFortescueAdder;
import com.powsybl.iidm.network.extensions.GeneratorShortCircuitAdder;
import com.powsybl.iidm.network.extensions.LineFortescueAdder;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.sc.extensions.GeneratorFortescueTypeAdder;
import com.powsybl.sc.util.ReferenceNetwork;
import com.powsybl.shortcircuit.*;
import org.apache.commons.math3.complex.Complex;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class ShortCircuitMonophasedTest {

    private LoadFlowParameters parameters;

    private MatrixFactory matrixFactory;

    private LoadFlow.Runner loadFlowRunner;

    @BeforeEach
    void setUp() {
        parameters = new LoadFlowParameters();
        matrixFactory = new DenseMatrixFactory();
        loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(matrixFactory));
    }

    @Test
    void shortCircuitMono6NodesIec9094() {

        LoadFlowParameters loadFlowParameters = LoadFlowParameters.load();
        loadFlowParameters.setTwtSplitShuntAdmittance(true);

        Network network = ReferenceNetwork.create6NodesIec9094();

        MatrixFactory matrixFactory = new DenseMatrixFactory();

        List<ShortCircuitFault> faultList = new ArrayList<>();
        ShortCircuitFault sc1 = new ShortCircuitFault("B3", "F1", "B3", new ShortCircuitFaultImpedance(new Complex(0.)), ShortCircuitFault.ShortCircuitType.MONOPHASED);
        faultList.add(sc1);

        ShortCircuitEngineParameters.PeriodType periodType = ShortCircuitEngineParameters.PeriodType.SUB_TRANSIENT;
        ShortCircuitNormIec shortCircuitNormIec = new ShortCircuitNormIec();
        ShortCircuitEngineParameters scbParameters = new ShortCircuitEngineParameters(loadFlowParameters, matrixFactory,
                ShortCircuitEngineParameters.AnalysisType.SELECTIVE, faultList, false, ShortCircuitEngineParameters.VoltageProfileType.NOMINAL, false, periodType, shortCircuitNormIec);
        ShortCircuitUnbalancedEngine scbEngine = new ShortCircuitUnbalancedEngine(network, scbParameters);

        scbEngine.run();
        List<Double> val = new ArrayList<>();
        for (Map.Entry<ShortCircuitFault, ShortCircuitResult> res : scbEngine.resultsPerFault.entrySet()) {
            val.add(res.getValue().getIk().abs());
        }

        // here Icc = 1/sqrt(3)*Eth(pu)/Zth(pu100)*Sb100/Vb*1000
        // and I"k = sqrt(3) * cmax * Un /(Zeq) and expected I"k = 35.64 kA with some approximations on the impedance values
        assertEquals(35.70435548244156, val.getFirst(), 0.00001);

    }

    @Test
    void shortCircuitMono8NodesIEC9094() {

        LoadFlowParameters loadFlowParameters = LoadFlowParameters.load();
        loadFlowParameters.setTwtSplitShuntAdmittance(true);

        Network network = ReferenceNetwork.create8NodesIEC9094();

        MatrixFactory matrixFactory = new DenseMatrixFactory();

        Complex zFault = new Complex(0.);
        ShortCircuitFaultImpedance scz = new ShortCircuitFaultImpedance(zFault);
        List<ShortCircuitFault> faultList = new ArrayList<>();
        ShortCircuitFault sc1 = new ShortCircuitFault("B2", "sc1", "B2", scz, ShortCircuitFault.ShortCircuitType.MONOPHASED);
        faultList.add(sc1);
        ShortCircuitFault sc2 = new ShortCircuitFault("B3", "sc2", "B3", scz, ShortCircuitFault.ShortCircuitType.MONOPHASED);
        faultList.add(sc2);
        ShortCircuitFault sc3 = new ShortCircuitFault("B4", "sc3", "B4", scz, ShortCircuitFault.ShortCircuitType.MONOPHASED);
        faultList.add(sc3);
        ShortCircuitFault sc4 = new ShortCircuitFault("B5", "sc4", "B5", scz, ShortCircuitFault.ShortCircuitType.MONOPHASED);
        faultList.add(sc4);

        // additional faults
        ShortCircuitFault sc5 = new ShortCircuitFault("B2", "sc5", "B2", scz, ShortCircuitFault.ShortCircuitType.BIPHASED);
        faultList.add(sc5);
        ShortCircuitFault sc6 = new ShortCircuitFault("B3", "sc6", "B3", scz, ShortCircuitFault.ShortCircuitType.BIPHASED_GROUND);
        faultList.add(sc6);

        // additional faults with fault impedance
        Complex zFault2 = new Complex(0.0004, 0.0001);
        ShortCircuitFaultImpedance scz2 = new ShortCircuitFaultImpedance(zFault2, zFault2.multiply(3.), zFault2.multiply(4.));
        ShortCircuitFault sc7 = new ShortCircuitFault("B2", "sc5z", "B2", scz2, ShortCircuitFault.ShortCircuitType.BIPHASED);
        faultList.add(sc7);
        ShortCircuitFault sc8 = new ShortCircuitFault("B3", "sc6z", "B3", scz2, ShortCircuitFault.ShortCircuitType.BIPHASED_GROUND);
        faultList.add(sc8);

        ShortCircuitEngineParameters.PeriodType periodType = ShortCircuitEngineParameters.PeriodType.TRANSIENT;
        ShortCircuitNormIec shortCircuitNormIec = new ShortCircuitNormIec();
        ShortCircuitEngineParameters scbParameters = new ShortCircuitEngineParameters(loadFlowParameters, matrixFactory,
                ShortCircuitEngineParameters.AnalysisType.SELECTIVE, faultList, false, ShortCircuitEngineParameters.VoltageProfileType.NOMINAL, false, periodType, shortCircuitNormIec);
        ShortCircuitUnbalancedEngine scbEngine = new ShortCircuitUnbalancedEngine(network, scbParameters);

        scbEngine.run();
        Map<String, Double> values = new HashMap<>();
        for (Map.Entry<ShortCircuitFault, ShortCircuitResult> res : scbEngine.resultsPerFault.entrySet()) {
            values.put(res.getKey().getFaultId(), res.getValue().getIk().abs());
        }

        //I"k = sqrt(3) * cmax * Un /(Zeq)
        assertEquals(15.9722, values.get("sc1"), 0.00001); // bus 2 : expected doc value : 15.9722 kA
        assertEquals(10.410558286260768, values.get("sc2"), 0.00001); // bus 3 : expected doc value : 10.4106 kA
        assertEquals(9.049787523396647, values.get("sc3"), 0.00001); // bus 4 : expected doc value : 9.0498 kA
        assertEquals(17.0452, values.get("sc4"), 0.00001); // bus 5 : expected doc value : 17.0452 kA

        // test to check that non monophased faults does not have an impact on monophased results
        assertEquals(27.524923816893264, values.get("sc5"), 0.00001); // biphased not in ref doc
        assertEquals(7.078052952491013, values.get("sc6"), 0.00001); // biphased not in ref doc

        assertEquals(27.215335984438305, values.get("sc5z"), 0.00001);
        assertEquals(7.076191397298427, values.get("sc6z"), 0.00001);

    }

    @Test
    void shortCircuitProviderIecTestNetworkMono() {

        LoadFlowParameters loadFlowParameters = LoadFlowParameters.load();
        loadFlowParameters.setTwtSplitShuntAdmittance(true);

        Network network = ReferenceNetwork.create8NodesIEC9094();

        ShortCircuitAnalysisProvider provider = new OpenShortCircuitProvider(new DenseMatrixFactory());
        ComputationManager cm = LocalComputationManager.getDefault();
        ShortCircuitParameters scp = new ShortCircuitParameters();

        List<Fault> faults = new ArrayList<>();
        BusFault bf1 = new BusFault("F1", "B2", 0., 0., Fault.ConnectionType.SERIES, Fault.FaultType.SINGLE_PHASE);
        faults.add(bf1);

        BusFault bf2 = new BusFault("F2", "B2", 0.0001, 0.0002, Fault.ConnectionType.SERIES, Fault.FaultType.SINGLE_PHASE);
        faults.add(bf2);

        ShortCircuitAnalysisResult scar = provider.run(network, faults, scp, cm, Collections.emptyList()).join();

        List<FaultResult> frs = scar.getFaultResults();
        MagnitudeFaultResult magnitudeFaultResult = (MagnitudeFaultResult) frs.get(0);
        assertEquals(14.548104511643936, magnitudeFaultResult.getCurrent(), 0.00001);

        MagnitudeFaultResult magnitudeFaultResult2 = (MagnitudeFaultResult) frs.get(1);
        assertEquals(14.464754153502392, magnitudeFaultResult2.getCurrent(), 0.00001);

    }

    @Test
    void computeIoTestNew() {
        Network network = createGiard(NetworkFactory.findDefault());

        loadFlowRunner.run(network, parameters);

        network.getGenerator("GB").newExtension(GeneratorFortescueAdder.class)
                .withGrounded(true)
                .withXz(130) // initialized with subtransXd by default
                .withRz(0)
                .withRn(0)
                .withXn(0)
                .add();

        network.getGenerator("GB").newExtension(GeneratorFortescueTypeAdder.class)
                .add();

        List<ShortCircuitFault> faultList = new ArrayList<>();
        ShortCircuitFault sc1 = new ShortCircuitFault("BP", "sc1", "BP", new ShortCircuitFaultImpedance(new Complex(0.)), ShortCircuitFault.ShortCircuitType.MONOPHASED);

        faultList.add(sc1);

        LoadFlowParameters loadFlowParameters = new LoadFlowParameters();
        ShortCircuitEngineParameters.PeriodType periodType = ShortCircuitEngineParameters.PeriodType.TRANSIENT;
        ShortCircuitNorm shortCircuitNorm = new ShortCircuitNormNone();
        ShortCircuitEngineParameters scunbParameters = new ShortCircuitEngineParameters(loadFlowParameters, matrixFactory,
                ShortCircuitEngineParameters.AnalysisType.SELECTIVE, faultList, true, ShortCircuitEngineParameters.VoltageProfileType.CALCULATED, false, periodType, shortCircuitNorm);
        ShortCircuitUnbalancedEngine scunbEngine = new ShortCircuitUnbalancedEngine(network, scunbParameters);

        scunbEngine.run();

        //assertEquals(2.8286512112174034, scunbEngine.results.get(sc1).getIox() / Math.sqrt(3), 0.000001); // results changed with modification of input data
        //assertEquals(1.6331225382399293, scunbEngine.results.get(sc1).getIoy() / Math.sqrt(3), 0.000001);
        assertEquals(2.7099928109273916, scunbEngine.resultsPerFault.get(sc1).getIo().getReal() / Math.sqrt(3), 0.000001);
        assertEquals(1.5646150788908801, scunbEngine.resultsPerFault.get(sc1).getIo().getImaginary() / Math.sqrt(3), 0.000001);

    }

    public static Network createGiard(NetworkFactory networkFactory) {

        //   M1           P             M2            machine M1: isolated neutral with X'd1, Xmi1, Xmo1
        //  (~)-|---Xd1---|---Xd2---|--(~)            machine M2: grounded neutral through Xn, with X'd2, Xmi2, Xmo2
        //          Xi1       Xi2       |             In P, there is a direct monophased fault to the ground (phase 1)
        //          Xo1       Xo2       Xn
        //                              |
        //                            /////
        //
        // Direct schema:
        //                    P
        //  --X'd1--|---Xd1---|---Xd2---|--X'd2--           Xd'1 = 65 ohms  Xd'2 = 130 ohms
        //  ^                                   ^           Xd1 = 30 ohms   Xd2 = 15 ohms
        //  | Ed1                               | Ed2
        //  |                                   |
        // ///                                 ///
        //
        // Inverse            P
        //  --Xmi1--|---Xi1---|---Xi2---|--Xmi2--           Xi = Xd
        //  |                                   |
        //  |                                   |
        // ///                                 ///
        //
        // Homopolar          P
        //  --XmO1--|---Xo1---|---Xo2---|--Xmo2--           Xmo1 = Xd'1 / 3
        //                                      |           Xmo2 = Xd'2 / 3
        //                                    3.Xn          Xo1 = 3 * Xd1
        //                                      |           Xo2 = 3 * Xd2
        //                                     ///          Xn = 25 ohms

        Objects.requireNonNull(networkFactory);

        double pgen = 0.;
        double xd1 = 30.;
        double xd2 = 15.;
        double coeffXo1 = 3.;
        double coeffXo2 = 3.;
        double uac = 420.;
        double ubc = 410.;

        Network network = networkFactory.createNetwork("Giard exemple", "test");
        Substation substationA = network.newSubstation()
                .setId("SA")
                .setCountry(Country.FR)
                .add();
        VoltageLevel vlA = substationA.newVoltageLevel()
                .setId("VL_A")
                .setNominalV(380.0)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(500)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus busA = vlA.getBusBreakerView().newBus()
                .setId("BA")
                .add();
        busA.setV(uac).setAngle(0.);
        Generator genA = vlA.newGenerator()
                .setId("GA")
                .setBus(busA.getId())
                .setMinP(-10.0)
                .setMaxP(150)
                .setTargetP(pgen)
                .setTargetV(uac)
                .setVoltageRegulatorOn(true)
                .add();

        genA.newExtension(GeneratorShortCircuitAdder.class)
                .withDirectSubtransX(65)
                .withDirectTransX(65)
                .withStepUpTransformerX(0.)
                .add();

        Substation substationP = network.newSubstation()
                .setId("SP")
                .setCountry(Country.FR)
                .add();
        VoltageLevel vlP = substationP.newVoltageLevel()
                .setId("VL_P")
                .setNominalV(380.0)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(500)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus busP = vlP.getBusBreakerView().newBus()
                .setId("BP")
                .add();
        busP.setV(413.0).setAngle(0);

        Substation substationB = network.newSubstation()
                .setId("SB")
                .setCountry(Country.FR)
                .add();
        VoltageLevel vlB = substationB.newVoltageLevel()
                .setId("VL_B")
                .setNominalV(380.0)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(500)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus busB = vlB.getBusBreakerView().newBus()
                .setId("BB")
                .add();
        busB.setV(ubc).setAngle(0.);
        Generator genB = vlB.newGenerator()
                .setId("GB")
                .setBus(busB.getId())
                .setMinP(-10.0)
                .setMaxP(150)
                .setTargetP(pgen)
                .setTargetV(ubc)
                .setVoltageRegulatorOn(true)
                .add();

        genB.newExtension(GeneratorShortCircuitAdder.class)
                .withDirectSubtransX(130)
                .withDirectTransX(130)
                .withStepUpTransformerX(0.)
                .add();

        Line babp = network.newLine()
                .setId("BA_BP")
                .setVoltageLevel1(vlA.getId())
                .setBus1(busA.getId())
                .setConnectableBus1(busA.getId())
                .setVoltageLevel2(vlP.getId())
                .setBus2(busP.getId())
                .setConnectableBus2(busP.getId())
                .setR(0.0)
                .setX(xd1)
                .setG1(0.0)
                .setB1(0.0)
                .setG2(0.0)
                .setB2(0.0)
                .add();

        Line bpbb = network.newLine()
                .setId("BP_BB")
                .setVoltageLevel1(vlP.getId())
                .setBus1(busP.getId())
                .setConnectableBus1(busP.getId())
                .setVoltageLevel2(vlB.getId())
                .setBus2(busB.getId())
                .setConnectableBus2(busB.getId())
                .setR(0.0)
                .setX(xd2)
                .setG1(0.0)
                .setB1(0.0)
                .setG2(0.0)
                .setB2(0.0)
                .add();

        babp.newExtension(LineFortescueAdder.class)
                .withRz(0.0)
                .withXz(coeffXo1 * xd1)
                .add();
        bpbb.newExtension(LineFortescueAdder.class)
                .withRz(0.0)
                .withXz(coeffXo2 * xd2)
                .add();

        return network;
    }
}
