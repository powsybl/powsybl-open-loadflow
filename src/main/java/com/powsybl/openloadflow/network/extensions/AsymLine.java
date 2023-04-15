package com.powsybl.openloadflow.network.extensions;

/**
 * This is an extension to a LfBranch Line to describe the asymmetry of a line to be used for an
 * unbalanced load flow.
 *
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class AsymLine {

    public static final String PROPERTY_ASYMMETRICAL = "Asymmetrical";

    private final boolean phaseOpenA;
    private final boolean phaseOpenB;
    private final boolean phaseOpenC;
    private final AsymLinePiValues piValues;
    private final AsymLineAdmittanceMatrix admittanceMatrix;

    public AsymLine(double r1, double x1, double gi1, double bi1, double gj1, double bj1, boolean phaseOpenA,
                    double r2, double x2, double gi2, double bi2, double gj2, double bj2, boolean phaseOpenB,
                    double r3, double x3, double gi3, double bi3, double gj3, double bj3, boolean phaseOpenC) {

        this.phaseOpenA = phaseOpenA;
        this.phaseOpenB = phaseOpenB;
        this.phaseOpenC = phaseOpenC;
        piValues = new AsymLinePiValues(r1, x1, gi1, bi1, gj1, bj1,
                                        r2, x2, gi2, bi2, gj2, bj2,
                                        r3, x3, gi3, bi3, gj3, bj3);
        admittanceMatrix = new AsymLineAdmittanceMatrix(this);
    }

    public AsymLineAdmittanceMatrix getAdmittanceMatrix() {
        return admittanceMatrix;
    }

    public AsymLinePiValues getPiValues() {
        return piValues;
    }

    public boolean isPhaseOpenA() {
        return phaseOpenA;
    }

    public boolean isPhaseOpenB() {
        return phaseOpenB;
    }

    public boolean isPhaseOpenC() {
        return phaseOpenC;
    }
}
