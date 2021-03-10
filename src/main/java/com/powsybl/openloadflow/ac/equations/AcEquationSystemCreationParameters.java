/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.equations;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class AcEquationSystemCreationParameters {

    private final boolean phaseControl;

    private final boolean transformerVoltageControl;

    private final boolean shuntVoltageControl;

    private final boolean forceA1Var;

    public AcEquationSystemCreationParameters(boolean phaseControl, boolean transformerVoltageControl, boolean shuntVoltageControl) {
        this(phaseControl, transformerVoltageControl, shuntVoltageControl, false);
    }

    public AcEquationSystemCreationParameters(boolean phaseControl, boolean transformerVoltageControl,
                                              boolean shuntVoltageControl, boolean forceA1Var) {
        this.phaseControl = phaseControl;
        this.transformerVoltageControl = transformerVoltageControl;
        this.shuntVoltageControl = shuntVoltageControl;
        this.forceA1Var = forceA1Var;
    }

    public boolean isPhaseControl() {
        return phaseControl;
    }

    public boolean isTransformerVoltageControl() {
        return transformerVoltageControl;
    }

    public boolean isShuntVoltageControl() {
        return shuntVoltageControl;
    }

    public boolean isForceA1Var() {
        return forceA1Var;
    }
}
