/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network;

import com.powsybl.openloadflow.network.impl.LfNetworkCopier;

/**
 * Copyable element of a LfNetwork that can be called when copying the LfNetwork
 * with the {@link LfNetworkCopier}
 *
 * @author Sylvestre Prabakaran {@literal <sylvestre.prabakaran at rte-france.com>}
 */
public interface LfCopyable<E, P> {

    /**
     * Create a flat copy of the object in the given copyNetwork. The object should be added manually then
     *
     * @param parentElement   The parent to copy the object in (usually a LfNetwork, or a LfBus)
     * @return the copied object (after being created, it should then be added manually in the parent element)
     */
    E copy(P parentElement);
}
