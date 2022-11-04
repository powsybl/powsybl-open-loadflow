/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.sensi;

import com.powsybl.openloadflow.dc.equations.DcEquationType;
import com.powsybl.openloadflow.dc.equations.DcVariableType;
import com.powsybl.openloadflow.graph.GraphConnectivity;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.impl.PropagatedContingency;

import java.util.*;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 * @author Gaël Macherel <gael.macherel@artelys.com>
 */
public class ConnectivityAnalysisResult {

    private final Map<AbstractSensitivityAnalysis.LfSensitivityFactor<DcVariableType, DcEquationType>, Double> predefinedResultsSensi = new HashMap<>();

    private final Map<AbstractSensitivityAnalysis.LfSensitivityFactor<DcVariableType, DcEquationType>, Double> predefinedResultsRef = new HashMap<>();

    private final Collection<PropagatedContingency> contingencies = new HashSet<>();

    private final Set<String> elementsToReconnect;

    private final Set<LfBus> disabledBuses;

    private final Set<LfBus> slackConnectedComponent;

    protected ConnectivityAnalysisResult(Set<String> elementsToReconnect, Collection<AbstractSensitivityAnalysis.LfSensitivityFactor<DcVariableType, DcEquationType>> factors,
                                         GraphConnectivity<LfBus, LfBranch> connectivity, LfNetwork lfNetwork) {
        this.elementsToReconnect = elementsToReconnect;
        slackConnectedComponent = connectivity.getConnectedComponent(lfNetwork.getSlackBus());
        disabledBuses = connectivity.getVerticesRemovedFromMainComponent();
        fillPredefinedResults(factors, connectivity);
    }

    private void fillPredefinedResults(Collection<AbstractSensitivityAnalysis.LfSensitivityFactor<DcVariableType, DcEquationType>> factors,
                                       GraphConnectivity<LfBus, LfBranch> connectivity) {
        Set<LfBranch> disabledBranches = connectivity.getEdgesRemovedFromMainComponent();
        for (AbstractSensitivityAnalysis.LfSensitivityFactor<DcVariableType, DcEquationType> factor : factors) {
            if (factor.getStatus() == AbstractSensitivityAnalysis.LfSensitivityFactor.Status.VALID) {
                // after a contingency, we check if the factor function and the variable are in different connected components
                boolean variableConnected = factor.isVariableConnectedToSlackComponent(disabledBuses, disabledBranches);
                boolean functionConnected = factor.isFunctionConnectedToSlackComponent(disabledBuses, disabledBranches);
                if (!variableConnected && functionConnected) {
                    // VALID_ONLY_FOR_FUNCTION status
                    predefinedResultsSensi.put(factor, 0d);
                }
                if (!variableConnected && !functionConnected) {
                    // SKIP status
                    predefinedResultsSensi.put(factor, Double.NaN);
                    predefinedResultsRef.put(factor, Double.NaN);
                }
                if (variableConnected && !functionConnected) {
                    // ZERO status
                    predefinedResultsSensi.put(factor, 0d);
                    predefinedResultsRef.put(factor, Double.NaN);
                }
            } else if (factor.getStatus() == AbstractSensitivityAnalysis.LfSensitivityFactor.Status.VALID_ONLY_FOR_FUNCTION) {
                // Sensitivity equals 0 for VALID_REFERENCE factors
                predefinedResultsSensi.put(factor, 0d);
                if (!factor.isFunctionConnectedToSlackComponent(disabledBuses, disabledBranches)) {
                    // The reference is not in the main componant of the post contingency network.
                    // Therefore, its value cannot be computed.
                    predefinedResultsRef.put(factor, Double.NaN);
                }
            } else {
                throw new IllegalStateException("Unexpected factor status: " + factor.getStatus());
            }
        }
    }

    void setSensitivityValuePredefinedResults() {
        predefinedResultsSensi.forEach(AbstractSensitivityAnalysis.LfSensitivityFactor::setSensitivityValuePredefinedResult);
    }

    void setFunctionPredefinedResults() {
        predefinedResultsRef.forEach(AbstractSensitivityAnalysis.LfSensitivityFactor::setFunctionPredefinedResult);
    }

    public Collection<PropagatedContingency> getContingencies() {
        return contingencies;
    }

    public Set<String> getElementsToReconnect() {
        return elementsToReconnect;
    }

    public Set<LfBus> getDisabledBuses() {
        return disabledBuses;
    }

    public Set<LfBus> getSlackConnectedComponent() {
        return slackConnectedComponent;
    }
}
