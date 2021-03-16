/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.equations;

import com.powsybl.openloadflow.util.branchesCurrentManager.AllBranchesCurrentManager;
import com.powsybl.openloadflow.util.branchesCurrentManager.BranchesCurrentManager;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class AcEquationSystemCreationParameters {

    private final boolean phaseControl;

    private final boolean transformerVoltageControl;

    private final boolean forceA1Var;

    private final BranchesCurrentManager branchesCurrentManager;

    public AcEquationSystemCreationParameters(boolean phaseControl, boolean transformerVoltageControl) {
        this(phaseControl, transformerVoltageControl, false);
    }

    public AcEquationSystemCreationParameters(boolean phaseControl, boolean transformerVoltageControl, boolean forceA1Var) {
        this(phaseControl, transformerVoltageControl, forceA1Var, new AllBranchesCurrentManager());
    }

    public AcEquationSystemCreationParameters(boolean phaseControl, boolean transformerVoltageControl, boolean forceA1Var, BranchesCurrentManager branchesCurrentManager) {
        this.phaseControl = phaseControl;
        this.transformerVoltageControl = transformerVoltageControl;
        this.forceA1Var = forceA1Var;
        this.branchesCurrentManager = branchesCurrentManager;
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

    public BranchesCurrentManager getBranchesCurrentManager() {
        return branchesCurrentManager;
    }
}
