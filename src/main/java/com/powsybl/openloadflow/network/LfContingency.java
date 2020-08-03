/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import java.util.List;
import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class LfContingency {

    private final String id;

    private final List<LfBranch> branches;

    public LfContingency(String id, List<LfBranch> branches) {
        this.id = Objects.requireNonNull(id);
        this.branches = Objects.requireNonNull(branches);
    }

    public String getId() {
        return id;
    }

    public List<LfBranch> getBranches() {
        return branches;
    }
}
