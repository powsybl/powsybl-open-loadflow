/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.commons.extensions.AbstractExtension;
import com.powsybl.security.results.BranchResult;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class OlfBranchResult extends AbstractExtension<BranchResult> {

    private final double r1;

    private final double continuousR1;

    public OlfBranchResult(double r1, double continuousR1) {
        this.r1 = r1;
        this.continuousR1 = continuousR1;
    }

    @Override
    public String getName() {
        return "OlfBranchResult";
    }

    public double getR1() {
        return r1;
    }

    public double getContinuousR1() {
        return continuousR1;
    }
}
