/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.equations.vector;

import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.equations.EquationTermArray;
import com.powsybl.openloadflow.equations.VariableSet;

import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public abstract class AbstractBranchEquationTermArrayEvaluator implements EquationTermArray.Evaluator<AcVariableType> {

    protected final AcBranchVector branchVector;

    protected final VariableSet<AcVariableType> variableSet;

    protected AbstractBranchEquationTermArrayEvaluator(AcBranchVector branchVector, VariableSet<AcVariableType> variableSet) {
        this.branchVector = Objects.requireNonNull(branchVector);
        this.variableSet = Objects.requireNonNull(variableSet);
    }

    @Override
    public boolean isDisabled(int branchNum) {
        return branchVector.disabled[branchNum];
    }

    public double b1(int branchNum) {
        return branchVector.b1[branchNum];
    }

    public double b2(int branchNum) {
        return branchVector.b2[branchNum];
    }

    public double g1(int branchNum) {
        return branchVector.g1[branchNum];
    }

    public double g2(int branchNum) {
        return branchVector.g2[branchNum];
    }

    public double y(int branchNum) {
        return branchVector.y[branchNum];
    }

    public double ksi(int branchNum) {
        return branchVector.ksi[branchNum];
    }
}
