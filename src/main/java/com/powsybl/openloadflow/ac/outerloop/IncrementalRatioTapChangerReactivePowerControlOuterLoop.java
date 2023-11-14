/**
 * Copyright (c) 2022, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.outerloop;

import com.powsybl.commons.reporter.Reporter;
import com.powsybl.openloadflow.ac.AcOuterLoopContext;
import com.powsybl.openloadflow.lf.outerloop.IncrementalContextData;
import com.powsybl.openloadflow.lf.outerloop.OuterLoopStatus;
import org.apache.commons.lang3.mutable.MutableObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Pierre Arvy {@literal <pierre.arvy at artelys.com>}
 */
public class IncrementalRatioTapChangerReactivePowerControlOuterLoop implements AcOuterLoop {

    private static final Logger LOGGER = LoggerFactory.getLogger(IncrementalRatioTapChangerReactivePowerControlOuterLoop.class);

    public static final String NAME = "IncrementalRatioTapChangerReactivePowerControl";

    private final int maxTapShift;

    public IncrementalRatioTapChangerReactivePowerControlOuterLoop(int maxTapShift) {
        this.maxTapShift = maxTapShift;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public void initialize(AcOuterLoopContext context) {
        var contextData = new IncrementalContextData();
        context.setData(contextData);
    }

    @Override
    public OuterLoopStatus check(AcOuterLoopContext context, Reporter reporter) {
        MutableObject<OuterLoopStatus> status = new MutableObject<>(OuterLoopStatus.STABLE);
        return status.getValue();
    }
}
