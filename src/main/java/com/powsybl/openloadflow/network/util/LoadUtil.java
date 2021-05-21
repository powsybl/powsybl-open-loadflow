/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.util;

import com.powsybl.iidm.network.Load;
import com.powsybl.iidm.network.extensions.LoadDetail;

public final class LoadUtil {

    public static double getParticipationFactor(Load load, boolean distributedOnConformLoad, double absLoadTargetP, double absVariableLoadTargetP) {
        if (distributedOnConformLoad) {
            return  load.getExtension(LoadDetail.class) == null ? 0. : Math.abs(load.getExtension(LoadDetail.class).getVariableActivePower()) / absVariableLoadTargetP;
        } else {
            return Math.abs(load.getP0()) / absLoadTargetP;
        }
    }

    public static double getPowerFactor(Load load) {
        return load.getP0() != 0 ? load.getQ0() / load.getP0() : 1;
    }

    private LoadUtil() {
    }
}
