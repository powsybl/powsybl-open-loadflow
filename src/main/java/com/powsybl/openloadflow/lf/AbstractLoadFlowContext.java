/**
 * Copyright (c) 2022, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.lf;

import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.equations.JacobianMatrix;
import com.powsybl.openloadflow.equations.Quantity;
import com.powsybl.openloadflow.network.LfNetwork;

import java.util.Objects;

/**
 * @author Jean-Luc Bouchot (Artelys) {@literal <jlbouchot at gmail.com>}
 */
public abstract class AbstractLoadFlowContext <V extends Enum<V> & Quantity, E extends Enum<E> & Quantity, P extends AbstractLoadFlowParameters>
        implements LoadFlowContext<V, E, P>, AutoCloseable {

    protected final LfNetwork network;

    protected final P parameters;

    protected EquationSystem<V, E> equationSystem;

    protected JacobianMatrix<V, E> jacobianMatrix;

    protected AbstractLoadFlowContext(LfNetwork network, P parameters) {
        this.network = Objects.requireNonNull(network);
        this.parameters = Objects.requireNonNull(parameters);
    }

    public P getParameters() {
        return parameters;
    }

    public LfNetwork getNetwork() {
        return network;
    }

    @Override
    public void close() {
        if (jacobianMatrix != null) {
            jacobianMatrix.close();
        }
    }
}
