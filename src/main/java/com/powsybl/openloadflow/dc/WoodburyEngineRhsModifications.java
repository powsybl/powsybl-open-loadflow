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

/**
 * Store the overrides of a right hand side, due to contingencies modifying it.
 * These overrides are used in {@link WoodburyEngine}, to compute post-contingency states.
 *
 * For example, in a {@link com.powsybl.openloadflow.sensi.DcSensitivityAnalysis}, some elements of a GLSK may not be in the connected
 * component anymore due to contingencies. Because the connectivity changed, the right hand side should be changed.
 * Instead of overwriting the right hand side, on override is stored.
 *
 * @author Pierre Arvy {@literal <pierre.arvy at artelys.com>}
 */
public class WoodburyEngineRhsModifications {

    // rhs overrides for propagated contingencies
    private final HashMap<PropagatedContingency, DenseMatrix> rhsOverrideByPropagatedContingency;
    // rhs overrides for a connectivity analysis result (group of contingencies breaking connectivity)
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
