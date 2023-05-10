package com.powsybl.openloadflow.ac;

import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.openloadflow.network.extensions.AsymThreePhaseTransfo;
import com.powsybl.openloadflow.network.extensions.LegConnectionType;
import com.powsybl.openloadflow.util.ComplexMatrix;
import com.powsybl.openloadflow.util.Fortescue;
import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.geometry.euclidean.twod.Vector2D;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

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

    public DenseMatrix init4busFeeder(LegConnectionType leg1Type, LegConnectionType leg2Type, int numPhaseDisconnected) {
        double vBase2 = 12.47;
        double vBase3 = 4.16;
        double sBase = 2;

        double vWinding2 = vBase2;
        double vWinding3 = vBase3;
        if (leg1Type == LegConnectionType.DELTA) {
            vWinding2 = vBase2 * Math.sqrt(3);
        }
        if (leg2Type == LegConnectionType.DELTA) {
            vWinding3 = vBase3 * Math.sqrt(3);
        }

        double zBase = vWinding3 * vWinding3 / sBase / 3.;
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

        AsymThreePhaseTransfo asym3phaseTfo = new AsymThreePhaseTransfo(leg1ConnectionType, leg2ConnectionType,
                ya, yb, yc, rho, zG1, zG2, connectionList);

        DenseMatrix yabc = asym3phaseTfo.getYabc();

        return yabc;
    }

    @Test
    void asym3phase4busFeederTransfoTest() {
        // test of an asymmetric three phase transformer
        LegConnectionType leg1ConnectionType = LegConnectionType.Y_GROUNDED;
        LegConnectionType leg2ConnectionType = LegConnectionType.Y_GROUNDED;

        DenseMatrix yabc = init4busFeeder(leg1ConnectionType, leg2ConnectionType, 0); //asym3phaseTfo.getYabc();

        Vector2D va2Cart = Fortescue.getCartesianFromPolar(7.107, Math.toRadians(-0.3));
        Vector2D vb2Cart = Fortescue.getCartesianFromPolar(7.140, Math.toRadians(-120.3));
        Vector2D vc2Cart = Fortescue.getCartesianFromPolar(7.121, Math.toRadians(119.6));

        Vector2D va3Cart = Fortescue.getCartesianFromPolar(2.2476, Math.toRadians(-3.7));
        Vector2D vb3Cart = Fortescue.getCartesianFromPolar(2.269, Math.toRadians(-123.5));
        Vector2D vc3Cart = Fortescue.getCartesianFromPolar(2.256, Math.toRadians(116.4));

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
        ComplexMatrix i23 = getComplexCurrent(iabc2Iabc3);

    }

    @Test
    void asym3phase4busFeederYgDeltaTransfoTest() {
        // test of an asymmetric three phase transformer
        LegConnectionType leg1ConnectionType = LegConnectionType.Y_GROUNDED;
        LegConnectionType leg2ConnectionType = LegConnectionType.DELTA;

        DenseMatrix yabc = init4busFeeder(leg1ConnectionType, leg2ConnectionType, 0);

        Vector2D va2Cart = Fortescue.getCartesianFromPolar(7.113, Math.toRadians(-0.3));
        Vector2D vb2Cart = Fortescue.getCartesianFromPolar(7.132, Math.toRadians(-120.3));
        Vector2D vc2Cart = Fortescue.getCartesianFromPolar(7.123, Math.toRadians(119.6));

        Vector2D vab3Cart = Fortescue.getCartesianFromPolar(3.906, Math.toRadians(-3.5));
        Vector2D vbc3Cart = Fortescue.getCartesianFromPolar(3.915, Math.toRadians(-123.6));
        Vector2D vca3Cart = Fortescue.getCartesianFromPolar(3.909, Math.toRadians(116.3));

        Complex vab3Complex = new Complex(vab3Cart.getX(), vab3Cart.getY());
        Complex vca3Complex = new Complex(vca3Cart.getX(), vca3Cart.getY());
        // we suppose Va3 = 1/sqrt(3).Vab3 (arbitrary)

        Complex va3 = new Complex(vab3Cart.getX(), vab3Cart.getY()).multiply(1. / Math.sqrt(3.));
        Complex vb3 = va3.add(vab3Complex.multiply(-1.));
        Complex vc3 = vca3Complex.add(va3);

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
        ComplexMatrix i23 = getComplexCurrent(iabc2Iabc3);

    }

    @Test
    void asym3phase4busFeederOpenYgDeltaTransfoTest() {
        // test of an asymmetric three phase transformer
        LegConnectionType leg1ConnectionType = LegConnectionType.Y_GROUNDED;
        LegConnectionType leg2ConnectionType = LegConnectionType.DELTA;

        DenseMatrix yabc = init4busFeeder(leg1ConnectionType, leg2ConnectionType, 3);

        Vector2D va2Cart = Fortescue.getCartesianFromPolar(6.984, Math.toRadians(0.4));
        Vector2D vb2Cart = Fortescue.getCartesianFromPolar(7.167, Math.toRadians(-121.7));
        Vector2D vc2Cart = Fortescue.getCartesianFromPolar(7.293, Math.toRadians(120.5));

        Vector2D vab3Cart = Fortescue.getCartesianFromPolar(3.701, Math.toRadians(-0.9));
        Vector2D vbc3Cart = Fortescue.getCartesianFromPolar(4.076, Math.toRadians(-126.5));
        Vector2D vca3Cart = Fortescue.getCartesianFromPolar(3.572, Math.toRadians(110.9));

        Complex vab3Complex = new Complex(vab3Cart.getX(), vab3Cart.getY());
        Complex vca3Complex = new Complex(vca3Cart.getX(), vca3Cart.getY());
        // we suppose Va3 = 1/sqrt(3).Vab3 (arbitrary)
        Complex va3 = new Complex(vab3Cart.getX(), vab3Cart.getY()).multiply(1. / Math.sqrt(3.));

        Complex vb3 = va3.add(vab3Complex.multiply(-1.));
        Complex vc3 = vca3Complex.add(va3);

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
        ComplexMatrix i23 = getComplexCurrent(iabc2Iabc3);

    }

    public ComplexMatrix getComplexCurrent(DenseMatrix iabc2Iabc3) {
        ComplexMatrix i23 = new ComplexMatrix(6, 1);
        for (int i = 1; i <= 6; i++) {
            Complex term = new Complex(iabc2Iabc3.get(2 * (i - 1), 0), iabc2Iabc3.get(2 * (i - 1) + 1, 0));
            i23.set(i, 1, term);
        }

        System.out.println("Ia2 = " + i23.getTerm(1, 1).abs() + " ( " + Math.toDegrees(i23.getTerm(1, 1).getArgument()));
        System.out.println("Ib2 = " + i23.getTerm(2, 1).abs() + " ( " + Math.toDegrees(i23.getTerm(2, 1).getArgument()));
        System.out.println("Ic2 = " + i23.getTerm(3, 1).abs() + " ( " + Math.toDegrees(i23.getTerm(3, 1).getArgument()));

        System.out.println("Ia3 = " + i23.getTerm(4, 1).abs() + " ( " + Math.toDegrees(i23.getTerm(4, 1).getArgument()));
        System.out.println("Ib3 = " + i23.getTerm(5, 1).abs() + " ( " + Math.toDegrees(i23.getTerm(5, 1).getArgument()));
        System.out.println("Ic3 = " + i23.getTerm(6, 1).abs() + " ( " + Math.toDegrees(i23.getTerm(6, 1).getArgument()));

        return i23;

    }

}
