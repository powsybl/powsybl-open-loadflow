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
import com.powsybl.iidm.network.Network;

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
}
