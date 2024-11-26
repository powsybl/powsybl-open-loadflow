/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.solver;

import com.google.auto.service.AutoService;
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
public class NewtonRaphsonFactory implements AcSolverFactory {

    public static final String NAME = "NEWTON_RAPHSON";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public AcSolverParameters createParameters(LoadFlowParameters parameters) {
        OpenLoadFlowParameters parametersExt = OpenLoadFlowParameters.get(parameters);
        return new NewtonRaphsonParameters()
                .setStoppingCriteria(createNewtonRaphsonStoppingCriteria(parametersExt))
                .setMaxIterations(parametersExt.getMaxSolverIterations())
                .setMinRealisticVoltage(parametersExt.getMinRealisticVoltage())
                .setMaxRealisticVoltage(parametersExt.getMaxRealisticVoltage())
                .setStateVectorScalingMode(parametersExt.getStateVectorScalingMode())
                .setLineSearchStateVectorScalingMaxIteration(parametersExt.getLineSearchStateVectorScalingMaxIteration())
                .setLineSearchStateVectorScalingStepFold(parametersExt.getLineSearchStateVectorScalingStepFold())
                .setMaxVoltageChangeStateVectorScalingMaxDv(parametersExt.getMaxVoltageChangeStateVectorScalingMaxDv())
                .setMaxVoltageChangeStateVectorScalingMaxDphi(parametersExt.getMaxVoltageChangeStateVectorScalingMaxDphi())
                .setAlwaysUpdateNetwork(parametersExt.isAlwaysUpdateNetwork());
    }

    @Override
    public AcSolver create(LfNetwork network, AcLoadFlowParameters parameters, EquationSystem<AcVariableType, AcEquationType> equationSystem,
                           JacobianMatrix<AcVariableType, AcEquationType> j, TargetVector<AcVariableType, AcEquationType> targetVector,
                           EquationVector<AcVariableType, AcEquationType> equationVector) {
        return new NewtonRaphson(network, (NewtonRaphsonParameters) parameters.getAcSolverParameters(), equationSystem, j, targetVector, equationVector, parameters.isDetailedReport());
    }

    private static NewtonRaphsonStoppingCriteria createNewtonRaphsonStoppingCriteria(OpenLoadFlowParameters parametersExt) {
        return switch (parametersExt.getNewtonRaphsonStoppingCriteriaType()) {
            case UNIFORM_CRITERIA ->
                    new DefaultNewtonRaphsonStoppingCriteria(parametersExt.getNewtonRaphsonConvEpsPerEq());
            case PER_EQUATION_TYPE_CRITERIA ->
                    new PerEquationTypeStoppingCriteria(parametersExt.getNewtonRaphsonConvEpsPerEq(), parametersExt.getMaxActivePowerMismatch(),
                            parametersExt.getMaxReactivePowerMismatch(), parametersExt.getMaxVoltageMismatch(),
                            parametersExt.getMaxAngleMismatch(), parametersExt.getMaxRatioMismatch(),
                            parametersExt.getMaxSusceptanceMismatch());
        };
    }
}
