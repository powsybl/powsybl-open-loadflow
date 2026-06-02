/**
 * Copyright (c) 2025, SuperGrid Institute (http://www.supergrid-institute.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.iidm.network.DcSwitch;
import com.powsybl.openloadflow.network.LfDcBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.LfNetworkParameters;
import com.powsybl.openloadflow.network.LfNetworkStateUpdateParameters;
import com.powsybl.openloadflow.network.LfNetworkUpdateReport;

import java.util.Objects;

/**
 * @author Landry Huet {@literal <landry.huet at supergrid-institute.com>}
 */
public class LfDcSwitchImpl extends AbstractLfDcLine {

    private final Ref<DcSwitch> dcSwitchRef;

    public LfDcSwitchImpl(LfDcBus dcBus1, LfDcBus dcBus2, LfNetwork network, DcSwitch dcSwitch, LfNetworkParameters parameters) {
        super(network, dcBus1, dcBus2, dcSwitch.getR());
        this.dcSwitchRef = Ref.create(dcSwitch, parameters.isCacheEnabled());
    }

    public static LfDcSwitchImpl create(DcSwitch dcSwitch,
                                        LfNetwork network,
                                        LfDcBus dcBus1,
                                        LfDcBus dcBus2,
                                        LfNetworkParameters parameters) {
        Objects.requireNonNull(network);
        Objects.requireNonNull(dcSwitch);
        Objects.requireNonNull(dcBus1);
        Objects.requireNonNull(dcBus2);
        Objects.requireNonNull(parameters);
        return new LfDcSwitchImpl(dcBus1, dcBus2, network, dcSwitch, parameters);
    }

    private DcSwitch getDcSwitch() {
        return dcSwitchRef.get();
    }

    @Override
    public String getId() {
        return getDcSwitch().getId();
    }

    @Override
    public void updateState(LfNetworkStateUpdateParameters parameters, LfNetworkUpdateReport updateReport) {
        if (isDisabled()) {
            updateFlows(Double.NaN, Double.NaN, Double.NaN, Double.NaN);
        } else {
            updateFlows(i1.eval(), i2.eval(), p1.eval(), p2.eval());
        }
    }

    @Override
    public void updateFlows(double i1, double i2, double p1, double p2) {
        // For now no terminal in DcSwitch, so skip.
    }
}
