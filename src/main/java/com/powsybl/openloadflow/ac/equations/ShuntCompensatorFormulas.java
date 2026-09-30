/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.equations;

/**
 * Shunt-compensator AC flow residuals and Jacobian entries, delegated to by the shunt equation terms.
 *
 * <p>GENERATED — DO NOT EDIT. Regenerate with: python3 codegen/generate.py (see codegen/README.md).
 *
 * @author Powsybl codegen {@literal <see codegen/README.md>}
 */
@SuppressWarnings("squid:S00107")
public final class ShuntCompensatorFormulas {

    private ShuntCompensatorFormulas() {
    }

    public static double p(double v, double g) {
        return g * v * v;
    }

    public static double dpdv(double v, double g) {
        return 2 * g * v;
    }

    public static double q(double v, double b) {
        return -b * v * v;
    }

    public static double dqdv(double v, double b) {
        return -2 * b * v;
    }

    public static double dqdb(double v) {
        return -v * v;
    }
}
