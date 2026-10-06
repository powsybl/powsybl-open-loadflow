/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.equations;

import static com.powsybl.openloadflow.network.PiModel.A2;
import static com.powsybl.openloadflow.network.PiModel.R2;

/**
 * Closed-branch current-magnitude residuals and Jacobian entries, delegated to by the closed-branch current-magnitude equation terms.
 *
 * <p>GENERATED — DO NOT EDIT. Regenerate with: python3 codegen/generate.py (see codegen/README.md).
 *
 * @author Powsybl codegen {@literal <see codegen/README.md>}
 */
@SuppressWarnings("squid:S00107")
public final class ClosedBranchCurrentMagnitudeFormulas {

    private ClosedBranchCurrentMagnitudeFormulas() {
    }

    private static double reI1(double y, double ksi, double g1, double b1, double v1, double ph1, double r1, double a1, double v2, double ph2) {
        return r1 * (-R2 * v2 * y * Math.sin(A2 - a1 + ksi + ph2) + r1 * v1 * (-b1 * Math.sin(ph1) + g1 * Math.cos(ph1) + y * Math.sin(ksi + ph1)));
    }

    private static double imI1(double y, double ksi, double g1, double b1, double v1, double ph1, double r1, double a1, double v2, double ph2) {
        return r1 * (R2 * v2 * y * Math.cos(A2 - a1 + ksi + ph2) + r1 * v1 * (b1 * Math.cos(ph1) + g1 * Math.sin(ph1) - y * Math.cos(ksi + ph1)));
    }

    public static double i1(double y, double ksi, double g1, double b1, double v1, double ph1, double r1, double a1, double v2, double ph2) {
        return Math.hypot(reI1(y, ksi, g1, b1, v1, ph1, r1, a1, v2, ph2), imI1(y, ksi, g1, b1, v1, ph1, r1, a1, v2, ph2));
    }

    private static double dreI1dv1(double y, double ksi, double g1, double b1, double ph1, double r1) {
        return r1 * r1 * (-b1 * Math.sin(ph1) + g1 * Math.cos(ph1) + y * Math.sin(ksi + ph1));
    }

    private static double dimI1dv1(double y, double ksi, double g1, double b1, double ph1, double r1) {
        return r1 * r1 * (b1 * Math.cos(ph1) + g1 * Math.sin(ph1) - y * Math.cos(ksi + ph1));
    }

    public static double di1dv1(double y, double ksi, double g1, double b1, double v1, double ph1, double r1, double a1, double v2, double ph2) {
        double re = reI1(y, ksi, g1, b1, v1, ph1, r1, a1, v2, ph2);
        double im = imI1(y, ksi, g1, b1, v1, ph1, r1, a1, v2, ph2);
        return (re * dreI1dv1(y, ksi, g1, b1, ph1, r1) + im * dimI1dv1(y, ksi, g1, b1, ph1, r1)) / Math.hypot(re, im);
    }

    private static double dreI1dv2(double y, double ksi, double r1, double a1, double ph2) {
        return -R2 * r1 * y * Math.sin(A2 - a1 + ksi + ph2);
    }

    private static double dimI1dv2(double y, double ksi, double r1, double a1, double ph2) {
        return R2 * r1 * y * Math.cos(A2 - a1 + ksi + ph2);
    }

    public static double di1dv2(double y, double ksi, double g1, double b1, double v1, double ph1, double r1, double a1, double v2, double ph2) {
        double re = reI1(y, ksi, g1, b1, v1, ph1, r1, a1, v2, ph2);
        double im = imI1(y, ksi, g1, b1, v1, ph1, r1, a1, v2, ph2);
        return (re * dreI1dv2(y, ksi, r1, a1, ph2) + im * dimI1dv2(y, ksi, r1, a1, ph2)) / Math.hypot(re, im);
    }

    private static double dreI1dph1(double y, double ksi, double g1, double b1, double v1, double ph1, double r1) {
        return r1 * r1 * v1 * (-b1 * Math.cos(ph1) - g1 * Math.sin(ph1) + y * Math.cos(ksi + ph1));
    }

    private static double dimI1dph1(double y, double ksi, double g1, double b1, double v1, double ph1, double r1) {
        return r1 * r1 * v1 * (-b1 * Math.sin(ph1) + g1 * Math.cos(ph1) + y * Math.sin(ksi + ph1));
    }

    public static double di1dph1(double y, double ksi, double g1, double b1, double v1, double ph1, double r1, double a1, double v2, double ph2) {
        double re = reI1(y, ksi, g1, b1, v1, ph1, r1, a1, v2, ph2);
        double im = imI1(y, ksi, g1, b1, v1, ph1, r1, a1, v2, ph2);
        return (re * dreI1dph1(y, ksi, g1, b1, v1, ph1, r1) + im * dimI1dph1(y, ksi, g1, b1, v1, ph1, r1)) / Math.hypot(re, im);
    }

    private static double dreI1dph2(double y, double ksi, double r1, double a1, double v2, double ph2) {
        return -R2 * r1 * v2 * y * Math.cos(A2 - a1 + ksi + ph2);
    }

    private static double dimI1dph2(double y, double ksi, double r1, double a1, double v2, double ph2) {
        return -R2 * r1 * v2 * y * Math.sin(A2 - a1 + ksi + ph2);
    }

    public static double di1dph2(double y, double ksi, double g1, double b1, double v1, double ph1, double r1, double a1, double v2, double ph2) {
        double re = reI1(y, ksi, g1, b1, v1, ph1, r1, a1, v2, ph2);
        double im = imI1(y, ksi, g1, b1, v1, ph1, r1, a1, v2, ph2);
        return (re * dreI1dph2(y, ksi, r1, a1, v2, ph2) + im * dimI1dph2(y, ksi, r1, a1, v2, ph2)) / Math.hypot(re, im);
    }

