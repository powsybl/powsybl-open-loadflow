/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.openloadflow.ac.outerloop.OuterLoop;
import com.powsybl.openloadflow.ac.outerloop.OuterLoopContext;
import com.powsybl.openloadflow.ac.outerloop.OuterLoopStatus;
import com.powsybl.openloadflow.network.LfContingency;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class ContingencyOuterLoop implements OuterLoop {

    private static final Logger LOGGER = LoggerFactory.getLogger(ContingencyOuterLoop.class);

    private final List<LfContingency> contingencies;

    public ContingencyOuterLoop(List<LfContingency> contingencies) {
        this.contingencies = Objects.requireNonNull(contingencies);
    }

    @Override
    public String getType() {
        return "Contingency";
    }

    @Override
    public OuterLoopStatus check(OuterLoopContext context) {
        if (contingencies.isEmpty() || context.getIteration() >= contingencies.size()) {
            return OuterLoopStatus.STABLE;
        }
        LfContingency contingency = contingencies.get(context.getIteration());
        LOGGER.info("Simulate contingency '{}'", contingency.getId());
        // TODO
        return OuterLoopStatus.UNSTABLE;
    }
}
