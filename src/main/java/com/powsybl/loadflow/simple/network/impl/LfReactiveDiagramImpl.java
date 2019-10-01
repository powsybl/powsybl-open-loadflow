/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.loadflow.simple.network.impl;

import com.powsybl.iidm.network.ReactiveLimits;
import com.powsybl.loadflow.simple.network.LfReactiveDiagram;
import com.powsybl.loadflow.simple.network.PerUnit;

import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
class LfReactiveDiagramImpl implements LfReactiveDiagram {

    private final ReactiveLimits limits;

    LfReactiveDiagramImpl(ReactiveLimits limits) {
        this.limits = Objects.requireNonNull(limits);
    }

    @Override
    public double getMinQ(double p) {
        return limits.getMinQ(p) / PerUnit.SB;
    }

    @Override
    public double getMaxQ(double p) {
        return limits.getMaxQ(p) / PerUnit.SB;
    }
}