    public static double di1da1(double y, double ksi, double g1, double b1, double v1, double ph1, double r1, double a1, double v2, double ph2) {
        return -di1dph2(y, ksi, g1, b1, v1, ph1, r1, a1, v2, ph2);
    }

    private static double reI2(double y, double ksi, double g2, double b2, double v1, double ph1, double r1, double a1, double v2, double ph2) {
        return R2 * (R2 * v2 * (-b2 * Math.sin(ph2) + g2 * Math.cos(ph2) + y * Math.sin(ksi + ph2)) - r1 * v1 * y * Math.sin(-A2 + a1 + ksi + ph1));
    }

    private static double imI2(double y, double ksi, double g2, double b2, double v1, double ph1, double r1, double a1, double v2, double ph2) {
        return R2 * (R2 * v2 * (b2 * Math.cos(ph2) + g2 * Math.sin(ph2) - y * Math.cos(ksi + ph2)) + r1 * v1 * y * Math.cos(-A2 + a1 + ksi + ph1));
    }

    public static double i2(double y, double ksi, double g2, double b2, double v1, double ph1, double r1, double a1, double v2, double ph2) {
        return Math.hypot(reI2(y, ksi, g2, b2, v1, ph1, r1, a1, v2, ph2), imI2(y, ksi, g2, b2, v1, ph1, r1, a1, v2, ph2));
    }

    private static double dreI2dv1(double y, double ksi, double ph1, double r1, double a1) {
        return -R2 * r1 * y * Math.sin(-A2 + a1 + ksi + ph1);
    }

    private static double dimI2dv1(double y, double ksi, double ph1, double r1, double a1) {
        return R2 * r1 * y * Math.cos(-A2 + a1 + ksi + ph1);
    }

    public static double di2dv1(double y, double ksi, double g2, double b2, double v1, double ph1, double r1, double a1, double v2, double ph2) {
        double re = reI2(y, ksi, g2, b2, v1, ph1, r1, a1, v2, ph2);
        double im = imI2(y, ksi, g2, b2, v1, ph1, r1, a1, v2, ph2);
        return (re * dreI2dv1(y, ksi, ph1, r1, a1) + im * dimI2dv1(y, ksi, ph1, r1, a1)) / Math.hypot(re, im);
    }

    private static double dreI2dv2(double y, double ksi, double g2, double b2, double ph2) {
        return R2 * R2 * (-b2 * Math.sin(ph2) + g2 * Math.cos(ph2) + y * Math.sin(ksi + ph2));
    }

    private static double dimI2dv2(double y, double ksi, double g2, double b2, double ph2) {
        return R2 * R2 * (b2 * Math.cos(ph2) + g2 * Math.sin(ph2) - y * Math.cos(ksi + ph2));
    }

    public static double di2dv2(double y, double ksi, double g2, double b2, double v1, double ph1, double r1, double a1, double v2, double ph2) {
        double re = reI2(y, ksi, g2, b2, v1, ph1, r1, a1, v2, ph2);
        double im = imI2(y, ksi, g2, b2, v1, ph1, r1, a1, v2, ph2);
        return (re * dreI2dv2(y, ksi, g2, b2, ph2) + im * dimI2dv2(y, ksi, g2, b2, ph2)) / Math.hypot(re, im);
    }

    private static double dreI2dph1(double y, double ksi, double v1, double ph1, double r1, double a1) {
        return -R2 * r1 * v1 * y * Math.cos(-A2 + a1 + ksi + ph1);
    }

    private static double dimI2dph1(double y, double ksi, double v1, double ph1, double r1, double a1) {
        return -R2 * r1 * v1 * y * Math.sin(-A2 + a1 + ksi + ph1);
    }

    public static double di2dph1(double y, double ksi, double g2, double b2, double v1, double ph1, double r1, double a1, double v2, double ph2) {
        double re = reI2(y, ksi, g2, b2, v1, ph1, r1, a1, v2, ph2);
        double im = imI2(y, ksi, g2, b2, v1, ph1, r1, a1, v2, ph2);
        return (re * dreI2dph1(y, ksi, v1, ph1, r1, a1) + im * dimI2dph1(y, ksi, v1, ph1, r1, a1)) / Math.hypot(re, im);
    }

    private static double dreI2dph2(double y, double ksi, double g2, double b2, double v2, double ph2) {
        return R2 * R2 * v2 * (-b2 * Math.cos(ph2) - g2 * Math.sin(ph2) + y * Math.cos(ksi + ph2));
    }

    private static double dimI2dph2(double y, double ksi, double g2, double b2, double v2, double ph2) {
        return R2 * R2 * v2 * (-b2 * Math.sin(ph2) + g2 * Math.cos(ph2) + y * Math.sin(ksi + ph2));
    }

    public static double di2dph2(double y, double ksi, double g2, double b2, double v1, double ph1, double r1, double a1, double v2, double ph2) {
        double re = reI2(y, ksi, g2, b2, v1, ph1, r1, a1, v2, ph2);
        double im = imI2(y, ksi, g2, b2, v1, ph1, r1, a1, v2, ph2);
        return (re * dreI2dph2(y, ksi, g2, b2, v2, ph2) + im * dimI2dph2(y, ksi, g2, b2, v2, ph2)) / Math.hypot(re, im);
    }

    public static double di2da1(double y, double ksi, double g2, double b2, double v1, double ph1, double r1, double a1, double v2, double ph2) {
        return di2dph1(y, ksi, g2, b2, v1, ph1, r1, a1, v2, ph2);
    }
}
