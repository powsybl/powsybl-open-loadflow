/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class ExponentialLoadModel {

    public static final double DEFAULT_ALPHA = 0;
    public static final double DEFAULT_BETA = 0;

    private double alpha = DEFAULT_ALPHA;

    private double beta = DEFAULT_BETA;

    public double getAlpha() {
        return alpha;
    }

    public ExponentialLoadModel setAlpha(double alpha) {
        this.alpha = alpha;
        return this;
    }

    public double getBeta() {
        return beta;
    }

    public ExponentialLoadModel setBeta(double beta) {
        this.beta = beta;
        return this;
    }
}
