package com.powsybl.openloadflow.ac.equations;

import com.powsybl.openloadflow.equations.Variable;
import org.apache.commons.math3.util.Pair;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public final class GenericGeneratorTerm {
    // We define :
    // T0g(l,m,n,f,g,h) = Vf*Vg*Vh * Vl*Vm*Vn * ( cos(a_l+a_m+a_n-a_f-a_g-a_h) + j * sin(a_l+a_m+a_n-a_f-a_g-a_h) )
    //
    // We define :
    // T1g(f,g,h,p,q) = Vf*exp(j*a_f) * Vg*exp(j*a_g) * Vh*exp(j*a_h) * [Vo^3*exp(-3*j*a_o) + Vi^3*exp(-3*j*a_i) + Vd^3*exp(-3*j*a_d) - 3*Vd*Vo*Vi*exp(-j*(a_o+a_d+a_i)] * (p+j*q)
    // which gives:
    // T1g(f,g,h,p,q) = Tx1g(f,g,h,p,q) + j * Ty1g(f,g,h,p,q)
    // with
    // Tx1g(f,g,h,p,q) =   p * [Tx0g(o,o,o,f,g,h) - 3 * Tx0g(o,d,i,f,g,h) + Tx0g(i,i,i,f,g,h) + Tx0g(d,d,d,f,g,h)]
    //                   + q * [Ty0g(o,o,o,f,g,h) - 3 * Ty0g(o,d,i,f,g,h) + Ty0g(i,i,i,f,g,h) - Ty0g(d,d,d,f,g,h)] )
    // and
    // Ty1g(f,g,h,p,q) =   p * [-Ty0g(o,o,o,f,g,h) + 3 * Ty0g(o,d,i,f,g,h) - Ty0g(i,i,i,f,g,h) - Ty0g(d,d,d,f,g,h)]
    //                   + q * [ Tx0g(o,o,o,f,g,h) - 3 * Tx0g(o,d,i,f,g,h) + Tx0g(i,i,i,f,g,h) + Ty0g(d,d,d,f,g,h)] )

    private GenericGeneratorTerm() {

    }

    public static Pair<Double, Double> t0g(int l, int m, int n, int f, int g, int h, FortescueLoadEquationTerm equationTerm) {
        // T0g(l,m,n,f,g,h) = Vf*Vg*Vh * Vl*Vm*Vn * ( cos(a_l+a_m+a_n-a_f-a_g-a_h) + j * sin(a_l+a_m+a_n-a_f-a_g-a_h) )

        double vl = equationTerm.v(l);
        double vm = equationTerm.v(m);
        double vn = equationTerm.v(n);
        double vf = equationTerm.v(f);
        double vg = equationTerm.v(g);
        double vh = equationTerm.v(h);
        double al = equationTerm.ph(l);
        double am = equationTerm.ph(m);
        double an = equationTerm.ph(n);
        double af = equationTerm.ph(f);
        double ag = equationTerm.ph(g);
        double ah = equationTerm.ph(h);

        double module = vf * vg * vh * vl * vm * vn;
        double angle = al + am + an - af - ag - ah;
        return new Pair<>(module * Math.cos(angle), module * Math.sin(angle));
    }

    public static Pair<Integer, Integer> getPower(int l, int m, int n, int f, int g, int h, int inputSequence) {
        int power = 0; // gives the number of V magnitude matching the input sequence
        int angleCoef = 0; // give the number of angles matching the input sequence, it could be negative as {f,g,h} have negative coefficients
        if (l == inputSequence) {
            power++;
            angleCoef++;
        }
        if (m == inputSequence) {
            power++;
            angleCoef++;
        }
        if (n == inputSequence) {
            power++;
            angleCoef++;
        }
        if (f == inputSequence) {
            power++;
            angleCoef--;
        }
        if (g == inputSequence) {
            power++;
            angleCoef--;
        }
        if (h == inputSequence) {
            power++;
            angleCoef--;
        }
        return new Pair<>(power, angleCoef);
    }

    public static Pair<Double, Double> dt0g(int l, int m, int n, int f, int g, int h, FortescueLoadEquationTerm equationTerm, Variable<AcVariableType> derVariable) {

        double vl = equationTerm.v(l);
        double vm = equationTerm.v(m);
        double vn = equationTerm.v(n);
        double vf = equationTerm.v(f);
        double vg = equationTerm.v(g);
        double vh = equationTerm.v(h);
        double al = equationTerm.ph(l);
        double am = equationTerm.ph(m);
        double an = equationTerm.ph(n);
        double af = equationTerm.ph(f);
        double ag = equationTerm.ph(g);
        double ah = equationTerm.ph(h);

        boolean isMagnitudeDerivative;
        int derSequence;
        double varDerivativeValue;
        if (derVariable.getType() == AcVariableType.BUS_V) {
            isMagnitudeDerivative = true;
            derSequence = 1;
            varDerivativeValue = equationTerm.v(1);
        } else if (derVariable.getType() == AcVariableType.BUS_V_HOMOPOLAR) {
            isMagnitudeDerivative = true;
            derSequence = 0;
            varDerivativeValue = equationTerm.v(0);
        } else if (derVariable.getType() == AcVariableType.BUS_V_INVERSE) {
            isMagnitudeDerivative = true;
            derSequence = 2;
            varDerivativeValue = equationTerm.v(2);
        } else if (derVariable.getType() == AcVariableType.BUS_PHI) {
            isMagnitudeDerivative = false;
            derSequence = 1;
            varDerivativeValue = equationTerm.ph(1);
        } else if (derVariable.getType() == AcVariableType.BUS_PHI_HOMOPOLAR) {
            isMagnitudeDerivative = false;
            derSequence = 0;
            varDerivativeValue = equationTerm.ph(0);
        } else if (derVariable.getType() == AcVariableType.BUS_PHI_INVERSE) {
            isMagnitudeDerivative = false;
            derSequence = 2;
            varDerivativeValue = equationTerm.ph(2);
        } else {
            throw new IllegalStateException("Unknown variable: " + derVariable);
        }

        Pair<Integer, Integer> powers = getPower(l, m, n, f, g, h, derSequence);
        int power = powers.getFirst();
        int angleCoef = powers.getSecond();

        double epsilon = 0.000000001;

        double derivativeX = 0.;
        double derivativeY = 0.;

        //t0g = vf * vg * vh * vl * vm * vn * ( Math.cos(al + am + an - af - ag - ah) + j * Math.sin(al + am + an - af - ag - ah) );
        double angle = al + am + an - af - ag - ah;
        if (isMagnitudeDerivative) {
            double tmpValX = Math.cos(angle);
            double tmpValY = Math.sin(angle);
            double derMagnitudes = 0.;
            if (power > 0) {
                if (varDerivativeValue > epsilon) {
                    derMagnitudes = power * vf * vg * vh * vl * vm * vn / varDerivativeValue;
                }
            }
            derivativeX = derMagnitudes * tmpValX;
            derivativeY = derMagnitudes * tmpValY;
        } else {
            if (angleCoef != 0) {
                derivativeX = -angleCoef * vf * vg * vh * vl * vm * vn * Math.sin(angle);
                derivativeY = angleCoef * vf * vg * vh * vl * vm * vn * Math.cos(angle);
            }
        }

        return new Pair<>(derivativeX, derivativeY);
    }

    // Tx1g(f,g,h,p,q) =   p * [Tx0g(o,o,o,f,g,h) - 3 * Tx0g(o,d,i,f,g,h) + Tx0g(i,i,i,f,g,h) + Tx0g(d,d,d,f,g,h)]
    //                   + q * [Ty0g(o,o,o,f,g,h) - 3 * Ty0g(o,d,i,f,g,h) + Ty0g(i,i,i,f,g,h) + Ty0g(d,d,d,f,g,h)] )
    // and
    // Ty1g(f,g,h,p,q) =   p * [-Ty0g(o,o,o,f,g,h) + 3 * Ty0g(o,d,i,f,g,h) - Ty0g(i,i,i,f,g,h) - Ty0g(d,d,d,f,g,h)]
    //                   + q * [ Tx0g(o,o,o,f,g,h) - 3 * Tx0g(o,d,i,f,g,h) + Tx0g(i,i,i,f,g,h) + Tx0g(d,d,d,f,g,h)] )

    public static Pair<Double, Double> t1g(int f, int g, int h, double p, double q, FortescueLoadEquationTerm equationTerm) {

        Pair<Double, Double> t0Gooofgh = t0g(0, 0, 0, f, g, h, equationTerm);
        Pair<Double, Double> t0Godifgh = t0g(0, 1, 2, f, g, h, equationTerm);
        Pair<Double, Double> t0Giiifgh = t0g(2, 2, 2, f, g, h, equationTerm);
        Pair<Double, Double> t0Gdddfgh = t0g(1, 1, 1, f, g, h, equationTerm);

        double tx1g = p * (t0Gooofgh.getFirst() - 3 * t0Godifgh.getFirst() + t0Giiifgh.getFirst() + t0Gdddfgh.getFirst())
                - q * (t0Gooofgh.getSecond() - 3 * t0Godifgh.getSecond() + t0Giiifgh.getSecond() + t0Gdddfgh.getSecond());

        double ty1g = -p * (-t0Gooofgh.getSecond() + 3 * t0Godifgh.getSecond() - t0Giiifgh.getSecond() - t0Gdddfgh.getSecond())
                + q * (t0Gooofgh.getFirst() - 3 * t0Godifgh.getFirst() + t0Giiifgh.getFirst() + t0Gdddfgh.getFirst());

        return new Pair<>(tx1g, ty1g);
    }

    public static Pair<Double, Double> dt1g(int f, int g, int h, double p, double q, FortescueLoadEquationTerm equationTerm, Variable<AcVariableType> derVariable) {

        // For now we suppose that p and q are constants not depending on voltage
        Pair<Double, Double> dt0Gooofgh = dt0g(0, 0, 0, f, g, h, equationTerm, derVariable);
        Pair<Double, Double> dt0Godifgh = dt0g(0, 1, 2, f, g, h, equationTerm, derVariable);
        Pair<Double, Double> dt0Giiifgh = dt0g(2, 2, 2, f, g, h, equationTerm, derVariable);
        Pair<Double, Double> dt0Gdddfgh = dt0g(1, 1, 1, f, g, h, equationTerm, derVariable);

        double dtx1g = p * (dt0Gooofgh.getFirst() - 3 * dt0Godifgh.getFirst() + dt0Giiifgh.getFirst() + dt0Gdddfgh.getFirst())
                - q * (dt0Gooofgh.getSecond() - 3 * dt0Godifgh.getSecond() + dt0Giiifgh.getSecond() + dt0Gdddfgh.getSecond());

        double dty1g = -p * (-dt0Gooofgh.getSecond() + 3 * dt0Godifgh.getSecond() - dt0Giiifgh.getSecond() - dt0Gdddfgh.getSecond())
                + q * (dt0Gooofgh.getFirst() - 3 * dt0Godifgh.getFirst() + dt0Giiifgh.getFirst() + dt0Gdddfgh.getFirst());

        return new Pair<>(dtx1g, dty1g);
    }

    public static double denom(FortescueLoadEquationTerm equationTerm) {
        return 2 * t0g(0, 0, 0, 2, 2, 2, equationTerm).getFirst() + 2 * t0g(0, 0, 0, 1, 1, 1, equationTerm).getFirst()
                + 2 * t0g(2, 2, 2, 1, 1, 1, equationTerm).getFirst() - 6 * t0g(0, 1, 2, 1, 1, 1, equationTerm).getFirst()
                - 6 * t0g(0, 1, 2, 0, 0, 0, equationTerm).getFirst() - 6 * t0g(0, 1, 2, 2, 2, 2, equationTerm).getFirst()
                + 9 * t0g(0, 1, 2, 0, 1, 2, equationTerm).getFirst() + t0g(0, 0, 0, 0, 0, 0, equationTerm).getFirst()
                + t0g(1, 1, 1, 1, 1, 1, equationTerm).getFirst() + t0g(2, 2, 2, 2, 2, 2, equationTerm).getFirst();
    }

    public static double dDenom(FortescueLoadEquationTerm equationTerm, Variable<AcVariableType> derVariable) {
        return 2 * dt0g(0, 0, 0, 2, 2, 2, equationTerm, derVariable).getFirst() + 2 * dt0g(0, 0, 0, 1, 1, 1, equationTerm, derVariable).getFirst()
                + 2 * dt0g(2, 2, 2, 1, 1, 1, equationTerm, derVariable).getFirst() - 6 * dt0g(0, 1, 2, 1, 1, 1, equationTerm, derVariable).getFirst()
                - 6 * dt0g(0, 1, 2, 0, 0, 0, equationTerm, derVariable).getFirst() - 6 * dt0g(0, 1, 2, 2, 2, 2, equationTerm, derVariable).getFirst()
                + 9 * dt0g(0, 1, 2, 0, 1, 2, equationTerm, derVariable).getFirst() + dt0g(0, 0, 0, 0, 0, 0, equationTerm, derVariable).getFirst()
                + dt0g(1, 1, 1, 1, 1, 1, equationTerm, derVariable).getFirst() + dt0g(2, 2, 2, 2, 2, 2, equationTerm, derVariable).getFirst();
    }
}
