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

    private final HashMap<PropagatedContingency, DenseMatrix> rhsOverrideByPropagatedContingency;
    private final HashMap<ConnectivityBreakAnalysis.ConnectivityAnalysisResult, DenseMatrix> rhsOverrideForAConnectivity;

    public WoodburyEngineRhsModifications() {
        this.rhsOverrideByPropagatedContingency = new HashMap<>();
        this.rhsOverrideForAConnectivity = new HashMap<>();
    }

    public void addRhsOverrideByPropagatedContingency(PropagatedContingency contingency, DenseMatrix flowRhs) {
        this.rhsOverrideByPropagatedContingency.put(contingency, flowRhs);
    }

    public Optional<DenseMatrix> getRhsOverrideByPropagatedContingency(PropagatedContingency contingency) {
        return Optional.ofNullable(this.rhsOverrideByPropagatedContingency.get(contingency));
    }

    public void addRhsOverrideForAConnectivity(ConnectivityBreakAnalysis.ConnectivityAnalysisResult connectivityAnalysisResult, DenseMatrix flowRhs) {
        this.rhsOverrideForAConnectivity.put(connectivityAnalysisResult, flowRhs);
    }

    public Optional<DenseMatrix> getRhsOverrideForAConnectivity(ConnectivityBreakAnalysis.ConnectivityAnalysisResult connectivityAnalysisResult) {
        return Optional.ofNullable(this.rhsOverrideForAConnectivity.get(connectivityAnalysisResult));
    }

}
