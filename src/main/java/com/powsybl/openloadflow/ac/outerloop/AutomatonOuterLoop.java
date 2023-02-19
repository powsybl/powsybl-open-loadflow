/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.outerloop;

import com.powsybl.commons.reporter.Reporter;
import com.powsybl.openloadflow.ac.OuterLoop;
import com.powsybl.openloadflow.ac.OuterLoopContext;
import com.powsybl.openloadflow.ac.OuterLoopStatus;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class AutomatonOuterLoop implements OuterLoop {

    @Override
    public String getType() {
        return "Automaton";
    }

    @Override
    public OuterLoopStatus check(OuterLoopContext context, Reporter reporter) {
        // TODO
        return OuterLoopStatus.STABLE;
    }
}
