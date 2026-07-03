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
 * Open-branch current-magnitude residuals and Jacobian entries, delegated to by the open-branch current-magnitude equation terms.
 *
 * <p>GENERATED — DO NOT EDIT. Regenerate with: python3 codegen/generate.py (see codegen/README.md).
 *
 * @author Powsybl codegen {@literal <see codegen/README.md>}
 */
@SuppressWarnings("squid:S00107")
public final class OpenBranchCurrentMagnitudeFormulas {

    private OpenBranchCurrentMagnitudeFormulas() {
    }

    private static double reI2(double y, double cosKsi, double sinKsi, double g1, double b1, double g2, double b2, double v2, double ph2) {
        return R2 * R2 * v2 * (-(b2 + (b1 * y * y - cosKsi * y * (b1 * b1 + g1 * g1)) / ((-b1 + cosKsi * y) * (-b1 + cosKsi * y) + (g1 + sinKsi * y) * (g1 + sinKsi * y))) * Math.sin(ph2) + (g2 + (g1 * y * y + sinKsi * y * (b1 * b1 + g1 * g1)) / ((-b1 + cosKsi * y) * (-b1 + cosKsi * y) + (g1 + sinKsi * y) * (g1 + sinKsi * y))) * Math.cos(ph2));
    }

    private static double imI2(double y, double cosKsi, double sinKsi, double g1, double b1, double g2, double b2, double v2, double ph2) {
        return R2 * R2 * v2 * ((b2 + (b1 * y * y - cosKsi * y * (b1 * b1 + g1 * g1)) / ((-b1 + cosKsi * y) * (-b1 + cosKsi * y) + (g1 + sinKsi * y) * (g1 + sinKsi * y))) * Math.cos(ph2) + (g2 + (g1 * y * y + sinKsi * y * (b1 * b1 + g1 * g1)) / ((-b1 + cosKsi * y) * (-b1 + cosKsi * y) + (g1 + sinKsi * y) * (g1 + sinKsi * y))) * Math.sin(ph2));
    }

    public static double i2(double y, double cosKsi, double sinKsi, double g1, double b1, double g2, double b2, double v2, double ph2) {
        return Math.hypot(reI2(y, cosKsi, sinKsi, g1, b1, g2, b2, v2, ph2), imI2(y, cosKsi, sinKsi, g1, b1, g2, b2, v2, ph2));
    }

    private static double dreI2dv2(double y, double cosKsi, double sinKsi, double g1, double b1, double g2, double b2, double ph2) {
        return R2 * R2 * (-(b2 + (b1 * y * y - cosKsi * y * (b1 * b1 + g1 * g1)) / ((-b1 + cosKsi * y) * (-b1 + cosKsi * y) + (g1 + sinKsi * y) * (g1 + sinKsi * y))) * Math.sin(ph2) + (g2 + (g1 * y * y + sinKsi * y * (b1 * b1 + g1 * g1)) / ((-b1 + cosKsi * y) * (-b1 + cosKsi * y) + (g1 + sinKsi * y) * (g1 + sinKsi * y))) * Math.cos(ph2));
    }

    private static double dimI2dv2(double y, double cosKsi, double sinKsi, double g1, double b1, double g2, double b2, double ph2) {
        return R2 * R2 * ((b2 + (b1 * y * y - cosKsi * y * (b1 * b1 + g1 * g1)) / ((-b1 + cosKsi * y) * (-b1 + cosKsi * y) + (g1 + sinKsi * y) * (g1 + sinKsi * y))) * Math.cos(ph2) + (g2 + (g1 * y * y + sinKsi * y * (b1 * b1 + g1 * g1)) / ((-b1 + cosKsi * y) * (-b1 + cosKsi * y) + (g1 + sinKsi * y) * (g1 + sinKsi * y))) * Math.sin(ph2));
    }

    public static double di2dv2(double y, double cosKsi, double sinKsi, double g1, double b1, double g2, double b2, double v2, double ph2) {
        double re = reI2(y, cosKsi, sinKsi, g1, b1, g2, b2, v2, ph2);
        double im = imI2(y, cosKsi, sinKsi, g1, b1, g2, b2, v2, ph2);
        return (re * dreI2dv2(y, cosKsi, sinKsi, g1, b1, g2, b2, ph2) + im * dimI2dv2(y, cosKsi, sinKsi, g1, b1, g2, b2, ph2)) / Math.hypot(re, im);
    }

