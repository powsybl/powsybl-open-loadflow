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

/**
 * @author Pierre Arvy {@literal <pierre.arvy at artelys.com>}
 */
public class WoodburyEngineResult {

    // TODO add documentation
    public record WoodburyStates(DenseMatrix flowStates, DenseMatrix injectionStates) {
    }

    private final WoodburyStates preContingencyStates;
    private final HashMap<PropagatedContingency, WoodburyStates> postContingencyStates;

    public WoodburyEngineResult(WoodburyStates preContingencyStates,
                                Map<PropagatedContingency, WoodburyStates> postContingencyStates) {
        this.preContingencyStates = preContingencyStates;
        this.postContingencyStates = new HashMap<>(postContingencyStates);
    }

    public WoodburyStates getPreContingencyStates() {
        return preContingencyStates;
    }

    public WoodburyStates getPostContingencyWoodburyStates(PropagatedContingency contingency) {
        return postContingencyStates.get(contingency);
    }
}
