/**
 * Copyright (c) 2026, Elia Group (https://www.eliagroup.eu)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.se.solver;

import com.powsybl.openloadflow.ac.equations.AcEquationType;
import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.equations.JacobianMatrix;

/**
 * @author Šime Pavlić {@literal <sime.pavlic at kickstage.com>}
 */
public interface WlsSolverFactory {

    String getName();

    WlsSolver create(WlsProblem problem, JacobianMatrix<AcVariableType, AcEquationType> jacobian, WlsParameters parameters);
}
