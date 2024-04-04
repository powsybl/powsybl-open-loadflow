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
import java.util.Map;

/**$
 * @author Pierre Arvy {@literal <pierre.arvy at artelys.com>}
 */
public class WoodburyEngineRhsModifications {

    private final HashMap<PropagatedContingency, double[]> newFlowRhsByPropagatedContingency;
    private final HashMap<WoodburyEngine.ConnectivityAnalysisResult, double[]> newFlowRhsForAConnectivity;
    private final HashMap<PropagatedContingency, DenseMatrix> newInjectionRhsByPropagatedContingency;
    private final HashMap<WoodburyEngine.ConnectivityAnalysisResult, DenseMatrix> newInjectionRhsForAConnectivity;

    public WoodburyEngineRhsModifications() {
        this.newFlowRhsByPropagatedContingency = new HashMap<>();
        this.newFlowRhsForAConnectivity = new HashMap<>();
        this.newInjectionRhsByPropagatedContingency = new HashMap<>();
        this.newInjectionRhsForAConnectivity = new HashMap<>();
    }

    public Map<PropagatedContingency, double[]> getNewFlowRhsByPropagatedContingency() {
        return newFlowRhsByPropagatedContingency;
    }

    public Map<WoodburyEngine.ConnectivityAnalysisResult, double[]> getNewFlowRhsForAConnectivity() {
        return newFlowRhsForAConnectivity;
    }

    public Map<PropagatedContingency, DenseMatrix> getNewInjectionRhsByPropagatedContingency() {
        return newInjectionRhsByPropagatedContingency;
    }

    public Map<WoodburyEngine.ConnectivityAnalysisResult, DenseMatrix> getNewInjectionRhsForAConnectivity() {
        return newInjectionRhsForAConnectivity;
    }
}
