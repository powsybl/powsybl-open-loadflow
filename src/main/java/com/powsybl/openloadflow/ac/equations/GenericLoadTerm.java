package com.powsybl.openloadflow.ac.equations;

import com.powsybl.openloadflow.equations.Variable;
import org.apache.commons.math3.util.Pair;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public final class GenericLoadTerm {

    // We define :
    // T0g(l,m,n,f,g,h) = Vf*Vg*Vh * Vl*Vm*Vn * ( cos(a_l+a_m+a_n-a_f-a_g-a_h) + j * sin(a_l+a_m+a_n-a_f-a_g-a_h) )
    //
    // We define :
    // T1g(f,g,h,p,q) = Vf*exp(j*a_f) * Vg*exp(j*a_g) * Vh*exp(j*a_h) * [Vo^3*exp(-3*j*a_o) + Vi^3*exp(-3*j*a_i) + Vd^3*exp(-3*j*a_d) - 3*Vd*Vo*Vi*exp(-j*(a_o+a_d+a_i)] * (p+j*q)
    // which gives:
    // T1g(f,g,h,p,q) = Tx1g(f,g,h,p,q) + j * Ty1g(f,g,h,p,q)
    // with
    // Tx1g(f,g,h,p,q) =   p * [Tx0g(f,g,h,o,o,o) - 3 * Tx0g(f,g,h,o,d,i) + Tx0g(f,g,h,i,i,i) + Tx0g(f,g,h,d,d,d)]
    //                   + q * [Ty0g(f,g,h,o,o,o) - 3 * Ty0g(f,g,h,o,d,i) + Ty0g(f,g,h,i,i,i) - Ty0g(f,g,h,d,d,d)] )
    // and
    // Ty1g(f,g,h,p,q) =   p * [-Ty0g(f,g,h,o,o,o) + 3 * Ty0g(f,g,h,o,d,i) - Ty0g(f,g,h,i,i,i) - Ty0g(f,g,h,d,d,d)]
    //                   + q * [ Tx0g(f,g,h,o,o,o) - 3 * Tx0g(f,g,h,o,d,i) + Tx0g(f,g,h,i,i,i) + Ty0g(f,g,h,d,d,d)] )

    private GenericLoadTerm() {

    }

    public static Pair<Double, Double> t0Load(int l, int m, int n, int f, int g, int h, FortescueLoadEquationTerm equationTerm) {
        // T0Load is a generic basic term used to compute the value of a load and its derivative in fortescue sequence.
        // The simple expression of a load in A, B, C phasor could be very complex in fortescue sequences, this is why we use a decomposition using generic terms
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

    public static double getMagnitudeDerivative(int l, int m, int n, int f, int g, int h, int inputSequence, FortescueLoadEquationTerm equationTerm) {
        double vl = equationTerm.v(l);
        double vm = equationTerm.v(m);
        double vn = equationTerm.v(n);
        double vf = equationTerm.v(f);
        double vg = equationTerm.v(g);
        double vh = equationTerm.v(h);
        if (l == inputSequence) {
            return vm * vn * vf * vg * vh;
        } else if (m == inputSequence) {
            return vl * vn * vf * vg * vh;
        } else if (n == inputSequence) {
            return vl * vm * vf * vg * vh;
        } else if (f == inputSequence) {
            return vl * vm * vn * vg * vh;
        } else if (g == inputSequence) {
            return vl * vm * vn * vf * vh;
        } else if (h == inputSequence) {
            return vl * vm * vn * vf * vg;
        } else {
            throw new IllegalStateException(" Derivation magnitude not found in the expression ");
        }

    }

    public static Pair<Double, Double> dt0Load(int l, int m, int n, int f, int g, int h, FortescueLoadEquationTerm equationTerm, Variable<AcVariableType> derVariable) {

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
                    // TODO check if now expression is better :
                    // derMagnitudes = power * vf * vg * vh * vl * vm * vn / varDerivativeValue;
                    derMagnitudes = power * getMagnitudeDerivative(l, m, n, f, g, h, derSequence, equationTerm);
                }
            } else if (power < 0) {
                throw new IllegalStateException("Power cannot be negative ");
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

    // Tx1g(f,g,h,p,q) =   p * [Tx0g(f,g,h,o,o,o) - 3 * Tx0g(f,g,h,o,d,i) + Tx0g(f,g,h,i,i,i) + Tx0g(f,g,h,d,d,d)]
    //                   + q * [Ty0g(f,g,h,o,o,o) - 3 * Ty0g(f,g,h,o,d,i) + Ty0g(f,g,h,i,i,i) + Ty0g(f,g,h,d,d,d)] )
    // and
    // Ty1g(f,g,h,p,q) =   p * [-Ty0g(f,g,h,o,o,o) + 3 * Ty0g(f,g,h,o,d,i) - Ty0g(f,g,h,i,i,i) - Ty0g(f,g,h,d,d,d)]
    //                   + q * [ Tx0g(f,g,h,o,o,o) - 3 * Tx0g(f,g,h,o,d,i) + Tx0g(f,g,h,i,i,i) + Tx0g(f,g,h,d,d,d)] )

    public static Pair<Double, Double> t1Load(int f, int g, int h, double p, double q, FortescueLoadEquationTerm equationTerm) {

        // T1Load is used as a bascic term to build balanced and unbalanced loads, taking into account the specific shape of the expression
        // of an A,B,C load in fortescue sequences

        /*Pair<Double, Double> t0Gooofgh = t0Load(0, 0, 0, f, g, h, equationTerm);
        Pair<Double, Double> t0Godifgh = t0Load(0, 1, 2, f, g, h, equationTerm);
        Pair<Double, Double> t0Giiifgh = t0Load(2, 2, 2, f, g, h, equationTerm);
        Pair<Double, Double> t0Gdddfgh = t0Load(1, 1, 1, f, g, h, equationTerm);*/
        // TODO test correction
        Pair<Double, Double> t0Gooofgh = t0Load(f, g, h, 0, 0, 0, equationTerm);
        Pair<Double, Double> t0Godifgh = t0Load(f, g, h, 0, 1, 2, equationTerm);
        Pair<Double, Double> t0Giiifgh = t0Load(f, g, h, 2, 2, 2, equationTerm);
        Pair<Double, Double> t0Gdddfgh = t0Load(f, g, h, 1, 1, 1, equationTerm);

        double tx1g = p * (t0Gooofgh.getFirst() - 3 * t0Godifgh.getFirst() + t0Giiifgh.getFirst() + t0Gdddfgh.getFirst())
                - q * (t0Gooofgh.getSecond() - 3 * t0Godifgh.getSecond() + t0Giiifgh.getSecond() + t0Gdddfgh.getSecond());

        double ty1g = -p * (-t0Gooofgh.getSecond() + 3 * t0Godifgh.getSecond() - t0Giiifgh.getSecond() - t0Gdddfgh.getSecond())
                + q * (t0Gooofgh.getFirst() - 3 * t0Godifgh.getFirst() + t0Giiifgh.getFirst() + t0Gdddfgh.getFirst());

        return new Pair<>(tx1g, ty1g);
    }

    public static Pair<Double, Double> dt1Load(int f, int g, int h, double p, double q, FortescueLoadEquationTerm equationTerm, Variable<AcVariableType> derVariable) {

        // For now we suppose that p and q are constants not depending on voltage
        Pair<Double, Double> dt0Gooofgh = dt0Load(f, g, h, 0, 0, 0, equationTerm, derVariable);
        Pair<Double, Double> dt0Godifgh = dt0Load(f, g, h, 0, 1, 2, equationTerm, derVariable);
        Pair<Double, Double> dt0Giiifgh = dt0Load(f, g, h, 2, 2, 2, equationTerm, derVariable);
        Pair<Double, Double> dt0Gdddfgh = dt0Load(f, g, h, 1, 1, 1, equationTerm, derVariable);

        double dtx1g = p * (dt0Gooofgh.getFirst() - 3 * dt0Godifgh.getFirst() + dt0Giiifgh.getFirst() + dt0Gdddfgh.getFirst())
                - q * (dt0Gooofgh.getSecond() - 3 * dt0Godifgh.getSecond() + dt0Giiifgh.getSecond() + dt0Gdddfgh.getSecond());

        double dty1g = -p * (-dt0Gooofgh.getSecond() + 3 * dt0Godifgh.getSecond() - dt0Giiifgh.getSecond() - dt0Gdddfgh.getSecond())
                + q * (dt0Gooofgh.getFirst() - 3 * dt0Godifgh.getFirst() + dt0Giiifgh.getFirst() + dt0Gdddfgh.getFirst());

        return new Pair<>(dtx1g, dty1g);
    }

    public static double denom(FortescueLoadEquationTerm equationTerm) {
        return 2 * t0Load(0, 0, 0, 2, 2, 2, equationTerm).getFirst() + 2 * t0Load(0, 0, 0, 1, 1, 1, equationTerm).getFirst()
                + 2 * t0Load(2, 2, 2, 1, 1, 1, equationTerm).getFirst() - 6 * t0Load(0, 1, 2, 1, 1, 1, equationTerm).getFirst()
                - 6 * t0Load(0, 1, 2, 0, 0, 0, equationTerm).getFirst() - 6 * t0Load(0, 1, 2, 2, 2, 2, equationTerm).getFirst()
                + 9 * t0Load(0, 1, 2, 0, 1, 2, equationTerm).getFirst() + t0Load(0, 0, 0, 0, 0, 0, equationTerm).getFirst()
                + t0Load(1, 1, 1, 1, 1, 1, equationTerm).getFirst() + t0Load(2, 2, 2, 2, 2, 2, equationTerm).getFirst();
    }

    public static double dDenom(FortescueLoadEquationTerm equationTerm, Variable<AcVariableType> derVariable) {
        return 2 * dt0Load(0, 0, 0, 2, 2, 2, equationTerm, derVariable).getFirst() + 2 * dt0Load(0, 0, 0, 1, 1, 1, equationTerm, derVariable).getFirst()
                + 2 * dt0Load(2, 2, 2, 1, 1, 1, equationTerm, derVariable).getFirst() - 6 * dt0Load(0, 1, 2, 1, 1, 1, equationTerm, derVariable).getFirst()
                - 6 * dt0Load(0, 1, 2, 0, 0, 0, equationTerm, derVariable).getFirst() - 6 * dt0Load(0, 1, 2, 2, 2, 2, equationTerm, derVariable).getFirst()
                + 9 * dt0Load(0, 1, 2, 0, 1, 2, equationTerm, derVariable).getFirst() + dt0Load(0, 0, 0, 0, 0, 0, equationTerm, derVariable).getFirst()
                + dt0Load(1, 1, 1, 1, 1, 1, equationTerm, derVariable).getFirst() + dt0Load(2, 2, 2, 2, 2, 2, equationTerm, derVariable).getFirst();
    }
}
