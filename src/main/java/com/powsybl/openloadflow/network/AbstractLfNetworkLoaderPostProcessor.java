/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public abstract class AbstractLfNetworkLoaderPostProcessor implements LfNetworkLoaderPostProcessor {

    @Override
    public LoadingPolicy getLoadingPolicy() {
        return LoadingPolicy.ALWAYS;
    }

    @Override
    public void onBusAdded(Object element, LfBus lfBus) {
        // to implement
    }

    @Override
    public void onBranchAdded(Object element, LfBranch lfBranch) {
        // to implement
    }

    @Override
    public void onInjectionAdded(Object element, LfBus lfBus) {
        // to implement
    }
}
