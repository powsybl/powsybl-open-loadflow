/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network.action;

import com.powsybl.action.Action;
import com.powsybl.commons.PowsyblException;
import com.powsybl.contingency.Contingency;
import com.powsybl.contingency.ContingencyContextType;
import com.powsybl.contingency.strategy.ConditionalActions;
import com.powsybl.contingency.strategy.OperatorStrategy;
import com.powsybl.openloadflow.network.impl.PropagatedContingency;
import com.powsybl.openloadflow.util.Indexed;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public final class OperatorStrategies {

    private OperatorStrategies() {
    }

    private static boolean hasValidContingency(OperatorStrategy operatorStrategy, Set<String> contingencyIds) {
        if (operatorStrategy.getContingencyContext().getContextType() == ContingencyContextType.SPECIFIC) {
            return contingencyIds.contains(operatorStrategy.getContingencyContext().getContingencyId());
        }
        return true;
    }

    private static Optional<String> findMissingActionId(OperatorStrategy operatorStrategy, Set<String> actionIds) {
        for (ConditionalActions conditionalActions : operatorStrategy.getConditionalActions()) {
            for (String actionId : conditionalActions.getActionIds()) {
                if (!actionIds.contains(actionId)) {
                    return Optional.of(actionId);
                }
            }
        }
        return Optional.empty();
    }

    private static void throwMissingOperatorStrategyContingency(OperatorStrategy operatorStrategy) {
        throw new PowsyblException("Operator strategy '" + operatorStrategy.getId() + "' is associated to contingency '"
                + operatorStrategy.getContingencyContext().getContingencyId() + "' but this contingency is not present in the list");

    }

    private static void throwMissingOperatorStrategyAction(OperatorStrategy operatorStrategy, String actionId) {
        throw new PowsyblException("Operator strategy '" + operatorStrategy.getId() + "' is associated to action '"
                + actionId + "' but this action is not present in the list");
    }

    public static Map<String, List<Indexed<OperatorStrategy>>> indexByContingencyId(List<PropagatedContingency> propagatedContingencies,
                                                                                    List<OperatorStrategy> operatorStrategies,
                                                                                    Map<String, Action> actionsById,
                                                                                    boolean checkOperatorStrategies) {

        Set<String> contingencyIds = propagatedContingencies.stream().map(propagatedContingency -> propagatedContingency.getContingency().getId()).collect(Collectors.toSet());
        Map<String, List<Indexed<OperatorStrategy>>> operatorStrategiesByContingencyId = new HashMap<>();
        Set<String> actionIds = actionsById.keySet();
        for (int i = 0; i < operatorStrategies.size(); i++) {
            OperatorStrategy operatorStrategy = operatorStrategies.get(i);
            Indexed<OperatorStrategy> indexedOperatorStrategy = new Indexed<>(i, operatorStrategy);
            if (hasValidContingency(operatorStrategy, contingencyIds)) {
                if (checkOperatorStrategies) {
                    findMissingActionId(operatorStrategy, actionIds)
                            .ifPresent(id -> throwMissingOperatorStrategyAction(operatorStrategy, id));
                }
                switch (operatorStrategy.getContingencyContext().getContextType()) {
                    case ALL, ONLY_CONTINGENCIES -> {
                        for (String contingencyId : contingencyIds) {
                            operatorStrategiesByContingencyId.computeIfAbsent(contingencyId, key -> new ArrayList<>())
                                    .add(indexedOperatorStrategy);
                        }
                    }
                    case SPECIFIC ->
                        operatorStrategiesByContingencyId.computeIfAbsent(operatorStrategy.getContingencyContext().getContingencyId(), key -> new ArrayList<>())
                                .add(indexedOperatorStrategy);
                    case NONE -> {
                        // nothing to do
                    }
                }
            } else {
                if (checkOperatorStrategies) {
                    throwMissingOperatorStrategyContingency(operatorStrategy);
                }
            }
        }
        return operatorStrategiesByContingencyId;
    }

    public static Set<Action> getNeededActions(Map<String, List<Indexed<OperatorStrategy>>> operatorStrategiesByContingencyId,
                                               Map<String, Action> actionsById) {
        return operatorStrategiesByContingencyId.entrySet().stream().flatMap(e -> e.getValue().stream())
                .flatMap(operatorStrategy -> operatorStrategy.value().getConditionalActions().stream()
                        .flatMap(conditionalActions -> conditionalActions.getActionIds().stream())
                .map(actionsById::get)
                .filter(Objects::nonNull)).collect(Collectors.toSet());
    }

    public static void check(List<OperatorStrategy> operatorStrategies, List<Contingency> contingencies, List<Action> actions) {
        // Check now that every operator strategy references an existing contingency. It will be impossible to do after
        // contingencies are split per partition.
        final Set<String> contingencyIds = contingencies.stream().map(Contingency::getId).collect(Collectors.toSet());
        operatorStrategies.stream()
                .filter(o -> !hasValidContingency(o, contingencyIds))
                .findAny()
                .ifPresent(OperatorStrategies::throwMissingOperatorStrategyContingency);

        // Check action ids to report exception to the main thread
        final Set<String> actionIds = actions.stream().map(Action::getId).collect(Collectors.toSet());
        operatorStrategies
                .forEach(o -> findMissingActionId(o, actionIds)
                        .ifPresent(id -> throwMissingOperatorStrategyAction(o, id)));
    }
}
