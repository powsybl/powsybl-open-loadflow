/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.equations;

import java.util.Set;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class AcEquationSystemCreationParameters {

    private final boolean phaseControl;

    private final boolean transformerVoltageControl;

    private final boolean forceA1Var;

    private final Set<String> branchesWithCurrent;

    public AcEquationSystemCreationParameters(boolean phaseControl, boolean transformerVoltageControl) {
        this(phaseControl, transformerVoltageControl, false);
    }

    public AcEquationSystemCreationParameters(boolean phaseControl, boolean transformerVoltageControl, boolean forceA1Var) {
        this(phaseControl, transformerVoltageControl, forceA1Var, null);
    }

    public AcEquationSystemCreationParameters(boolean phaseControl, boolean transformerVoltageControl, boolean forceA1Var, Set<String> branchesWithCurrent) {
        this.phaseControl = phaseControl;
        this.transformerVoltageControl = transformerVoltageControl;
        this.forceA1Var = forceA1Var;
        this.branchesWithCurrent = branchesWithCurrent;
    }

    public boolean isPhaseControl() {
        return phaseControl;
    }

    public boolean isTransformerVoltageControl() {
        return transformerVoltageControl;
    }

    public boolean isForceA1Var() {
        return forceA1Var;
    }

    public Set<String> getBranchesWithCurrent() {
        return branchesWithCurrent;
    }

}
