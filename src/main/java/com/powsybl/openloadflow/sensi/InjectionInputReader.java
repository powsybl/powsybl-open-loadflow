package com.powsybl.openloadflow.sensi;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.dc.*;
import com.powsybl.openloadflow.dc.equations.DcEquationType;
import com.powsybl.openloadflow.dc.equations.DcVariableType;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.impl.PropagatedContingency;
import com.powsybl.openloadflow.network.util.ParticipatingElement;
import com.powsybl.sensitivity.SensitivityAnalysisResult;
import com.powsybl.sensitivity.SensitivityResultWriter;

import java.util.*;
import java.util.stream.Collectors;

import static com.powsybl.openloadflow.dc.DcLoadFlowEngine.solve;

class InjectionInputReader extends AbstractWoodburyEngineInputReader {

    private final LfNetwork lfNetwork;
    private final Map<PropagatedContingency, DisabledNetwork> disabledNetworkByPropagatedContingency;
    private final SensitivityResultWriter resultWriter;
    private final DcLoadFlowContext loadFlowContext;
    private final List<ParticipatingElement> participatingElements;
    private final OpenLoadFlowParameters lfParametersExt;
    private final AbstractSensitivityAnalysis.SensitivityFactorGroupList<DcVariableType, DcEquationType> factorGroups;
    private final ConnectivityBreakAnalysis.ConnectivityBreakAnalysisResults connectivityData;
    private final LoadFlowParameters lfParameters;
    private final ReportNode reportNode;
    private final DenseMatrix preContingencyInjectionStates;

    public InjectionInputReader(LfNetwork lfNetwork, Map<PropagatedContingency, DisabledNetwork> disabledNetworkByPropagatedContingency, SensitivityResultWriter resultWriter, DcLoadFlowContext loadFlowContext, List<ParticipatingElement> participatingElements, OpenLoadFlowParameters lfParametersExt, AbstractSensitivityAnalysis.SensitivityFactorGroupList<DcVariableType, DcEquationType> factorGroups, ConnectivityBreakAnalysis.ConnectivityBreakAnalysisResults connectivityData, LoadFlowParameters lfParameters, ReportNode reportNode, DenseMatrix preContingencyInjectionStates) {
        this.lfNetwork = lfNetwork;
        this.disabledNetworkByPropagatedContingency = disabledNetworkByPropagatedContingency;
        this.resultWriter = resultWriter;
        this.loadFlowContext = loadFlowContext;
        this.participatingElements = participatingElements;
        this.lfParametersExt = lfParametersExt;
        this.factorGroups = factorGroups;
        this.connectivityData = connectivityData;
        this.lfParameters = lfParameters;
        this.reportNode = reportNode;
        this.preContingencyInjectionStates = preContingencyInjectionStates;
    }

    /**
     * @return True if the disabled buses change the slack distribution or the GLSK. False otherwise.
     */
    boolean hasRhsChangedDueToConnectivityBreak(LoadFlowParameters lfParameters, AbstractSensitivityAnalysis.SensitivityFactorGroupList<DcVariableType, DcEquationType> factorGroups,
                                                Set<LfBus> disabledBuses, List<ParticipatingElement> participatingElements) {
        boolean rhsChanged = hasRhsChangedDueToDisabledSlackBus(lfParameters, disabledBuses, participatingElements);
        if (factorGroups.hasMultiVariables()) {
            // some elements of the GLSK may not be in the connected component anymore, we recompute the injections
            rhsChanged |= AbstractSensitivityAnalysis.rescaleGlsk(factorGroups, disabledBuses);
        }
        return rhsChanged;
    }

    Optional<DenseMatrix> getPreContingencyInjectionRhsOverride(PropagatedContingency contingency, Set<LfBus> disabledBuses, Set<LfBranch> partialDisabledBranches) {
        DenseMatrix preContingencyStatesOverride = null;

        Set<LfBranch> disabledBranches = contingency.getBranchIdsToOpen().keySet().stream().map(lfNetwork::getBranchById).collect(Collectors.toSet());
        disabledBranches.addAll(partialDisabledBranches);
        DisabledNetwork disabledNetwork = new DisabledNetwork(disabledBuses, disabledBranches);
        disabledNetworkByPropagatedContingency.put(contingency, disabledNetwork);

        if (contingency.getGeneratorIdsToLose().isEmpty() && contingency.getLoadIdsToLoose().isEmpty()) {
            resultWriter.writeContingencyStatus(contingency.getIndex(), contingency.hasNoImpact() ? SensitivityAnalysisResult.Status.NO_IMPACT : SensitivityAnalysisResult.Status.SUCCESS);
        } else {
            // if we have a contingency including the loss of a DC line or a generator or a load
            // save base state for later restoration after each contingency
            NetworkState networkState = NetworkState.save(lfNetwork);
            LfContingency lfContingency = contingency.toLfContingency(lfNetwork).orElse(null);
            if (lfContingency != null) {
                DcLoadFlowParameters dcLoadFlowParameters = loadFlowContext.getParameters();
                lfContingency.apply(dcLoadFlowParameters.getBalanceType());
                List<ParticipatingElement> modifiedParticipatingElements = participatingElements;
                boolean rhsChanged = AbstractSensitivityAnalysis.isDistributedSlackOnGenerators(loadFlowContext.getParameters()) && !contingency.getGeneratorIdsToLose().isEmpty()
                        || AbstractSensitivityAnalysis.isDistributedSlackOnLoads(loadFlowContext.getParameters()) && !contingency.getLoadIdsToLoose().isEmpty();
                if (rhsChanged) {
                    modifiedParticipatingElements = DcSensitivityAnalysis.getNewNormalizedParticipationFactors(loadFlowContext, lfParametersExt, lfContingency, modifiedParticipatingElements);
                }
                if (factorGroups.hasMultiVariables()) {
                    Set<LfBus> impactedBuses = lfContingency.getLoadAndGeneratorBuses();
                    rhsChanged |= AbstractSensitivityAnalysis.rescaleGlsk(factorGroups, impactedBuses);
                }
                if (rhsChanged) {
                    preContingencyStatesOverride = DcSensitivityAnalysis.getPreContingencyInjectionRhs(loadFlowContext, factorGroups, modifiedParticipatingElements);
                }
                // write contingency status
                resultWriter.writeContingencyStatus(contingency.getIndex(), SensitivityAnalysisResult.Status.SUCCESS);
            } else {
                // write contingency status
                resultWriter.writeContingencyStatus(contingency.getIndex(), SensitivityAnalysisResult.Status.NO_IMPACT);
            }
            networkState.restore();
        }

        return Optional.ofNullable(preContingencyStatesOverride);
    }

