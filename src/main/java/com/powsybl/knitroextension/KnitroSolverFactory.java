/**
 * Copyright (c) 2024, Artelys (https://www.artelys.com/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.knitroextension;

import com.google.auto.service.AutoService;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.ac.AcLoadFlowParameters;
import com.powsybl.openloadflow.ac.equations.AcEquationType;
import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.ac.solver.*;
import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.equations.EquationVector;
import com.powsybl.openloadflow.equations.JacobianMatrix;
import com.powsybl.openloadflow.equations.TargetVector;
import com.powsybl.openloadflow.network.LfNetwork;
//import com.powsybl.knitroextension.ExternalSolverExtensionParameters;

/**
 * @author Pierre Arvy {@literal <pierre.arvy at artelys.com>}
 */
@AutoService(AcSolverFactory.class)
public class KnitroSolverFactory implements AcSolverFactory {

    public static final String NAME = "KNITRO";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public AcSolverParameters createParameters(OpenLoadFlowParameters parametersExt, LoadFlowParameters parameters) {
        KnitroSolverParameters knitroSolverParameters = new KnitroSolverParameters()
                .setStoppingCriteria(createKnitroStoppingCriteria(parametersExt))
                .setMaxIterations(parametersExt.getMaxSolverIterations())
                .setStateVectorScalingMode(parametersExt.getStateVectorScalingMode())
                .setLineSearchStateVectorScalingMaxIteration(parametersExt.getLineSearchStateVectorScalingMaxIteration())
                .setLineSearchStateVectorScalingStepFold(parametersExt.getLineSearchStateVectorScalingStepFold())
                .setMaxVoltageChangeStateVectorScalingMaxDv(parametersExt.getMaxVoltageChangeStateVectorScalingMaxDv())
                .setMaxVoltageChangeStateVectorScalingMaxDphi(parametersExt.getMaxVoltageChangeStateVectorScalingMaxDphi())
                .setAlwaysUpdateNetwork(parametersExt.isAlwaysUpdateNetwork());
        if (parameters.getExtension(ExternalSolverExtensionParameters.class)!=null) {
            knitroSolverParameters
                .setGradientComputationMode(parameters.getExtension(ExternalSolverExtensionParameters.class).getGradientComputationMode())
                .setGradientUserRoutine(parameters.getExtension(ExternalSolverExtensionParameters.class).getGradientUserRoutine())
                .setLowerVoltageBound(parameters.getExtension(ExternalSolverExtensionParameters.class).getLowerVoltageBound())
                .setLowerVoltageBound(parameters.getExtension(ExternalSolverExtensionParameters.class).getLowerVoltageBound());
        }
        return knitroSolverParameters;
    }

    @Override
    public AcSolver create(LfNetwork network, AcLoadFlowParameters parameters, EquationSystem<AcVariableType, AcEquationType> equationSystem,
                           JacobianMatrix<AcVariableType, AcEquationType> j, TargetVector<AcVariableType, AcEquationType> targetVector,
                           EquationVector<AcVariableType, AcEquationType> equationVector) {
        return new KnitroSolver(network, (KnitroSolverParameters) parameters.getAcSolverParameters(), equationSystem,
                j, targetVector, equationVector, parameters.isDetailedReport());
    }

    private static KnitroSolverStoppingCriteria createKnitroStoppingCriteria(OpenLoadFlowParameters parametersExt) {
        return new DefaultKnitroSolverStoppingCriteria(Math.pow(10, -6)); //TODO
    }
}
