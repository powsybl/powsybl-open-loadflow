/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * In LF model, an IIDM loads lost in modelled by a power shift (so a partial shift of the full LF load) and a list of
 * IIDM load ID because LF loads are made of an aggregation of IIDM loads.
 *
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class LfLoadLoss {

    private final PowerShift powerShift = new PowerShift();

    private final Set<String> lostLoadIds = new LinkedHashSet<>();

    public PowerShift getPowerShift() {
        return powerShift;
    }

    public Set<String> getLostLoadIds() {
        return lostLoadIds;
    }
}
