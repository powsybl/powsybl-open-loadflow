/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.equations;

import static com.powsybl.openloadflow.network.PiModel.R2;

/**
 * Closed-branch AC flow residuals and Jacobian entries, shared by the scalar equation-term path and the vectorized AcNetworkVector loop.
 *
 * <p>GENERATED — DO NOT EDIT. Regenerate with: python3 codegen/generate.py (see codegen/README.md).
 *
 * @author Powsybl codegen {@literal <see codegen/README.md>}
 */
@SuppressWarnings("squid:S00107")
public final class ClosedBranchFormulas {

    private ClosedBranchFormulas() {
    }

    public static double p1(double y, double sinKsi, double g1, double v1, double r1, double v2, double sinTheta) {
        return r1 * v1 * (-R2 * sinTheta * v2 * y + g1 * r1 * v1 + r1 * sinKsi * v1 * y);
    }

    public static double dp1dv1(double y, double sinKsi, double g1, double v1, double r1, double v2, double sinTheta) {
        return r1 * v1 * (g1 * r1 + r1 * sinKsi * y) + r1 * (-R2 * sinTheta * v2 * y + g1 * r1 * v1 + r1 * sinKsi * v1 * y);
    }

    public static double dp1dv2(double y, double v1, double r1, double sinTheta) {
        return -R2 * r1 * sinTheta * v1 * y;
    }

    public static double dp1dph1(double y, double v1, double r1, double v2, double cosTheta) {
        return R2 * cosTheta * r1 * v1 * v2 * y;
    }

    public static double dp1dph2(double y, double v1, double r1, double v2, double cosTheta) {
        return -R2 * cosTheta * r1 * v1 * v2 * y;
    }

    public static double dp1da1(double y, double v1, double r1, double v2, double cosTheta) {
        return R2 * cosTheta * r1 * v1 * v2 * y;
    }

    public static double dp1dr1(double y, double sinKsi, double g1, double v1, double r1, double v2, double sinTheta) {
        return r1 * v1 * (g1 * v1 + sinKsi * v1 * y) + v1 * (-R2 * sinTheta * v2 * y + g1 * r1 * v1 + r1 * sinKsi * v1 * y);
    }

    public static double q1(double y, double cosKsi, double b1, double v1, double r1, double v2, double cosTheta) {
        return r1 * v1 * (-R2 * cosTheta * v2 * y - b1 * r1 * v1 + cosKsi * r1 * v1 * y);
    }

    public static double dq1dv1(double y, double cosKsi, double b1, double v1, double r1, double v2, double cosTheta) {
        return r1 * v1 * (-b1 * r1 + cosKsi * r1 * y) + r1 * (-R2 * cosTheta * v2 * y - b1 * r1 * v1 + cosKsi * r1 * v1 * y);
    }

    public static double dq1dv2(double y, double v1, double r1, double cosTheta) {
        return -R2 * cosTheta * r1 * v1 * y;
    }

    public static double dq1dph1(double y, double v1, double r1, double v2, double sinTheta) {
        return -R2 * r1 * sinTheta * v1 * v2 * y;
    }

    public static double dq1dph2(double y, double v1, double r1, double v2, double sinTheta) {
        return R2 * r1 * sinTheta * v1 * v2 * y;
    }

    public static double dq1da1(double y, double v1, double r1, double v2, double sinTheta) {
        return -R2 * r1 * sinTheta * v1 * v2 * y;
    }

    public static double dq1dr1(double y, double cosKsi, double b1, double v1, double r1, double v2, double cosTheta) {
        return r1 * v1 * (-b1 * v1 + cosKsi * v1 * y) + v1 * (-R2 * cosTheta * v2 * y - b1 * r1 * v1 + cosKsi * r1 * v1 * y);
    }

    public static double p2(double y, double sinKsi, double g2, double v1, double r1, double v2, double sinTheta) {
        return R2 * v2 * (R2 * g2 * v2 + R2 * sinKsi * v2 * y - r1 * sinTheta * v1 * y);
    }

    public static double dp2dv1(double y, double r1, double v2, double sinTheta) {
        return -R2 * r1 * sinTheta * v2 * y;
    }

    public static double dp2dv2(double y, double sinKsi, double g2, double v1, double r1, double v2, double sinTheta) {
        return R2 * v2 * (R2 * g2 + R2 * sinKsi * y) + R2 * (R2 * g2 * v2 + R2 * sinKsi * v2 * y - r1 * sinTheta * v1 * y);
    }

    public static double dp2dph1(double y, double v1, double r1, double v2, double cosTheta) {
        return -R2 * cosTheta * r1 * v1 * v2 * y;
    }

    public static double dp2dph2(double y, double v1, double r1, double v2, double cosTheta) {
        return R2 * cosTheta * r1 * v1 * v2 * y;
    }

    public static double dp2da1(double y, double v1, double r1, double v2, double cosTheta) {
        return -R2 * cosTheta * r1 * v1 * v2 * y;
    }

    public static double dp2dr1(double y, double v1, double v2, double sinTheta) {
        return -R2 * sinTheta * v1 * v2 * y;
    }

    public static double q2(double y, double cosKsi, double b2, double v1, double r1, double v2, double cosTheta) {
        return R2 * v2 * (-R2 * b2 * v2 + R2 * cosKsi * v2 * y - cosTheta * r1 * v1 * y);
    }

    public static double dq2dv1(double y, double r1, double v2, double cosTheta) {
        return -R2 * cosTheta * r1 * v2 * y;
    }

    public static double dq2dv2(double y, double cosKsi, double b2, double v1, double r1, double v2, double cosTheta) {
        return R2 * v2 * (-R2 * b2 + R2 * cosKsi * y) + R2 * (-R2 * b2 * v2 + R2 * cosKsi * v2 * y - cosTheta * r1 * v1 * y);
    }

    public static double dq2dph1(double y, double v1, double r1, double v2, double sinTheta) {
        return R2 * r1 * sinTheta * v1 * v2 * y;
    }

    public static double dq2dph2(double y, double v1, double r1, double v2, double sinTheta) {
        return -R2 * r1 * sinTheta * v1 * v2 * y;
    }

    public static double dq2da1(double y, double v1, double r1, double v2, double sinTheta) {
        return R2 * r1 * sinTheta * v1 * v2 * y;
    }

    public static double dq2dr1(double y, double v1, double v2, double cosTheta) {
        return -R2 * cosTheta * v1 * v2 * y;
    }
}
