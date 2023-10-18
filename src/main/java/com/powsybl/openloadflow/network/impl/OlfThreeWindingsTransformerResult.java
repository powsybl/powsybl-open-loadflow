/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.commons.extensions.AbstractExtension;
import com.powsybl.security.results.ThreeWindingsTransformerResult;

/**
 * @author Bertrand Rix <bertrand.rix at artelys.com>
 */
public class OlfThreeWindingsTransformerResult extends AbstractExtension<ThreeWindingsTransformerResult> {

    private final double v1;

    private final double v2;

    private final double v3;

    private final double angle1;

    private final double angle2;

    private final double angle3;

    public OlfThreeWindingsTransformerResult(double v1, double v2, double v3, double angle1, double angle2, double angle3) {
        this.v1 = v1;
        this.v2 = v2;
        this.v3 = v3;
        this.angle1 = angle1;
        this.angle2 = angle2;
        this.angle3 = angle3;
    }

    public double getV1() {
        return v1;
    }

    public double getV2() {
        return v2;
    }

    public double getV3() {
        return v3;
    }

    public double getAngle1() {
        return angle1;
    }

    public double getAngle2() {
        return angle2;
    }

    public double getAngle3() {
        return angle3;
    }

    @Override
    public String getName() {
        return "OlfThreeWindingsTransformerResult";
    }
}
