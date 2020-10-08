/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.sa;

import com.powsybl.contingency.BranchContingency;

/**
 * @author Florian Dupuy <florian.dupuy at rte-france.com>
 */
public class LfBranchContingency extends BranchContingency {

    public LfBranchContingency(String id) {
        super(id);
    }

    @Override
    public LfBranchTripping toTask() {
        return new LfBranchTripping(id, voltageLevelId);
    }

}
