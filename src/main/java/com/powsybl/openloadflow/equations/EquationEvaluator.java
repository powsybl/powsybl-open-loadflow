/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.equations;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public interface EquationEvaluator {

    double getP1(int branchNum);

    double getP2(int branchNum);

    double getQ1(int branchNum);

    double getQ2(int branchNum);

    double getV(int busNum);

    double getAngle(int busNum);

    double eval(int column);

    interface DerivativeHandler {

        void onRow(int row, double value);
    }

    void der(int column, DerivativeHandler handler);
}
