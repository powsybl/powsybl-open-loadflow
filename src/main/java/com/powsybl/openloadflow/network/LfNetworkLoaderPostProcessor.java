/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network;

import org.apache.commons.compress.utils.Lists;

import java.util.List;
import java.util.ServiceLoader;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public interface LfNetworkLoaderPostProcessor {

    enum LoadingPolicy {
        ALWAYS,
        SELECTION
    }

    static List<LfNetworkLoaderPostProcessor> findAll() {
        return Lists.newArrayList(ServiceLoader.load(LfNetworkLoaderPostProcessor.class, LfNetworkLoaderPostProcessor.class.getClassLoader()).iterator());
    }

    String getName();

    LoadingPolicy getLoadingPolicy();

    void onBusAdded(Object element, LfBus lfBus);

    void onBranchAdded(Object element, LfBranch lfBranch);

    void onInjectionAdded(Object element, LfBus lfBus);
}