    @Override
    public void process(Handler handler) {

        Map<String, ComputedContingencyElement> contingencyElementByBranch = connectivityData.contingencyElementByBranch();
        for (ConnectivityBreakAnalysis.ConnectivityAnalysisResult connectivityAnalysisResult : connectivityData.connectivityAnalysisResults()) {
            Set<LfBus> disabledBuses = connectivityAnalysisResult.getDisabledBuses();
            List<ParticipatingElement> participatingElementsForThisConnectivity = participatingElements;

            // as we are processing contingencies with connectivity break, we have to reset active power flow of a hvdc line
            // if one bus of the line is lost.
            processHvdcLinesWithDisconnection(loadFlowContext, disabledBuses, connectivityAnalysisResult);

            // we need to recompute the injection rhs because the connectivity changed
            DenseMatrix preContingencyStatesOverrideConnectivityBreak;
            boolean rhsChanged = hasRhsChangedDueToConnectivityBreak(lfParameters, factorGroups, disabledBuses, participatingElementsForThisConnectivity);
            if (rhsChanged) {
                participatingElementsForThisConnectivity = new ArrayList<>(lfParameters.isDistributedSlack()
                        ? AbstractSensitivityAnalysis.getParticipatingElements(connectivityAnalysisResult.getSlackConnectedComponent(), lfParameters.getBalanceType(), lfParametersExt) // will also be used to recompute the loadflow
                        : Collections.emptyList());
                preContingencyStatesOverrideConnectivityBreak = DcSensitivityAnalysis.getPreContingencyInjectionRhs(loadFlowContext, factorGroups, participatingElementsForThisConnectivity);
                // compute pre-contingency states values override
                solve(preContingencyStatesOverrideConnectivityBreak, loadFlowContext.getJacobianMatrix(), reportNode);
            } else {
                preContingencyStatesOverrideConnectivityBreak = preContingencyInjectionStates;
            }

            Set<String> elementsToReconnect = connectivityAnalysisResult.getElementsToReconnect();
            for (PropagatedContingency contingency : connectivityAnalysisResult.getContingencies()) {
                Optional<DenseMatrix> preContingencyInjectionStatesOverride = getPreContingencyInjectionRhsOverride(contingency, disabledBuses, connectivityAnalysisResult.getPartialDisabledBranches());
                Collection<ComputedContingencyElement> contingencyElements = contingency.getBranchIdsToOpen().keySet().stream()
                        .filter(element -> !elementsToReconnect.contains(element))
                        .map(contingencyElementByBranch::get)
                        .toList();
                preContingencyInjectionStatesOverride.ifPresentOrElse(override -> {
                    // compute pre-contingency states values override
                    solve(override, loadFlowContext.getJacobianMatrix(), reportNode);
                    handler.onContingency(contingency, contingencyElements, override);
                }, () -> handler.onContingency(contingency, contingencyElements, preContingencyStatesOverrideConnectivityBreak));
            }
        }

        for (PropagatedContingency contingency : connectivityData.nonBreakingConnectivityContingencies()) {
            Optional<DenseMatrix> preContingencyStatesOverride = getPreContingencyInjectionRhsOverride(contingency, Collections.emptySet(), Collections.emptySet());
            Collection<ComputedContingencyElement> contingencyElements = contingency.getBranchIdsToOpen().keySet().stream()
                    .map(contingencyElementByBranch::get)
                    .toList();
            preContingencyStatesOverride.ifPresentOrElse(override -> {
                // compute pre-contingency states values override
                solve(override, loadFlowContext.getJacobianMatrix(), reportNode);
                handler.onContingency(contingency, contingencyElements, override);
            }, () -> handler.onContingency(contingency, contingencyElements, preContingencyInjectionStates));
        }
    }
}
