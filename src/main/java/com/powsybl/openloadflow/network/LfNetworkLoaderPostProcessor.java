/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network;

import com.powsybl.commons.util.ServiceLoaderCache;

import java.util.Collections;
import java.util.List;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public interface LfNetworkLoaderPostProcessor {

    enum LoadingPolicy {
        ALWAYS,
        SELECTION
    }

    ServiceLoaderCache<LfNetworkLoaderPostProcessor> SERVICE_LOADER_CACHE = new ServiceLoaderCache<>(LfNetworkLoaderPostProcessor.class);

    static List<LfNetworkLoaderPostProcessor> findAll() {
        return Collections.unmodifiableList(SERVICE_LOADER_CACHE.getServices());
    }

    String getName();

    LoadingPolicy getLoadingPolicy();

    void onBusAdded(Object element, LfBus lfBus);

    void onBranchAdded(Object element, LfBranch lfBranch);

    void onInjectionAdded(Object element, LfBus lfBus);

    void onAreaAdded(Object element, LfArea lfArea);

    void onLfNetworkLoaded(Object element, LfNetwork lfNetwork);
}
