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
 * A generic wrapper for network changes that abstract from a contingency or an operator strategy.
 *
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class LfNetworkChange {
    private final PropagatedContingency propagatedContingency;
    private final LfContingency lfContingency;
    private final LfOperatorStrategy lfOperatorStrategy;
    private final DisabledNetwork disabledNetwork;
    private final EnabledNetwork enabledNetwork;

    public LfNetworkChange(LfNetwork lfNetwork, PropagatedContingency propagatedContingency, LfContingency lfContingency, LfOperatorStrategy lfOperatorStrategy) {
        Objects.requireNonNull(lfNetwork);
        this.propagatedContingency = propagatedContingency;
        this.lfContingency = lfContingency;
        this.lfOperatorStrategy = lfOperatorStrategy;
        Set<LfBus> disabledBuses = new HashSet<>();
        Map<LfBranch, DisabledBranchStatus> branchesStatus = new HashMap<>();
        Set<LfHvdc> disabledHvdcs = new HashSet<>();
        Set<LfBus> enabledBuses = new HashSet<>();
        Set<LfBranch> enabledBranches = new HashSet<>();
        if (lfContingency != null) {
            disabledBuses.addAll(lfContingency.getDisabledNetwork().getBuses());
            branchesStatus.putAll(lfContingency.getDisabledNetwork().getBranchesStatus());
            disabledHvdcs.addAll(lfContingency.getDisabledNetwork().getHvdcs());
        }
        if (lfOperatorStrategy != null) {
            List<AbstractLfBranchAction<?>> branchActions = new ArrayList<>();
            List<LfAction> otherActions = new ArrayList<>();
            LfActionUtils.split(lfOperatorStrategy.getActions(), branchActions, otherActions);
            NetworkActivations networkActivations = AbstractLfBranchAction.getNetworkActivations(lfNetwork, lfContingency, branchActions);
            disabledBuses.addAll(networkActivations.getDisabledNetwork().getBuses());
            branchesStatus.putAll(networkActivations.getDisabledNetwork().getBranchesStatus());
            // check we don't re-enable a bus or a branch disabled by the contingency
            // TODO to support later
            for (LfBus bus : networkActivations.getEnabledNetwork().getBuses()) {
                if (disabledBuses.contains(bus)) {
                    throw new PowsyblException("Network change should not enable a bus already disabled by the contingency");
                }
            }
            for (LfBranch branch : networkActivations.getEnabledNetwork().getBranches()) {
                if (branchesStatus.containsKey(branch)) {
                    throw new PowsyblException("Network change should not enable a branch already disabled by the contingency");
                }
            }
            enabledBuses.addAll(networkActivations.getEnabledNetwork().getBuses());
            enabledBranches.addAll(networkActivations.getEnabledNetwork().getBranches());
        }
        disabledNetwork = new DisabledNetwork(disabledBuses, branchesStatus, disabledHvdcs);
        enabledNetwork = new EnabledNetwork(enabledBuses, enabledBranches);
    }

    public String getContingencyId() {
        return propagatedContingency != null ? propagatedContingency.getContingency().getId() : "";
    }

    public String getOperatorStrategyId() {
        return lfOperatorStrategy != null ? lfOperatorStrategy.getIndexedOperatorStrategy().value().getId() : null;
    }

    public boolean hasImpact() {
        return lfContingency != null || lfOperatorStrategy != null;
    }

    public DisabledNetwork getDisabledNetwork() {
        return disabledNetwork;
    }

    public EnabledNetwork getEnabledNetwork() {
        return enabledNetwork;
    }

    public void apply(LoadFlowParameters.BalanceType balanceType) {
        disabledNetwork.apply();
        enabledNetwork.apply();
        if (lfContingency != null) {
            lfContingency.processLostPowerChanges(balanceType, true);
        }
    }

    public int getContingencyIndex() {
        return propagatedContingency != null ? propagatedContingency.getIndex() : -1;
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
