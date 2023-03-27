package com.powsybl.openloadflow.network.Extensions.iidm;

public class LineAsymmetricalPiValues {

    private final PiValuesPhase piPhase1;
    private final PiValuesPhase piPhase2;
    private final PiValuesPhase piPhase3;

    public LineAsymmetricalPiValues(double r1, double x1, double gi1, double bi1, double gj1, double bj1,
                                    double r2, double x2, double gi2, double bi2, double gj2, double bj2,
                                    double r3, double x3, double gi3, double bi3, double gj3, double bj3) {
        this.piPhase1 = new PiValuesPhase(r1, x1, gi1, bi1, gj1, bj1);
        this.piPhase2 = new PiValuesPhase(r2, x2, gi2, bi2, gj2, bj2);
        this.piPhase3 = new PiValuesPhase(r3, x3, gi3, bi3, gj3, bj3);
    }

    public LineAsymmetricalPiValues(double rZero, double xZero, double giZero, double biZero, double gjZero, double bjZero,
                             double rNegative, double xNegative, double giNegative, double biNegative, double gjNegative, double bjNegative) {
        this.piPhase1 = new PiValuesPhase(rZero, xZero, giZero, biZero, gjZero, bjZero);
        this.piPhase2 = null;
        this.piPhase3 = new PiValuesPhase(rNegative, xNegative, giNegative, biNegative, gjNegative, bjNegative);
    }

    public class PiValuesPhase {
        private final double r;
        private final double x;
        private final double bi;
        private final double gi;
        private final double bj;
        private final double gj;

        PiValuesPhase(double r, double x, double gi, double bi, double gj, double bj) {
            this.r = r;
            this.x = x;
            this.gi = gi;
            this.bi = bi;
            this.gj = gj;
            this.bj = bj;
        }

        public double getBi() {
            return bi;
        }

        public double getBj() {
            return bj;
        }

        public double getGi() {
            return gi;
        }

        public double getGj() {
            return gj;
        }

        public double getR() {
            return r;
        }

        public double getX() {
            return x;
        }
    }

    public PiValuesPhase getPiPhase1() {
        return piPhase1;
    }

    public PiValuesPhase getPiPhase2() {
        return piPhase2;
    }

    public PiValuesPhase getPiPhase3() {
        return piPhase3;
    }
}
