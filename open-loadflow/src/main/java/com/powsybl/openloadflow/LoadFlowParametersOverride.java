/**
 * Copyright (c) 2025, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow;

import com.powsybl.loadflow.LoadFlowParameters;

import java.util.List;

/**
 * @author Valentin Mouradian {@literal <valentin.mouradian at artelys.com>}
 */
public interface LoadFlowParametersOverride {

    LoadFlowParametersOverride NO_OVERRIDE = new LoadFlowParametersOverride() { };

    default boolean isDistributedSlack(LoadFlowParameters loadFlowParameters) {
        return loadFlowParameters.isDistributedSlack();
    }

    default boolean isAreaInterchangeControl(OpenLoadFlowParameters openLoadFlowParameters) {
        return openLoadFlowParameters.isAreaInterchangeControl();
    }

    default LoadFlowParameters.BalanceType getBalanceType(LoadFlowParameters loadFlowParameters) {
        return loadFlowParameters.getBalanceType();
    }

    default List<String> getOuterLoopNames(OpenLoadFlowParameters openLoadFlowParameters) {
        return openLoadFlowParameters.getOuterLoopNames();
    }

}
