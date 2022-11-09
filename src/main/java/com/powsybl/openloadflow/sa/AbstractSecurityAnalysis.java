/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.sa;

import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.reporter.Reporter;
import com.powsybl.computation.CompletableFutureTask;
import com.powsybl.computation.ComputationManager;
import com.powsybl.contingency.ContingenciesProvider;
import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.openloadflow.ac.nr.NewtonRaphsonStatus;
import com.powsybl.openloadflow.graph.GraphConnectivityFactory;
import com.powsybl.openloadflow.network.LfAction;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.impl.PropagatedContingency;
import com.powsybl.security.*;
import com.powsybl.security.action.Action;
import com.powsybl.security.condition.AllViolationCondition;
import com.powsybl.security.condition.AnyViolationCondition;
import com.powsybl.security.condition.AtLeastOneViolationCondition;
import com.powsybl.security.condition.TrueCondition;
import com.powsybl.security.monitor.StateMonitor;
import com.powsybl.security.monitor.StateMonitorIndex;
import com.powsybl.security.strategy.OperatorStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public abstract class AbstractSecurityAnalysis {

    protected static final Logger LOGGER = LoggerFactory.getLogger(AbstractSecurityAnalysis.class);

    protected final Network network;

    protected final MatrixFactory matrixFactory;

    protected final GraphConnectivityFactory<LfBus, LfBranch> connectivityFactory;

    protected final StateMonitorIndex monitorIndex;

    protected final Reporter reporter;

    protected AbstractSecurityAnalysis(Network network, MatrixFactory matrixFactory, GraphConnectivityFactory<LfBus, LfBranch> connectivityFactory,
                                       List<StateMonitor> stateMonitors, Reporter reporter) {
        this.network = Objects.requireNonNull(network);
        this.matrixFactory = Objects.requireNonNull(matrixFactory);
        this.connectivityFactory = Objects.requireNonNull(connectivityFactory);
        this.monitorIndex = new StateMonitorIndex(stateMonitors);
        this.reporter = Objects.requireNonNull(reporter);
    }

    public CompletableFuture<SecurityAnalysisReport> run(String workingVariantId, SecurityAnalysisParameters securityAnalysisParameters,
                                                         ContingenciesProvider contingenciesProvider, ComputationManager computationManager,
                                                         List<OperatorStrategy> operatorStrategies, List<Action> actions) {
        Objects.requireNonNull(workingVariantId);
        Objects.requireNonNull(securityAnalysisParameters);
        Objects.requireNonNull(contingenciesProvider);
        return CompletableFutureTask.runAsync(() -> {
            network.getVariantManager().setWorkingVariant(workingVariantId);
            return runSync(workingVariantId, securityAnalysisParameters, contingenciesProvider, computationManager, operatorStrategies, actions);
        }, computationManager.getExecutor());
    }

    abstract SecurityAnalysisReport runSync(String workingVariantId, SecurityAnalysisParameters securityAnalysisParameters, ContingenciesProvider contingenciesProvider,
                                            ComputationManager computationManager, List<OperatorStrategy> operatorStrategies, List<Action> actions);

    public static PostContingencyComputationStatus postContingencyStatusFromNRStatus(NewtonRaphsonStatus status) {
        switch (status) {
            case CONVERGED:
                return PostContingencyComputationStatus.CONVERGED;
            case MAX_ITERATION_REACHED:
                return PostContingencyComputationStatus.MAX_ITERATION_REACHED;
            case SOLVER_FAILED:
                return PostContingencyComputationStatus.SOLVER_FAILED;
            case NO_CALCULATION:
                return PostContingencyComputationStatus.NO_IMPACT;
            case UNREALISTIC_STATE:
                return PostContingencyComputationStatus.FAILED;
            default:
                throw new PowsyblException("Unsupported Newton Raphson status : " + status);
        }
    }

    public static LoadFlowResult.ComponentResult.Status loadFlowResultStatusFromNRStatus(NewtonRaphsonStatus status) {
        switch (status) {
            case CONVERGED:
                return LoadFlowResult.ComponentResult.Status.CONVERGED;
            case MAX_ITERATION_REACHED:
                return LoadFlowResult.ComponentResult.Status.MAX_ITERATION_REACHED;
            case SOLVER_FAILED:
                return LoadFlowResult.ComponentResult.Status.SOLVER_FAILED;
            case NO_CALCULATION:
            case UNREALISTIC_STATE:
                return LoadFlowResult.ComponentResult.Status.FAILED;
            default:
                throw new PowsyblException("Unsupported Newton Raphson status : " + status);
        }
    }

    protected static Map<String, LfAction> createLfActions(LfNetwork network, List<Action> actions) {
        return actions.stream().collect(Collectors.toMap(Action::getId, action -> new LfAction(action, network)));
    }

    protected static Map<String, List<OperatorStrategy>> indexOperatorStrategiesByContingencyId(List<PropagatedContingency> propagatedContingencies,
                                                                                              List<OperatorStrategy> operatorStrategies) {
        Set<String> contingencyIds = propagatedContingencies.stream().map(propagatedContingency -> propagatedContingency.getContingency().getId()).collect(Collectors.toSet());
        Map<String, List<OperatorStrategy>> operatorStrategiesByContingencyId = new HashMap<>();
        for (OperatorStrategy operatorStrategy : operatorStrategies) {
            if (contingencyIds.contains(operatorStrategy.getContingencyId())) {
                operatorStrategiesByContingencyId.computeIfAbsent(operatorStrategy.getContingencyId(), key -> new ArrayList<>()).add(operatorStrategy);
            } else {
                throw new PowsyblException("An operator strategy associated to contingency '" + operatorStrategy.getContingencyId() +
                        "' but this contingency is not present in the list of contingencies");
            }
        }
        return operatorStrategiesByContingencyId;
    }

    protected boolean checkCondition(OperatorStrategy operatorStrategy, LimitViolationsResult limitViolationsResult) {
        Set<String> limitViolationEquipmentIds = limitViolationsResult.getLimitViolations().stream()
                .map(LimitViolation::getSubjectId)
                .collect(Collectors.toSet());
        switch (operatorStrategy.getCondition().getType()) {
            case TrueCondition.NAME:
                return true;
            case AnyViolationCondition.NAME:
                return !limitViolationEquipmentIds.isEmpty();
            case AtLeastOneViolationCondition.NAME: {
                AtLeastOneViolationCondition atLeastCondition = (AtLeastOneViolationCondition) operatorStrategy.getCondition();
                Set<String> commonEquipmentIds = atLeastCondition.getViolationIds().stream()
                        .distinct()
                        .filter(limitViolationEquipmentIds::contains)
                        .collect(Collectors.toSet());
                return !commonEquipmentIds.isEmpty();
            }
            case AllViolationCondition.NAME: {
                AllViolationCondition allCondition = (AllViolationCondition) operatorStrategy.getCondition();
                Set<String> commonEquipmentIds = allCondition.getViolationIds().stream()
                        .distinct()
                        .filter(limitViolationEquipmentIds::contains)
                        .collect(Collectors.toSet());
                return commonEquipmentIds.equals(new HashSet<>(allCondition.getViolationIds()));
            }
            default:
                throw new UnsupportedOperationException("Unsupported condition type: " + operatorStrategy.getCondition().getType());
        }
    }
}
