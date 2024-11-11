/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.solver;

import com.powsybl.commons.PowsyblException;
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
import org.apache.commons.compress.utils.Lists;

import java.util.List;
import java.util.Objects;
import java.util.ServiceLoader;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public interface AcSolverFactory {

    static List<AcSolverFactory> findAll() {
        return Lists.newArrayList(ServiceLoader.load(AcSolverFactory.class, AcSolverFactory.class.getClassLoader()).iterator());
    }

    static AcSolverFactory find(String name) {
        Objects.requireNonNull(name);
        return findAll().stream().filter(asf -> name.equals(asf.getName()))
                .findFirst().orElseThrow(() -> new PowsyblException("AC Solver '" + name + "' not found"));
    }

    String getName();

    AcSolverParameters createParameters(OpenLoadFlowParameters parametersExt, LoadFlowParameters parameters);

    AcSolver create(LfNetwork network,
                    AcLoadFlowParameters parameters,
                    EquationSystem<AcVariableType, AcEquationType> equationSystem,
                    JacobianMatrix<AcVariableType, AcEquationType> j,
                    TargetVector<AcVariableType, AcEquationType> targetVector,
                    EquationVector<AcVariableType, AcEquationType> equationVector);
}
