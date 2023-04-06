package com.powsybl.openloadflow.network.Extensions;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
// This is an extension to a LfBranch Line to describe the asymmetry of a line to be used for an unbalanced load flow
public class AsymLine {

    public static final String PROPERTY_ASYMMETRICAL = "Asymmetrical";

    public AsymLine(double r1, double x1, double gi1, double bi1, double gj1, double bj1, boolean isPhaseOpenA,
                    double r2, double x2, double gi2, double bi2, double gj2, double bj2, boolean isPhaseOpenB,
                    double r3, double x3, double gi3, double bi3, double gj3, double bj3, boolean isPhaseOpenC) {

        this.isOpenA = isPhaseOpenA;
        this.isOpenB = isPhaseOpenB;
        this.isOpenC = isPhaseOpenC;
        this.piValues = new AsymLinePiValues(r1, x1, gi1, bi1, gj1, bj1,
                r2, x2, gi2, bi2, gj2, bj2,
                r3, x3, gi3, bi3, gj3, bj3);
        this.admittanceMatrix = new AsymLineAdmittanceMatrix(this);

    }

    private final boolean isOpenA;
    private final boolean isOpenB;
    private final boolean isOpenC;
    private final AsymLinePiValues piValues;
    private final AsymLineAdmittanceMatrix admittanceMatrix;

    public boolean isDisconnectionAsymmetryDetected() {
        return isOpenA || isOpenB || isOpenC;
    }

    public boolean isAdmittanceAsymmetryDetected() {
        boolean isAsymmetry = false;
        if (admittanceMatrix != null) {
            isAsymmetry = AsymLineAdmittanceMatrix.isAdmittanceCoupled(admittanceMatrix.getmY012());
        }

        return isAsymmetry;
    }

    public AsymLineAdmittanceMatrix getAdmittanceMatrix() {
        return admittanceMatrix;
    }

    public AsymLinePiValues getPiValues() {
        return piValues;
    }

    public boolean isOpenA() {
        return isOpenA;
    }

    public boolean isOpenB() {
        return isOpenB;
    }

    public boolean isOpenC() {
        return isOpenC;
    }
}
