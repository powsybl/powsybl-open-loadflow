/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.equations;

import com.google.common.base.Stopwatch;
import com.powsybl.commons.PowsyblException;
import com.powsybl.math.matrix.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.powsybl.openloadflow.util.Markers.PERFORMANCE_MARKER;

/**
 * @author Jeanne Archambault {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class JacobianMatrixFastDecoupled<V extends Enum<V> & Quantity, E extends Enum<E> & Quantity>
        extends JacobianMatrix<V, E> {

    private int rangeIndex;

    private boolean isPhySystem;

    private List<Equation<V, E>> subsetEquationsToSolve;

    private List<Variable<V>> subsetVariablesToFind;

    public JacobianMatrixFastDecoupled(EquationSystem<V, E> equationSystem,
                                       MatrixFactory matrixFactory,
                                       int rangeIndex,
                                       boolean isPhySystem) {
        super(equationSystem, matrixFactory);
        this.rangeIndex = rangeIndex;
        this.isPhySystem = isPhySystem;
        setSubsetEquationsToSolve();
        setSubsetVariablesToFind();
    }

    public void setSubsetEquationsToSolve() {
        if (isPhySystem) {
            this.subsetEquationsToSolve = equationSystem.getIndex().getSortedEquationsToSolve().subList(0, rangeIndex);
        } else {
            this.subsetEquationsToSolve = equationSystem.getIndex().getSortedEquationsToSolve().subList(rangeIndex, equationSystem.getIndex().getSortedEquationsToSolve().size());
        }
    }

    public void setSubsetVariablesToFind() {
        if (isPhySystem) {
            this.subsetVariablesToFind = equationSystem.getIndex().getSortedVariablesToFind().subList(0, rangeIndex);
        } else {
            this.subsetVariablesToFind = equationSystem.getIndex().getSortedVariablesToFind().subList(rangeIndex, equationSystem.getIndex().getSortedVariablesToFind().size());
        }
    }

    @Override
    public void initDer() {
        Stopwatch stopwatch = Stopwatch.createStarted();

        int rowCount = subsetEquationsToSolve.size();
        int columnCount = subsetVariablesToFind.size();

        if (rowCount != columnCount) {
            throw new PowsyblException("Expected to have same number of equations (" + rowCount
                    + ") and variables (" + columnCount + ")");
        }

        int estimatedNonZeroValueCount = rowCount * 3;
        matrix = matrixFactory.create(rowCount, columnCount, estimatedNonZeroValueCount);

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
                    return matrix.addAndGetIndex(row- rangeIndex, column- rangeIndex, value);
                }, rangeIndex, isPhySystem);            }
        }

        LOGGER.debug(PERFORMANCE_MARKER, "Jacobian matrix built in {} us", stopwatch.elapsed(TimeUnit.MICROSECONDS));
    }

    @Override
    public void updateDer() {
        Stopwatch stopwatch = Stopwatch.createStarted();

        matrix.reset();
        for (Equation<V, E> eq : subsetEquationsToSolve) {
            eq.derFastDecoupled((variable, value, matrixElementIndex) -> {
                matrix.addAtIndex(matrixElementIndex, value);
                return matrixElementIndex; // don't change element index
            }, rangeIndex, isPhySystem);
        }

        LOGGER.debug(PERFORMANCE_MARKER, "Jacobian matrix values updated in {} us", stopwatch.elapsed(TimeUnit.MICROSECONDS));
    }
}
