/**
 * Copyright (c) 2024, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.dc;

import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.openloadflow.network.impl.PropagatedContingency;

import java.util.HashMap;
import java.util.Optional;

/**$
 * @author Pierre Arvy {@literal <pierre.arvy at artelys.com>}
 */
public class WoodburyEngineRhsModifications {

    private final HashMap<PropagatedContingency, DenseMatrix> newFlowRhsByPropagatedContingency;
    private final HashMap<ConnectivityBreakAnalysis.ConnectivityAnalysisResult, DenseMatrix> newFlowRhsForAConnectivity;
    private final HashMap<PropagatedContingency, DenseMatrix> newInjectionRhsByPropagatedContingency;
    private final HashMap<ConnectivityBreakAnalysis.ConnectivityAnalysisResult, DenseMatrix> newInjectionRhsForAConnectivity;

    public WoodburyEngineRhsModifications() {
        this.newFlowRhsByPropagatedContingency = new HashMap<>();
        this.newFlowRhsForAConnectivity = new HashMap<>();
        this.newInjectionRhsByPropagatedContingency = new HashMap<>();
        this.newInjectionRhsForAConnectivity = new HashMap<>();
    }

    public void addFlowRhsOverrideByPropagatedContingency(PropagatedContingency contingency, DenseMatrix flowRhs) {
        this.newFlowRhsByPropagatedContingency.put(contingency, flowRhs);
    }

    public Optional<DenseMatrix> getFlowRhsOverrideByPropagatedContingency(PropagatedContingency contingency) {
        return Optional.ofNullable(this.newFlowRhsByPropagatedContingency.get(contingency));
    }

    public void addFlowRhsOverrideForAConnectivity(ConnectivityBreakAnalysis.ConnectivityAnalysisResult connectivityAnalysisResult, DenseMatrix flowRhs) {
        this.newFlowRhsForAConnectivity.put(connectivityAnalysisResult, flowRhs);
    }

    public Optional<DenseMatrix> getFlowRhsOverrideForAConnectivity(ConnectivityBreakAnalysis.ConnectivityAnalysisResult connectivityAnalysisResult) {
        return Optional.ofNullable(this.newFlowRhsForAConnectivity.get(connectivityAnalysisResult));
    }

    public void addInjectionRhsOverrideByPropagatedContingency(PropagatedContingency contingency, DenseMatrix injectionRhs) {
        this.newInjectionRhsByPropagatedContingency.put(contingency, injectionRhs);
    }

    public Optional<DenseMatrix> getInjectionRhsOverrideByPropagatedContingency(PropagatedContingency contingency) {
        return Optional.ofNullable(this.newInjectionRhsByPropagatedContingency.get(contingency));
    }

    public void addInjectionRhsOverrideForAConnectivity(ConnectivityBreakAnalysis.ConnectivityAnalysisResult connectivityAnalysisResult, DenseMatrix injectionRhs) {
        this.newInjectionRhsForAConnectivity.put(connectivityAnalysisResult, injectionRhs);
    }

    public Optional<DenseMatrix> getInjectionRhsOverrideForAConnectivity(ConnectivityBreakAnalysis.ConnectivityAnalysisResult connectivityAnalysisResult) {
        return Optional.ofNullable(this.newInjectionRhsForAConnectivity.get(connectivityAnalysisResult));
    }

}
