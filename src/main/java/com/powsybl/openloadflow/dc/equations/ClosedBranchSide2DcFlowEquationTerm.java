/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.dc.equations;

import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.PiModel;

import java.util.Objects;

import static com.powsybl.openloadflow.network.PiModel.A2;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public final class ClosedBranchSide2DcFlowEquationTerm extends AbstractClosedBranchDcFlowEquationTerm {

    boolean useTransformerRatio;
    DcApproximationType dcApproximationType;
    PiModel piModel;

    private ClosedBranchSide2DcFlowEquationTerm(LfBranch branch, LfBus bus1, LfBus bus2, VariableSet<DcVariableType> variableSet,
                                                boolean deriveA1, boolean useTransformerRatio, DcApproximationType dcApproximationType) {
        super(branch, bus1, bus2, variableSet, deriveA1, useTransformerRatio, dcApproximationType);
        this.useTransformerRatio = useTransformerRatio;
        this.dcApproximationType = dcApproximationType;
        this.piModel = branch.getPiModel();
    }

    public static ClosedBranchSide2DcFlowEquationTerm create(LfBranch branch, LfBus bus1, LfBus bus2, VariableSet<DcVariableType> variableSet,
                                                             boolean deriveA1, boolean useTransformerRatio, DcApproximationType dcApproximationType) {
        Objects.requireNonNull(branch);
        Objects.requireNonNull(bus1);
        Objects.requireNonNull(bus2);
        Objects.requireNonNull(variableSet);
        return new ClosedBranchSide2DcFlowEquationTerm(branch, bus1, bus2, variableSet, deriveA1, useTransformerRatio, dcApproximationType);
    }

    @Override
    protected double calculateSensi(double ph1, double ph2, double a1) {
        double deltaPhase = ph2 - ph1 + A2 - a1;
        return calculatePower(useTransformerRatio, dcApproximationType, piModel) * deltaPhase;
    }

    @Override
    public double der(Variable<DcVariableType> variable) {
        Objects.requireNonNull(variable);
        if (variable.equals(ph1Var)) {
            return -power;
        } else if (variable.equals(ph2Var)) {
            return power;
        } else if (variable.equals(a1Var)) {
            return -power;
        } else {
            throw new IllegalStateException("Unknown variable: " + variable);
        }
    }

    @Override
    public double rhs() {
        if (a1Var != null) {
            return power * A2;
        } else {
            return power * (A2 - a1());
        }
    }

    @Override
    protected String getName() {
        return "dc_p_2";
    }
}
