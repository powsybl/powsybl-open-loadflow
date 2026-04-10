/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.sensi;

import com.powsybl.commons.PowsyblException;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.action.AbstractLfBranchAction;
import com.powsybl.openloadflow.network.action.LfAction;
import com.powsybl.openloadflow.network.action.LfActionUtils;
import com.powsybl.openloadflow.network.action.LfOperatorStrategy;
import com.powsybl.openloadflow.network.impl.PropagatedContingency;

import java.util.*;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class LfNetworkChange {
    private final PropagatedContingency propagatedContingency;
    private final LfContingency lfContingency;
    private final LfOperatorStrategy lfOperatorStrategy;
    private final DisabledNetwork disabledNetwork;

    public LfNetworkChange(LfNetwork lfNetwork, PropagatedContingency propagatedContingency, LfContingency lfContingency, LfOperatorStrategy lfOperatorStrategy) {
        Objects.requireNonNull(lfNetwork);
        this.propagatedContingency = Objects.requireNonNull(propagatedContingency);
        this.lfContingency = lfContingency;
        this.lfOperatorStrategy = lfOperatorStrategy;
        Set<LfBus> disabledBuses = new HashSet<>();
        Set<LfBranch> disabledBranches = new HashSet<>();
        if (lfContingency != null) {
            disabledBuses.addAll(lfContingency.getDisabledNetwork().getBuses());
            disabledBranches.addAll(lfContingency.getDisabledNetwork().getBranches());
        }
        if (lfOperatorStrategy != null) {
            List<AbstractLfBranchAction<?>> branchActions = new ArrayList<>();
            List<LfAction> otherActions = new ArrayList<>();
            LfActionUtils.split(lfOperatorStrategy.getActions(), branchActions, otherActions);
            NetworkActivations networkActivations = AbstractLfBranchAction.getNetworkActivations(lfNetwork, lfContingency, branchActions);
            disabledBuses.addAll(networkActivations.getDisabledNetwork().getBuses());
            disabledBranches.addAll(networkActivations.getDisabledNetwork().getBranches());
            if (!networkActivations.getEnabledNetwork().getBuses().isEmpty()) {
                throw new PowsyblException("Network change should not add new buses");
            }
        }
        disabledNetwork = new DisabledNetwork(disabledBuses, disabledBranches);
    }

    public String getContingencyId() {
        return propagatedContingency.getContingency().getId();
    }

    public boolean hasImpact() {
        return lfContingency != null || lfOperatorStrategy != null;
    }

    public DisabledNetwork getDisabledNetwork() {
        return disabledNetwork;
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
