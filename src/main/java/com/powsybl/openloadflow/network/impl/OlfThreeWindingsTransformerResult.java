package com.powsybl.openloadflow.network.impl;

import com.powsybl.commons.extensions.AbstractExtension;
import com.powsybl.security.results.ThreeWindingsTransformerResult;

public class OlfThreeWindingsTransformerResult extends AbstractExtension<ThreeWindingsTransformerResult> {

    private final double v1;

    private final double v2;

    private final double v3;

    private final double angle1;

    private final double angle2;

    private final double angle3;

    OlfThreeWindingsTransformerResult(double v1, double v2, double v3, double angle1, double angle2, double angle3) {
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
