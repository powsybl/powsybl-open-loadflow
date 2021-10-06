/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.Injection;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public interface LfNetworkLoaderPostProcessor {

    void busAdded(Identifiable identifiable, LfBus lfBus);

    void branchAdded(Identifiable identifiable, LfBranch lfBranch);

    void injectionAdded(Injection injection, LfBus lfBus);
}
