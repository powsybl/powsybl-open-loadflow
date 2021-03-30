/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.sensi;

import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class WeightedSensitivityVariable {

    private final String id;

    private final double weight;

    public WeightedSensitivityVariable(String id, double weight) {
        this.id = Objects.requireNonNull(id);
        this.weight = weight;
    }

    public String getId() {
        return id;
    }

    public double getWeight() {
        return weight;
    }
}
