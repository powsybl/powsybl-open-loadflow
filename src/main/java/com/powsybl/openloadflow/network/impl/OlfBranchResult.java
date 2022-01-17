/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.security.results.BranchResult;

import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class OlfBranchResult extends BranchResult {

    private final double r1;

    private final double continuousR1;

    public OlfBranchResult(String branchId, double p1, double q1, double i1, double p2, double q2, double i2,
                           double flowTransfer, double r1, double continuousR1) {
        super(branchId, p1, q1, i1, p2, q2, i2, flowTransfer);
        this.r1 = r1;
        this.continuousR1 = continuousR1;
    }

    public double getR1() {
        return r1;
    }

    public double getContinuousR1() {
        return continuousR1;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        OlfBranchResult that = (OlfBranchResult) o;
        return Double.compare(that.r1, r1) == 0 && Double.compare(that.continuousR1, continuousR1) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), r1, continuousR1);
    }
}
