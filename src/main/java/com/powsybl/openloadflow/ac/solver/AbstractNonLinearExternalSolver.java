/**
 * Copyright (c) 2024, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.solver;

import com.powsybl.openloadflow.ac.equations.AcEquationType;
import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.equations.EquationVector;
import com.powsybl.openloadflow.equations.JacobianMatrix;
import com.powsybl.openloadflow.equations.TargetVector;
import com.powsybl.openloadflow.network.LfNetwork;

/**
 * @author Pierre Arvy {@literal <pierre.arvy at artelys.com>}
 */
public abstract class AbstractNonLinearExternalSolver extends AbstractAcSolver {

    protected AbstractNonLinearExternalSolver(LfNetwork network, EquationSystem<AcVariableType, AcEquationType> equationSystem, JacobianMatrix<AcVariableType, AcEquationType> j, TargetVector<AcVariableType, AcEquationType> targetVector, EquationVector<AcVariableType, AcEquationType> equationVector, boolean detailedReport) {
        super(network, equationSystem, j, targetVector, equationVector, detailedReport);
    }

//    // Solver
//    abstract void createVariables(int numVar, int numCt);
//
//    abstract void setVariablesType(List<Integer> listVarTypes);
//
//    abstract void setVariablesLowerBound(List<Double> listVarLoBnd);
//
//    abstract void setVariablesUpperBound(List<Double> listVarUpBnd);
//
//    abstract void setInitialState(List<Double> listXInitial);
//
//    abstract void addLinearConstraints(List );
//
//    abstract void addNonLinearConstraints();
//
//    abstract void solveProblem();
//
//    // Utils

}
