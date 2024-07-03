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
 * Provides the values to apply Woodbury formula in {@link WoodburyEngine}.
 * It is composed of contingency elements and pre-contingency angle states values.
 * These values depend on the contingency due to its potential modifications of the network.
 *
 * For example, in a {@link com.powsybl.openloadflow.sensi.DcSensitivityAnalysis}, some elements of a GLSK may not be in the connected
 * component anymore due to contingencies. Because the connectivity changed, the network is changed, and so its pre-contingency states values.
 *
 * @author Pierre Arvy {@literal <pierre.arvy at artelys.com>}
 * @author Bertrand Rix {@literal <bertrand.rix at artelys.com>}
 */
public interface WoodburyEngineInputReader {

    interface Handler {
        void onContingency(PropagatedContingency contingency, Collection<ComputedContingencyElement> contingencyElements, DenseMatrix preContingencyStates);
    }

    void process(Handler handler);
}
