/**
 * Copyright (c) 2025, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.equations;

import com.google.common.base.Stopwatch;
import com.powsybl.math.matrix.*;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.powsybl.openloadflow.util.Markers.PERFORMANCE_MARKER;

/**
 * @author Jeanne Archambault {@literal <jeanne.archambault at artelys.com>}
 */
public class JacobianMatrixFastDecoupled<V extends Enum<V> & Quantity, E extends Enum<E> & Quantity>
        extends JacobianMatrix<V, E> {

    private final int rangeIndex;

    private final boolean isPhySystem;

    public JacobianMatrixFastDecoupled(EquationSystem<V, E> equationSystem,
                                       MatrixFactory matrixFactory,
                                       int rangeIndex,
                                       boolean isPhySystem) {
        super(equationSystem, matrixFactory);
        this.rangeIndex = rangeIndex;
        this.isPhySystem = isPhySystem;
    }

    @Override
    protected void initDer() {
        Stopwatch stopwatch = Stopwatch.createStarted();

        List<Equation<V, E>> subsetEquationsToSolve = isPhySystem ? equationSystem.getIndex().getSortedEquationsToSolve().subList(0, rangeIndex)
                : equationSystem.getIndex().getSortedEquationsToSolve().subList(rangeIndex, equationSystem.getIndex().getSortedEquationsToSolve().size());

        int rowColumnCount = subsetEquationsToSolve.size();

        int estimatedNonZeroValueCount = rowColumnCount * 3;
        matrix = matrixFactory.create(rowColumnCount, rowColumnCount, estimatedNonZeroValueCount);

        for (Equation<V, E> eq : subsetEquationsToSolve) {
            int column = eq.getColumn();
            if (isPhySystem) {
                eq.derFastDecoupled((variable, value, matrixElementIndex) -> {
                    int row = variable.getRow();
                    return matrix.addAndGetIndex(row, column, value);
                }, rangeIndex, isPhySystem);
            } else {
                eq.derFastDecoupled((variable, value, matrixElementIndex) -> {
                    int row = variable.getRow();
                    return matrix.addAndGetIndex(row - rangeIndex, column - rangeIndex, value);
                }, rangeIndex, isPhySystem);
            }
        }

        LOGGER.debug(PERFORMANCE_MARKER, "Fast Decoupled Jacobian matrix built in {} us", stopwatch.elapsed(TimeUnit.MICROSECONDS));
    }

    @Override
    protected void updateDer() {
        Stopwatch stopwatch = Stopwatch.createStarted();

        List<Equation<V, E>> subsetEquationsToSolve = isPhySystem ? equationSystem.getIndex().getSortedEquationsToSolve().subList(0, rangeIndex)
                : equationSystem.getIndex().getSortedEquationsToSolve().subList(rangeIndex, equationSystem.getIndex().getSortedEquationsToSolve().size());

        matrix.reset();
        for (Equation<V, E> eq : subsetEquationsToSolve) {
            eq.derFastDecoupled((variable, value, matrixElementIndex) -> {
                matrix.addAtIndex(matrixElementIndex, value);
                return matrixElementIndex; // don't change element index
            }, rangeIndex, isPhySystem);
        }

        LOGGER.debug(PERFORMANCE_MARKER, "Fast Decoupled Jacobian matrix values updated in {} us", stopwatch.elapsed(TimeUnit.MICROSECONDS));
    }
}
