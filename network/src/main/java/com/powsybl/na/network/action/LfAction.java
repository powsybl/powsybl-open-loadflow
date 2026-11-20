/**
 * Copyright (c) 2025, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.na.network.action;

import com.powsybl.na.network.LfContingency;
import com.powsybl.na.network.LfNetwork;
import com.powsybl.na.network.LfNetworkParameters;

/**
 * @author Bertrand Rix {@literal <bertrand.rix at artelys.com>}
 */
public interface LfAction {

    String getId();

    String getType();

    boolean isValid();

    boolean apply(LfNetwork network, LfContingency contingency, LfNetworkParameters networkParameters);
}
