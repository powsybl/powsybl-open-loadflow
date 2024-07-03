/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * Copyright (c) 2024, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
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
import static com.powsybl.openloadflow.dc.DcUtils.*;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 * @author Pierre Arvy {@literal <pierre.arvy at artelys.com>}
 */
class InjectionInputReader extends AbstractWoodburyEngineInputReader {

    private final Map<PropagatedContingency, DisabledNetwork> disabledNetworkByPropagatedContingency;
    private final SensitivityResultWriter resultWriter;
    private final AbstractSensitivityAnalysis.SensitivityFactorGroupList<DcVariableType, DcEquationType> factorGroups;

    public InjectionInputReader(Map<PropagatedContingency, DisabledNetwork> disabledNetworkByPropagatedContingency, SensitivityResultWriter resultWriter, DcLoadFlowContext loadFlowContext,
                                List<ParticipatingElement> participatingElements, OpenLoadFlowParameters lfParametersExt, AbstractSensitivityAnalysis.SensitivityFactorGroupList<DcVariableType,
            DcEquationType> factorGroups, ConnectivityBreakAnalysis.ConnectivityBreakAnalysisResults connectivityData, LoadFlowParameters lfParameters, ReportNode reportNode,
                                DenseMatrix preContingencyInjectionStates) {
        super(loadFlowContext, lfParameters, lfParametersExt, participatingElements, preContingencyInjectionStates, connectivityData, reportNode);
        this.disabledNetworkByPropagatedContingency = disabledNetworkByPropagatedContingency;
        this.resultWriter = resultWriter;
        this.factorGroups = factorGroups;
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

    @Override
    protected Optional<DenseMatrix> getPreContingencyRhsOverride(PropagatedContingency contingency, Set<LfBus> disabledBuses, Set<LfBranch> partialDisabledBranches,
                                                                 Set<String> elementsToReconnect, List<ParticipatingElement> participatingElements) {
        // Note that elementsToReconnect and participatingElements are not used for injections.
        return getPreContingencyRhsOverride(contingency, disabledBuses, partialDisabledBranches);
    }

    @Override
    protected Optional<DenseMatrix> getPreContingencyRhsOverride(PropagatedContingency contingency, Set<LfBus> disabledBuses, Set<LfBranch> partialDisabledBranches) {
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
                boolean rhsChanged = isDistributedSlackOnGenerators(loadFlowContext.getParameters()) && !contingency.getGeneratorIdsToLose().isEmpty()
                        || isDistributedSlackOnLoads(loadFlowContext.getParameters()) && !contingency.getLoadIdsToLoose().isEmpty();
                if (rhsChanged) {
                    modifiedParticipatingElements = getNewNormalizedParticipationFactors(loadFlowContext, lfParametersExt, lfContingency, modifiedParticipatingElements);
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
    protected Overrides getOverrides(ConnectivityBreakAnalysis.ConnectivityAnalysisResult connectivityAnalysisResult,
                                     Set<LfBus> disabledBuses, List<ParticipatingElement> participatingElements) {
        DenseMatrix preContingencyStatesOverride = preContingencyStates;
        List<ParticipatingElement> participatingElementsOverride = participatingElements;
        boolean rhsChanged = hasRhsChangedDueToConnectivityBreak(lfParameters, factorGroups, disabledBuses, participatingElementsOverride);
        if (rhsChanged) {
            participatingElementsOverride = new ArrayList<>(lfParameters.isDistributedSlack()
                    ? getParticipatingElements(connectivityAnalysisResult.getSlackConnectedComponent(), lfParameters.getBalanceType(), lfParametersExt) // will also be used to recompute the loadflow
                    : Collections.emptyList());
            preContingencyStatesOverride = DcSensitivityAnalysis.getPreContingencyInjectionRhs(loadFlowContext, factorGroups, participatingElementsOverride);
            // compute pre-contingency states values override
            solve(preContingencyStatesOverride, loadFlowContext.getJacobianMatrix(), reportNode);
        }
        return new Overrides(participatingElementsOverride, preContingencyStatesOverride);
    }
}
