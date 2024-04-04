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

/**
 * @author Pierre Arvy {@literal <pierre.arvy at artelys.com>}
 */
public class WoodburyEngineResult {

    public record WoodburyStates(DenseMatrix flowStates, DenseMatrix injectionStates) {
    }

    private double[] preContingencyFlowStates;
    private DenseMatrix preContingencyInjectionStates;
    private final HashMap<PropagatedContingency, WoodburyStates> postContingenciesWoodburyStates;

    public WoodburyEngineResult() {
        postContingenciesWoodburyStates = new HashMap<>();
    }

    public double[] getPreContingencyFlowStates() {
        return preContingencyFlowStates;
    }

    public void setPreContingencyFlowStates(double[] preContingencyFlowStates) {
        this.preContingencyFlowStates = preContingencyFlowStates;
    }

    public DenseMatrix getPreContingencyInjectionStates() {
        return preContingencyInjectionStates;
    }

    public void setPreContingencyInjectionStates(DenseMatrix preContingencyInjectionStates) {
        this.preContingencyInjectionStates = preContingencyInjectionStates;
    }

    public void addPostContingencyWoodburyStates(PropagatedContingency contingency, WoodburyStates postContingencyWoodburyStates) {
        postContingenciesWoodburyStates.put(contingency, postContingencyWoodburyStates);
    }

    public WoodburyStates getPostContingencyWoodburyStates(PropagatedContingency contingency) {
        return postContingenciesWoodburyStates.get(contingency);
    }
}
