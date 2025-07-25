/*
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */

package com.powsybl.openloadflow.equations;

import com.powsybl.math.matrix.Matrix;

import java.util.Map;

public interface VectorEngine<V extends Enum<V> & Quantity> {

    interface VecToVal {
        double value(double v1, double v2, double sinKsi, double cosKsi, double sinTheta2, double cosTheta2,
                     double sinTheta1, double cosTheta1,
                     double b1, double b2, double g1, double g2, double y,
                     double g12, double b12, double a1, double r1, int branchNum, Map<Integer, String> branchNameByBranchNum);
        // brancNum and branchNameByBranchNum provided as debug info
        // For example to set a conditional breakpoint in an equation for a given branch
    }

    void der(boolean update, Matrix matrix);

    void evalLhs(double[] array);
}
