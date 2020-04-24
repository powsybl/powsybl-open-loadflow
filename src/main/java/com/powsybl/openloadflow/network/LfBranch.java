/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import com.powsybl.iidm.network.Branch;
import com.powsybl.openloadflow.util.Evaluable;

import java.util.Optional;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public interface LfBranch {

    String getId();

    int getNum();

    void setNum(int num);

    LfBus getBus1();

    LfBus getBus2();

    void setP1(Evaluable p1);

    void setP2(Evaluable p2);

    void setQ1(Evaluable q1);

    void setQ2(Evaluable q2);

    void setA1(double a1);

    void setA2(double a2);

    PiModel getPiModel();

    Optional<PhaseControl> getPhaseControl();

    Branch getBranch();

    void updateState();
}
