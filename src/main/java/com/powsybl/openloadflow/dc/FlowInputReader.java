/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * Copyright (c) 2024, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.dc;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.impl.PropagatedContingency;
import com.powsybl.openloadflow.network.util.ParticipatingElement;

import java.util.*;
import java.util.stream.Collectors;

import static com.powsybl.openloadflow.dc.DcLoadFlowEngine.solve;
import static com.powsybl.openloadflow.dc.DcUtils.*;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 * @author Pierre Arvy {@literal <pierre.arvy at artelys.com>}
 */
public class FlowInputReader extends AbstractWoodburyEngineInputReader {

    public FlowInputReader(ConnectivityBreakAnalysis.ConnectivityBreakAnalysisResults connectivityData, DcLoadFlowContext loadFlowContext,
                           LoadFlowParameters lfParameters, OpenLoadFlowParameters lfParametersExt, List<ParticipatingElement> participatingElements,
                           ReportNode reportNode, DenseMatrix preContingencyFlowStates) {
        super(loadFlowContext, lfParameters, lfParametersExt, participatingElements, preContingencyFlowStates, connectivityData, reportNode);
    }

    Set<LfBranch> getDisabledPhaseTapChangers(PropagatedContingency contingency, Set<String> elementsToReconnect) {
        return contingency.getBranchIdsToOpen().keySet().stream()
                .filter(element -> !elementsToReconnect.contains(element))
                .map(connectivityData.contingencyElementByBranch()::get)
                .map(ComputedContingencyElement::getLfBranch)
                .filter(LfBranch::hasPhaseControllerCapability)
                .collect(Collectors.toSet());
    }

    @Override
    protected Optional<DenseMatrix> getPreContingencyRhsOverride(PropagatedContingency contingency, Set<LfBus> disabledBuses, Set<LfBranch> partialDisabledBranches,
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
                if (isDistributedSlackOnGenerators(loadFlowContext.getParameters()) && !contingency.getGeneratorIdsToLose().isEmpty()
                        || isDistributedSlackOnLoads(loadFlowContext.getParameters()) && !contingency.getLoadIdsToLoose().isEmpty()) {
                    newParticipatingElements = getNewNormalizedParticipationFactors(loadFlowContext, lfParametersExt, lfContingency, newParticipatingElements);
                }
            }
            DenseMatrix preContingencyFlowRhsOverride = getPreContingencyFlowRhs(loadFlowContext, newParticipatingElements, disabledNetwork);
            networkState.restore();
            return Optional.of(preContingencyFlowRhsOverride);
        } else {
            // in case a phase tap changer is lost, flow rhs must be updated
            Set<LfBranch> disabledPhaseTapChangers = getDisabledPhaseTapChangers(contingency, elementsToReconnect);
            if (!disabledPhaseTapChangers.isEmpty()) {
                return Optional.of(getPreContingencyFlowRhs(loadFlowContext, participatingElements,
                        new DisabledNetwork(disabledBuses, disabledPhaseTapChangers)));
            } else {
                // no override is needed
                return Optional.empty();
            }
        }
    }

    @Override
    protected Overrides getOverrides(ConnectivityBreakAnalysis.ConnectivityAnalysisResult connectivityAnalysisResult,
                                     Set<LfBus> disabledBuses, List<ParticipatingElement> participatingElements) {
        List<ParticipatingElement> participatingElementsOverride = participatingElements;
        // we need to recompute the rhs because the connectivity changed
        boolean rhsChanged = hasRhsChangedDueToDisabledSlackBus(lfParameters, disabledBuses, participatingElementsOverride);
        if (rhsChanged) {
            participatingElementsOverride = new ArrayList<>(lfParameters.isDistributedSlack()
                    ? getParticipatingElements(connectivityAnalysisResult.getSlackConnectedComponent(), lfParameters.getBalanceType(), lfParametersExt) // will also be used to recompute the loadflow
                    : Collections.emptyList());
        }

        DisabledNetwork disabledNetwork = new DisabledNetwork(disabledBuses, Collections.emptySet());
        DenseMatrix preContingencyStatesOverride = getPreContingencyFlowRhs(loadFlowContext, participatingElementsOverride, disabledNetwork);
        // compute pre-contingency states values override
        solve(preContingencyStatesOverride, loadFlowContext.getJacobianMatrix(), reportNode);
        return new Overrides(participatingElementsOverride, preContingencyStatesOverride);
    }
}
