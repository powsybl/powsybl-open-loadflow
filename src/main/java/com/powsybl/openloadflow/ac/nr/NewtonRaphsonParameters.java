/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.nr;

import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class NewtonRaphsonParameters {

    public static final int DEFAULT_MAX_ITERATION = 30;

    private int maxIteration = DEFAULT_MAX_ITERATION;

    public int getMaxIteration() {
        return maxIteration;
    }

    private NewtonRaphsonStoppingCriteria stoppingCriteria = new DefaultNewtonRaphsonStoppingCriteria();

    public static int checkMaxIteration(int maxIteration) {
        if (maxIteration < 1) {
            throw new IllegalArgumentException("Invalid max iteration value: " + maxIteration);
        }
        return maxIteration;
    }

    public NewtonRaphsonParameters setMaxIteration(int maxIteration) {
        this.maxIteration =  checkMaxIteration(maxIteration);
        return this;
    }

    public NewtonRaphsonStoppingCriteria getStoppingCriteria() {
        return stoppingCriteria;
    }

    public NewtonRaphsonParameters setStoppingCriteria(NewtonRaphsonStoppingCriteria stoppingCriteria) {
        this.stoppingCriteria = Objects.requireNonNull(stoppingCriteria);
        return this;
    }

    @Override
    public String toString() {
        return "NewtonRaphsonParameters(" +
                "maxIteration=" + maxIteration +
                ", stoppingCriteria=" + stoppingCriteria.getClass().getSimpleName() +
                ')';
    }
}
