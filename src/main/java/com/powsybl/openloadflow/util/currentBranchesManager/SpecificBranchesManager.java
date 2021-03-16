/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.util.currentBranchesManager;

import com.powsybl.openloadflow.network.LfBranch;

import java.util.Collection;

/**
 * @author Gael Macherel <gael.macherel at artelys.com>
 */
public class SpecificBranchesManager implements CurrentBranchesManager {
    private final Collection<LfBranch> branches;

    public SpecificBranchesManager(Collection<LfBranch> branches) {
        this.branches = branches;
    }

    public boolean shouldCreate(LfBranch lfBranch) {
        return branches.contains(lfBranch);
    }
}
