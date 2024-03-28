/**
 * Copyright (c) 2024, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.dc;

import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.openloadflow.network.DisabledNetwork;
import com.powsybl.openloadflow.network.impl.PropagatedContingency;

import java.util.HashMap;
import java.util.Map;

// TODO : refactor to avoid matrices storage
public class WoodburyEngineResult {

    public static class PostContingencyWoodburyResult {
        private DenseMatrix postContingencyFlowStates;
        private DenseMatrix postContingencyStates;
        private DisabledNetwork postContingencyDisabledNetwork;

        public PostContingencyWoodburyResult(DenseMatrix postContingencyFlowStates, DenseMatrix postContingencyStates, DisabledNetwork postContingencyDisabledNetwork) {
            this.postContingencyFlowStates = postContingencyFlowStates;
            this.postContingencyStates = postContingencyStates;
            this.postContingencyDisabledNetwork = postContingencyDisabledNetwork;
        }

        public DenseMatrix getPostContingencyFlowStates() {
            return postContingencyFlowStates;
        }

        public DenseMatrix getPostContingencyStates() {
            return postContingencyStates;
        }

        public DisabledNetwork getPostContingencyDisabledNetwork() {
            return postContingencyDisabledNetwork;
        }
    }

    private double[] preContingenciesFlowStates;
    private DenseMatrix preContingenciesStates;
    private final HashMap<PropagatedContingency, PostContingencyWoodburyResult> postContingencyWoodburyResults;

    public WoodburyEngineResult() {
        postContingencyWoodburyResults = new HashMap<>();
    }

    public void setPreContingenciesFlowStates(double[] preContingenciesFlowStates) {
        this.preContingenciesFlowStates = preContingenciesFlowStates;
    }

    public double[] getPreContingenciesFlowStates() {
        return preContingenciesFlowStates;
    }

    public void setPreContingenciesStates(DenseMatrix preContingenciesStates) {
        this.preContingenciesStates = preContingenciesStates;
    }

    public DenseMatrix getPreContingenciesStates() {
        return preContingenciesStates;
    }

    public Map<PropagatedContingency, PostContingencyWoodburyResult> getPostContingencyWoodburyResults() {
        return postContingencyWoodburyResults;
    }
}
