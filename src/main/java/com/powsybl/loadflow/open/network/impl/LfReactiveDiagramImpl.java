/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.loadflow.open.network.impl;

import com.powsybl.iidm.network.MinMaxReactiveLimits;
import com.powsybl.iidm.network.ReactiveCapabilityCurve;
import com.powsybl.iidm.network.ReactiveLimits;
import com.powsybl.loadflow.open.network.LfReactiveDiagram;
import com.powsybl.loadflow.open.network.PerUnit;

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
        return limits.getMinQ(p * PerUnit.SB) / PerUnit.SB;
    }

    @Override
    public double getMaxQ(double p) {
        return limits.getMaxQ(p * PerUnit.SB) / PerUnit.SB;
    }

    @Override
    public double getMaxRangeQ() {
        double maxRangeQ = Double.NaN;
        switch (limits.getKind()) {
            case CURVE:
                ReactiveCapabilityCurve reactiveCapabilityCurve = (ReactiveCapabilityCurve) limits;
                for (ReactiveCapabilityCurve.Point point : reactiveCapabilityCurve.getPoints()) {
                    if (Double.isNaN(maxRangeQ)) {
                        maxRangeQ = point.getMaxQ() - point.getMinQ();
                    } else {
                        maxRangeQ = Math.max(maxRangeQ, point.getMaxQ() - point.getMinQ());
                    }
                }
                break;

            case MIN_MAX:
                MinMaxReactiveLimits minMaxReactiveLimits = (MinMaxReactiveLimits) limits;
                maxRangeQ = minMaxReactiveLimits.getMaxQ() - minMaxReactiveLimits.getMinQ();
                break;

            default:
                throw new IllegalStateException("Unknown reactive limits kind: " + limits.getKind());
        }
        return maxRangeQ / PerUnit.SB;
    }
}
