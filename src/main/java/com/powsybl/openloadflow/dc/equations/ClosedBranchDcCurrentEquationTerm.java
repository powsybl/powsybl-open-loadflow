/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.dc.equations;

import com.powsybl.iidm.network.TwoSides;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.util.Evaluable;

/**
 * @author Anne Tilloy {@literal <anne.tilloy at rte-france.com>}
 */
public final class ClosedBranchDcCurrentEquationTerm implements Evaluable {

    LfBranch branch;

    TwoSides side;

    double dcPowerFactor;

    private ClosedBranchDcCurrentEquationTerm(LfBranch branch, TwoSides side, double dcPowerFactor) {
        this.branch = branch;
        this.side = side;
        this.dcPowerFactor = dcPowerFactor;
    }

    public static ClosedBranchDcCurrentEquationTerm create(LfBranch branch, TwoSides side, double dcPowerFactor) {
        return new ClosedBranchDcCurrentEquationTerm(branch, side, dcPowerFactor);
    }

    public double eval() {
        double p = side == TwoSides.ONE ? branch.getP1().eval() : branch.getP2().eval();
        return Math.abs(p) / dcPowerFactor;
    }

    protected String getName() {
        return "dc_i_" + side.getNum();
    }
}
