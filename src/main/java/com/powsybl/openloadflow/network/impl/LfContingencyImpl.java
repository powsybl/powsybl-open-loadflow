/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.contingency.Contingency;
import com.powsybl.openloadflow.network.LfContingency;

import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class LfContingencyImpl implements LfContingency {

    private final Contingency contingency;

    public LfContingencyImpl(Contingency contingency) {
        this.contingency = Objects.requireNonNull(contingency);
    }

    @Override
    public String getId() {
        return contingency.getId();
    }
}
