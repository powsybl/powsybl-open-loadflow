/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network.action;

import com.powsybl.contingency.strategy.OperatorStrategy;
import com.powsybl.openloadflow.util.Indexed;

import java.util.List;
import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class LfOperatorStrategy {

    private final Indexed<OperatorStrategy> indexedOperatorStrategy;

    private final List<LfAction> actions;

    public LfOperatorStrategy(Indexed<OperatorStrategy> indexedOperatorStrategy, List<LfAction> actions) {
        this.indexedOperatorStrategy = Objects.requireNonNull(indexedOperatorStrategy);
        this.actions = Objects.requireNonNull(actions);
    }

    public Indexed<OperatorStrategy> getIndexedOperatorStrategy() {
        return indexedOperatorStrategy;
    }

    public int getIndex() {
        return indexedOperatorStrategy.index();
    }

    public List<LfAction> getActions() {
        return actions;
    }
}
