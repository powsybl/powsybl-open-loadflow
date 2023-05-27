/**
 * Copyright (c) 2023, Jean-Baptiste Heyberger <jbheyberger at gmail.com> ,
 *                     Geoffroy Jamgotchian <geoffroy.jamgotchian at gmail.com>
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import java.util.Objects;

/**
 * This is an extension to a LfBranch Line to describe the asymmetry of a line to be used for an
 * unbalanced load flow.
 *
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class LfAsymLine {

    private final SimplePiModel piZeroComponent;
    private final SimplePiModel piPositiveComponent;
    private final SimplePiModel piNegativeComponent;
    private final boolean phaseOpenA;
    private final boolean phaseOpenB;
    private final boolean phaseOpenC;
    private final LfAsymLineAdmittanceMatrix admittanceMatrix;

    public LfAsymLine(SimplePiModel piZeroComponent, SimplePiModel piPositiveComponent, SimplePiModel piNegativeComponent,
                      boolean phaseOpenA, boolean phaseOpenB, boolean phaseOpenC) {
        this.piZeroComponent = Objects.requireNonNull(piZeroComponent);
        this.piPositiveComponent = Objects.requireNonNull(piPositiveComponent);
        this.piNegativeComponent = Objects.requireNonNull(piNegativeComponent);
        this.phaseOpenA = phaseOpenA;
        this.phaseOpenB = phaseOpenB;
        this.phaseOpenC = phaseOpenC;
        admittanceMatrix = new LfAsymLineAdmittanceMatrix(this);
    }

    public LfAsymLineAdmittanceMatrix getAdmittanceMatrix() {
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
