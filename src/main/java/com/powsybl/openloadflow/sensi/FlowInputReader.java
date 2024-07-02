package com.powsybl.openloadflow.sensi;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.dc.AbstractWoodburyEngineInputReader;
import com.powsybl.openloadflow.dc.ComputedContingencyElement;
import com.powsybl.openloadflow.dc.ConnectivityBreakAnalysis;
import com.powsybl.openloadflow.dc.DcLoadFlowContext;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.impl.PropagatedContingency;
import com.powsybl.openloadflow.network.util.ParticipatingElement;

import java.util.*;
import java.util.stream.Collectors;

import static com.powsybl.openloadflow.dc.DcLoadFlowEngine.solve;

class FlowInputReader extends AbstractWoodburyEngineInputReader {
    private final ConnectivityBreakAnalysis.ConnectivityBreakAnalysisResults connectivityData;
    private final LfNetwork lfNetwork;
    private final DcLoadFlowContext loadFlowContext;
    private final LoadFlowParameters lfParameters;
    private final OpenLoadFlowParameters lfParametersExt;
    private final List<ParticipatingElement> participatingElements;
    private final ReportNode reportNode;
    private final DenseMatrix preContingencyFlowStates;

    public FlowInputReader(ConnectivityBreakAnalysis.ConnectivityBreakAnalysisResults connectivityData, LfNetwork lfNetwork, DcLoadFlowContext loadFlowContext, LoadFlowParameters lfParameters, OpenLoadFlowParameters lfParametersExt, List<ParticipatingElement> participatingElements, ReportNode reportNode, DenseMatrix preContingencyFlowStates) {
        this.connectivityData = connectivityData;
        this.lfNetwork = lfNetwork;
        this.loadFlowContext = loadFlowContext;
        this.lfParameters = lfParameters;
        this.lfParametersExt = lfParametersExt;
        this.participatingElements = participatingElements;
        this.reportNode = reportNode;
        this.preContingencyFlowStates = preContingencyFlowStates;
    }

    Set<LfBranch> getDisabledPhaseTapChangers(PropagatedContingency contingency, Set<String> elementsToReconnect) {
        return contingency.getBranchIdsToOpen().keySet().stream()
                .filter(element -> !elementsToReconnect.contains(element))
                .map(connectivityData.contingencyElementByBranch()::get)
                .map(ComputedContingencyElement::getLfBranch)
                .filter(LfBranch::hasPhaseControllerCapability)
                .collect(Collectors.toSet());
    }

    Optional<DenseMatrix> getPreContingencyFlowRhsOverride(PropagatedContingency contingency, Set<LfBus> disabledBuses, Set<LfBranch> partialDisabledBranches,
                                                           Set<String> elementsToReconnect, List<ParticipatingElement> participatingElements) {
        if (!contingency.getGeneratorIdsToLose().isEmpty() || !contingency.getLoadIdsToLoose().isEmpty()) {
            Set<LfBranch> disabledBranches = contingency.getBranchIdsToOpen().keySet().stream().map(lfNetwork::getBranchById).collect(Collectors.toSet());
            disabledBranches.addAll(partialDisabledBranches);
            DisabledNetwork disabledNetwork = new DisabledNetwork(disabledBuses, disabledBranches);

            // if we have a contingency including the loss of a DC line or a generator or a load
            // save base state for later restoration
            NetworkState networkState = NetworkState.save(lfNetwork);
            LfContingency lfContingency = contingency.toLfContingency(lfNetwork).orElse(null);
            List<ParticipatingElement> newParticipatingElements = participatingElements;
            if (lfContingency != null) {
                lfContingency.apply(lfParameters.getBalanceType());
                // if participating elements are changed
                if (AbstractSensitivityAnalysis.isDistributedSlackOnGenerators(loadFlowContext.getParameters()) && !contingency.getGeneratorIdsToLose().isEmpty()
                        || AbstractSensitivityAnalysis.isDistributedSlackOnLoads(loadFlowContext.getParameters()) && !contingency.getLoadIdsToLoose().isEmpty()) {
                    newParticipatingElements = DcSensitivityAnalysis.getNewNormalizedParticipationFactors(loadFlowContext, lfParametersExt, lfContingency, newParticipatingElements);
                }
            }
            DenseMatrix preContingencyFlowRhsOverride = DcSensitivityAnalysis.getPreContingencyFlowRhs(loadFlowContext, newParticipatingElements, disabledNetwork);
            networkState.restore();
            return Optional.of(preContingencyFlowRhsOverride);
        } else {
            // in case a phase tap changer is lost, flow rhs must be updated
            Set<LfBranch> disabledPhaseTapChangers = getDisabledPhaseTapChangers(contingency, elementsToReconnect);
            if (!disabledPhaseTapChangers.isEmpty()) {
                return Optional.of(DcSensitivityAnalysis.getPreContingencyFlowRhs(loadFlowContext, participatingElements,
                        new DisabledNetwork(disabledBuses, disabledPhaseTapChangers)));
            } else {
                // no override is needed
                return Optional.empty();
            }
        }
    }

