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
import com.powsybl.openloadflow.network.util.ParticipatingElement;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**$
 * @author Pierre Arvy {@literal <pierre.arvy at artelys.com>}
 */
public class WoodburyEngineRhsModification {

    private final HashMap<PropagatedContingency, DenseMatrix> newInjectionVectorsByPropagatedContingency;

    private final HashMap<WoodburyEngine.ConnectivityAnalysisResult, DenseMatrix> newInjectionVectorsForAConnectivity;

    private final HashMap<PropagatedContingency, List<ParticipatingElement>> newParticipatingElementsByPropagatedContingency;

    private final HashMap<WoodburyEngine.ConnectivityAnalysisResult, List<ParticipatingElement>> newParticipantElementsForAConnectivity;

    public WoodburyEngineRhsModification() {
        this.newInjectionVectorsByPropagatedContingency = new HashMap<>();
        this.newInjectionVectorsForAConnectivity = new HashMap<>();
        this.newParticipatingElementsByPropagatedContingency = new HashMap<>();
        this.newParticipantElementsForAConnectivity = new HashMap<>();
    }

    public Map<PropagatedContingency, DenseMatrix> getNewInjectionVectorsByPropagatedContingency() {
        return newInjectionVectorsByPropagatedContingency;
    }

    public Map<PropagatedContingency, List<ParticipatingElement>> getNewParticipatingElementsByPropagatedContingency() {
        return newParticipatingElementsByPropagatedContingency;
    }

    public Map<WoodburyEngine.ConnectivityAnalysisResult, DenseMatrix> getNewInjectionVectorsForAConnectivity() {
        return newInjectionVectorsForAConnectivity;
    }

    public Map<WoodburyEngine.ConnectivityAnalysisResult, List<ParticipatingElement>> getNewParticipantElementsForAConnectivity() {
        return newParticipantElementsForAConnectivity;
    }
}
