package com.powsybl.openloadflow.network.extensions;

import com.powsybl.openloadflow.network.SimplePiModel;

import java.util.Objects;

/**
 * This is an extension to a LfBranch Line to describe the asymmetry of a line to be used for an
 * unbalanced load flow.
 *
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class AsymLine {

    public static final String PROPERTY_ASYMMETRICAL = "Asymmetrical";

    private final SimplePiModel piZeroComponent;
    private final SimplePiModel piPositiveComponent;
    private final SimplePiModel piNegativeComponent;
    private final boolean phaseOpenA;
    private final boolean phaseOpenB;
    private final boolean phaseOpenC;
    private final AsymLineAdmittanceMatrix admittanceMatrix;

    public AsymLine(SimplePiModel piZeroComponent, SimplePiModel piPositiveComponent, SimplePiModel piNegativeComponent,
                    boolean phaseOpenA, boolean phaseOpenB, boolean phaseOpenC) {
        this.piZeroComponent = Objects.requireNonNull(piZeroComponent);
        this.piPositiveComponent = Objects.requireNonNull(piPositiveComponent);
        this.piNegativeComponent = Objects.requireNonNull(piNegativeComponent);
        this.phaseOpenA = phaseOpenA;
        this.phaseOpenB = phaseOpenB;
        this.phaseOpenC = phaseOpenC;
        admittanceMatrix = new AsymLineAdmittanceMatrix(this);
    }

    public AsymLineAdmittanceMatrix getAdmittanceMatrix() {
        return admittanceMatrix;
    }

    public SimplePiModel getPiZeroComponent() {
        return piZeroComponent;
    }

    public SimplePiModel getPiPositiveComponent() {
        return piPositiveComponent;
    }

    public SimplePiModel getPiNegativeComponent() {
        return piNegativeComponent;
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
