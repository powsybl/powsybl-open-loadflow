/**
 * Copyright (c) 2026, Elia Group (https://www.eliagroup.eu)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.equations;

import com.powsybl.math.matrix.MatrixFactory;

/**
 * @author Šime Pavlić {@literal <sime.pavlic at kickstage.com>}
 */
public class RectangularJacobianMatrix<V extends Enum<V> & Quantity, E extends Enum<E> & Quantity>
        extends AbstractJacobianMatrix<V, E> {

    public RectangularJacobianMatrix(EquationSystem<V, E> equationSystem, MatrixFactory matrixFactory,
                                     boolean invalidateValuesWhenStateUpdate) {
        super(equationSystem, matrixFactory, invalidateValuesWhenStateUpdate);
    }

    public RectangularJacobianMatrix(EquationSystem<V, E> equationSystem, MatrixFactory matrixFactory) {
        this(equationSystem, matrixFactory, true);
    }
}
