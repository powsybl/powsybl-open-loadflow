/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.solver;

import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.util.ServiceLoaderCache;
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

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public interface AcSolverFactory {

    ServiceLoaderCache<AcSolverFactory> SERVICE_LOADER_CACHE = new ServiceLoaderCache<>(AcSolverFactory.class);

    static List<AcSolverFactory> findAll() {
        return Collections.unmodifiableList(SERVICE_LOADER_CACHE.getServices());
    }

    static AcSolverFactory find(String name) {
        Objects.requireNonNull(name);
        return findAll().stream().filter(asf -> name.equals(asf.getName()))
                .findFirst().orElseThrow(() -> new PowsyblException("AC Solver '" + name + "' not found"));
    }

    void checkSolverAndParameterConsistency(LoadFlowParameters parameters, OpenLoadFlowParameters parametersExt);

    String getName();

    AcSolverParameters createParameters(LoadFlowParameters parameters);

    AcSolver create(LfNetwork network,
                    AcLoadFlowParameters parameters,
                    EquationSystem<AcVariableType, AcEquationType> equationSystem,
                    JacobianMatrix<AcVariableType, AcEquationType> j,
                    TargetVector<AcVariableType, AcEquationType> targetVector,
                    EquationVector<AcVariableType, AcEquationType> equationVector);
}
