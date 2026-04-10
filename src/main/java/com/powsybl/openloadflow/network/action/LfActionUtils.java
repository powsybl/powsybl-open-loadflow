/**
 * Copyright (c) 2025, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * Copyright (c) 2022-2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network.action;

import com.powsybl.action.*;
import com.powsybl.iidm.network.Network;
import com.powsybl.openloadflow.network.LfContingency;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.LfNetworkParameters;
import com.powsybl.openloadflow.util.Reports;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author Bertrand Rix {@literal <bertrand.rix at artelys.com>}
 * @author Anne Tilloy {@literal <anne.tilloy at rte-france.com>}
 * @author Jean-Luc Bouchot {@literal <jlbouchot at gmail.com>}
 */
public final class LfActionUtils {

    private LfActionUtils() {
    }

    public static LfAction createLfAction(Action action, Network network, LfNetwork lfNetwork) {
        Objects.requireNonNull(action);
        Objects.requireNonNull(network);
        return switch (action.getType()) {
            case SwitchAction.NAME -> new LfSwitchAction((SwitchAction) action, lfNetwork);
            case TerminalsConnectionAction.NAME ->
                new LfTerminalsConnectionAction((TerminalsConnectionAction) action, lfNetwork);
            case PhaseTapChangerTapPositionAction.NAME ->
                new LfPhaseTapChangerAction((PhaseTapChangerTapPositionAction) action, lfNetwork);
            case RatioTapChangerTapPositionAction.NAME ->
                new LfRatioTapChangerAction((RatioTapChangerTapPositionAction) action, lfNetwork);
            case LoadAction.NAME ->
                new LfLoadAction((LoadAction) action, network, lfNetwork);
            case GeneratorAction.NAME -> new LfGeneratorAction((GeneratorAction) action, lfNetwork);
            case HvdcAction.NAME -> new LfHvdcAction((HvdcAction) action, lfNetwork);
            case ShuntCompensatorPositionAction.NAME ->
                new LfShuntCompensatorPositionAction((ShuntCompensatorPositionAction) action, lfNetwork);
            case AreaInterchangeTargetAction.NAME ->
                new LfAreaInterchangeTargetAction((AreaInterchangeTargetAction) action, lfNetwork);
            default -> throw new UnsupportedOperationException("Unsupported action type: " + action.getType());
        };
    }

    public static Map<String, LfAction> createLfActions(LfNetwork lfNetwork, Set<Action> actions, Network network) {
        return actions.stream()
                .map(action -> LfActionUtils.createLfAction(action, network, lfNetwork))
                .collect(Collectors.toMap(LfAction::getId, Function.identity()));
    }

    public static void split(List<LfAction> actions, List<AbstractLfBranchAction<?>> branchActions, List<LfAction> otherActions) {
        Objects.requireNonNull(actions);
        Objects.requireNonNull(branchActions);
        Objects.requireNonNull(otherActions);
        for (LfAction action : actions) {
            if (action instanceof AbstractLfBranchAction<?>) {
                branchActions.add((AbstractLfBranchAction<?>) action);
            } else {
                otherActions.add(action);
            }
        }
    }

    public static void applyListOfActions(List<LfAction> actions, LfNetwork network, LfContingency contingency, LfNetworkParameters networkParameters) {
        Objects.requireNonNull(actions);
        Objects.requireNonNull(network);

        List<AbstractLfBranchAction<?>> branchActions = new ArrayList<>();
        List<LfAction> otherActions = new ArrayList<>();
        split(actions, branchActions, otherActions);

        // first apply action modifying connectivity
        AbstractLfBranchAction.getNetworkActivations(network, contingency, branchActions).apply();

        // then process remaining changes of actions
        otherActions.forEach(action -> {
                if (!action.apply(network, contingency, networkParameters)) {
                    Reports.reportActionApplicationFailure(action.getId(), contingency.getId(), network.getReportNode());
                }
            });
    }
}
