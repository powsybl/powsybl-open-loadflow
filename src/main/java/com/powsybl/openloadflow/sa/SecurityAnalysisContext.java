package com.powsybl.openloadflow.sa;

import com.powsybl.action.Action;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.openloadflow.lf.AbstractLoadFlowParameters;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.impl.PropagatedContingency;
import com.powsybl.openloadflow.sa.ContingencyActivePowerLossDistribution;
import com.powsybl.openloadflow.util.Indexed;
import com.powsybl.security.SecurityAnalysisParameters;
import com.powsybl.security.limitreduction.LimitReduction;
import com.powsybl.contingency.strategy.OperatorStrategy;

import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import org.apache.commons.lang3.tuple.Pair;

public class SecurityAnalysisContext<P extends AbstractLoadFlowParameters<P>> {

    private final List<LfNetwork> networksToSimulate;
    private final P parameters;
    private final SecurityAnalysisParameters securityAnalysisParameters;
    private final Map<Pair<Integer, Integer>, Queue<PropagatedContingency>> queueContingenciesByComponent;
    private final Map<String, List<Indexed<OperatorStrategy>>> operatorStrategiesByContingencyId;
    private final Set<Action> neededActions;
    private final List<LimitReduction> limitReductions;
    private final ContingencyActivePowerLossDistribution contingencyActivePowerLossDistribution;
    private final LoadFlowParameters lfParameters;

    public SecurityAnalysisContext(List<LfNetwork> networksToSimulate,
                                   P parameters,
                                   SecurityAnalysisParameters securityAnalysisParameters,
                                   Map<Pair<Integer, Integer>, Queue<PropagatedContingency>> queueContingenciesByComponent,
                                   Map<String, List<Indexed<OperatorStrategy>>> operatorStrategiesByContingencyId,
                                   Set<Action> neededActions,
                                   List<LimitReduction> limitReductions,
                                   ContingencyActivePowerLossDistribution contingencyActivePowerLossDistribution,
                                   LoadFlowParameters lfParameters) {
        this.networksToSimulate = networksToSimulate;
        this.parameters = parameters;
        this.securityAnalysisParameters = securityAnalysisParameters;
        this.queueContingenciesByComponent = queueContingenciesByComponent;
        this.operatorStrategiesByContingencyId = operatorStrategiesByContingencyId;
        this.neededActions = neededActions;
        this.limitReductions = limitReductions;
        this.contingencyActivePowerLossDistribution = contingencyActivePowerLossDistribution;
        this.lfParameters = lfParameters;
    }

    public List<LfNetwork> getNetworksToSimulate() {
        return networksToSimulate;
    }

    public P getParameters() {
        return parameters;
    }

    public SecurityAnalysisParameters getSecurityAnalysisParameters() {
        return securityAnalysisParameters;
    }

    public Map<Pair<Integer, Integer>, Queue<PropagatedContingency>> getQueueContingenciesByComponent() {
        return queueContingenciesByComponent;
    }

    public Map<String, List<Indexed<OperatorStrategy>>> getOperatorStrategiesById() {
        return operatorStrategiesByContingencyId;
    }

    public Set<Action> getNeededActions() {
        return neededActions;
    }

    public List<LimitReduction> getLimitReductions() {
        return limitReductions;
    }

    public ContingencyActivePowerLossDistribution getContingencyActivePowerLossDistribution() {
        return contingencyActivePowerLossDistribution;
    }

    public LoadFlowParameters getLfParameters() {
        return lfParameters;
    }
}

