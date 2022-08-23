/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.iidm.network.Load;
import com.powsybl.openloadflow.network.AbstractPropertyBag;
import com.powsybl.openloadflow.network.LfLoad;

import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class LfLoadImpl extends AbstractPropertyBag implements LfLoad {

    private final Load load;

    public LfLoadImpl(Load load) {
        this.load = Objects.requireNonNull(load);
    }

    @Override
    public String getId() {
        return load.getId();
    }

    @Override
    public double getTargetP() {
        return load.getP0();
    }

    @Override
    public double getTargetQ() {
        return load.getQ0();
    }
}
