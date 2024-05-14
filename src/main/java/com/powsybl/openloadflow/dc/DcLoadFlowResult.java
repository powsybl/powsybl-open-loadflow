/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.dc;

import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.openloadflow.lf.AbstractLoadFlowResult;
import com.powsybl.openloadflow.network.LfNetwork;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class DcLoadFlowResult extends AbstractLoadFlowResult {

    private final boolean success;

    public DcLoadFlowResult(LfNetwork network, double slackBusActivePowerMismatch, boolean success) {
        super(network, slackBusActivePowerMismatch);
        this.success = success;
    }

    @Override
    public boolean isSuccess() {
        return success;
    }

    @Override
    public Status toComponentResultStatus() {
        if (network.getValidity() != LfNetwork.Validity.VALID) {
            return new Status(LoadFlowResult.ComponentResult.Status.NO_CALCULATION, network.getValidity().toString());
        }
        if (success) {
            return new Status(LoadFlowResult.ComponentResult.Status.CONVERGED, "Converged");
        }
        return new Status(LoadFlowResult.ComponentResult.Status.FAILED, "Solver Failed");
    }
}
