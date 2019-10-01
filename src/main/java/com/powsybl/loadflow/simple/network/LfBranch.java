/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.loadflow.simple.network;

import com.powsybl.loadflow.simple.util.Evaluable;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public interface LfBranch {

    LfBus getBus1();

    LfBus getBus2();

    void setP1(Evaluable p1);

    void setP2(Evaluable p2);

    void setQ1(Evaluable q1);

    void setQ2(Evaluable q2);

    double x();

    double y();

    double ksi();

    double g1();

    double g2();

    double b1();

    double b2();

    double r1();

    double r2();

    double a1();

    double a2();

    void updateState();
}
