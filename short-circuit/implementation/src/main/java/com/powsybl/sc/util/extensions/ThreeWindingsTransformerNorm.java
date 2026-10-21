/**
 * Copyright (c) 2022, Jean-Baptiste Heyberger & Geoffroy Jamgotchian
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.util.extensions;

import static com.powsybl.sc.extensions.FortescueConstants.DEFAULT_COEFF_RO;
import static com.powsybl.sc.extensions.FortescueConstants.DEFAULT_COEFF_XO;
import static com.powsybl.sc.extensions.ShortCircuitConstants.DEFAULT_COEFF_K;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class ThreeWindingsTransformerNorm {

    public static class T3wLeg {

        private double legCoeffRoOverload;
        private double legCoeffXoOverload;
        private double ktR;
        private double ktX;
        private double ktRo;
        private double ktXo;

        public T3wLeg(double legCoeffRoOverload, double legCoeffXoOverload,
                      double ktR, double ktX, double ktRo, double ktXo) {
            this.legCoeffRoOverload = legCoeffRoOverload;
            this.legCoeffXoOverload = legCoeffXoOverload;
            this.ktR = ktR;
            this.ktX = ktX;
            this.ktRo = ktRo;
            this.ktXo = ktXo;
        }

        public double getKtR() {
            return ktR;
        }

        public double getKtRo() {
            return ktRo;
        }

        public double getKtX() {
            return ktX;
        }

        public double getKtXo() {
            return ktXo;
        }

        public double getLegCoeffRoOverload() {
            return legCoeffRoOverload;
        }

        public double getLegCoeffXoOverload() {
            return legCoeffXoOverload;
        }

        public void setKtR(double ktR) {
            this.ktR = ktR;
        }

        public void setKtX(double ktX) {
            this.ktX = ktX;
        }

        public void setKtRo(double ktRo) {
            this.ktRo = ktRo;
        }

        public void setKtXo(double ktXo) {
            this.ktXo = ktXo;
        }

        public void setLegCoeffRoOverload(double legCoeffRo) {
            this.legCoeffRoOverload = legCoeffRo;
        }

        public void setLegCoeffXoOverload(double legCoeffXo) {
            this.legCoeffXoOverload = legCoeffXo;
        }

    }

    public static final String NAME = "threeWindingsTransformerNorm";

    private final T3wLeg leg1;
    private final T3wLeg leg2;
    private final T3wLeg leg3;
    private boolean overloadHomopolarCoefs;

    public ThreeWindingsTransformerNorm(double leg1CoeffRo, double leg2CoeffRo, double leg3CoeffRo,
                                        double leg1CoeffXo, double leg2CoeffXo, double leg3CoeffXo,
                                        double kt1R, double kt1X, double kt2R, double kt2X, double kt3R, double kt3X,
                                        double kt1Ro, double kt1Xo, double kt2Ro, double kt2Xo, double kt3Ro, double kt3Xo, boolean isOverloadHomopolarCoefs) {

        this.leg1 = new T3wLeg(leg1CoeffRo, leg1CoeffXo,
                kt1R, kt1X, kt1Ro, kt1Xo);
        this.leg2 = new T3wLeg(leg2CoeffRo, leg2CoeffXo,
                kt2R, kt2X, kt2Ro, kt2Xo);
        this.leg3 = new T3wLeg(leg3CoeffRo, leg3CoeffXo,
                kt3R, kt3X, kt3Ro, kt3Xo);
        this.overloadHomopolarCoefs = isOverloadHomopolarCoefs;
    }

    public ThreeWindingsTransformerNorm() {
        this(DEFAULT_COEFF_RO, DEFAULT_COEFF_RO, DEFAULT_COEFF_RO,
                DEFAULT_COEFF_XO, DEFAULT_COEFF_XO, DEFAULT_COEFF_XO,
                DEFAULT_COEFF_K, DEFAULT_COEFF_K, DEFAULT_COEFF_K, DEFAULT_COEFF_K, DEFAULT_COEFF_K, DEFAULT_COEFF_K,
                DEFAULT_COEFF_K, DEFAULT_COEFF_K, DEFAULT_COEFF_K, DEFAULT_COEFF_K, DEFAULT_COEFF_K, DEFAULT_COEFF_K, false);
    }

    public T3wLeg getLeg1() {
        return leg1;
    }

    public T3wLeg getLeg2() {
        return leg2;
    }

    public T3wLeg getLeg3() {
        return leg3;
    }

    public boolean isOverloadHomopolarCoefs() {
        return overloadHomopolarCoefs;
    }

    public void setOverloadHomopolarCoefs(boolean overloadHomopolarCoefs) {
        this.overloadHomopolarCoefs = overloadHomopolarCoefs;
    }
}
