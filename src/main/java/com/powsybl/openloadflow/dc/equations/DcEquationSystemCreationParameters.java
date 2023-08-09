/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.dc.equations;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class DcEquationSystemCreationParameters {

    private final boolean updateFlows;

    /**
     * The purpose of this option is to add a constant a1 var to the equation system, to calculate sensitivity regarding
     * phase.
     */
    private final boolean forcePhaseControlOffAndAddAngle1Var;

    private final boolean useTransformerRatio;

    private final boolean phaseShifterRegulationOn;

    public DcEquationSystemCreationParameters(boolean updateFlows, boolean forcePhaseControlOffAndAddAngle1Var,
                                              boolean useTransformerRatio, boolean phaseShifterRegulationOn) {
        this.updateFlows = updateFlows;
        this.forcePhaseControlOffAndAddAngle1Var = forcePhaseControlOffAndAddAngle1Var;
        this.useTransformerRatio = useTransformerRatio;
        this.phaseShifterRegulationOn = phaseShifterRegulationOn;
    }

    public boolean isUpdateFlows() {
        return updateFlows;
    }

    public boolean isForcePhaseControlOffAndAddAngle1Var() {
        return forcePhaseControlOffAndAddAngle1Var;
    }

    public boolean isUseTransformerRatio() {
        return useTransformerRatio;
    }

    public boolean isPhaseShifterRegulationOn() {
        return phaseShifterRegulationOn;
    }

    @Override
    public String toString() {
        return "DcEquationSystemCreationParameters(" +
                "updateFlows=" + updateFlows +
                ", forcePhaseControlOffAndAddAngle1Var=" + forcePhaseControlOffAndAddAngle1Var +
                ", useTransformerRatio=" + useTransformerRatio +
                ')';
    }
}
