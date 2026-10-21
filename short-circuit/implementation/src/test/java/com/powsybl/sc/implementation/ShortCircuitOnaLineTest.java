/**
 * Copyright (c) 2026, Jean-Baptiste Heyberger
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.implementation;

import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.sc.util.ReferenceNetwork;
import org.apache.commons.math3.complex.Complex;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class ShortCircuitOnaLineTest {

    @Test
    void shortCircuitIec31NodePlus() {

        // This test shows the possibility to compute the Thevenin impedance in the middle of a line, knowing Zth at each end of the line
        LoadFlowParameters loadFlowParameters = LoadFlowParameters.load();
        loadFlowParameters.setTwtSplitShuntAdmittance(true);

        Network network = ReferenceNetwork.create6NodeIec9094Plus();

        MatrixFactory matrixFactory = new DenseMatrixFactory();

        List<ShortCircuitFault> faultList = new ArrayList<>();
        ShortCircuitFault sc1 = new ShortCircuitFault("B3", "F1", "B3", new ShortCircuitFaultImpedance(new Complex(0.)), ShortCircuitFault.ShortCircuitType.TRIPHASED_GROUND);
        faultList.add(sc1);
        ShortCircuitFault sc2 = new ShortCircuitFault("B4", "F2", "B4", new ShortCircuitFaultImpedance(new Complex(0.)), ShortCircuitFault.ShortCircuitType.TRIPHASED_GROUND);
        faultList.add(sc2);
        ShortCircuitFault sc3 = new ShortCircuitFault("B6", "F3", "B6", new ShortCircuitFaultImpedance(new Complex(0.)), ShortCircuitFault.ShortCircuitType.TRIPHASED_GROUND);
        faultList.add(sc3);
        ShortCircuitFault sc4 = new ShortCircuitFault("B5", "F4", "B5", new ShortCircuitFaultImpedance(new Complex(0.)), ShortCircuitFault.ShortCircuitType.TRIPHASED_GROUND);
        faultList.add(sc4);
        ShortCircuitFault sc5 = new ShortCircuitFault("B55", "F5", "B55", new ShortCircuitFaultImpedance(new Complex(0.)), ShortCircuitFault.ShortCircuitType.TRIPHASED_GROUND);
        faultList.add(sc5);
        ShortCircuitFault sc6 = new ShortCircuitFault("B25", "F6", "B25", new ShortCircuitFaultImpedance(new Complex(0.)), ShortCircuitFault.ShortCircuitType.TRIPHASED_GROUND);
        faultList.add(sc6);
        ShortCircuitFault sc7 = new ShortCircuitFault("B2", "F7", "B2", new ShortCircuitFaultImpedance(new Complex(0.)), ShortCircuitFault.ShortCircuitType.TRIPHASED_GROUND);
        faultList.add(sc7);

        ShortCircuitEngineParameters.PeriodType periodType = ShortCircuitEngineParameters.PeriodType.SUB_TRANSIENT;
        ShortCircuitNormIec shortCircuitNormIec = new ShortCircuitNormIec();
        ShortCircuitEngineParameters scbParameters = new ShortCircuitEngineParameters(loadFlowParameters, matrixFactory,
                ShortCircuitEngineParameters.AnalysisType.SELECTIVE, faultList, true, ShortCircuitEngineParameters.VoltageProfileType.NOMINAL, false, periodType, shortCircuitNormIec);
        ShortCircuitBalancedEngine scbEngine = new ShortCircuitBalancedEngine(network, scbParameters);

        scbEngine.run();
        List<Double> val = new ArrayList<>();
        List<Double> coefPeakb = new ArrayList<>();
        List<Double> coefPeakc = new ArrayList<>();
        List<Complex> zth = new ArrayList<>();
        List<Double> rOverX = new ArrayList<>();
        LfNetwork lfn = scbEngine.resultsPerFault.get(sc1).getLfNetwork();
        for (Map.Entry<ShortCircuitFault, ShortCircuitResult> res : scbEngine.resultsPerFault.entrySet()) {
            val.add(res.getValue().getIk().abs());
            coefPeakb.add(res.getValue().getPeakCoefb());
            coefPeakc.add(res.getValue().getPeakCoefcDirect());
            zth.add(res.getValue().getZd());
            rOverX.add(res.getValue().getMaxRoverX());
        }

        // here Icc = 1/sqrt(3)*Eth(pu)/Zth(pu100)*Sb100/Vb*1000
        // and I"k = 1/sqrt(3) * cmax * Un /(Zeq) and expected I"k = 34.62 kA
        assertEquals(34.62398968800272, val.get(0), 0.00001);
        assertEquals(34.1162841954478, val.get(1), 0.00001);
        assertEquals(6.945173672144295, val.get(2), 0.00001);

        // Peak current method b
        assertEquals(70.73492731970777, val.get(0) * coefPeakb.get(0) * Math.sqrt(2.), 0.00001); // FIXME: expected 81.36 kA but factor 1.15 not triggered: check R/X ratio of all lines
        assertEquals(69.04648240748665, val.get(1) * coefPeakb.get(1) * Math.sqrt(2.), 0.00001);
        assertEquals(11.922267036509417, val.get(2) * coefPeakb.get(2) * Math.sqrt(2.), 0.00001);

        // Peak current method c
        assertEquals(70.86099614556585, val.get(0) * coefPeakc.get(0) * Math.sqrt(2.), 0.00001); // FIXME: expected 81.36 kA but factor 1.15 not triggered: check R/X ratio of all lines
        assertEquals(69.06786972125418, val.get(1) * coefPeakc.get(1) * Math.sqrt(2.), 0.00001);
        assertEquals(10.367447265701943, val.get(2) * coefPeakc.get(2) * Math.sqrt(2.), 0.00001);

        // We try to reproduce a short circuit at 30% of line 4 close to bus 5
        // fault on line 4
        Complex zth1Bus5 = zth.get(3);
        Complex zthBus55 = zth.get(4);
        Complex zth2Bus6 = zth.get(2);
        LfBranch b5b55 = lfn.getBranchById("L455_B5_B55");
        LfBranch b55b6 = lfn.getBranchById("L4_B55_B6");
        Complex zlb5b55 = new Complex(b5b55.getPiModel().getR(), b5b55.getPiModel().getX());
        Complex zlb55b6 = new Complex(b55b6.getPiModel().getR(), b55b6.getPiModel().getX());
        Complex zl = zlb5b55.add(zlb55b6);

        // we now use the formula to compute Za and Zb from Zth1 and Zth2
        //
        //      B1                        B2
        //      +-----------Zl------------+
        //      |                         |
        //     Za                         Zb
        //      |                         |
        //    /////                   ////////
        // Knowing Zth1 with fault in B1 and Zth2 with fault in B2, we can decompose with Za, Zb and Zl
        // Computed formulas give:                Zl
        //                         Za = ----------------------- * (2.Zth1 - Zl +/- sqrt(Zl² + 4.Zth1.Zth2 ))
        //                               2 * (Zth2 - Zth1 + Zl)
        // and Zb with symetric formula
        Complex zaLine4 = getZa(zth1Bus5, zth2Bus6, zl);
        //Complex zbLine5 = getZb(zth1Bus5, zth2Bus6, zl);

        assertEquals(4.623156048834316, zaLine4.getReal(), 0.00001);
        assertEquals(5.354477752673843, zaLine4.getImaginary(), 0.00001);
        assertEquals(zthBus55.getReal(), zth1Bus5.add(zlb5b55).getReal(), 0.00001);

        // for the fault on line 2
        Complex zthBus2 = zth.get(6);
        Complex zthBus4 = zth.get(1);
        Complex zthBus25 = zth.get(5);
        LfBranch b225 = lfn.getBranchById("L2_B2_B25");
        LfBranch b254 = lfn.getBranchById("L2_B25_B4");
        Complex zlb2b25 = new Complex(b225.getPiModel().getR(), b225.getPiModel().getX());
        Complex zlb25b4 = new Complex(b254.getPiModel().getR(), b254.getPiModel().getX());
        Complex zl2 = zlb2b25.add(zlb25b4);

        // we show below the equivalence between Zth values using a bus at 30% of the line and the methodology computing it from Zth at both ends
        assertEquals(getZthLine(zthBus2, zthBus4, zl2, 0.).getReal(), zthBus2.getReal(), 0.00001);
        assertEquals(getZthLine(zthBus2, zthBus4, zl2, 30.).getReal(), zthBus25.getReal(), 0.0001);
        assertEquals(getZthLine(zthBus2, zthBus4, zl2, 100.).getReal(), zthBus4.getReal(), 0.00001);
    }

    @Test
    void shortCircuitIec31NodePlusUnbalanced() {

        // This test shows the possibility to compute the Thevenin impedance in the middle of a line, knowing Zth at each end of the line
        LoadFlowParameters loadFlowParameters = LoadFlowParameters.load();
        loadFlowParameters.setTwtSplitShuntAdmittance(true);

        Network network = ReferenceNetwork.create6NodeIec9094Plus();

        MatrixFactory matrixFactory = new DenseMatrixFactory();

        List<ShortCircuitFault> faultList = new ArrayList<>();
        ShortCircuitFault sc1 = new ShortCircuitFault("B3", "F1", "B3", new ShortCircuitFaultImpedance(new Complex(0.)), ShortCircuitFault.ShortCircuitType.MONOPHASED);
        faultList.add(sc1);
        ShortCircuitFault sc2 = new ShortCircuitFault("B4", "F2", "B4", new ShortCircuitFaultImpedance(new Complex(0.)), ShortCircuitFault.ShortCircuitType.MONOPHASED);
        faultList.add(sc2);
        ShortCircuitFault sc3 = new ShortCircuitFault("B6", "F3", "B6", new ShortCircuitFaultImpedance(new Complex(0.)), ShortCircuitFault.ShortCircuitType.MONOPHASED);
        faultList.add(sc3);
        ShortCircuitFault sc4 = new ShortCircuitFault("B5", "F4", "B5", new ShortCircuitFaultImpedance(new Complex(0.)), ShortCircuitFault.ShortCircuitType.MONOPHASED);
        faultList.add(sc4);
        ShortCircuitFault sc5 = new ShortCircuitFault("B55", "F5", "B55", new ShortCircuitFaultImpedance(new Complex(0.)), ShortCircuitFault.ShortCircuitType.MONOPHASED);
        faultList.add(sc5);
        ShortCircuitFault sc6 = new ShortCircuitFault("B25", "F6", "B25", new ShortCircuitFaultImpedance(new Complex(0.)), ShortCircuitFault.ShortCircuitType.MONOPHASED);
        faultList.add(sc6);
        ShortCircuitFault sc7 = new ShortCircuitFault("B2", "F7", "B2", new ShortCircuitFaultImpedance(new Complex(0.)), ShortCircuitFault.ShortCircuitType.MONOPHASED);
        faultList.add(sc7);

        ShortCircuitEngineParameters.PeriodType periodType = ShortCircuitEngineParameters.PeriodType.SUB_TRANSIENT;
        ShortCircuitNormIec shortCircuitNormIec = new ShortCircuitNormIec();
        ShortCircuitEngineParameters scbParameters = new ShortCircuitEngineParameters(loadFlowParameters, matrixFactory,
                ShortCircuitEngineParameters.AnalysisType.SELECTIVE, faultList, true, ShortCircuitEngineParameters.VoltageProfileType.NOMINAL, false, periodType, shortCircuitNormIec);
        ShortCircuitUnbalancedEngine scbEngine = new ShortCircuitUnbalancedEngine(network, scbParameters);

        scbEngine.run();
        Map<ShortCircuitFault, Double> val = new HashMap<>();
        Map<ShortCircuitFault, Double> coefPeakb = new HashMap<>();
        Map<ShortCircuitFault, Double> coefPeakc = new HashMap<>();
        Map<ShortCircuitFault, Complex> zth = new HashMap<>();
        Map<ShortCircuitFault, Double> rOverX = new HashMap<>();
        LfNetwork lfn = scbEngine.resultsPerFault.get(sc1).getLfNetwork();
        for (Map.Entry<ShortCircuitFault, ShortCircuitResult> res : scbEngine.resultsPerFault.entrySet()) {
            ShortCircuitFault shortCircuitFault = res.getKey();
            val.put(shortCircuitFault, res.getValue().getIk().abs());
            coefPeakb.put(shortCircuitFault, res.getValue().getPeakCoefb());
            coefPeakc.put(shortCircuitFault, res.getValue().getPeakCoefc());
            zth.put(shortCircuitFault, res.getValue().getZd());
            rOverX.put(shortCircuitFault, res.getValue().getMaxRoverX());
        }

        // and I"k = 1/sqrt(3) * cmax * Un /(Zeq) and expected I"k = 35.64 kA with some approximations on the impedance values
        assertEquals(35.704355482441656, val.get(sc1), 0.00001);
        assertEquals(34.98241168878666, val.get(sc2), 0.00001);
        assertEquals(4.8337057506726575, val.get(sc3), 0.00001);

        // Peak current method b
        assertEquals(72.94205586372965, val.get(sc1) * coefPeakb.get(sc1) * Math.sqrt(2.), 0.00001);
        assertEquals(70.79940064409273, val.get(sc2) * coefPeakb.get(sc2) * Math.sqrt(2.), 0.00001);
        assertEquals(8.297665898056362, val.get(sc3) * coefPeakb.get(sc3) * Math.sqrt(2.), 0.00001);

        // Peak current method c
        assertEquals(71.86772021431725, val.get(sc1) * coefPeakc.get(sc1) * Math.sqrt(2.), 0.00001); // use of general forumla to compute Kc
        assertEquals(68.63980536534321, val.get(sc2) * coefPeakc.get(sc2) * Math.sqrt(2.), 0.00001);
        assertEquals(7.284963377037304, val.get(sc3) * coefPeakc.get(sc3) * Math.sqrt(2.), 0.00001);
    }

    public static Complex getZa(Complex zth1, Complex zth2, Complex zl) {
        Complex denomA = zth2.subtract(zth1).add(zl).multiply(2.);
        Complex deltaA = zl.multiply(zl).add(zth1.multiply(zth2).multiply(4.)).sqrt();
        Complex tmp1A = zth1.multiply(2.).subtract(zl);

        Complex za1 = zl.divide(denomA).multiply(tmp1A.add(deltaA));
        //Complex za2 = zl.divide(denomA).multiply(tmp1A.subtract(deltaA));
        return za1;
    }

    public static Complex getZb(Complex zth1, Complex zth2, Complex zl) {
        return getZa(zth2, zth1, zl);
    }

    public static Complex getZthLine(Complex zth1, Complex zth2, Complex zl, double percentage) {
        Complex za = getZa(zth1, zth2, zl);
        Complex zb = getZb(zth1, zth2, zl);
        // Zth = (percent * Zl + Za) // ((1-percent) * Zl + Zb)
        Complex y1 = zl.multiply(percentage / 100.).add(za).reciprocal();
        Complex y2 = zl.multiply(100. - percentage).divide(100.).add(zb).reciprocal();
        return y1.add(y2).reciprocal();
    }

}