    private static double reI1(double y, double cosKsi, double sinKsi, double g1, double b1, double g2, double b2, double v1, double ph1, double r1) {
        return r1 * r1 * v1 * (-(b1 + (b2 * y * y - cosKsi * y * (b2 * b2 + g2 * g2)) / ((-b2 + cosKsi * y) * (-b2 + cosKsi * y) + (g2 + sinKsi * y) * (g2 + sinKsi * y))) * Math.sin(ph1) + (g1 + (g2 * y * y + sinKsi * y * (b2 * b2 + g2 * g2)) / ((-b2 + cosKsi * y) * (-b2 + cosKsi * y) + (g2 + sinKsi * y) * (g2 + sinKsi * y))) * Math.cos(ph1));
    }

    private static double imI1(double y, double cosKsi, double sinKsi, double g1, double b1, double g2, double b2, double v1, double ph1, double r1) {
        return r1 * r1 * v1 * ((b1 + (b2 * y * y - cosKsi * y * (b2 * b2 + g2 * g2)) / ((-b2 + cosKsi * y) * (-b2 + cosKsi * y) + (g2 + sinKsi * y) * (g2 + sinKsi * y))) * Math.cos(ph1) + (g1 + (g2 * y * y + sinKsi * y * (b2 * b2 + g2 * g2)) / ((-b2 + cosKsi * y) * (-b2 + cosKsi * y) + (g2 + sinKsi * y) * (g2 + sinKsi * y))) * Math.sin(ph1));
    }

    public static double i1(double y, double cosKsi, double sinKsi, double g1, double b1, double g2, double b2, double v1, double ph1, double r1) {
        return Math.hypot(reI1(y, cosKsi, sinKsi, g1, b1, g2, b2, v1, ph1, r1), imI1(y, cosKsi, sinKsi, g1, b1, g2, b2, v1, ph1, r1));
    }

    private static double dreI1dv1(double y, double cosKsi, double sinKsi, double g1, double b1, double g2, double b2, double ph1, double r1) {
        return r1 * r1 * (-(b1 + (b2 * y * y - cosKsi * y * (b2 * b2 + g2 * g2)) / ((-b2 + cosKsi * y) * (-b2 + cosKsi * y) + (g2 + sinKsi * y) * (g2 + sinKsi * y))) * Math.sin(ph1) + (g1 + (g2 * y * y + sinKsi * y * (b2 * b2 + g2 * g2)) / ((-b2 + cosKsi * y) * (-b2 + cosKsi * y) + (g2 + sinKsi * y) * (g2 + sinKsi * y))) * Math.cos(ph1));
    }

    private static double dimI1dv1(double y, double cosKsi, double sinKsi, double g1, double b1, double g2, double b2, double ph1, double r1) {
        return r1 * r1 * ((b1 + (b2 * y * y - cosKsi * y * (b2 * b2 + g2 * g2)) / ((-b2 + cosKsi * y) * (-b2 + cosKsi * y) + (g2 + sinKsi * y) * (g2 + sinKsi * y))) * Math.cos(ph1) + (g1 + (g2 * y * y + sinKsi * y * (b2 * b2 + g2 * g2)) / ((-b2 + cosKsi * y) * (-b2 + cosKsi * y) + (g2 + sinKsi * y) * (g2 + sinKsi * y))) * Math.sin(ph1));
    }

    public static double di1dv1(double y, double cosKsi, double sinKsi, double g1, double b1, double g2, double b2, double v1, double ph1, double r1) {
        double re = reI1(y, cosKsi, sinKsi, g1, b1, g2, b2, v1, ph1, r1);
        double im = imI1(y, cosKsi, sinKsi, g1, b1, g2, b2, v1, ph1, r1);
        return (re * dreI1dv1(y, cosKsi, sinKsi, g1, b1, g2, b2, ph1, r1) + im * dimI1dv1(y, cosKsi, sinKsi, g1, b1, g2, b2, ph1, r1)) / Math.hypot(re, im);
    }
}
