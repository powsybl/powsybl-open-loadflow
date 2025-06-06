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
import com.powsybl.openloadflow.graph.GraphConnectivity;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.util.Reports;

import java.util.List;
import java.util.Objects;

import static com.powsybl.openloadflow.network.action.AbstractLfBranchAction.updateBusesAndBranchStatus;

/**
 * @author Bertrand Rix {@literal <bertrand.rix at artelys.com>}
 * @author Anne Tilloy {@literal <anne.tilloy at rte-france.com>}
 * @author Jean-Luc Bouchot {@literal <jlbouchot at gmail.com>}
 */
public final class LfActionUtils {

    private LfActionUtils() {
    }

    public static LfAction createLfAction(Action action, Network network, boolean breakers, LfNetwork lfNetwork) {
        Objects.requireNonNull(action);
        Objects.requireNonNull(network);
        return switch (action.getType()) {
            case SwitchAction.NAME -> new LfSwitchAction(action.getId(), (SwitchAction) action, lfNetwork);
            case TerminalsConnectionAction.NAME ->
                new LfTerminalsConnectionAction(action.getId(), (TerminalsConnectionAction) action, lfNetwork);
            case PhaseTapChangerTapPositionAction.NAME ->
                new LfPhaseTapChangerAction(action.getId(), (PhaseTapChangerTapPositionAction) action, lfNetwork);
            case RatioTapChangerTapPositionAction.NAME ->
                new LfRatioTapChangerAction(action.getId(), (RatioTapChangerTapPositionAction) action, lfNetwork);
            case LoadAction.NAME ->
                new LfLoadAction(action.getId(), (LoadAction) action, network, breakers);
            case GeneratorAction.NAME -> new LfGeneratorAction(action.getId(), (GeneratorAction) action, lfNetwork);
            case HvdcAction.NAME -> new LfHvdcAction(action.getId(), (HvdcAction) action);
            case ShuntCompensatorPositionAction.NAME ->
                new LfShuntCompensatorPositionAction(action.getId(), (ShuntCompensatorPositionAction) action);
            case AreaInterchangeTargetAction.NAME ->
                new LfAreaInterchangeTargetAction(action.getId(), (AreaInterchangeTargetAction) action);
            default -> throw new UnsupportedOperationException("Unsupported action type: " + action.getType());
        };
    }

    public static void applyListOfActions(List<LfAction> actions, LfNetwork network, LfContingency contingency, LfNetworkParameters networkParameters) {
        Objects.requireNonNull(actions);
        Objects.requireNonNull(network);

        // first apply action modifying connectivity
        List<LfAction> branchActions = actions.stream()
            .filter(action -> action instanceof AbstractLfBranchAction<?>)
            .toList();
        updateConnectivity(branchActions, network, contingency);

        // then process remaining changes of actions
        actions.stream()
            .filter(action -> !(action instanceof AbstractLfBranchAction<?>))
            .forEach(action -> {
                if (!action.apply(network, contingency, networkParameters)) {
                    Reports.reportActionApplicationFailure(action.getId(), contingency.getId(), network.getReportNode());
                }
            });
    }

    private static void updateConnectivity(List<LfAction> branchActions, LfNetwork network, LfContingency contingency) {
        GraphConnectivity<LfBus, LfBranch> connectivity = network.getConnectivity();

        // re-update connectivity according to post contingency state (revert after LfContingency apply)
        connectivity.startTemporaryChanges();
        contingency.getDisabledNetwork().getBranches().forEach(connectivity::removeEdge);

        // update connectivity according to post action state
        connectivity.startTemporaryChanges();

        branchActions.forEach(action -> {
            if (!((AbstractLfBranchAction<?>) action).applyOnConnectivity(connectivity)) {
                Reports.reportActionApplicationFailure(action.getId(), contingency.getId(), network.getReportNode());
            }
        });

        updateBusesAndBranchStatus(connectivity);

        // reset connectivity to discard post contingency connectivity and post action connectivity
        connectivity.undoTemporaryChanges();
        connectivity.undoTemporaryChanges();
    }

}
