/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.equations.vector;

import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.equations.Variable;

/**
 * @author Sylvestre Prabakaran {@literal <sylvestre.prabakaran at rte-france.com>}
 */
public interface ClosedBranchEquationTermArrayEvaluator {

    double y(int branchNum);

    double ksi(int branchNum);

    double b1(int branchNum);

    double b2(int branchNum);

    double g1(int branchNum);

    double g2(int branchNum);

    double r1(int branchNum);

    double a1(int branchNum);

    Variable<AcVariableType> getPhi1Var(int branchNum);

    Variable<AcVariableType> getPhi2Var(int branchNum);

    Variable<AcVariableType> getV1Var(int branchNum);

    Variable<AcVariableType> getV2Var(int branchNum);

    Variable<AcVariableType> getA1Var(int branchNum);

    Variable<AcVariableType> getR1Var(int branchNum);
}
