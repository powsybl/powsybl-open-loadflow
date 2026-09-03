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
 * Open-branch AC active/reactive flow residuals and Jacobian entries, delegated to by the open-branch equation terms.
 *
 * <p>GENERATED — DO NOT EDIT. Regenerate with: python3 codegen/generate.py (see codegen/README.md).
 *
 * @author Powsybl codegen {@literal <see codegen/README.md>}
 */
@SuppressWarnings("squid:S00107")
public final class OpenBranchFormulas {

    private OpenBranchFormulas() {
    }

    public static double p2(double y, double cosKsi, double sinKsi, double g1, double b1, double g2, double v2) {
        return R2 * R2 * v2 * v2 * (g1 * y * y / ((-b1 + cosKsi * y) * (-b1 + cosKsi * y) + (g1 + sinKsi * y) * (g1 + sinKsi * y)) + g2 + sinKsi * y * (b1 * b1 + g1 * g1) / ((-b1 + cosKsi * y) * (-b1 + cosKsi * y) + (g1 + sinKsi * y) * (g1 + sinKsi * y)));
    }

    public static double dp2dv2(double y, double cosKsi, double sinKsi, double g1, double b1, double g2, double v2) {
        return 2 * R2 * R2 * v2 * (g1 * y * y / ((-b1 + cosKsi * y) * (-b1 + cosKsi * y) + (g1 + sinKsi * y) * (g1 + sinKsi * y)) + g2 + sinKsi * y * (b1 * b1 + g1 * g1) / ((-b1 + cosKsi * y) * (-b1 + cosKsi * y) + (g1 + sinKsi * y) * (g1 + sinKsi * y)));
    }

    public static double q2(double y, double cosKsi, double sinKsi, double g1, double b1, double b2, double v2) {
        return -R2 * R2 * v2 * v2 * (b1 * y * y / ((-b1 + cosKsi * y) * (-b1 + cosKsi * y) + (g1 + sinKsi * y) * (g1 + sinKsi * y)) + b2 - cosKsi * y * (b1 * b1 + g1 * g1) / ((-b1 + cosKsi * y) * (-b1 + cosKsi * y) + (g1 + sinKsi * y) * (g1 + sinKsi * y)));
    }

    public static double dq2dv2(double y, double cosKsi, double sinKsi, double g1, double b1, double b2, double v2) {
        return -2 * R2 * R2 * v2 * (b1 * y * y / ((-b1 + cosKsi * y) * (-b1 + cosKsi * y) + (g1 + sinKsi * y) * (g1 + sinKsi * y)) + b2 - cosKsi * y * (b1 * b1 + g1 * g1) / ((-b1 + cosKsi * y) * (-b1 + cosKsi * y) + (g1 + sinKsi * y) * (g1 + sinKsi * y)));
    }

    public static double p1(double y, double cosKsi, double sinKsi, double g1, double g2, double b2, double v1, double r1) {
        return r1 * r1 * v1 * v1 * (g1 + g2 * y * y / ((-b2 + cosKsi * y) * (-b2 + cosKsi * y) + (g2 + sinKsi * y) * (g2 + sinKsi * y)) + sinKsi * y * (b2 * b2 + g2 * g2) / ((-b2 + cosKsi * y) * (-b2 + cosKsi * y) + (g2 + sinKsi * y) * (g2 + sinKsi * y)));
    }

    public static double dp1dv1(double y, double cosKsi, double sinKsi, double g1, double g2, double b2, double v1, double r1) {
        return 2 * r1 * r1 * v1 * (g1 + g2 * y * y / ((-b2 + cosKsi * y) * (-b2 + cosKsi * y) + (g2 + sinKsi * y) * (g2 + sinKsi * y)) + sinKsi * y * (b2 * b2 + g2 * g2) / ((-b2 + cosKsi * y) * (-b2 + cosKsi * y) + (g2 + sinKsi * y) * (g2 + sinKsi * y)));
    }

    public static double q1(double y, double cosKsi, double sinKsi, double b1, double g2, double b2, double v1, double r1) {
        return -r1 * r1 * v1 * v1 * (b1 + b2 * y * y / ((-b2 + cosKsi * y) * (-b2 + cosKsi * y) + (g2 + sinKsi * y) * (g2 + sinKsi * y)) - cosKsi * y * (b2 * b2 + g2 * g2) / ((-b2 + cosKsi * y) * (-b2 + cosKsi * y) + (g2 + sinKsi * y) * (g2 + sinKsi * y)));
    }

    public static double dq1dv1(double y, double cosKsi, double sinKsi, double b1, double g2, double b2, double v1, double r1) {
        return -2 * r1 * r1 * v1 * (b1 + b2 * y * y / ((-b2 + cosKsi * y) * (-b2 + cosKsi * y) + (g2 + sinKsi * y) * (g2 + sinKsi * y)) - cosKsi * y * (b2 * b2 + g2 * g2) / ((-b2 + cosKsi * y) * (-b2 + cosKsi * y) + (g2 + sinKsi * y) * (g2 + sinKsi * y)));
    }
}
