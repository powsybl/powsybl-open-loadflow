/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph;

import com.powsybl.openloadflow.graph.dtree.AbstractSetView;

/**
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 */
public abstract class AbstractComponent<V> extends AbstractSetView<V> implements Component<V> {

    private int num;

    public int getNumber() {
        return num;
    }

    void setNum(int num) {
        this.num = num;
    }
}
