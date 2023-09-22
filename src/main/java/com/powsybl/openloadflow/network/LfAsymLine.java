/**
 * Copyright (c) 2023, Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 * Copyright (c) 2023, Geoffroy Jamgotchian <geoffroy.jamgotchian at gmail.com>
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network;

import com.powsybl.openloadflow.network.extensions.AsymBusVariableType;
import com.powsybl.openloadflow.util.ComplexMatrix;

import java.util.Objects;

/**
 * This is an extension to a LfBranch Line to describe the asymmetry of a line to be used for an
 * unbalanced load flow.
 *
 * @author Jean-Baptiste Heyberger {@literal <jbheyberger at gmail.com>}
 */
public class LfAsymLine {

    private final ComplexMatrix yabc;
    private final SimplePiModel piZeroComponent;
    private final SimplePiModel piPositiveComponent;
    private final SimplePiModel piNegativeComponent;

    private final boolean phaseOpenA; // phases exist but might be open
    private final boolean phaseOpenB;
    private final boolean phaseOpenC;

    private final boolean hasPhaseA1;
    private final boolean hasPhaseB1;
    private final boolean hasPhaseC1;
    private final boolean hasPhaseA2;
    private final boolean hasPhaseB2;
    private final boolean hasPhaseC2;

    private final LfAsymLineAdmittanceMatrix admittanceMatrix;
    private final boolean isSide1FortescueRepresentation;
    private final boolean isSide2FortescueRepresentation;
    private final AsymBusVariableType side1VariableType;
    private final AsymBusVariableType side2VariableType;

    // Fortescue description of the equipment through the 3 sequences PI models
    public LfAsymLine(SimplePiModel piZeroComponent, SimplePiModel piPositiveComponent, SimplePiModel piNegativeComponent,
                      boolean phaseOpenA, boolean phaseOpenB, boolean phaseOpenC, AsymBusVariableType side1VariableType, AsymBusVariableType side2VariableType) {
        this.piZeroComponent = Objects.requireNonNull(piZeroComponent);
        this.piPositiveComponent = Objects.requireNonNull(piPositiveComponent);
        this.piNegativeComponent = Objects.requireNonNull(piNegativeComponent);
        this.yabc = null;
        this.phaseOpenA = phaseOpenA;
        this.phaseOpenB = phaseOpenB;
        this.phaseOpenC = phaseOpenC;
        this.hasPhaseA1 = true;
        this.hasPhaseB1 = true;
        this.hasPhaseC1 = true;
        this.hasPhaseA2 = true;
        this.hasPhaseB2 = true;
        this.hasPhaseC2 = true;
        admittanceMatrix = new LfAsymLineAdmittanceMatrix(this);
        this.isSide1FortescueRepresentation = true;
        this.isSide2FortescueRepresentation = true;
        this.side1VariableType = side1VariableType;
        this.side2VariableType = side2VariableType;
    }

    public LfAsymLine(ComplexMatrix yabc, boolean phaseOpenA, boolean phaseOpenB, boolean phaseOpenC,
                      boolean isSide1FortescueRepresentation, boolean isSide2FortescueRepresentation,
                      boolean hasPhaseA1, boolean hasPhaseB1, boolean hasPhaseC1, boolean hasPhaseA2, boolean hasPhaseB2, boolean hasPhaseC2,
                      AsymBusVariableType side1VariableType, AsymBusVariableType side2VariableType) {
        this.piZeroComponent = null;
        this.piPositiveComponent = null;
        this.piNegativeComponent = null;
        this.yabc = yabc;
        this.phaseOpenA = phaseOpenA;
        this.phaseOpenB = phaseOpenB;
        this.phaseOpenC = phaseOpenC;
        this.hasPhaseA1 = hasPhaseA1;
        this.hasPhaseB1 = hasPhaseB1;
        this.hasPhaseC1 = hasPhaseC1;
        this.hasPhaseA2 = hasPhaseA2;
        this.hasPhaseB2 = hasPhaseB2;
        this.hasPhaseC2 = hasPhaseC2;
        this.isSide1FortescueRepresentation = isSide1FortescueRepresentation;
        this.isSide2FortescueRepresentation = isSide2FortescueRepresentation;
        this.side1VariableType = side1VariableType;
        this.side2VariableType = side2VariableType;
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

    public ComplexMatrix getYabc() {
        return yabc;
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

    public boolean isSide1FortescueRepresentation() {
        return isSide1FortescueRepresentation;
    }

    public boolean isSide2FortescueRepresentation() {
        return isSide2FortescueRepresentation;
    }

    public boolean isHasPhaseA1() {
        return hasPhaseA1;
    }

    public boolean isHasPhaseB1() {
        return hasPhaseB1;
    }

    public boolean isHasPhaseC1() {
        return hasPhaseC1;
    }

    public boolean isHasPhaseA2() {
        return hasPhaseA2;
    }

    public boolean isHasPhaseB2() {
        return hasPhaseB2;
    }

    public boolean isHasPhaseC2() {
        return hasPhaseC2;
    }

    public AsymBusVariableType getSide1VariableType() {
        return side1VariableType;
    }

    public AsymBusVariableType getSide2VariableType() {
        return side2VariableType;
    }
}
