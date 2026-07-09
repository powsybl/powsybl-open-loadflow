/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.equations;

/**
 * HVDC AC-emulation active-flow formulas (piecewise; derivative neglects the cable loss, per OLF) — delegated to by the HVDC AC-emulation equation terms.
 *
 * <p>GENERATED — DO NOT EDIT. Regenerate with: python3 codegen/generate.py (see codegen/README.md).
 *
 * @author Powsybl codegen {@literal <see codegen/README.md>}
 */
@SuppressWarnings("squid:S00107")
public final class HvdcAcEmulationFormulas {

    private HvdcAcEmulationFormulas() {
    }

    public static double p1(double p0, double k, double lossFactor1, double lossFactor2, double r, double ph1, double ph2) {
        return k * (ph1 - ph2) + p0 >= 0 ? k * (ph1 - ph2) + p0 : -(1 - lossFactor2) * (-r * (1 - lossFactor1) * (1 - lossFactor1) * (k * (ph1 - ph2) + p0) * (k * (ph1 - ph2) + p0) + (1 - lossFactor1) * Math.abs(k * (ph1 - ph2) + p0));
    }

    public static double dp1dph1(double p0, double k, double lossFactor1, double lossFactor2, double ph1, double ph2) {
        return k * (ph1 - ph2) + p0 >= 0 ? k : k * (1 - lossFactor1) * (1 - lossFactor2);
    }

    public static double dp1dph2(double p0, double k, double lossFactor1, double lossFactor2, double ph1, double ph2) {
        return k * (ph1 - ph2) + p0 >= 0 ? -k : -k * (1 - lossFactor1) * (1 - lossFactor2);
    }

    public static double p2(double p0, double k, double lossFactor1, double lossFactor2, double r, double ph1, double ph2) {
        return k * (ph1 - ph2) + p0 < 0 ? -k * (ph1 - ph2) - p0 : -(1 - lossFactor1) * (-r * (1 - lossFactor2) * (1 - lossFactor2) * (k * (ph1 - ph2) + p0) * (k * (ph1 - ph2) + p0) + (1 - lossFactor2) * Math.abs(k * (ph1 - ph2) + p0));
    }

    public static double dp2dph1(double p0, double k, double lossFactor1, double lossFactor2, double ph1, double ph2) {
        return k * (ph1 - ph2) + p0 < 0 ? -k : -k * (1 - lossFactor1) * (1 - lossFactor2);
    }

    public static double dp2dph2(double p0, double k, double lossFactor1, double lossFactor2, double ph1, double ph2) {
        return k * (ph1 - ph2) + p0 < 0 ? k : k * (1 - lossFactor1) * (1 - lossFactor2);
    }
}
