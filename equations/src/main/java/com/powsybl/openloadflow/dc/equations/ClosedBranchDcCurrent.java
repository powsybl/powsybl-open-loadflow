/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.dc.equations;

import com.powsybl.iidm.network.TwoSides;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.util.Evaluable;

import java.util.Objects;

/**
 * @author Anne Tilloy {@literal <anne.tilloy at rte-france.com>}
 */
public final class ClosedBranchDcCurrent implements Evaluable {

    private final LfBranch branch;

    private final TwoSides side;

    private final double dcPowerFactor;

    public ClosedBranchDcCurrent(LfBranch branch, TwoSides side, double dcPowerFactor) {
        this.branch = Objects.requireNonNull(branch);
        this.side = Objects.requireNonNull(side);
        this.dcPowerFactor = dcPowerFactor;
    }

    public double eval() {
        double p = side == TwoSides.ONE ? branch.getP1().eval() : branch.getP2().eval();
        return Math.abs(p) / dcPowerFactor;
    }
}
