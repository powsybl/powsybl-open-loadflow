/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.dc;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.impl.Networks;
import com.powsybl.openloadflow.network.impl.PropagatedContingency;
import com.powsybl.openloadflow.network.util.ParticipatingElement;

import java.util.*;

import static com.powsybl.openloadflow.dc.DcLoadFlowEngine.solve;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 * @author Gaël Macherel {@literal <gael.macherel@artelys.com>}
 */
public abstract class AbstractWoodburyEngineInputReader implements WoodburyEngineInputReader {

    protected final ConnectivityBreakAnalysis.ConnectivityBreakAnalysisResults connectivityData;
    protected final LfNetwork lfNetwork;
    protected final DcLoadFlowContext loadFlowContext;
    protected final LoadFlowParameters lfParameters;
    protected final OpenLoadFlowParameters lfParametersExt;
    protected final List<ParticipatingElement> participatingElements;
    protected final ReportNode reportNode;
    protected final DenseMatrix preContingencyStates; // flow or injection

    protected AbstractWoodburyEngineInputReader(ConnectivityBreakAnalysis.ConnectivityBreakAnalysisResults connectivityData, DcLoadFlowContext loadFlowContext, LoadFlowParameters lfParameters, OpenLoadFlowParameters lfParametersExt, List<ParticipatingElement> participatingElements, ReportNode reportNode, DenseMatrix preContingencyStates) {
        this.connectivityData = connectivityData;
        this.loadFlowContext = loadFlowContext;
        this.lfNetwork = loadFlowContext.getNetwork();
        this.lfParameters = lfParameters;
        this.lfParametersExt = lfParametersExt;
        this.participatingElements = participatingElements;
        this.reportNode = reportNode;
        this.preContingencyStates = preContingencyStates;
    }

    /**
     * @return True if the disabled buses change the slack distribution.
     */
    protected boolean hasRhsChangedDueToDisabledSlackBus(LoadFlowParameters lfParameters, Set<LfBus> disabledBuses, List<ParticipatingElement> participatingElements) {
        return lfParameters.isDistributedSlack() && participatingElements.stream().anyMatch(element -> disabledBuses.contains(element.getLfBus()));
    }

    protected void processHvdcLinesWithDisconnection(DcLoadFlowContext loadFlowContext, Set<LfBus> disabledBuses, ConnectivityBreakAnalysis.ConnectivityAnalysisResult connectivityAnalysisResult) {
        for (LfHvdc hvdc : loadFlowContext.getNetwork().getHvdcs()) {
            if (Networks.isIsolatedBusForHvdc(hvdc.getBus1(), disabledBuses) ^ Networks.isIsolatedBusForHvdc(hvdc.getBus2(), disabledBuses)) {
                connectivityAnalysisResult.getContingencies().forEach(contingency -> {
                    contingency.getGeneratorIdsToLose().add(hvdc.getConverterStation1().getId());
                    contingency.getGeneratorIdsToLose().add(hvdc.getConverterStation2().getId());
                });
            }
        }
    }

    protected abstract Optional<DenseMatrix> getPreContingencyRhsOverride(PropagatedContingency contingency, Set<LfBus> disabledBuses, Set<LfBranch> partialDisabledBranches,
                                                                          Set<String> elementsToReconnect, List<ParticipatingElement> participatingElements);

    protected Optional<DenseMatrix> getPreContingencyRhsOverride(PropagatedContingency contingency, Set<LfBus> disabledBuses, Set<LfBranch> partialDisabledBranches) {
        return getPreContingencyRhsOverride(contingency, disabledBuses, partialDisabledBranches, Collections.emptySet(), Collections.emptyList());
    }

    protected void extracted2(Handler handler, ConnectivityBreakAnalysis.ConnectivityAnalysisResult connectivityAnalysisResult, Map<String, ComputedContingencyElement> contingencyElementByBranch, Set<LfBus> disabledBuses,
                              List<ParticipatingElement> participatingElementsForThisConnectivity, DenseMatrix preContingencyStatesOverrideConnectivityBreak) {
        Set<String> elementsToReconnect = connectivityAnalysisResult.getElementsToReconnect();
        for (PropagatedContingency contingency : connectivityAnalysisResult.getContingencies()) {
            Collection<ComputedContingencyElement> contingencyElements = contingency.getBranchIdsToOpen().keySet().stream()
                    .filter(element -> !elementsToReconnect.contains(element))
                    .map(contingencyElementByBranch::get)
                    .toList();
            Optional<DenseMatrix> preContingencyStatesOverride = getPreContingencyRhsOverride(contingency, disabledBuses, connectivityAnalysisResult.getPartialDisabledBranches(),
                    elementsToReconnect, participatingElementsForThisConnectivity); // specific to flow
            preContingencyStatesOverride.ifPresentOrElse(override -> {
                // compute pre-contingency states values override
                solve(override, loadFlowContext.getJacobianMatrix(), reportNode);
                handler.onContingency(contingency, contingencyElements, override);
            }, () -> handler.onContingency(contingency, contingencyElements, preContingencyStatesOverrideConnectivityBreak));
        }
    }

    protected void extracted(Handler handler, Map<String, ComputedContingencyElement> contingencyElementByBranch) {
        for (PropagatedContingency contingency : connectivityData.nonBreakingConnectivityContingencies()) {
            Optional<DenseMatrix> preContingencyStatesOverride = getPreContingencyRhsOverride(contingency, Collections.emptySet(), Collections.emptySet());
            Collection<ComputedContingencyElement> contingencyElements = contingency.getBranchIdsToOpen().keySet().stream()
                    .map(contingencyElementByBranch::get)
                    .toList();
            preContingencyStatesOverride.ifPresentOrElse(override -> {
                // compute pre-contingency states values override
                solve(override, loadFlowContext.getJacobianMatrix(), reportNode);
                handler.onContingency(contingency, contingencyElements, override);
            }, () -> handler.onContingency(contingency, contingencyElements, preContingencyStates));
        }
    }
}
