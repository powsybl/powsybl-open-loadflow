/**
 * Copyright (c) 2023, Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 * Copyright (c) 2023, Geoffroy Jamgotchian <geoffroy.jamgotchian at gmail.com>
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.openloadflow.network.extensions.AsymThreePhaseTransfo;
import com.powsybl.openloadflow.network.extensions.LegConnectionType;
import com.powsybl.openloadflow.network.extensions.StepType;
import com.powsybl.openloadflow.util.ComplexMatrix;
import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.complex.ComplexUtils;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class AsymThreePhaseTfoTest {

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

        AsymThreePhaseTransfo asym3phaseTfo = new AsymThreePhaseTransfo(leg1ConnectionType, leg2ConnectionType, StepType.STEP_DOWN,
                ya, yb, yc, rho, zG1, zG2, connectionList);

        DenseMatrix yabc = asym3phaseTfo.getYabc();

        assertEquals(0.040504009900990144, yabc.get(0, 0), 0.000001);
        assertEquals(0.39694009900990224, yabc.get(0, 1), 0.000001);
        assertEquals(0.0, yabc.get(5, 4), 0.000001);

    }

    public static ComplexMatrix buildSinglePhaseAdmittanceMatrix(Complex z, Complex y1, Complex y2) {
        ComplexMatrix cm = new ComplexMatrix(2, 2);
        cm.set(1, 1, y1.add(z.reciprocal()));
        cm.set(1, 2, z.reciprocal().multiply(-1.));
        cm.set(2, 1, z.reciprocal().multiply(-1.));
        cm.set(2, 2, y2.add(z.reciprocal()));

        return cm;
    }

    public DenseMatrix initYabc4busFeeder(LegConnectionType leg1Type, LegConnectionType leg2Type, int numPhaseDisconnected, StepType stepLegConnectionType) {
        double vBase2 = 12.47;
        double vBase3 = 4.16;
        if (stepLegConnectionType == StepType.STEP_UP) {
            vBase3 = 24.9;
        }
        double sBase = 2.;

        double vWinding2 = vBase2;
        double vWinding3 = vBase3;
        if (leg1Type == LegConnectionType.Y_GROUNDED || leg1Type == LegConnectionType.Y) {
            vWinding2 = vBase2 / Math.sqrt(3.);
        }
        if (leg2Type == LegConnectionType.Y_GROUNDED || leg2Type == LegConnectionType.Y) {
            vWinding3 = vBase3 / Math.sqrt(3.);
        }

        double zBase = vWinding3 * vWinding3 / sBase;

        Complex zPhase = new Complex(1., 6.).multiply(zBase / 100.);
        Complex yPhase = new Complex(0., 0.);

        Complex rho = new Complex(1., 0.).multiply(vWinding3 / vWinding2);

        LegConnectionType leg1ConnectionType = leg1Type;
        LegConnectionType leg2ConnectionType = leg2Type;

        Complex zG1 = new Complex(0., 0.);
        Complex zG2 = new Complex(0., 0.);

        List<Boolean> connectionList = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            if (i == numPhaseDisconnected - 1) {
                connectionList.add(false);
            } else {
                connectionList.add(true);
            }

        }

        ComplexMatrix ya = buildSinglePhaseAdmittanceMatrix(zPhase, yPhase, yPhase);
        ComplexMatrix yb = buildSinglePhaseAdmittanceMatrix(zPhase, yPhase, yPhase);
        ComplexMatrix yc = buildSinglePhaseAdmittanceMatrix(zPhase, yPhase, yPhase);

        AsymThreePhaseTransfo asym3phaseTfo = new AsymThreePhaseTransfo(leg1ConnectionType, leg2ConnectionType, stepLegConnectionType,
                ya, yb, yc, rho, zG1, zG2, connectionList);

        DenseMatrix yabc = asym3phaseTfo.getYabc();

        return yabc;
    }

    @Test
    void ygYgTest() {
        // test of an asymmetric three phase transformer
        LegConnectionType leg1ConnectionType = LegConnectionType.Y_GROUNDED;
        LegConnectionType leg2ConnectionType = LegConnectionType.Y_GROUNDED;
        DenseMatrix yabc = initYabc4busFeeder(leg1ConnectionType, leg2ConnectionType, 0, StepType.STEP_DOWN);

        Complex va2 = ComplexUtils.polar2Complex(7.107, Math.toRadians(-0.3));
        Complex vb2 = ComplexUtils.polar2Complex(7.140, Math.toRadians(-120.3));
        Complex vc2 = ComplexUtils.polar2Complex(7.121, Math.toRadians(119.6));

        Complex va3 = ComplexUtils.polar2Complex(2.2476, Math.toRadians(-3.7));
        Complex vb3 = ComplexUtils.polar2Complex(2.269, Math.toRadians(-123.5));
        Complex vc3 = ComplexUtils.polar2Complex(2.256, Math.toRadians(116.4));

        DenseMatrix vabc2Vabc3 = buildV2V3(va2, vb2, vc2, va3, vb3, vc3);

        DenseMatrix iabc2Iabc3 = yabc.times(vabc2Vabc3);
        ComplexMatrix i23 = getComplexCurrent(iabc2Iabc3);

        assertEquals(0.10428374508582401, yabc.get(0, 0), 0.000001);
        assertEquals(0.6257024705149441, yabc.get(0, 1), 0.000001);
        assertEquals(-0.6257024705149441, yabc.get(5, 4), 0.000001);

        assertEquals(0.2887570522476377, i23.getTerm(1, 1).getReal(), 0.000001);
        assertEquals(-0.14632219207677777, i23.getTerm(2, 1).getImaginary(), 0.000001);
        assertEquals(0.8771236971578364, i23.getTerm(5, 1).getReal(), 0.000001);

    }

    @Test
    void ygDeltaTest() {

        // test of an asymmetric three phase transformer
        LegConnectionType leg1Type = LegConnectionType.Y_GROUNDED;
        LegConnectionType leg2Type = LegConnectionType.DELTA;

        DenseMatrix yabc = initYabc4busFeeder(leg1Type, leg2Type, 0, StepType.STEP_DOWN);

        Complex va2 = ComplexUtils.polar2Complex(7.113, Math.toRadians(-0.3));
        Complex vb2 = ComplexUtils.polar2Complex(7.132, Math.toRadians(-120.3));
        Complex vc2 = ComplexUtils.polar2Complex(7.123, Math.toRadians(119.6));

        Complex vab3 = ComplexUtils.polar2Complex(3.906, Math.toRadians(-3.5));
        Complex vbc3 = ComplexUtils.polar2Complex(3.915, Math.toRadians(-123.6));
        Complex vca3 = ComplexUtils.polar2Complex(3.909, Math.toRadians(116.3));

        DenseMatrix vabc2Vabc3 = buildV2V3(va2, vb2, vc2, vab3, vbc3, vca3);

        DenseMatrix iabc2Iabc3 = yabc.times(vabc2Vabc3);
        ComplexMatrix i23 = getComplexCurrent(iabc2Iabc3);

        assertEquals(0.27303558143037904, i23.getTerm(1, 1).getReal(), 0.000001);
        assertEquals(-0.18956777933726515, i23.getTerm(1, 1).getImaginary(), 0.000001);
        assertEquals(-0.30599036266520097, i23.getTerm(2, 1).getReal(), 0.000001);
        assertEquals(-0.14825736280052748, i23.getTerm(2, 1).getImaginary(), 0.000001);
        assertEquals(0.026104499970873718, i23.getTerm(3, 1).getReal(), 0.000001);
        assertEquals(0.33933258784465525, i23.getTerm(3, 1).getImaginary(), 0.000001);
        assertEquals(-0.42735447298282914, i23.getTerm(4, 1).getReal(), 0.000001);
        assertEquals(0.915348267789944, i23.getTerm(4, 1).getImaginary(), 0.000001);
    }

    @Test
    void ygDeltaOpenTest() {

        LegConnectionType leg1Type = LegConnectionType.Y_GROUNDED;
        LegConnectionType leg2Type = LegConnectionType.DELTA;

        DenseMatrix yabc = initYabc4busFeeder(leg1Type, leg2Type, 3, StepType.STEP_DOWN);

        Complex va2 = ComplexUtils.polar2Complex(6.984, Math.toRadians(0.4));
        Complex vb2 = ComplexUtils.polar2Complex(7.167, Math.toRadians(-121.7));
        Complex vc2 = ComplexUtils.polar2Complex(7.293, Math.toRadians(120.5));

        Complex vab3 = ComplexUtils.polar2Complex(3.701, Math.toRadians(-0.9));
        Complex vbc3 = ComplexUtils.polar2Complex(4.076, Math.toRadians(-126.5));
        Complex vca3 = ComplexUtils.polar2Complex(3.572, Math.toRadians(110.9));

        DenseMatrix vabc2Vabc3 = buildV2V3(va2, vb2, vc2, vab3, vbc3, vca3);

        DenseMatrix iabc2Iabc3 = yabc.times(vabc2Vabc3);
        ComplexMatrix i23 = getComplexCurrent(iabc2Iabc3);

        assertEquals(0.15388408382082094, i23.getTerm(1, 1).getReal(), 0.000001);
        assertEquals(-0.34697842658271666, i23.getTerm(1, 1).getImaginary(), 0.000001);
        assertEquals(-0.22247163746892218, i23.getTerm(2, 1).getReal(), 0.000001);
        assertEquals(-0.3135612330081986, i23.getTerm(2, 1).getImaginary(), 0.000001);
        assertEquals(-3.536804553588018E-17, i23.getTerm(3, 1).getReal(), 0.000001);
        assertEquals(9.261975581694497E-17, i23.getTerm(3, 1).getImaginary(), 0.000001);
        assertEquals(-0.26632148190092014, i23.getTerm(4, 1).getReal(), 0.000001);
        assertEquals(0.6005027060677525, i23.getTerm(4, 1).getImaginary(), 0.000001);
    }

    @Test
    void deltaYgTest() {

        LegConnectionType leg1Type = LegConnectionType.DELTA;
        LegConnectionType leg2Type = LegConnectionType.Y_GROUNDED;

        DenseMatrix yabc = initYabc4busFeeder(leg1Type, leg2Type, 0, StepType.STEP_DOWN);

        Complex vab2 = ComplexUtils.polar2Complex(12.340, Math.toRadians(29.7));
        Complex vbc2 = ComplexUtils.polar2Complex(12.349, Math.toRadians(-90.4));
        Complex vca2 = ComplexUtils.polar2Complex(12.318, Math.toRadians(149.6));

        Complex va3 = ComplexUtils.polar2Complex(2.249, Math.toRadians(-33.7));
        Complex vb3 = ComplexUtils.polar2Complex(2.263, Math.toRadians(-153.4));
        Complex vc3 = ComplexUtils.polar2Complex(2.259, Math.toRadians(86.4));

        DenseMatrix vabc2Vabc3 = buildV2V3(vab2, vbc2, vca2, va3, vb3, vc3);

        DenseMatrix iabc2Iabc3 = yabc.times(vabc2Vabc3);
        ComplexMatrix i23 = getComplexCurrent(iabc2Iabc3);

        assertEquals(0.2681475622733862, i23.getTerm(1, 1).getReal(), 0.000001);
        assertEquals(-0.19602965167346104, i23.getTerm(1, 1).getImaginary(), 0.000001);
        assertEquals(-0.296855542087167, i23.getTerm(2, 1).getReal(), 0.000001);
        assertEquals(-0.14318726020177058, i23.getTerm(2, 1).getImaginary(), 0.000001);
        assertEquals(0.02870797981378015, i23.getTerm(3, 1).getReal(), 0.000001);
        assertEquals(0.339216911875232, i23.getTerm(3, 1).getImaginary(), 0.000001);
        assertEquals(-0.4300369121879051, i23.getTerm(4, 1).getReal(), 0.000001);
        assertEquals(0.9410047879996393, i23.getTerm(4, 1).getImaginary(), 0.000001);
    }

    @Test
    void deltaDeltaTest() {

        LegConnectionType leg1Type = LegConnectionType.DELTA;
        LegConnectionType leg2Type = LegConnectionType.DELTA;

        DenseMatrix yabc = initYabc4busFeeder(leg1Type, leg2Type, 0, StepType.STEP_DOWN);

        Complex vab2 = ComplexUtils.polar2Complex(12.339, Math.toRadians(29.7));
        Complex vbc2 = ComplexUtils.polar2Complex(12.349, Math.toRadians(-90.4));
        Complex vca2 = ComplexUtils.polar2Complex(12.321, Math.toRadians(149.6));

        Complex vab3 = ComplexUtils.polar2Complex(3.911, Math.toRadians(26.5));
        Complex vbc3 = ComplexUtils.polar2Complex(3.914, Math.toRadians(-93.6));
        Complex vca3 = ComplexUtils.polar2Complex(3.905, Math.toRadians(146.4));

        DenseMatrix vabc2Vabc3 = buildV2V3(vab2, vbc2, vca2, vab3, vbc3, vca3);

        DenseMatrix iabc2Iabc3 = yabc.times(vabc2Vabc3);
        ComplexMatrix i23 = getComplexCurrent(iabc2Iabc3);

        assertEquals(0.27306924228440366, i23.getTerm(1, 1).getReal(), 0.000001);
        assertEquals(-0.19100805464714513, i23.getTerm(1, 1).getImaginary(), 0.000001);
        assertEquals(-0.3026240522804131, i23.getTerm(2, 1).getReal(), 0.000001);
        assertEquals(-0.14138876781223225, i23.getTerm(2, 1).getImaginary(), 0.000001);
        assertEquals(0.029554809996009235, i23.getTerm(3, 1).getReal(), 0.000001);
        assertEquals(0.3323968224593772, i23.getTerm(3, 1).getImaginary(), 0.000001);
        assertEquals(-0.8185513104054127, i23.getTerm(4, 1).getReal(), 0.000001);
        assertEquals(0.5725650099639185, i23.getTerm(4, 1).getImaginary(), 0.000001);
    }

    @Test
    void yDeltaTest() {

        // test of an asymmetric three phase transformer
        LegConnectionType leg1Type = LegConnectionType.Y;
        LegConnectionType leg2Type = LegConnectionType.DELTA;

        DenseMatrix yabc = initYabc4busFeeder(leg1Type, leg2Type, 0, StepType.STEP_DOWN);

        Complex va2 = ComplexUtils.polar2Complex(7.116, Math.toRadians(-0.03));
        Complex vb2 = ComplexUtils.polar2Complex(7.131, Math.toRadians(-120.3));
        Complex vc2 = ComplexUtils.polar2Complex(7.121, Math.toRadians(119.6));

        Complex vab3 = ComplexUtils.polar2Complex(3.906, Math.toRadians(-3.4));
        Complex vbc3 = ComplexUtils.polar2Complex(3.915, Math.toRadians(-123.6));
        Complex vca3 = ComplexUtils.polar2Complex(3.909, Math.toRadians(116.3));

        DenseMatrix vabc2Vabc3 = buildV2V3(va2, vb2, vc2, vab3, vbc3, vca3);

        DenseMatrix iabc2Iabc3 = yabc.times(vabc2Vabc3);
        ComplexMatrix i23 = getComplexCurrent(iabc2Iabc3);

        assertEquals(0.284684388832979, i23.getTerm(1, 1).getReal(), 0.000001);
        assertEquals(-0.18921260842174584, i23.getTerm(1, 1).getImaginary(), 0.000001);
        assertEquals(-0.30760066933716, i23.getTerm(2, 1).getReal(), 0.000001);
        assertEquals(-0.14890175930750207, i23.getTerm(2, 1).getImaginary(), 0.000001);
        assertEquals(0.022916280504181152, i23.getTerm(3, 1).getReal(), 0.000001);
        assertEquals(0.33811436772924747, i23.getTerm(3, 1).getImaginary(), 0.000001);
        assertEquals(-0.45303236561943727, i23.getTerm(4, 1).getReal(), 0.000001);
        assertEquals(0.9126252582326122, i23.getTerm(4, 1).getImaginary(), 0.000001);
    }

    @Test
    void yDeltaUnbalancedTest() {

        // test of an asymmetric three phase transformer
        LegConnectionType leg1Type = LegConnectionType.Y;
        LegConnectionType leg2Type = LegConnectionType.DELTA;

        DenseMatrix yabc = initYabc4busFeeder(leg1Type, leg2Type, 0, StepType.STEP_DOWN);

        Complex va2 = ComplexUtils.polar2Complex(7.116, Math.toRadians(-0.03));
        Complex vb2 = ComplexUtils.polar2Complex(7.142, Math.toRadians(-120.4));
        Complex vc2 = ComplexUtils.polar2Complex(7.109, Math.toRadians(119.6));

        Complex vab3 = ComplexUtils.polar2Complex(3.896, Math.toRadians(-2.8));
        Complex vbc3 = ComplexUtils.polar2Complex(3.972, Math.toRadians(-123.8));
        Complex vca3 = ComplexUtils.polar2Complex(3.874, Math.toRadians(115.7));

        DenseMatrix vabc2Vabc3 = buildV2V3(va2, vb2, vc2, vab3, vbc3, vca3);

        DenseMatrix iabc2Iabc3 = yabc.times(vabc2Vabc3);
        ComplexMatrix i23 = getComplexCurrent(iabc2Iabc3);

        assertEquals(0.2402538477862013, i23.getTerm(1, 1).getReal(), 0.000001);
        assertEquals(-0.20791096562598232, i23.getTerm(1, 1).getImaginary(), 0.000001);
        assertEquals(-0.26152346138443994, i23.getTerm(2, 1).getReal(), 0.000001);
        assertEquals(-0.1813189092154579, i23.getTerm(2, 1).getImaginary(), 0.000001);
        assertEquals(0.021269613598237758, i23.getTerm(3, 1).getReal(), 0.000001);
        assertEquals(0.3892298748414399, i23.getTerm(3, 1).getImaginary(), 0.000001);
        assertEquals(-0.37898789994281223, i23.getTerm(4, 1).getReal(), 0.000001);
        assertEquals(1.0334495263462005, i23.getTerm(4, 1).getImaginary(), 0.000001);
    }

    @Test
    void ygDeltaUpTest() {

        // test of an asymmetric three phase transformer
        LegConnectionType leg1Type = LegConnectionType.Y_GROUNDED;
        LegConnectionType leg2Type = LegConnectionType.DELTA;

        DenseMatrix yabc = initYabc4busFeeder(leg1Type, leg2Type, 0, StepType.STEP_UP);

        Complex va2 = ComplexUtils.polar2Complex(7.128, Math.toRadians(-0.3));
        Complex vb2 = ComplexUtils.polar2Complex(7.145, Math.toRadians(-120.3));
        Complex vc2 = ComplexUtils.polar2Complex(7.137, Math.toRadians(119.6));

        Complex vab3 = ComplexUtils.polar2Complex(23.746, Math.toRadians(56.7));
        Complex vbc3 = ComplexUtils.polar2Complex(23.722, Math.toRadians(-63.4));
        Complex vca3 = ComplexUtils.polar2Complex(23.698, Math.toRadians(176.7));

        DenseMatrix vabc2Vabc3 = buildV2V3(va2, vb2, vc2, vab3, vbc3, vca3);

        DenseMatrix iabc2Iabc3 = yabc.times(vabc2Vabc3);
        ComplexMatrix i23 = getComplexCurrent(iabc2Iabc3);

        assertEquals(0.2533986855709742, i23.getTerm(1, 1).getReal(), 0.000001);
        assertEquals(-0.1424933084118272, i23.getTerm(1, 1).getImaginary(), 0.000001);
        assertEquals(-0.2521278029015872, i23.getTerm(2, 1).getReal(), 0.000001);
        assertEquals(-0.14792504943201856, i23.getTerm(2, 1).getImaginary(), 0.000001);
        assertEquals(-0.0019146917820851428, i23.getTerm(3, 1).getReal(), 0.000001);
        assertEquals(0.29170806310525454, i23.getTerm(3, 1).getImaginary(), 0.000001);
        assertEquals(-0.14616735750604148, i23.getTerm(4, 1).getReal(), 0.000001);
        assertEquals(-0.0015705274593572384, i23.getTerm(4, 1).getImaginary(), 0.000001);
    }

    @Test
    void deltaYgUpTest() {

        LegConnectionType leg1Type = LegConnectionType.DELTA;
        LegConnectionType leg2Type = LegConnectionType.Y_GROUNDED;

        DenseMatrix yabc = initYabc4busFeeder(leg1Type, leg2Type, 0, StepType.STEP_UP);

        Complex vab2 = ComplexUtils.polar2Complex(12.361, Math.toRadians(29.7));
        Complex vbc2 = ComplexUtils.polar2Complex(12.372, Math.toRadians(-90.4));
        Complex vca2 = ComplexUtils.polar2Complex(12.348, Math.toRadians(149.6));

        Complex va3 = ComplexUtils.polar2Complex(13.697, Math.toRadians(26.7));
        Complex vb3 = ComplexUtils.polar2Complex(13.710, Math.toRadians(-93.4));
        Complex vc3 = ComplexUtils.polar2Complex(13.681, Math.toRadians(146.6));

        DenseMatrix vabc2Vabc3 = buildV2V3(vab2, vbc2, vca2, va3, vb3, vc3);

        DenseMatrix iabc2Iabc3 = yabc.times(vabc2Vabc3);
        ComplexMatrix i23 = getComplexCurrent(iabc2Iabc3);

        assertEquals(0.2532987861360225, i23.getTerm(1, 1).getReal(), 0.000001);
        assertEquals(-0.1434367868202543, i23.getTerm(1, 1).getImaginary(), 0.000001);
        assertEquals(-0.2510527819744879, i23.getTerm(2, 1).getReal(), 0.000001);
        assertEquals(-0.14814633744394362, i23.getTerm(2, 1).getImaginary(), 0.000001);
        assertEquals(-0.0022460041615348647, i23.getTerm(3, 1).getReal(), 0.000001);
        assertEquals(0.2915831242641983, i23.getTerm(3, 1).getImaginary(), 0.000001);
        assertEquals(-0.14590856873228664, i23.getTerm(4, 1).getReal(), 0.000001);
        assertEquals(-0.0014467004877244394, i23.getTerm(4, 1).getImaginary(), 0.000001);
    }

    @Test
    void test34() {
        // test of an asymmetric three phase transformer
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

        DenseMatrix b3 = ComplexMatrix.complexMatrixIdentity(3).getRealCartesianMatrix();
        DenseMatrix minusId3 = ComplexMatrix.getMatrixScaled(ComplexMatrix.complexMatrixIdentity(3), -1.).getRealCartesianMatrix();
        DenseMatrix zReal = zy.getRealCartesianMatrix();
        zReal.decomposeLU().solve(b3);

        double feetInMile = 5280;
        double length1InFeet = 2000;
        double length2InFeet = 2500;

        DenseMatrix minusB3 = b3.times(minusId3);
        DenseMatrix realYabc34 = AsymThreePhaseTransfo.buildFromBlocs(b3, minusB3, minusB3, b3);
        ComplexMatrix yabc34 = ComplexMatrix.getMatrixScaled(ComplexMatrix.getComplexMatrixFromRealCartesian(realYabc34), feetInMile / length2InFeet);

        Complex va3 = ComplexUtils.polar2Complex(2.249, Math.toRadians(-33.7));
        Complex vb3 = ComplexUtils.polar2Complex(2.263, Math.toRadians(-153.4));
        Complex vc3 = ComplexUtils.polar2Complex(2.259, Math.toRadians(86.4));

        Complex va4 = ComplexUtils.polar2Complex(1.920, Math.toRadians(-39.1));
        Complex vb4 = ComplexUtils.polar2Complex(2.054, Math.toRadians(-158.3));
        Complex vc4 = ComplexUtils.polar2Complex(1.986, Math.toRadians(80.9));

        DenseMatrix vabc3Vabc4 = buildV2V3(va3, vb3, vc3, va4, vb4, vc4);

        DenseMatrix iabc3Iabc4 = yabc34.getRealCartesianMatrix().times(vabc3Vabc4);
        ComplexMatrix i23 = getComplexCurrent(iabc3Iabc4);

        assertEquals(0.44749471117105044, i23.getTerm(1, 1).getReal(), 0.000001);
        assertEquals(-0.94199588685046, i23.getTerm(1, 1).getImaginary(), 0.000001);
        assertEquals(-0.9734720047009358, i23.getTerm(2, 1).getReal(), 0.000001);
        assertEquals(0.06972907399381911, i23.getTerm(2, 1).getImaginary(), 0.000001);
        assertEquals(0.5759369168782698, i23.getTerm(3, 1).getReal(), 0.000001);
        assertEquals(0.823887819940041, i23.getTerm(3, 1).getImaginary(), 0.000001);
        assertEquals(-0.44749471117105044, i23.getTerm(4, 1).getReal(), 0.000001);
        assertEquals(0.94199588685046, i23.getTerm(4, 1).getImaginary(), 0.000001);
    }

    public DenseMatrix buildV2V3(Complex va2, Complex vb2, Complex vc2, Complex va3, Complex vb3, Complex vc3) {
        DenseMatrix v = new DenseMatrix(12, 1);
        v.add(0, 0, va2.getReal());
        v.add(1, 0, va2.getImaginary());
        v.add(2, 0, vb2.getReal());
        v.add(3, 0, vb2.getImaginary());
        v.add(4, 0, vc2.getReal());
        v.add(5, 0, vc2.getImaginary());

        v.add(6, 0, va3.getReal());
        v.add(7, 0, va3.getImaginary());
        v.add(8, 0, vb3.getReal());
        v.add(9, 0, vb3.getImaginary());
        v.add(10, 0, vc3.getReal());
        v.add(11, 0, vc3.getImaginary());

        return v;
    }

    public ComplexMatrix getComplexCurrent(DenseMatrix iabc2Iabc3) {
        ComplexMatrix i23 = new ComplexMatrix(6, 1);
        for (int i = 1; i <= 6; i++) {
            Complex term = new Complex(iabc2Iabc3.get(2 * (i - 1), 0), iabc2Iabc3.get(2 * (i - 1) + 1, 0));
            i23.set(i, 1, term);
        }

        return i23;

    }

}
