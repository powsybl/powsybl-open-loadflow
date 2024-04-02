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
public class WoodburyEngineRhs {

    private final DenseMatrix initialInjectionRhs;
    private final double[] initialFlowRhs;

    private final HashMap<PropagatedContingency, DenseMatrix> newInjectionRhsByPropagatedContingency;
    private final HashMap<PropagatedContingency, double[]> newFlowRhsByPropagatedContingency;

    private final HashMap<WoodburyEngine.ConnectivityAnalysisResult, DenseMatrix> newInjectionRhsForAConnectivity;
    private final HashMap<WoodburyEngine.ConnectivityAnalysisResult, double[]> newFlowRhsForAConnectivity;

    public WoodburyEngineRhs(DenseMatrix initialInjectionRhs, double[] initialFlowRhs) {
        this.initialInjectionRhs = initialInjectionRhs;
        this.initialFlowRhs = initialFlowRhs;
        this.newFlowRhsByPropagatedContingency = new HashMap<>();
        this.newFlowRhsForAConnectivity = new HashMap<>();
        this.newInjectionRhsByPropagatedContingency = new HashMap<>();
        this.newInjectionRhsForAConnectivity = new HashMap<>();
    }

    public DenseMatrix getInitialInjectionRhs() {
        return initialInjectionRhs;
    }

    public double[] getInitialFlowRhs() {
        return initialFlowRhs;
    }

    public HashMap<PropagatedContingency, double[]> getNewFlowRhsByPropagatedContingency() {
        return newFlowRhsByPropagatedContingency;
    }

    public HashMap<WoodburyEngine.ConnectivityAnalysisResult, double[]> getNewFlowRhsForAConnectivity() {
        return newFlowRhsForAConnectivity;
    }

    public Map<PropagatedContingency, DenseMatrix> getNewInjectionRhsByPropagatedContingency() {
        return newInjectionRhsByPropagatedContingency;
    }

    public Map<WoodburyEngine.ConnectivityAnalysisResult, DenseMatrix> getNewInjectionRhsForAConnectivity() {
        return newInjectionRhsForAConnectivity;
    }
}
