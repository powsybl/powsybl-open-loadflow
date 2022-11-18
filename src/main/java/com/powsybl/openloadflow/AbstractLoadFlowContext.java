/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow;

import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.equations.JacobianMatrix;
import com.powsybl.openloadflow.equations.Quantity;
import com.powsybl.openloadflow.network.LfNetwork;

public abstract class AbstractLoadFlowContext <V extends Enum<V> & Quantity, E extends Enum<E> & Quantity, P extends AbstractLoadFlowParameters> implements AutoCloseable {

    protected final LfNetwork network;

    protected EquationSystem<V, E> equationSystem;

    protected JacobianMatrix<V, E> jacobianMatrix;

    protected P parameters;

    public AbstractLoadFlowContext(LfNetwork network, P param) {
        this.network = network;
        this.parameters = param;
    }

    public JacobianMatrix<V, E> getJacobianMatrix() {
        if (jacobianMatrix == null) {
            jacobianMatrix = new JacobianMatrix<>(getEquationSystem(), parameters.getMatrixFactory());
        }
        return jacobianMatrix;
    }

    public P getParameters() {
        return parameters;
    }

    public LfNetwork getNetwork() {
        return network;
    }

    public abstract EquationSystem<V, E> getEquationSystem();

    @Override
    public void close() {
        if (jacobianMatrix != null) {
            jacobianMatrix.close();
        }
    }
}
