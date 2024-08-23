/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.dc.equations;

import com.powsybl.loadflow.LoadFlowParameters;

import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class DcEquationSystemCreationParameters {

    public static final DcApproximationType DC_APPROXIMATION_TYPE_DEFAULT_VALUE = DcApproximationType.IGNORE_R;

    private boolean updateFlows = true;

    /**
     * The purpose of this option is to add a constant a1 var to the equation system, to calculate sensitivity regarding
     * phase.
     */
    private boolean forcePhaseControlOffAndAddAngle1Var = false;

    private boolean useTransformerRatio = LoadFlowParameters.DEFAULT_DC_USE_TRANSFORMER_RATIO_DEFAULT;

    private DcApproximationType dcApproximationType = DC_APPROXIMATION_TYPE_DEFAULT_VALUE;

    private double dcPowerFactor = LoadFlowParameters.DEFAULT_DC_POWER_FACTOR;

    public boolean isUpdateFlows() {
        return updateFlows;
    }

    public DcEquationSystemCreationParameters setUpdateFlows(boolean updateFlows) {
        this.updateFlows = updateFlows;
        return this;
    }

    public boolean isForcePhaseControlOffAndAddAngle1Var() {
        return forcePhaseControlOffAndAddAngle1Var;
    }

    public DcEquationSystemCreationParameters setForcePhaseControlOffAndAddAngle1Var(boolean forcePhaseControlOffAndAddAngle1Var) {
        this.forcePhaseControlOffAndAddAngle1Var = forcePhaseControlOffAndAddAngle1Var;
        return this;
    }

    public boolean isUseTransformerRatio() {
        return useTransformerRatio;
    }

    public DcEquationSystemCreationParameters setUseTransformerRatio(boolean useTransformerRatio) {
        this.useTransformerRatio = useTransformerRatio;
        return this;
    }

    public DcApproximationType getDcApproximationType() {
        return dcApproximationType;
    }

    public DcEquationSystemCreationParameters setDcApproximationType(DcApproximationType dcApproximationType) {
        this.dcApproximationType = Objects.requireNonNull(dcApproximationType);
        return this;
    }

    public DcEquationSystemCreationParameters setDcPowerFactor(double dcPowerFactor) {
        this.dcPowerFactor = dcPowerFactor;
        return this;
    }

    public double getDcPowerFactor() {
        return dcPowerFactor;
    }

    @Override
    public String toString() {
        return "DcEquationSystemCreationParameters(" +
                "updateFlows=" + updateFlows +
                ", forcePhaseControlOffAndAddAngle1Var=" + forcePhaseControlOffAndAddAngle1Var +
                ", useTransformerRatio=" + useTransformerRatio +
                ", dcApproximationType=" + dcApproximationType +
                ')';
    }
}
