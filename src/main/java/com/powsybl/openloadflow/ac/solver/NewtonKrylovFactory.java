/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.solver;

import com.google.auto.service.AutoService;
import com.powsybl.iidm.network.Load;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.ac.AcLoadFlowParameters;
import com.powsybl.openloadflow.ac.equations.AcEquationType;
import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.equations.EquationVector;
import com.powsybl.openloadflow.equations.JacobianMatrix;
import com.powsybl.openloadflow.equations.TargetVector;
import com.powsybl.openloadflow.network.LfNetwork;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
@AutoService(AcSolverFactory.class)
public class NewtonKrylovFactory implements AcSolverFactory {

    public static final String NAME = "NEWTON_KRYLOV";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public AcSolverParameters createParameters(OpenLoadFlowParameters parametersExt, LoadFlowParameters parameters) {
        return new NewtonKrylovParameters()
                .setLineSearch(parametersExt.isNewtonKrylovLineSearch())
                .setMaxIterations(parametersExt.getMaxNewtonKrylovIterations());
    }

    @Override
    public AcSolver create(LfNetwork network, AcLoadFlowParameters parameters, EquationSystem<AcVariableType, AcEquationType> equationSystem,
                           JacobianMatrix<AcVariableType, AcEquationType> j, TargetVector<AcVariableType, AcEquationType> targetVector,
                           EquationVector<AcVariableType, AcEquationType> equationVector) {
        return new NewtonKrylov(network, (NewtonKrylovParameters) parameters.getAcSolverParameters(), equationSystem, j, targetVector, equationVector);
    }
}
