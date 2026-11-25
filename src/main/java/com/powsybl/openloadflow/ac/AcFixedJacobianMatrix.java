/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.math.matrix.LUDecomposition;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.openloadflow.ac.equations.AcEquationType;
import com.powsybl.openloadflow.ac.equations.AcVariablePairing;
import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.equations.fixed.FixedJacobian;
import com.powsybl.openloadflow.network.LfNetwork;

/**
 * The AC Jacobian solved on a {@link FixedJacobian}: a matrix, sparse with the default matrix factory, whose matrix structure
 * stays the same when buses hold or release their voltage, when branches or buses are lost and when controls are
 * switched, so that its LU decomposition is analysed once ({@link AcJacobianStructure#FIXED}).
 *
 * <p>Only the solves change: right-hand sides and solutions are those of {@link AcJacobianMatrix}, in the numbering of
 * the index. {@link #getMatrix()} is still the matrix in the numbering of the index, built on demand for the consumers
 * that read the matrix itself; it is not factorised.
 *
 * @author Gautier Bureau {@literal <gautier.bureau at rte-france.com>}
 */
public class AcFixedJacobianMatrix extends AcJacobianMatrix {

    private final FixedJacobian<AcVariableType, AcEquationType> fixedJacobian;

    public AcFixedJacobianMatrix(EquationSystem<AcVariableType, AcEquationType> equationSystem, MatrixFactory matrixFactory,
                                 LfNetwork network) {
        super(equationSystem, matrixFactory, network);
        fixedJacobian = new FixedJacobian<>(equationSystem, new AcVariablePairing(network), matrixFactory);
    }

    public FixedJacobian<AcVariableType, AcEquationType> getFixedJacobian() {
        return fixedJacobian;
    }

    @Override
    protected void updateStatus(Status status) {
        super.updateStatus(status);
        fixedJacobian.invalidate();
    }

    @Override
    protected LUDecomposition getLUDecomposition() {
        checkSquare();
        return fixedJacobian;
    }

    @Override
    public void close() {
        super.close();
        fixedJacobian.close();
    }
}
