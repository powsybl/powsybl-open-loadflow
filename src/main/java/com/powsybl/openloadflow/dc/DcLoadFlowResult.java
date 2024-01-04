/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.dc;

import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.openloadflow.lf.AbstractLoadFlowResult;
import com.powsybl.openloadflow.network.LfNetwork;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class DcLoadFlowResult extends AbstractLoadFlowResult {

    private final boolean succeeded;

    public DcLoadFlowResult(LfNetwork network, double slackBusActivePowerMismatch, boolean succeeded) {
        super(network, slackBusActivePowerMismatch);
        this.succeeded = succeeded;
    }

    @Override
    public boolean isOk() {
        return isSucceeded();
    }

    public boolean isSucceeded() {
        return succeeded;
    }

    @Override
    public LoadFlowResult.ComponentResult.Status toComponentResultStatus() {
        return succeeded ? LoadFlowResult.ComponentResult.Status.CONVERGED : LoadFlowResult.ComponentResult.Status.FAILED;
    }
}
