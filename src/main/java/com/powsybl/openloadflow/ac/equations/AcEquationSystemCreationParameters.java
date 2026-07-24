/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.equations;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class AcEquationSystemCreationParameters {

    private final boolean forceA1Var;

    private boolean alternativeEquations;

    public AcEquationSystemCreationParameters() {
        this(false);
    }

    public AcEquationSystemCreationParameters(boolean forceA1Var) {
        this(forceA1Var, false);
    }

    public AcEquationSystemCreationParameters(boolean forceA1Var, boolean alternativeEquations) {
        this.forceA1Var = forceA1Var;
        this.alternativeEquations = alternativeEquations;
    }

    public boolean isForceA1Var() {
        return forceA1Var;
    }

    /**
     * Use alternative equations for buses with generator voltage control (local or remote), so that PV/PQ
     * switching preserves the Jacobian matrix structure and its symbolic factorization.
     */
    public boolean isAlternativeEquations() {
        return alternativeEquations;
    }

    public AcEquationSystemCreationParameters setAlternativeEquations(boolean alternativeEquations) {
        this.alternativeEquations = alternativeEquations;
        return this;
    }

    private boolean alternativeBusesCanBeDisabled;

    /**
     * When true, a trivial disabled alternative is created on the power balance equations of every bus eligible to
     * alternative equations, so that disabling any of them (contingency islanding and restore) preserves the matrix
     * structure and its symbolic factorization. Security and sensitivity analysis set it (their contingencies can
     * island any bus); a plain load flow never disables buses and leaves it false. It is set for the whole eligible
     * bus set rather than a precomputed islandable subset, so correctness does not depend on the connectivity
     * analysis anticipating every islanding.
     */
    public boolean isAlternativeBusesCanBeDisabled() {
        return alternativeBusesCanBeDisabled;
    }

    public AcEquationSystemCreationParameters setAlternativeBusesCanBeDisabled(boolean alternativeBusesCanBeDisabled) {
        this.alternativeBusesCanBeDisabled = alternativeBusesCanBeDisabled;
        return this;
    }

    @Override
    public String toString() {
        return "AcEquationSystemCreationParameters(" +
                "forceA1Var=" + forceA1Var +
                ", alternativeEquations=" + alternativeEquations +
                ')';
    }
}
