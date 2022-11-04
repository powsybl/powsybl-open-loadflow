/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.outerloop;

import com.powsybl.openloadflow.ac.equations.AcEquationSystem;
import com.powsybl.openloadflow.ac.equations.AcEquationType;
import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.equations.EquationVector;
import com.powsybl.openloadflow.equations.JacobianMatrix;
import com.powsybl.openloadflow.equations.TargetVector;
import com.powsybl.openloadflow.network.LfNetwork;

import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class AcLoadFlowContext implements AutoCloseable {

    private final LfNetwork network;

    private final AcLoadFlowParameters parameters;

    private EquationSystem<AcVariableType, AcEquationType> equationSystem;

    private JacobianMatrix<AcVariableType, AcEquationType> jacobianMatrix;

    private AcTargetVector targetVector;

    private EquationVector<AcVariableType, AcEquationType> equationVector;

    private AcLoadFlowResult result;

    private boolean networkUpdated = true;

    public AcLoadFlowContext(LfNetwork network, AcLoadFlowParameters parameters) {
        this.network = Objects.requireNonNull(network);
        this.parameters = Objects.requireNonNull(parameters);
    }

    public LfNetwork getNetwork() {
        return network;
    }

    public AcLoadFlowParameters getParameters() {
        return parameters;
    }

    public EquationSystem<AcVariableType, AcEquationType> getEquationSystem() {
        if (equationSystem == null) {
            equationSystem = AcEquationSystem.create(network, parameters.getEquationSystemCreationParameters());
        }
        return equationSystem;
    }

    public JacobianMatrix<AcVariableType, AcEquationType> getJacobianMatrix() {
        if (jacobianMatrix == null) {
            jacobianMatrix = new JacobianMatrix<>(getEquationSystem(), parameters.getMatrixFactory());
        }
        return jacobianMatrix;
    }

    public TargetVector<AcVariableType, AcEquationType> getTargetVector() {
        if (targetVector == null) {
            targetVector = new AcTargetVector(network, getEquationSystem());
        }
        return targetVector;
    }

    public EquationVector<AcVariableType, AcEquationType> getEquationVector() {
        if (equationVector == null) {
            equationVector = new EquationVector<>(getEquationSystem());
        }
        return equationVector;
    }

    public AcLoadFlowResult getResult() {
        return result;
    }

    public void setResult(AcLoadFlowResult result) {
        this.result = result;
    }

    public boolean isNetworkUpdated() {
        return networkUpdated;
    }

    public void setNetworkUpdated(boolean networkUpdated) {
        this.networkUpdated = networkUpdated;
    }

    @Override
    public void close() {
        if (jacobianMatrix != null) {
            jacobianMatrix.close();
        }
    }
}
