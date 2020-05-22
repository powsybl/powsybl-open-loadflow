/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import com.powsybl.openloadflow.util.Evaluable;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public interface LfShunt {

    enum ModelType {
        SUSCEPTANCE,
        VOLTAGE_SLOPE
    }

    interface Model {

        ModelType getType();
    }

    interface SusceptanceModel extends Model {

        @Override
        default ModelType getType() {
            return ModelType.SUSCEPTANCE;
        }

        public abstract double getB();
    }

    Model getModel();

    void setQ(Evaluable q);

    void updateState();
}
