/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.nr;

import com.powsybl.openloadflow.equations.UniformValueVoltageInitializer;
import com.powsybl.openloadflow.equations.VoltageInitializer;

import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class NewtonRaphsonParameters {

    private static final int DEFAULT_MAX_ITERATION = 30;

    private int maxIteration = DEFAULT_MAX_ITERATION;

    private VoltageInitializer voltageInitializer = new UniformValueVoltageInitializer();

    public int getMaxIteration() {
        return maxIteration;
    }

    private final NewtonRaphsonStoppingCriteria stoppingCriteria = new DefaultNewtonRaphsonStoppingCriteria();

    public NewtonRaphsonParameters setMaxIteration(int maxIteration) {
        if (maxIteration < 1) {
            throw new IllegalArgumentException("Invalid max iteration value: " + maxIteration);
        }
        this.maxIteration = maxIteration;
        return this;
    }

    public VoltageInitializer getVoltageInitializer() {
        return voltageInitializer;
    }

    public NewtonRaphsonParameters setVoltageInitializer(VoltageInitializer voltageInitializer) {
        this.voltageInitializer = Objects.requireNonNull(voltageInitializer);
        return this;
    }

    public NewtonRaphsonStoppingCriteria getStoppingCriteria() {
        return stoppingCriteria;
    }
}
