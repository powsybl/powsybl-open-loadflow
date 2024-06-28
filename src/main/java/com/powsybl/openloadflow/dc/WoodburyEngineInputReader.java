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

import java.util.Collection;

/**
 * @author Pierre Arvy {@literal <pierre.arvy at artelys.com>}
 * @author Bertrand Rix {@literal <bertrand.rix at artelys.com>}
 */
public interface WoodburyEngineInputReader {

    interface Handler {
        void onContingency(PropagatedContingency contingency, Collection<ComputedContingencyElement> contingencyElements, DenseMatrix preContingencyStates);
    }

    void process(Handler handler);
}
