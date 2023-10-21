/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.equations;

import gnu.trove.list.array.TIntArrayList;

import java.util.Objects;
import java.util.function.IntToDoubleFunction;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class EquationTermArray {

    final IntToDoubleFunction evaluator;

    final TIntArrayList elementNums = new TIntArrayList();
    final TIntArrayList termElementNums = new TIntArrayList();

    public EquationTermArray(IntToDoubleFunction evaluator) {
        this.evaluator = Objects.requireNonNull(evaluator);
    }

    public EquationTermArray addTerm(int elementNum, int termElementNum) {
        elementNums.add(elementNum);
        termElementNums.add(termElementNum);
        return this;
    }
}
