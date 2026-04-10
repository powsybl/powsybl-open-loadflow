/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.sensi;

import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.action.LfOperatorStrategy;
import com.powsybl.openloadflow.network.impl.PropagatedContingency;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class LfNetworkChange {
    private final PropagatedContingency propagatedContingency;
    private final LfContingency lfContingency;
    private final LfOperatorStrategy lfOperatorStrategy;

    public LfNetworkChange(PropagatedContingency propagatedContingency, LfContingency lfContingency, LfOperatorStrategy lfOperatorStrategy) {
        this.propagatedContingency = Objects.requireNonNull(propagatedContingency);
        this.lfContingency = lfContingency;
        this.lfOperatorStrategy = lfOperatorStrategy;
    }

    public String getContingencyId() {
        return propagatedContingency.getContingency().getId();
    }

    public boolean hasImpact() {
        return lfContingency != null || lfOperatorStrategy != null;
    }

    public DisabledNetwork getDisabledNetwork() {
        return lfContingency != null ? lfContingency.getDisabledNetwork() : new DisabledNetwork();
    }

    public void apply(LoadFlowParameters.BalanceType balanceType) {
        if (lfContingency != null) {
            lfContingency.apply(balanceType);
        }
    }

    public int getContingencyIndex() {
        return propagatedContingency.getIndex();
    }

    public int getOperatorStrategyIndex() {
        return lfOperatorStrategy != null ? lfOperatorStrategy.getIndex() : -1;
    }

    public double getActivePowerLoss() {
        return lfContingency != null ? lfContingency.getActivePowerLoss() : 0;
    }

    public Map<LfLoad, LfLostLoad> getLostLoads() {
        return lfContingency != null ? lfContingency.getLostLoads() : Collections.emptyMap();
    }

    public Set<LfGenerator> getLostGenerators() {
        return lfContingency != null ? lfContingency.getLostGenerators() : Collections.emptySet();
    }

    public Set<LfBus> getLoadAndGeneratorBuses() {
        return lfContingency != null ? lfContingency.getLoadAndGeneratorBuses() : Collections.emptySet();
    }
}
