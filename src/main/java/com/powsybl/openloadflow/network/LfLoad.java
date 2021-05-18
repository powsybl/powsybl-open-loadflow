/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public interface LfLoad extends LfElement {

    double getP0();

    double getParticipationFactor(boolean distributedOnConformLoad, double absLoadTargetP, double absVariableLoadTargetP);

    double getPowerFactor();

    void updateState(double diffP, boolean loadPowerFactorConstant);
}
