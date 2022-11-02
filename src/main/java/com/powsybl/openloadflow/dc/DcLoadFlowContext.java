/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.dc;

import com.powsybl.openloadflow.dc.equations.DcEquationType;
import com.powsybl.openloadflow.dc.equations.DcVariableType;
import com.powsybl.openloadflow.dc.DcTargetVector;
import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.equations.EquationVector;
import com.powsybl.openloadflow.equations.JacobianMatrix;
import com.powsybl.openloadflow.network.LfNetwork;

import java.util.Objects;

/**
 * @author Jean-Luc Bouchot (Artelys) <jlbouchot at gmail.com>
 */
public class DcLoadFlowContext {

    private final LfNetwork network;

    private final DcLoadFlowParameters parameters;

    private EquationSystem<DcVariableType, DcEquationType> equationSystem;

    private JacobianMatrix<DcVariableType, DcEquationType> jacobianMatrix;

    private DcTargetVector targetVector;

    private EquationVector<DcVariableType, DcEquationType> equationVector;
}
