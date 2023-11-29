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
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class OlfBranchResult extends AbstractExtension<BranchResult> {

    private final double r1;

    private final double continuousR1;

    private final double v1;

    private final double v2;

    private final double angle1;

    private final double angle2;

    public OlfBranchResult(double r1, double continuousR1, double v1, double v2, double angle1, double angle2) {
        this.r1 = r1;
        this.continuousR1 = continuousR1;
        this.v1 = v1;
        this.v2 = v2;
        this.angle1 = angle1;
        this.angle2 = angle2;
    }

    @Override
    public String getName() {
        return "OlfBranchResult";
    }

    public double getR1() {
        return r1;
    }

    public double getV1() {
        return v1;
    }

    public double getV2() {
        return v2;
    }

    public double getAngle1() {
        return angle1;
    }

    public double getAngle2() {
        return angle2;
    }

    public double getContinuousR1() {
        return continuousR1;
    }
}
