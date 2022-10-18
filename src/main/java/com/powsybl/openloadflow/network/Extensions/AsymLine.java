package com.powsybl.openloadflow.network.Extensions;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
// This is an extension to a LfBranch Line to describe the asymmetry of a line to be used for an unbalanced load flow
public class AsymLine {

    public static final String PROPERTY_ASYMMETRICAL = "Asymmetrical";

    public class AsymLinePhase {
        private final double rPhase;
        private final double xPhase;
        private final boolean isPhaseOpen;

        AsymLinePhase(double rPhase, double xPhase, boolean isPhaseOpen) {
            this.rPhase = rPhase;
            this.xPhase = xPhase;
            this.isPhaseOpen = isPhaseOpen;
        }

        public double getrPhase() {
            return rPhase;
        }

        public boolean isPhaseOpen() {
            return isPhaseOpen;
        }

        public double getxPhase() {
            return xPhase;
        }
    }

    public AsymLine(double rPhaseA, double xPhaseA, boolean isPhaseOpenA,
                    double rPhaseB, double xPhaseB, boolean isPhaseOpenB,
                    double rPhaseC, double xPhaseC, boolean isPhaseOpenC) {

        this.linePhaseA = new AsymLinePhase(rPhaseA, xPhaseA, isPhaseOpenA);
        this.linePhaseB = new AsymLinePhase(rPhaseB, xPhaseB, isPhaseOpenB);
        this.linePhaseC = new AsymLinePhase(rPhaseC, xPhaseC, isPhaseOpenC);
        this.admittanceTerms = new AsymLineAdmittanceTerms(this);
    }

    private final AsymLinePhase linePhaseA;
    private final AsymLinePhase linePhaseB;
    private final AsymLinePhase linePhaseC;
    private final AsymLineAdmittanceTerms admittanceTerms;

    public AsymLinePhase getLinePhaseA() {
        return linePhaseA;
    }

    public AsymLinePhase getLinePhaseB() {
        return linePhaseB;
    }

    public AsymLinePhase getLinePhaseC() {
        return linePhaseC;
    }

    public boolean isDisconnectionAsymmetryDetected() {
        return linePhaseA.isPhaseOpen() | linePhaseB.isPhaseOpen() | linePhaseC.isPhaseOpen();
    }

    public AsymLineAdmittanceTerms getAdmittanceTerms() {
        return admittanceTerms;
    }
}
