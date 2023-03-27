package com.powsybl.openloadflow.network.Extensions;

import com.powsybl.openloadflow.util.Fortescue;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
// This is an extension to a LfBranch Line to describe the asymmetry of a line to be used for an unbalanced load flow
public class AsymLine {

    public static final String PROPERTY_ASYMMETRICAL = "Asymmetrical";

    public AsymLine(double r1, double x1, double gi1, double bi1, double gj1, double bj1, boolean isPhaseOpenA,
                    double r2, double x2, double gi2, double bi2, double gj2, double bj2, boolean isPhaseOpenB,
                    double r3, double x3, double gi3, double bi3, double gj3, double bj3, boolean isPhaseOpenC,
                    Fortescue.ComponentType componentType) {

        this.isOpenA = isPhaseOpenA;
        this.isOpenB = isPhaseOpenB;
        this.isOpenC = isPhaseOpenC;
        this.piValues = new AsymLinePiValues(r1, x1, gi1, bi1, gj1, bj1,
                r2, x2, gi2, bi2, gj2, bj2,
                r3, x3, gi3, bi3, gj3, bj3,
                componentType);
        this.admittanceTerms = null;
        this.admittanceMatrix = new AsymLineAdmittanceMatrix(this, componentType);

    }

    public AsymLine(double y11x, double y11y, double y12x, double y12y, double y13x, double y13y,
                    double y21x, double y21y, double y22x, double y22y, double y23x, double y23y,
                    double y31x, double y31y, double y32x, double y32y, double y33x, double y33y,
                    boolean isPhaseOpenA, boolean isPhaseOpenB, boolean isPhaseOpenC,
                    Fortescue.ComponentType componentType) {

        this.isOpenA = isPhaseOpenA;
        this.isOpenB = isPhaseOpenB;
        this.isOpenC = isPhaseOpenC;
        this.piValues = null;
        this.admittanceTerms = new AsymLineAdmittanceTerms(y11x, y11y, y12x, y12y, y13x, y13y,
                y21x, y21y, y22x, y22y, y23x, y23y,
                y31x, y31y, y32x, y32y, y33x, y33y,
                componentType);

        this.admittanceMatrix = new AsymLineAdmittanceMatrix(this, componentType);
    }

    private final boolean isOpenA;
    private final boolean isOpenB;
    private final boolean isOpenC;
    private final AsymLinePiValues piValues; // contains ABC or Zero-Negative components depending of the componentType in input
    private final AsymLineAdmittanceTerms admittanceTerms; // contains ABC or Zero-Positive-Negative components depending of the componentType in input
    private final AsymLineAdmittanceMatrix admittanceMatrix;

    public boolean isDisconnectionAsymmetryDetected() {
        return isOpenA | isOpenB | isOpenC;
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

    public AsymLineAdmittanceTerms getAdmittanceTerms() {
        return admittanceTerms;
    }
}
