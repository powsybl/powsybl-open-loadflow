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
    public static final double DEFAULT_MIN_REALISTIC_VOLTAGE = 0.5;
    public static final double DEFAULT_MAX_REALISTIC_VOLTAGE = 1.5;
    public static final StateVectorScalingMode DEFAULT_STATE_VECTOR_SCALING_MODE = StateVectorScalingMode.NONE;

    private int maxIteration = DEFAULT_MAX_ITERATION;

    private double minRealisticVoltage = DEFAULT_MIN_REALISTIC_VOLTAGE;

    private double maxRealisticVoltage = DEFAULT_MAX_REALISTIC_VOLTAGE;

    private StateVectorScalingMode stateVectorScalingMode = DEFAULT_STATE_VECTOR_SCALING_MODE;

    public int getMaxIteration() {
        return maxIteration;
    }

    private NewtonRaphsonStoppingCriteria stoppingCriteria = new DefaultNewtonRaphsonStoppingCriteria();

    private boolean detailedNrReport = false;

    public static int checkMaxIteration(int maxIteration) {
        if (maxIteration < 1) {
            throw new IllegalArgumentException("Invalid max iteration value: " + maxIteration);
        }
        return maxIteration;
    }

    public NewtonRaphsonParameters setMaxIteration(int maxIteration) {
        this.maxIteration = checkMaxIteration(maxIteration);
        return this;
    }

    public double getMinRealisticVoltage() {
        return minRealisticVoltage;
    }

    public NewtonRaphsonParameters setMinRealisticVoltage(double minRealisticVoltage) {
        this.minRealisticVoltage = minRealisticVoltage;
        return this;
    }

    public double getMaxRealisticVoltage() {
        return maxRealisticVoltage;
    }

    public NewtonRaphsonParameters setMaxRealisticVoltage(double maxRealisticVoltage) {
        this.maxRealisticVoltage = maxRealisticVoltage;
        return this;
    }

    public NewtonRaphsonStoppingCriteria getStoppingCriteria() {
        return stoppingCriteria;
    }

    public NewtonRaphsonParameters setStoppingCriteria(NewtonRaphsonStoppingCriteria stoppingCriteria) {
        this.stoppingCriteria = Objects.requireNonNull(stoppingCriteria);
        return this;
    }

    public StateVectorScalingMode getStateVectorScalingMode() {
        return stateVectorScalingMode;
    }

    public NewtonRaphsonParameters setStateVectorScalingMode(StateVectorScalingMode stateVectorScalingMode) {
        this.stateVectorScalingMode = Objects.requireNonNull(stateVectorScalingMode);
        return this;
    }

    public boolean getDetailedNrReport() {
        return detailedNrReport;
    }

    public NewtonRaphsonParameters setDetailedNrReport(boolean detailedNrReport) {
        this.detailedNrReport = detailedNrReport;
        return this;
    }

    @Override
    public String toString() {
        return "NewtonRaphsonParameters(" +
                "maxIteration=" + maxIteration +
                ", minRealisticVoltage=" + minRealisticVoltage +
                ", maxRealisticVoltage=" + maxRealisticVoltage +
                ", stoppingCriteria=" + stoppingCriteria.getClass().getSimpleName() +
                ", stateVectorScalingMode=" + stateVectorScalingMode +
                ')';
    }
}
