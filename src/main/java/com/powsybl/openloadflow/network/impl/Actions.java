/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.action.*;
import com.powsybl.commons.PowsyblException;
import com.powsybl.iidm.network.Branch;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Switch;
import com.powsybl.iidm.network.TieLine;
import com.powsybl.openloadflow.network.LfTopoConfig;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public final class Actions {

    private static final String NOT_FOUND = "' not found in the network";

    private Actions() {
    }

    public static void check(Network network, List<Action> actions) {
        for (Action action : actions) {
            switch (action.getType()) {
                case SwitchAction.NAME: {
                    SwitchAction switchAction = (SwitchAction) action;
                    if (network.getSwitch(switchAction.getSwitchId()) == null) {
                        throw new PowsyblException("Switch '" + switchAction.getSwitchId() + NOT_FOUND);
                    }
                    break;
                }

                case TerminalsConnectionAction.NAME: {
                    TerminalsConnectionAction terminalsConnectionAction = (TerminalsConnectionAction) action;
                    if (network.getBranch(terminalsConnectionAction.getElementId()) == null) {
                        throw new PowsyblException("Branch '" + terminalsConnectionAction.getElementId() + NOT_FOUND);
                    }
                    break;
                }

                case PhaseTapChangerTapPositionAction.NAME,
                     RatioTapChangerTapPositionAction.NAME: {
                    String transformerId = action.getType().equals(PhaseTapChangerTapPositionAction.NAME) ?
                            ((PhaseTapChangerTapPositionAction) action).getTransformerId() : ((RatioTapChangerTapPositionAction) action).getTransformerId();
                    if (network.getTwoWindingsTransformer(transformerId) == null
                            && network.getThreeWindingsTransformer(transformerId) == null) {
                        throw new PowsyblException("Transformer '" + transformerId + NOT_FOUND);
                    }
                    break;
                }

                case LoadAction.NAME: {
                    LoadAction loadAction = (LoadAction) action;
                    if (network.getLoad(loadAction.getLoadId()) == null) {
                        throw new PowsyblException("Load '" + loadAction.getLoadId() + NOT_FOUND);
                    }
                    break;
                }

                case GeneratorAction.NAME: {
                    GeneratorAction generatorAction = (GeneratorAction) action;
                    if (network.getGenerator(generatorAction.getGeneratorId()) == null) {
                        throw new PowsyblException("Generator '" + generatorAction.getGeneratorId() + NOT_FOUND);
                    }
                    break;
                }

                case HvdcAction.NAME: {
                    HvdcAction hvdcAction = (HvdcAction) action;
                    if (network.getHvdcLine(hvdcAction.getHvdcId()) == null) {
                        throw new PowsyblException("Hvdc line '" + hvdcAction.getHvdcId() + NOT_FOUND);
                    }
                    break;
                }

                case ShuntCompensatorPositionAction.NAME: {
                    ShuntCompensatorPositionAction shuntCompensatorPositionAction = (ShuntCompensatorPositionAction) action;
                    if (network.getShuntCompensator(shuntCompensatorPositionAction.getShuntCompensatorId()) == null) {
                        throw new PowsyblException("Shunt compensator '" + shuntCompensatorPositionAction.getShuntCompensatorId() + "' not found");
                    }
                    break;
                }

                case AreaInterchangeTargetAction.NAME: {
                    AreaInterchangeTargetAction areaInterchangeAction = (AreaInterchangeTargetAction) action;
                    if (network.getArea(areaInterchangeAction.getAreaId()) == null) {
                        throw new PowsyblException("Area '" + areaInterchangeAction.getAreaId() + "' not found");
                    }
                    break;
                }

                default:
                    throw new UnsupportedOperationException("Unsupported action type: " + action.getType());
            }
        }
    }

    public static Map<String, Action> indexById(List<Action> actions) {
        return actions.stream()
                .collect(Collectors.toMap(
                        Action::getId,
                        Function.identity(),
                        (action1, action2) -> {
                            throw new PowsyblException("An action '" + action1.getId() + "' already exist");
                        }
                ));
    }

    public static void addAllSwitchesToOperate(LfTopoConfig topoConfig, Network network, List<Action> actions) {
        actions.stream().filter(action -> action.getType().equals(SwitchAction.NAME))
                .forEach(action -> {
                    String switchId = ((SwitchAction) action).getSwitchId();
                    Switch sw = network.getSwitch(switchId);
                    boolean toOpen = ((SwitchAction) action).isOpen();
                    if (sw.isOpen() && !toOpen) { // the switch is open and the action will close it.
                        topoConfig.getSwitchesToClose().add(sw);
                    } else if (!sw.isOpen() && toOpen) { // the switch is closed and the action will open it.
                        topoConfig.getSwitchesToOpen().add(sw);
                    }
                });
    }

    public static void addAllPtcToOperate(LfTopoConfig topoConfig, List<Action> actions) {
        for (Action action : actions) {
            if (PhaseTapChangerTapPositionAction.NAME.equals(action.getType())) {
                PhaseTapChangerTapPositionAction ptcAction = (PhaseTapChangerTapPositionAction) action;
                ptcAction.getSide().ifPresentOrElse(
                        side -> topoConfig.addBranchIdWithPtcToRetain(LfLegBranch.getId(side, ptcAction.getTransformerId())), // T3WT
                        () -> topoConfig.addBranchIdWithPtcToRetain(ptcAction.getTransformerId()) // T2WT
                );
            }
        }
    }

    public static void addAllRtcToOperate(LfTopoConfig topoConfig, List<Action> actions) {
        for (Action action : actions) {
            if (RatioTapChangerTapPositionAction.NAME.equals(action.getType())) {
                RatioTapChangerTapPositionAction rtcAction = (RatioTapChangerTapPositionAction) action;
                rtcAction.getSide().ifPresentOrElse(
                        side -> topoConfig.addBranchIdWithRtcToRetain(LfLegBranch.getId(side, rtcAction.getTransformerId())), // T3WT
                        () -> topoConfig.addBranchIdWithRtcToRetain(rtcAction.getTransformerId()) // T2WT
                );
            }
        }
    }

    public static void addAllShuntsToOperate(LfTopoConfig topoConfig, List<Action> actions) {
        actions.stream().filter(action -> action.getType().equals(ShuntCompensatorPositionAction.NAME))
                .forEach(action -> topoConfig.addShuntIdToOperate(((ShuntCompensatorPositionAction) action).getShuntCompensatorId()));
    }

    public static void addAllBranchesToClose(LfTopoConfig topoConfig, Network network, List<Action> actions) {
        // only branches open at both side or open at one side are visible in the LfNetwork.
        for (Action action : actions) {
            if (TerminalsConnectionAction.NAME.equals(action.getType())) {
                TerminalsConnectionAction terminalsConnectionAction = (TerminalsConnectionAction) action;
                if (terminalsConnectionAction.getSide().isEmpty() && !terminalsConnectionAction.isOpen()) {
                    Branch<?> branch = network.getBranch(terminalsConnectionAction.getElementId());
                    if (branch != null && !(branch instanceof TieLine) &&
                            !branch.getTerminal1().isConnected() && !branch.getTerminal2().isConnected()) {
                        // both terminals must be disconnected. If only one is connected, the branch is present
                        // in the Lf network.
                        topoConfig.getBranchIdsToClose().add(terminalsConnectionAction.getElementId());
                    }
                }
            }
        }
    }
}
