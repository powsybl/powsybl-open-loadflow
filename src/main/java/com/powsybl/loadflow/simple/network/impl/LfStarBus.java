/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.loadflow.simple.network.impl;

import com.powsybl.iidm.network.ThreeWindingsTransformer;
import com.powsybl.loadflow.simple.network.AbstractFictitiousLfBus;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class LfStarBus extends AbstractFictitiousLfBus {

    private final ThreeWindingsTransformer t3wt;

    public LfStarBus(ThreeWindingsTransformer t3wt, int num) {
        super(num);
        this.t3wt = t3wt;
    }

    @Override
    public String getId() {
        return t3wt.getId() + "_BUS0";
    }

    @Override
    public double getNominalV() {
        return t3wt.getLeg1().getTerminal().getVoltageLevel().getNominalV();
    }

    @Override
    public int getNeighbors() {
        return 3;
    }
}
