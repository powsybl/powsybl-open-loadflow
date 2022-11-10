/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.dc;

import com.powsybl.openloadflow.dc.equations.DcEquationSystem;
import com.powsybl.openloadflow.dc.equations.DcEquationType;
import com.powsybl.openloadflow.dc.equations.DcVariableType;
import com.powsybl.openloadflow.equations.EquationSystem;
// import com.powsybl.openloadflow.equations.EquationVector;
import com.powsybl.openloadflow.equations.JacobianMatrix;
// import com.powsybl.openloadflow.equations.TargetVector;
import com.powsybl.openloadflow.network.LfNetwork;

import java.util.Objects;

/**
 * @author Jean-Luc Bouchot (Artelys) <jlbouchot at gmail.com>
 */
public class DcLoadFlowContext implements AutoCloseable {

    private final LfNetwork network;

    private final DcLoadFlowParameters parameters;

    private EquationSystem<DcVariableType, DcEquationType> equationSystem;

    private JacobianMatrix<DcVariableType, DcEquationType> jacobianMatrix;

    // private DcTargetVector targetVector;

    // private EquationVector<DcVariableType, DcEquationType> equationVector;

    public DcLoadFlowContext(LfNetwork network, DcLoadFlowParameters parameters) {
        this.network = Objects.requireNonNull(network);
        this.parameters = Objects.requireNonNull(parameters);
    }

    public LfNetwork getNetwork() {
        return network;
    }

    public DcLoadFlowParameters getParameters() {
        return parameters;
    }

    public EquationSystem<DcVariableType, DcEquationType> getEquationSystem() {
        if (equationSystem == null) {
            equationSystem = DcEquationSystem.create(network, parameters.getEquationSystemCreationParameters());
        }
        return equationSystem;
    }

    public JacobianMatrix<DcVariableType, DcEquationType> getJacobianMatrix() {
        if (jacobianMatrix == null) {
            jacobianMatrix = new JacobianMatrix<>(getEquationSystem(), parameters.getMatrixFactory());
        }
        return jacobianMatrix;
    }

    /*
    public TargetVector<DcVariableType, DcEquationType> getTargetVector() {
        if (targetVector == null) {
            targetVector = new DcTargetVector(network, getEquationSystem());
        }
        return targetVector;
    }
    */

    /*
    public EquationVector<DcVariableType, DcEquationType> getEquationVector() {
        if (equationVector == null) {
            equationVector = new EquationVector<>(getEquationSystem());
        }
        return equationVector;
    }
    */

    @Override
    public void close() {
        if (jacobianMatrix != null) {
            jacobianMatrix.close();
        }
    }
}
