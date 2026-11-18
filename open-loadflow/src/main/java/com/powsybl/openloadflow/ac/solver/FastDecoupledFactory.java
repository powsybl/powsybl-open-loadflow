/**
 * Copyright (c) 2025, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.solver;

import com.google.auto.service.AutoService;
import com.powsybl.commons.PowsyblException;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.na.equations.EquationSystem;
import com.powsybl.na.equations.EquationVector;
import com.powsybl.na.equations.JacobianMatrix;
import com.powsybl.na.equations.TargetVector;
import com.powsybl.na.equations.ac.AcEquationType;
import com.powsybl.na.equations.ac.AcVariableType;
import com.powsybl.na.network.LfNetwork;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.ac.AcLoadFlowParameters;

/**
 * @author Hadrien Godard {@literal <hadrien.godard at artelys.com>}
 */
@AutoService(AcSolverFactory.class)
public class FastDecoupledFactory extends NewtonRaphsonFactory {

    public static final String NAME = "FAST_DECOUPLED";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public void checkSolverAndParameterConsistency(LoadFlowParameters parameters, OpenLoadFlowParameters parametersExt) {
        if (parametersExt.isAsymmetrical()) {
            throw new PowsyblException("Fast-Decoupled solver is incompatible with asymmetrical load flow: asymmetrical OpenLoadFLowParameter should be switched to false");
        }
        if (parameters.isHvdcAcEmulation()) {
            throw new PowsyblException("Fast-Decoupled solver is incompatible with AcEmulation: hvdcAcEmulation LoadFlowParameter should be switched to false");
        }
        if (parametersExt.isAcDcNetwork()) {
            throw new PowsyblException("Fast-Decoupled solver is incompatible with AC-DC load flow: acDcNetwork OpenLoadFlowParameter should be switched to false");
        }
    }

    @Override
    public AcSolver create(LfNetwork network, AcLoadFlowParameters parameters, EquationSystem<AcVariableType, AcEquationType> equationSystem,
                           JacobianMatrix<AcVariableType, AcEquationType> j, TargetVector<AcVariableType, AcEquationType> targetVector,
                           EquationVector<AcVariableType, AcEquationType> equationVector) {
        return new FastDecoupled(network, (NewtonRaphsonParameters) parameters.getAcSolverParameters(), equationSystem, j, targetVector, equationVector, parameters.isDetailedReport());
    }
}