    @Override
    public void process(Handler handler) {

        Map<String, ComputedContingencyElement> contingencyElementByBranch = connectivityData.contingencyElementByBranch();
        for (ConnectivityBreakAnalysis.ConnectivityAnalysisResult connectivityAnalysisResult : connectivityData.connectivityAnalysisResults()) {
            List<ParticipatingElement> participatingElementsForThisConnectivity = participatingElements;
            Set<LfBus> disabledBuses = connectivityAnalysisResult.getDisabledBuses();

            // as we are processing contingencies with connectivity break, we have to reset active power flow of a hvdc line
            // if one bus of the line is lost.
            processHvdcLinesWithDisconnection(loadFlowContext, disabledBuses, connectivityAnalysisResult);

            // we need to recompute the rhs because the connectivity changed
            boolean rhsChanged = hasRhsChangedDueToDisabledSlackBus(lfParameters, disabledBuses, participatingElementsForThisConnectivity);
            if (rhsChanged) {
                participatingElementsForThisConnectivity = new ArrayList<>(lfParameters.isDistributedSlack()
                        ? AbstractSensitivityAnalysis.getParticipatingElements(connectivityAnalysisResult.getSlackConnectedComponent(), lfParameters.getBalanceType(), lfParametersExt) // will also be used to recompute the loadflow
                        : Collections.emptyList());
            }

            DisabledNetwork disabledNetwork = new DisabledNetwork(disabledBuses, Collections.emptySet());
            DenseMatrix preContingencyStatesOverrideConnectivityBreak = DcSensitivityAnalysis.getPreContingencyFlowRhs(loadFlowContext, participatingElementsForThisConnectivity, disabledNetwork);
            // compute pre-contingency states values override
            solve(preContingencyStatesOverrideConnectivityBreak, loadFlowContext.getJacobianMatrix(), reportNode);

            Set<String> elementsToReconnect = connectivityAnalysisResult.getElementsToReconnect();
            Set<LfBranch> partialDisabledBranches = connectivityAnalysisResult.getPartialDisabledBranches();
            for (PropagatedContingency contingency : connectivityAnalysisResult.getContingencies()) {
                Collection<ComputedContingencyElement> contingencyElements = contingency.getBranchIdsToOpen().keySet().stream()
                        .filter(element -> !elementsToReconnect.contains(element))
                        .map(contingencyElementByBranch::get)
                        .toList();
                Optional<DenseMatrix> preContingencyStatesOverride = getPreContingencyFlowRhsOverride(contingency, disabledBuses, partialDisabledBranches,
                        elementsToReconnect, participatingElementsForThisConnectivity);
                preContingencyStatesOverride.ifPresentOrElse(override -> {
                    // compute pre-contingency states values override
                    solve(override, loadFlowContext.getJacobianMatrix(), reportNode);
                    handler.onContingency(contingency, contingencyElements, override);
                }, () -> handler.onContingency(contingency, contingencyElements, preContingencyStatesOverrideConnectivityBreak));
            }
        }

        for (PropagatedContingency contingency : connectivityData.nonBreakingConnectivityContingencies()) {
            Collection<ComputedContingencyElement> contingencyElements = contingency.getBranchIdsToOpen().keySet().stream()
                    .map(contingencyElementByBranch::get)
                    .toList();
            Optional<DenseMatrix> preContingencyStatesOverride = getPreContingencyFlowRhsOverride(contingency, Collections.emptySet(), Collections.emptySet(), Collections.emptySet(), participatingElements);
            preContingencyStatesOverride.ifPresentOrElse(override -> {
                // compute pre-contingency states values override
                solve(override, loadFlowContext.getJacobianMatrix(), reportNode);
                handler.onContingency(contingency, contingencyElements, override);
            }, () -> handler.onContingency(contingency, contingencyElements, preContingencyFlowStates));
        }
    }
}
