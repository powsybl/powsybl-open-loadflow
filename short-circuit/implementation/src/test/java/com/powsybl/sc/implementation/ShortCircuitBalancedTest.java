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
import com.powsybl.iidm.network.extensions.GeneratorShortCircuitAdder;
import com.powsybl.iidm.network.extensions.ThreeWindingsTransformerFortescue;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.sc.util.ReferenceNetwork;
import com.powsybl.sc.util.extensions.ThreeWindingsTransformerNorm;
import com.powsybl.shortcircuit.*;
import org.apache.commons.math3.complex.Complex;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class ShortCircuitBalancedTest {

    private LoadFlowParameters parameters;

    private MatrixFactory matrixFactory;

    private LoadFlow.Runner loadFlowRunner;

    @BeforeEach
    void setUp() {
        parameters = new LoadFlowParameters();
        matrixFactory = new DenseMatrixFactory();
        loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(matrixFactory));
    }

    @Disabled
    @Test
    void computeIccTest() {
        Network nt2 = create2n(NetworkFactory.findDefault());
        loadFlowRunner.run(nt2, parameters);

        List<ShortCircuitFault> tmpV = new ArrayList<>();
        ShortCircuitFault sc2 = new ShortCircuitFault("B2", "sc2", "B2", new ShortCircuitFaultImpedance(new Complex(0.)), ShortCircuitFault.ShortCircuitType.TRIPHASED_GROUND);
        tmpV.add(sc2);

        ShortCircuitEngineParameters.PeriodType periodType = ShortCircuitEngineParameters.PeriodType.TRANSIENT;

        ShortCircuitEngineParameters.VoltageProfileType vp = ShortCircuitEngineParameters.VoltageProfileType.CALCULATED;
        ShortCircuitEngineParameters.AnalysisType at = ShortCircuitEngineParameters.AnalysisType.SELECTIVE;

        LoadFlowParameters loadFlowParameters = new LoadFlowParameters();
        ShortCircuitNorm shortCircuitNorm = new ShortCircuitNormNone();
        ShortCircuitEngineParameters scbParameters = new ShortCircuitEngineParameters(loadFlowParameters, matrixFactory, at, tmpV, true, vp, false, periodType, shortCircuitNorm);
        ShortCircuitBalancedEngine scbEngine = new ShortCircuitBalancedEngine(nt2, scbParameters);

        scbEngine.run();

        scbEngine.resultsPerFault.get(sc2).updateFeedersResult();

        assertEquals(-0.4316661015058293, scbEngine.resultsPerFault.get(sc2).getId().getReal(), 0.000001);
        assertEquals(-4.617486568622836, scbEngine.resultsPerFault.get(sc2).getId().getImaginary(), 0.000001);

    }

    @Test
    void openShortCircuitProvider2n() {

        //set up LF info
        LoadFlowParameters loadFlowParameters = LoadFlowParameters.load();
        loadFlowParameters.setTwtSplitShuntAdmittance(true);
        Network nt2 = create2n(NetworkFactory.findDefault());
        LoadFlow.run(nt2, loadFlowParameters);

        //set up ShortCircuitProvider info
        ShortCircuitAnalysisProvider provider = new OpenShortCircuitProvider(new DenseMatrixFactory());
        ComputationManager cm = LocalComputationManager.getDefault();
        ShortCircuitParameters scp = new ShortCircuitParameters();

        //CompletableFuture<ShortCircuitAnalysisResult> scar = provider.run(nt2, scp, cm);
        BusFault bf1 = new BusFault("F1", "B1");
        BusFault bf2 = new BusFault("F2", "B2");

        List<Fault> faults = List.of(bf1, bf2);

        ShortCircuitAnalysisResult scar = provider.run(nt2, faults, scp, cm, Collections.emptyList()).join();

        String providerName = provider.getName();
        String providerVersion = provider.getVersion();

        assertEquals(2, scar.getFaultResults().size());

        MagnitudeFaultResult m0 = (MagnitudeFaultResult) scar.getFaultResult("F1");
        MagnitudeFaultResult m1 = (MagnitudeFaultResult) scar.getFaultResult("F2");

        assertEquals(2.945047378902121, m0.getCurrent(), 0.00001);
        assertEquals(2.68267577453832, m1.getCurrent(), 0.00001); // expressed in kA and not A
        assertEquals("OpenShortCircuit", providerName);
        assertEquals("0.1", providerVersion);

    }

    /**
     * Verifies handling of:
     * - unknown elements
     * - bus faults on a branch element
     * - branch faults on non-branch element
     */
    @Test
    void openShortCircuitProvider2nInvalidElementId() {

        //set up LF info
        LoadFlowParameters loadFlowParameters = LoadFlowParameters.load();
        loadFlowParameters.setTwtSplitShuntAdmittance(true);
        Network nt2 = create2n(NetworkFactory.findDefault());
        LoadFlow.run(nt2, loadFlowParameters);

        //set up ShortCircuitProvider info
        ShortCircuitAnalysisProvider provider = new OpenShortCircuitProvider(new DenseMatrixFactory());
        ComputationManager cm = LocalComputationManager.getDefault();
        ShortCircuitParameters scp = new ShortCircuitParameters();

        //CompletableFuture<ShortCircuitAnalysisResult> scar = provider.run(nt2, scp, cm);
        BusFault bf1 = new BusFault("F1", "B1");
        BusFault bf2 = new BusFault("F2", "UNKNOWN_BUS");
        BusFault bf3 = new BusFault("F3", "B1_B2");
        BranchFault bf4 = new BranchFault("F4", "B1_B2", 10);
        BranchFault bf5 = new BranchFault("F5", "UNKNOWN_BRANCH", 10);
        BranchFault bf6 = new BranchFault("F6", "B1", 10);

        List<Fault> faults = List.of(bf1, bf2, bf3, bf4, bf5, bf6);

        ShortCircuitAnalysisResult scar = provider.run(nt2, faults, scp, cm, Collections.emptyList()).join();

        assertEquals(2, scar.getFaultResults().size());
        assertNull(scar.getFaultResult("F2"));
        assertNull(scar.getFaultResult("F3"));
        assertNull(scar.getFaultResult("F5"));
        assertNull(scar.getFaultResult("F6"));

        MagnitudeFaultResult m1 = (MagnitudeFaultResult) scar.getFaultResult("F1");
        MagnitudeFaultResult m4 = (MagnitudeFaultResult) scar.getFaultResult("F4");

        assertEquals(2.945047378902121, m1.getCurrent(), 0.00001);
        assertEquals(2.916471923828125, m4.getCurrent(), 0.00001);

    }

    @Test
    void openShortCircuitProvider2nFaultsOnLine() {
        //set up LF info
        LoadFlowParameters loadFlowParameters = LoadFlowParameters.load();
        loadFlowParameters.setTwtSplitShuntAdmittance(true);
        Network nt2 = create2n(NetworkFactory.findDefault());
        LoadFlow.run(nt2, loadFlowParameters);

        //set up ShortCircuitProvider info
        ShortCircuitAnalysisProvider provider = new OpenShortCircuitProvider(new DenseMatrixFactory());
        ComputationManager cm = LocalComputationManager.getDefault();
        ShortCircuitParameters scp = new ShortCircuitParameters();

        BranchFault bf1 = new BranchFault("F1", "B1_B2", 10);
        BranchFault bf2 = new BranchFault("F2", "B1_B2", 70);

        List<Fault> faults = List.of(bf1, bf2);

        ShortCircuitAnalysisResult scar = provider.run(nt2, faults, scp, cm, Collections.emptyList()).join();

        MagnitudeFaultResult m0 = (MagnitudeFaultResult) scar.getFaultResult("F1");
        MagnitudeFaultResult m1 = (MagnitudeFaultResult) scar.getFaultResult("F2");

        assertEquals(2.916471923828125, m0.getCurrent(), 0.00001);
        assertEquals(2.75623681640625, m1.getCurrent(), 0.00001);
    }

    @Test
    void openShortCircuitProvider2nFaultsOnTransformer() {

        //set up LF info
        LoadFlowParameters loadFlowParameters = LoadFlowParameters.load();
        loadFlowParameters.setTwtSplitShuntAdmittance(true);
        Network nt2 = create2nTfo(NetworkFactory.findDefault());
        LoadFlow.run(nt2, loadFlowParameters);

        //set up ShortCircuitProvider info
        ShortCircuitAnalysisProvider provider = new OpenShortCircuitProvider(new DenseMatrixFactory());
        ComputationManager cm = LocalComputationManager.getDefault();
        ShortCircuitParameters scp = new ShortCircuitParameters();

        BranchFault bf1 = new BranchFault("F1", "B1_B2", 10);
        BranchFault bf2 = new BranchFault("F2", "B1_B2", 70);

        List<Fault> faults = List.of(bf1, bf2);

        ShortCircuitAnalysisResult scar = provider.run(nt2, faults, scp, cm, Collections.emptyList()).join();

        MagnitudeFaultResult m0 = (MagnitudeFaultResult) scar.getFaultResult("F1");
        MagnitudeFaultResult m1 = (MagnitudeFaultResult) scar.getFaultResult("F2");

        assertEquals(2.932280029296875, m0.getCurrent(), 0.00001);
        assertEquals(2.85796875, m1.getCurrent(), 0.00001);
    }

    @Test
    void openShortCircuitProviderBusAndBranchFaults2n() {
        //set up LF info
        LoadFlowParameters loadFlowParameters = LoadFlowParameters.load();
        loadFlowParameters.setTwtSplitShuntAdmittance(true);
        Network nt2 = create2n(NetworkFactory.findDefault());
        LoadFlow.run(nt2, loadFlowParameters);

        //set up ShortCircuitProvider info
        ShortCircuitAnalysisProvider provider = new OpenShortCircuitProvider(new DenseMatrixFactory());
        ComputationManager cm = LocalComputationManager.getDefault();
        ShortCircuitParameters scp = new ShortCircuitParameters();

        BusFault bf1 = new BusFault("F1", "B1");
        BusFault bf2 = new BusFault("F2", "B2");

        BranchFault bf3 = new BranchFault("F3", "B1_B2", 0);
        BranchFault bf4 = new BranchFault("F4", "B1_B2", 100);

        BranchFault bf5 = new BranchFault("F5", "B1_B2", 70);

        List<Fault> faults = List.of(bf1, bf2, bf3, bf4, bf5);

        ShortCircuitAnalysisResult scar = provider.run(nt2, faults, scp, cm, Collections.emptyList()).join();

        assertEquals(2.945047378902121, getCurrentResult(scar, "F1"), 0.00001);
        assertEquals(2.68267577453832, getCurrentResult(scar, "F2"), 0.00001);
        assertEquals(2.945047378902121, getCurrentResult(scar, "F3"), 0.00001);
        assertEquals(2.68267577453832, getCurrentResult(scar, "F4"), 0.00001);
        assertEquals(2.75623681640625, getCurrentResult(scar, "F5"), 0.00001);
    }

    @Test
    void openShortCircuitProvider4n() {

        //set up LF info
        LoadFlowParameters loadFlowParameters = LoadFlowParameters.load();
        loadFlowParameters.setTwtSplitShuntAdmittance(true);
        Network nt4 = create4n(NetworkFactory.findDefault());
        LoadFlow.run(nt4, loadFlowParameters);

        //set up ShortCircuitProvider info
        ShortCircuitAnalysisProvider provider = new OpenShortCircuitProvider(new DenseMatrixFactory());
        ComputationManager cm = LocalComputationManager.getDefault();
        ShortCircuitParameters scp = new ShortCircuitParameters();

        BusFault bf1 = new BusFault("F1", "B1");
        BusFault bf2 = new BusFault("F2", "B2");
        BusFault bf3 = new BusFault("F3", "B3");
        BusFault bf4 = new BusFault("F4", "B4");
        List<Fault> faults = List.of(bf1, bf2, bf3, bf4);

        ShortCircuitAnalysisResult scar = provider.run(nt4, faults, scp, cm, Collections.emptyList()).join();

        assertEquals(3.5471650598424766, getCurrentResult(scar, "F1"), 0.00001);
        assertEquals(3.7476107037718006, getCurrentResult(scar, "F2"), 0.00001);
        assertEquals(3.5923793102301785, getCurrentResult(scar, "F3"), 0.00001);
        assertEquals(3.411642741114655, getCurrentResult(scar, "F4"), 0.00001);

    }

    @Test
    void openShortCircuitProviderBusAndBranchFaults4n() {

        //set up LF info
        LoadFlowParameters loadFlowParameters = LoadFlowParameters.load();
        loadFlowParameters.setTwtSplitShuntAdmittance(true);
        Network nt4 = create4n(NetworkFactory.findDefault());
        LoadFlow.run(nt4, loadFlowParameters);

        //set up ShortCircuitProvider info
        ShortCircuitAnalysisProvider provider = new OpenShortCircuitProvider(new DenseMatrixFactory());
        ComputationManager cm = LocalComputationManager.getDefault();
        ShortCircuitParameters scp = new ShortCircuitParameters();

        BusFault bf1 = new BusFault("F1", "B1");

        BranchFault bf2 = new BranchFault("F2", "B1_B2", 0);
        BranchFault bf3 = new BranchFault("F3", "B1_B2", 30);
        BranchFault bf4 = new BranchFault("F4", "B1_B3", 30);
        BranchFault bf5 = new BranchFault("F5", "B1_B4", 30);
        BranchFault bf6 = new BranchFault("F6", "B2_B3", 30);
        BranchFault bf7 = new BranchFault("F7", "B3_B4", 30);

        List<Fault> faults = List.of(bf1, bf2, bf3, bf4, bf5, bf6, bf7);

        ShortCircuitAnalysisResult scar = provider.run(nt4, faults, scp, cm, Collections.emptyList()).join();

        assertEquals(3.5471650598424766, getCurrentResult(scar, "F1"), 0.00001);
        assertEquals(3.5471650598424766, getCurrentResult(scar, "F2"), 0.00001);
        assertEquals(3.569175048828125, getCurrentResult(scar, "F3"), 0.00001);
        assertEquals(3.497883544921875, getCurrentResult(scar, "F4"), 0.00001);
        assertEquals(3.459245849609375, getCurrentResult(scar, "F5"), 0.00001);
        assertEquals(3.673041748046875, getCurrentResult(scar, "F6"), 0.00001);
        assertEquals(3.505940673828125, getCurrentResult(scar, "F7"), 0.00001);

    }

    @Test
    void openShortCircuitProvider2nTfo() {

        //set up LF info
        LoadFlowParameters loadFlowParameters = LoadFlowParameters.load();
        loadFlowParameters.setTwtSplitShuntAdmittance(true);
        Network nt2 = create2nTfo(NetworkFactory.findDefault());
        LoadFlow.run(nt2, loadFlowParameters);

        //set up ShortCircuitProvider info
        ShortCircuitAnalysisProvider provider = new OpenShortCircuitProvider(new DenseMatrixFactory());
        ComputationManager cm = LocalComputationManager.getDefault();
        ShortCircuitParameters scp = new ShortCircuitParameters();

        //CompletableFuture<ShortCircuitAnalysisResult> scar = provider.run(nt2, scp, cm);
        List<Fault> faults = new ArrayList<>(); // TODO

        BusFault bf1 = new BusFault("F1", "B1");
        BusFault bf2 = new BusFault("F2", "B2");
        faults.add(bf1);
        faults.add(bf2);

        ShortCircuitAnalysisResult scar = provider.run(nt2, faults, scp, cm, Collections.emptyList()).join();

        assertEquals(2.945050248502227, getCurrentResult(scar, "F1"), 0.00001);
        assertEquals(1.881491000035193, getCurrentResult(scar, "F2"), 0.00001);

    }

    @Test
    void shortCircuitSystematic() {

        LoadFlowParameters loadFlowParameters = LoadFlowParameters.load();
        loadFlowParameters.setTwtSplitShuntAdmittance(true);

        Network nt2 = create2n(NetworkFactory.findDefault());
        LoadFlow.run(nt2, loadFlowParameters);

        MatrixFactory matrixFactory = new DenseMatrixFactory();

        List<ShortCircuitFault> tmpV = new ArrayList<>();

        ShortCircuitEngineParameters.PeriodType periodType = ShortCircuitEngineParameters.PeriodType.TRANSIENT;

        ShortCircuitNorm shortCircuitNorm = new ShortCircuitNormNone();
        ShortCircuitEngineParameters scbParameters = new ShortCircuitEngineParameters(loadFlowParameters, matrixFactory,
                ShortCircuitEngineParameters.AnalysisType.SYSTEMATIC, tmpV, false, ShortCircuitEngineParameters.VoltageProfileType.NOMINAL, false, periodType, shortCircuitNorm);
        ShortCircuitBalancedEngine scbEngine = new ShortCircuitBalancedEngine(nt2, scbParameters);

        scbEngine.run();
        List<Double> val = new ArrayList<>();
        for (Map.Entry<ShortCircuitFault, ShortCircuitResult> res : scbEngine.resultsPerFault.entrySet()) {
            val.add(res.getValue().getId().getReal());
        }

        assertEquals(0.0996007987855852, val.get(0), 0.00001);
        assertEquals(0.0999999987871081, val.get(1), 0.00001);

    }

    @Test
    void shortCircuit8NodesIEC9094() {

        LoadFlowParameters loadFlowParameters = LoadFlowParameters.load();
        loadFlowParameters.setTwtSplitShuntAdmittance(true);

        Network network = ReferenceNetwork.create8NodesIEC9094();

        MatrixFactory matrixFactory = new DenseMatrixFactory();

        Complex zFaultToGround = new Complex(0.);
        ShortCircuitFaultImpedance scFaultz = new ShortCircuitFaultImpedance(zFaultToGround);
        List<ShortCircuitFault> faultList = new ArrayList<>();
        ShortCircuitFault sc1 = new ShortCircuitFault("B1", "sc1", "B1", scFaultz, ShortCircuitFault.ShortCircuitType.TRIPHASED_GROUND);
        faultList.add(sc1);
        ShortCircuitFault sc2 = new ShortCircuitFault("B2", "sc2", "B2", scFaultz, ShortCircuitFault.ShortCircuitType.TRIPHASED_GROUND);
        faultList.add(sc2);
        ShortCircuitFault sc3 = new ShortCircuitFault("B3", "sc3", "B3", scFaultz, ShortCircuitFault.ShortCircuitType.TRIPHASED_GROUND);
        faultList.add(sc3);
        ShortCircuitFault sc4 = new ShortCircuitFault("B4", "sc4", "B4", scFaultz, ShortCircuitFault.ShortCircuitType.TRIPHASED_GROUND);
        faultList.add(sc4);
        ShortCircuitFault sc5 = new ShortCircuitFault("B5", "sc5", "B5", scFaultz, ShortCircuitFault.ShortCircuitType.TRIPHASED_GROUND);
        faultList.add(sc5);
        ShortCircuitFault sc6 = new ShortCircuitFault("B6", "sc6", "B6", scFaultz, ShortCircuitFault.ShortCircuitType.TRIPHASED_GROUND);
        faultList.add(sc6);
        ShortCircuitFault sc7 = new ShortCircuitFault("B7", "sc7", "B7", scFaultz, ShortCircuitFault.ShortCircuitType.TRIPHASED_GROUND);
        faultList.add(sc7);
        ShortCircuitFault sc8 = new ShortCircuitFault("B8", "sc8", "B8", scFaultz, ShortCircuitFault.ShortCircuitType.TRIPHASED_GROUND);
        faultList.add(sc8);

        ShortCircuitEngineParameters.PeriodType periodType = ShortCircuitEngineParameters.PeriodType.TRANSIENT;
        ShortCircuitNormIec shortCircuitNormIec = new ShortCircuitNormIec();
        ShortCircuitEngineParameters scbParameters = new ShortCircuitEngineParameters(loadFlowParameters, matrixFactory,
                ShortCircuitEngineParameters.AnalysisType.SELECTIVE, faultList, true, ShortCircuitEngineParameters.VoltageProfileType.NOMINAL, false, periodType, shortCircuitNormIec);
        ShortCircuitBalancedEngine scbEngine = new ShortCircuitBalancedEngine(network, scbParameters);

        scbEngine.run();
        Map<ShortCircuitFault, Double> valuesIk = new HashMap<>();
        Map<ShortCircuitFault, Complex> valuesZd = new HashMap<>();
        Map<ShortCircuitFault, Complex> valuesId = new HashMap<>();
        Map<ShortCircuitFault, Complex> valuesVd = new HashMap<>();
        Map<ShortCircuitFault, Complex> valuesEth = new HashMap<>();
        for (Map.Entry<ShortCircuitFault, ShortCircuitResult> res : scbEngine.resultsPerFault.entrySet()) {
            valuesIk.put(res.getKey(), res.getValue().getIk().abs());
            valuesZd.put(res.getKey(), res.getValue().getZd());
            valuesId.put(res.getKey(), res.getValue().getId());
            valuesVd.put(res.getKey(), res.getValue().getVd());
            valuesEth.put(res.getKey(), res.getValue().getEth());
        }

        // I"k = 1/sqrt(3) * cmax * Un /(Zeq)
        assertEquals(40.64478476116188, valuesIk.get(sc1), 0.001); // bus 1 : expected in doc = 40.6447 kA
        assertEquals(31.783052222534174, valuesIk.get(sc2), 0.001); // bus 2 : expected in doc =  31.7831 kA
        assertEquals(19.672955775750143, valuesIk.get(sc3), 0.001); // bus 3 : expected in doc =  19.673 kA
        assertEquals(16.227655866910894, valuesIk.get(sc4), 0.001); // bus 4 : expected in doc =  16.2277 kA
        assertEquals(33.18941481677016, valuesIk.get(sc5), 0.001); // bus 5 : expected in doc =  33.1894 kA
        assertEquals(37.56287899040728, valuesIk.get(sc6), 0.001); // bus 6 : expected in doc =  37.5629 kA
        assertEquals(25.589463480212533, valuesIk.get(sc7), 0.001); // bus 7 : expected in doc =  25.5895 kA
        assertEquals(13.577771545200052, valuesIk.get(sc8), 0.001); // bus 8 : expected in doc =  13.5778 kA

        assertEquals(0.004092194811702985, valuesZd.get(sc1).getImaginary(), 0.000001);
        assertEquals(0.017334559734154213, valuesZd.get(sc5).getImaginary(), 0.000001);
        assertEquals(0.2377979119689316, valuesZd.get(sc7).getImaginary(), 0.000001);

        assertEquals(1.153344202949403, valuesId.get(sc7).getReal(), 0.000001);
        assertEquals(-242.02989930029395, valuesId.get(sc1).getImaginary(), 0.000001);

        assertEquals(0., valuesVd.get(sc6).getReal(), 0.000001);
        assertEquals(0., valuesVd.get(sc2).getImaginary(), 0.000001);

        assertEquals(1., valuesEth.get(sc6).getReal(), 0.000001);
        assertEquals(0., valuesEth.get(sc2).getImaginary(), 0.000001);

        //assertEquals(4039.8610235151364, values.get(8), 0.1); // T3 U0 node : for check only
        //assertEquals(4039.8610235151364, values.get(8), 0.1); // T4 U0 node : for check only

        // Test on the IEC norm
        TwoWindingsTransformer t2w = network.getTwoWindingsTransformer("T5");
        double kt2w = shortCircuitNormIec.getKtT2W(t2w);

        assertEquals(0.9765171405429419, kt2w, 0.0000001);
        assertEquals("IEC", shortCircuitNormIec.getNormType());
        assertEquals(1., shortCircuitNormIec.getCminVoltageFactor(30.), 0.000001);

        ShortCircuitNormNone shortCircuitNormNone = new ShortCircuitNormNone();

        kt2w = shortCircuitNormNone.getKtT2W(t2w);

        assertEquals(1.0, kt2w, 0.0000001);
        assertEquals("NONE", shortCircuitNormNone.getNormType());
        assertEquals(1., shortCircuitNormNone.getCminVoltageFactor(30.), 0.000001);

        ThreeWindingsTransformer t3w = network.getThreeWindingsTransformer("T3");
        double kt3w = shortCircuitNormIec.getKtT3Wij(t3w, 1, 2);
        assertEquals(1.011217185074488, kt3w, 0.000001);
        kt3w = shortCircuitNormIec.getKtT3Wij(t3w, 1, 3);
        assertEquals(0.9344644092032702, kt3w, 0.000001);
        kt3w = shortCircuitNormIec.getKtT3Wij(t3w, 2, 3);
        assertEquals(0.9638258888806438, kt3w, 0.000001);
        kt3w = shortCircuitNormIec.getKtT3Wij(t3w, 3, 1);
        assertEquals(0.9344644092032702, kt3w, 0.000001);

        shortCircuitNormIec.setKtT3Wi(t3w);

        ThreeWindingsTransformerFortescue extension = t3w.getExtension(ThreeWindingsTransformerFortescue.class);
        double coefcX0 = extension.getLeg3().getXz() / t3w.getLeg3().getX();
        assertEquals(1.0, coefcX0, 0.000001);

        ThreeWindingsTransformerNorm t3wNormExtension = shortCircuitNormIec.getNormExtensions().getNormExtension(t3w);
        double kTaR = t3wNormExtension.getLeg1().getKtR();
        double kTbX = t3wNormExtension.getLeg2().getKtX();
        assertEquals(0.86939867723079, kTaR, 0.000001);
        assertEquals(-6.710685661589687, kTbX, 0.000001);

        Generator g1 = network.getGenerator("G1");
        double kg = shortCircuitNormIec.getKg(g1);
        assertEquals(1.0015680959819921, kg, 0.000001);
    }

    @Test
    void shortCircuit6NodesIEC9094subtransient() {

        LoadFlowParameters loadFlowParameters = LoadFlowParameters.load();
        loadFlowParameters.setTwtSplitShuntAdmittance(true);

        Network network = ReferenceNetwork.create6NodesIec9094();

        MatrixFactory matrixFactory = new DenseMatrixFactory();

        List<ShortCircuitFault> faultList = new ArrayList<>();
        ShortCircuitFault sc1 = new ShortCircuitFault("B3", "F1", "B3", new ShortCircuitFaultImpedance(new Complex(0.)), ShortCircuitFault.ShortCircuitType.TRIPHASED_GROUND);
        faultList.add(sc1);
        ShortCircuitFault sc2 = new ShortCircuitFault("B4", "F2", "B4", new ShortCircuitFaultImpedance(new Complex(0.)), ShortCircuitFault.ShortCircuitType.TRIPHASED_GROUND);
        faultList.add(sc2);
        ShortCircuitFault sc3 = new ShortCircuitFault("B6", "F3", "B6", new ShortCircuitFaultImpedance(new Complex(0.)), ShortCircuitFault.ShortCircuitType.TRIPHASED_GROUND);
        faultList.add(sc3);

        ShortCircuitEngineParameters.PeriodType periodType = ShortCircuitEngineParameters.PeriodType.SUB_TRANSIENT;
        ShortCircuitNormIec shortCircuitNormIec = new ShortCircuitNormIec();
        ShortCircuitEngineParameters scbParameters = new ShortCircuitEngineParameters(loadFlowParameters, matrixFactory,
                ShortCircuitEngineParameters.AnalysisType.SELECTIVE, faultList, true, ShortCircuitEngineParameters.VoltageProfileType.NOMINAL, false, periodType, shortCircuitNormIec);
        ShortCircuitBalancedEngine scbEngine = new ShortCircuitBalancedEngine(network, scbParameters);

        scbEngine.run();
        Map<ShortCircuitFault, Double> val = new HashMap<>();
        Map<ShortCircuitFault, Double> coefPeakb = new HashMap<>();
        for (Map.Entry<ShortCircuitFault, ShortCircuitResult> res : scbEngine.resultsPerFault.entrySet()) {
            val.put(res.getKey(), res.getValue().getIk().abs());
            coefPeakb.put(res.getKey(), res.getValue().getPeakCoefb());
        }

        // here Icc = 1/sqrt(3)*Eth(pu)/Zth(pu100)*Sb100/Vb*1000
        // and I"k = 1/sqrt(3) * cmax * Un /(Zeq) and expected I"k = 34.62 kA
        assertEquals(34.62398968800272, val.get(sc1), 0.00001); // F1 expected value in the document: 34.62 kA
        assertEquals(34.1162841954478, val.get(sc2), 0.00001); // F2 expected value in the document: 34.12 kA
        assertEquals(6.945173672144295, val.get(sc3), 0.00001); // F3 expected value in the document: 6.95 kA

        // Peak current method b
        assertEquals(70.73492731970777, val.get(sc1) * coefPeakb.get(sc1) * Math.sqrt(2.), 0.00001); // FIXME: expected 81.36 kA but factor 1.15 not triggered: check R/X ratio of all lines
        assertEquals(69.04648240748665, val.get(sc2) * coefPeakb.get(sc2) * Math.sqrt(2.), 0.00001);
        assertEquals(11.922267036509417, val.get(sc3) * coefPeakb.get(sc3) * Math.sqrt(2.), 0.00001);
    }

    @Test
    void shortCircuitSubTransientReference() {

        LoadFlowParameters loadFlowParameters = LoadFlowParameters.load();
        loadFlowParameters.setTwtSplitShuntAdmittance(true);

        Network network = ReferenceNetwork.createShortCircuitReference();

        MatrixFactory matrixFactory = new DenseMatrixFactory();

        List<ShortCircuitFault> faultList = new ArrayList<>();
        ShortCircuitFault sc1 = new ShortCircuitFault("B7", "sc1", "B7", new ShortCircuitFaultImpedance(new Complex(0.)), ShortCircuitFault.ShortCircuitType.TRIPHASED_GROUND);
        faultList.add(sc1);

        ShortCircuitFault sc2 = new ShortCircuitFault("B7", "sc2", "B7", new ShortCircuitFaultImpedance(new Complex(0.0001, 0.0002)), ShortCircuitFault.ShortCircuitType.TRIPHASED_GROUND);
        faultList.add(sc2);

        ShortCircuitEngineParameters.PeriodType periodType = ShortCircuitEngineParameters.PeriodType.SUB_TRANSIENT;

        ShortCircuitNorm shortCircuitNorm = new ShortCircuitNormNone();
        ShortCircuitEngineParameters scbParameters = new ShortCircuitEngineParameters(loadFlowParameters, matrixFactory,
                ShortCircuitEngineParameters.AnalysisType.SELECTIVE, faultList, true, ShortCircuitEngineParameters.VoltageProfileType.NOMINAL, false, periodType, shortCircuitNorm);
        ShortCircuitBalancedEngine scbEngine = new ShortCircuitBalancedEngine(network, scbParameters);

        scbEngine.run();
        Map<ShortCircuitFault, Double> val = new HashMap<>();
        for (Map.Entry<ShortCircuitFault, ShortCircuitResult> res : scbEngine.resultsPerFault.entrySet()) {
            val.put(res.getKey(), res.getValue().getIk().abs());
        }

        // here Icc = 1/sqrt(3)*Eth(pu)/Zth(pu100)*Sb100/Vb*1000
        // and Idocumentation = Ib*Eth(pu)/Zth(pu15) then Idocumentation = Icc * Ib * sqrt(3) * Vb / (1000 * Sb15)  with Ib = 18.064
        // in the documentation, expected Idocumentation ~ 35.656 kA
        assertEquals(35.69309945355154, val.get(sc1) * 18.064 * 0.277 * Math.sqrt(3) / 15., 0.00001);
        assertEquals(35.69084362105586, val.get(sc2) * 18.064 * 0.277 * Math.sqrt(3) / 15., 0.00001);
    }

    private static double getCurrentResult(ShortCircuitAnalysisResult scar, String faultId) {
        return ((MagnitudeFaultResult) scar.getFaultResult(faultId)).getCurrent();
    }

    public static Network create2n(NetworkFactory networkFactory) {
        Objects.requireNonNull(networkFactory);

        double p0l2 = 10;
        double q0l2 = 10;
        double pgen = 10;
        double xl = 2.;

        Network network = networkFactory.createNetwork("2n", "test");
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
                .setMaxP(150)
                .setTargetP(pgen)
                .setTargetV(100.0)
                .setVoltageRegulatorOn(true)
                .add();

        gen1.newExtension(GeneratorShortCircuitAdder.class)
                .withDirectSubtransX(20)
                .withDirectTransX(20)
                .withStepUpTransformerX(0.)
                .add();

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
        vl2.newLoad()
                .setId("LOAD_2")
                .setBus(bus2.getId())
                .setP0(p0l2)
                .setQ0(q0l2)
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
                .setX(xl)
                .setG1(0.0)
                .setB1(0.0)
                .setG2(0.0)
                .setB2(0.0)
                .add();

        return network;
    }

    public static Network create4n(NetworkFactory networkFactory) {
        Objects.requireNonNull(networkFactory);
        //      2                               3
        //  (~)-|--------------X23--------------|-[X]  Po= 10.  Qo = 100.
        //      |--+                         +--|
        //         |                        /   |--+
        //         |                       /       |
        //         |                      /        |
        //        X12                    /         |
        //         |      +-----X13-----+         X34
        //         |     /                         |
        //      1  |    /                          |
        //      |--+   /                           |
        //      |-----+                         |--+
        //   +--|--------------X14--------------|-----[X] Po= 20.  Qo = 10.
        //   |                                  |--B4
        //   B1

        Network network = networkFactory.createNetwork("4n", "test");
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
        // test with shunt (could be removed)
        vl1.newShuntCompensator()
                .setId("SHUNT_1")
                .setBus(bus1.getId())
                .setSectionCount(1)
                .setVoltageRegulatorOn(false)
                .newLinearModel().setMaximumSectionCount(1).setBPerSection(-0.003).add()
                .add();

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
        Generator gen2 = vl2.newGenerator()
                .setId("G2")
                .setBus(bus2.getId())
                .setMinP(0.0)
                .setMaxP(150)
                .setTargetP(30)
                .setTargetV(100.0)
                .setVoltageRegulatorOn(true)
                .add();
        gen2.newExtension(GeneratorShortCircuitAdder.class)
                .withDirectTransX(20.)
                .withDirectSubtransX(20.)
                .withStepUpTransformerX(0.)
                .add();

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
        vl3.newLoad()
                .setId("LOAD_3")
                .setBus(bus3.getId())
                .setP0(10.0)
                .setQ0(100.)
                .add();

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
        vl4.newShuntCompensator()
                .setId("SHUNT_4")
                .setBus(bus4.getId())
                .setSectionCount(1)
                .setVoltageRegulatorOn(false)
                .newLinearModel().setMaximumSectionCount(1).setBPerSection(-0.00105).add()
                .add();
        vl4.newLoad()
                .setId("LOAD_4")
                .setBus(bus4.getId())
                .setP0(20.)
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
        network.newLine()
                .setId("B1_B3")
                .setVoltageLevel1(vl1.getId())
                .setBus1(bus1.getId())
                .setConnectableBus1(bus1.getId())
                .setVoltageLevel2(vl3.getId())
                .setBus2(bus3.getId())
                .setConnectableBus2(bus3.getId())
                .setR(0.0)
                .setX(1 / 0.4)
                .setG1(0.0)
                .setB1(0.0)
                .setG2(0.0)
                .setB2(0.0)
                .add();
        network.newLine()
                .setId("B1_B4")
                .setVoltageLevel1(vl1.getId())
                .setBus1(bus1.getId())
                .setConnectableBus1(bus1.getId())
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
        network.newLine()
                .setId("B2_B3")
                .setVoltageLevel1(vl2.getId())
                .setBus1(bus2.getId())
                .setConnectableBus1(bus2.getId())
                .setVoltageLevel2(vl3.getId())
                .setBus2(bus3.getId())
                .setConnectableBus2(bus3.getId())
                .setR(0.0)
                .setX(1 / 0.6)
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
                .setX(1 / 0.5)
                .setG1(0.0)
                .setB1(0.0)
                .setG2(0.0)
                .setB2(0.0)
                .add();

        return network;
    }

    public static Network create2nTfo(NetworkFactory networkFactory) {
        Objects.requireNonNull(networkFactory);

        double p0l2 = 10;
        double q0l2 = 10;
        double pGen = 10;
        double xl = 2.;

        Network network = networkFactory.createNetwork("2nTfo", "test");
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
                .setMaxP(150)
                .setTargetP(pGen)
                .setTargetV(100.0)
                .setVoltageRegulatorOn(true)
                .add();

        gen1.newExtension(GeneratorShortCircuitAdder.class)
                .withDirectSubtransX(20)
                .withDirectTransX(20)
                .withStepUpTransformerX(0.)
                .add();

        VoltageLevel vl2 = substation1.newVoltageLevel()
                .setId("VL_2")
                .setNominalV(150.0)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(200)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus2 = vl2.getBusBreakerView().newBus()
                .setId("B2")
                .add();
        bus2.setV(150.0).setAngle(0);
        vl2.newLoad()
                .setId("LOAD_2")
                .setBus(bus2.getId())
                .setP0(p0l2)
                .setQ0(q0l2)
                .add();

        TwoWindingsTransformer t2w = substation1.newTwoWindingsTransformer()
                .setId("B1_B2")
                .setVoltageLevel1(vl1.getId())
                .setBus1(bus1.getId())
                .setConnectableBus1(bus1.getId())
                .setVoltageLevel2(vl2.getId())
                .setBus2(bus2.getId())
                .setConnectableBus2(bus2.getId())
                .setR(0.0)
                .setX(xl)
                .setRatedU1(100.0)
                .setRatedU2(150.0)
                .setG(0.0)
                .setB(0.0)
                .add();

        return network;
    }
}

