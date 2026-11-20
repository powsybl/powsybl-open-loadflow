/*
 * Copyright (c) 2019-2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.solver;

import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class NewtonRaphsonParameters extends AbstractNewtonParameters<NewtonRaphsonParameters> implements AcSolverParameters {

    public static final int DEFAULT_MAX_ITERATIONS = 15;
    public static final StateVectorScalingMode DEFAULT_STATE_VECTOR_SCALING_MODE = StateVectorScalingMode.NONE;
    public static final boolean ALWAYS_UPDATE_NETWORK_DEFAULT_VALUE = false;

    public NewtonRaphsonParameters() {
        super(DEFAULT_MAX_ITERATIONS);
    }

    private StateVectorScalingMode stateVectorScalingMode = DEFAULT_STATE_VECTOR_SCALING_MODE;

    private int lineSearchStateVectorScalingMaxIteration = LineSearchStateVectorScaling.DEFAULT_MAX_ITERATION;

    private double lineSearchStateVectorScalingStepFold = LineSearchStateVectorScaling.DEFAULT_STEP_FOLD;

    private double maxVoltageChangeStateVectorScalingMaxDv = MaxVoltageChangeStateVectorScaling.DEFAULT_MAX_DV;

    private double maxVoltageChangeStateVectorScalingMaxDphi = MaxVoltageChangeStateVectorScaling.DEFAULT_MAX_DPHI;

    private NewtonRaphsonStoppingCriteria stoppingCriteria = new DefaultNewtonRaphsonStoppingCriteria();

    private boolean alwaysUpdateNetwork = ALWAYS_UPDATE_NETWORK_DEFAULT_VALUE;

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

    public boolean isAlwaysUpdateNetwork() {
        return alwaysUpdateNetwork;
    }

    public NewtonRaphsonParameters setAlwaysUpdateNetwork(boolean alwaysUpdateNetwork) {
        this.alwaysUpdateNetwork = alwaysUpdateNetwork;
        return this;
    }

    public int getLineSearchStateVectorScalingMaxIteration() {
        return lineSearchStateVectorScalingMaxIteration;
    }

    public NewtonRaphsonParameters setLineSearchStateVectorScalingMaxIteration(int lineSearchStateVectorScalingMaxIteration) {
        this.lineSearchStateVectorScalingMaxIteration = lineSearchStateVectorScalingMaxIteration;
        return this;

    }

    public double getLineSearchStateVectorScalingStepFold() {
        return lineSearchStateVectorScalingStepFold;
    }

    public NewtonRaphsonParameters setLineSearchStateVectorScalingStepFold(double lineSearchStateVectorScalingStepFold) {
        this.lineSearchStateVectorScalingStepFold = lineSearchStateVectorScalingStepFold;
        return this;
    }

    public double getMaxVoltageChangeStateVectorScalingMaxDv() {
        return maxVoltageChangeStateVectorScalingMaxDv;
    }

    public NewtonRaphsonParameters setMaxVoltageChangeStateVectorScalingMaxDv(double maxVoltageChangeStateVectorScalingMaxDv) {
        this.maxVoltageChangeStateVectorScalingMaxDv = maxVoltageChangeStateVectorScalingMaxDv;
        return this;
    }

    public double getMaxVoltageChangeStateVectorScalingMaxDphi() {
        return maxVoltageChangeStateVectorScalingMaxDphi;
    }

    public NewtonRaphsonParameters setMaxVoltageChangeStateVectorScalingMaxDphi(double maxVoltageChangeStateVectorScalingMaxDphi) {
        this.maxVoltageChangeStateVectorScalingMaxDphi = maxVoltageChangeStateVectorScalingMaxDphi;
        return this;
    }

    @Override
    public String toString() {
        return "NewtonRaphsonParameters(" +
                "maxIterations=" + maxIterations +
                ", stoppingCriteria=" + stoppingCriteria.getClass().getSimpleName() +
                ", stateVectorScalingMode=" + stateVectorScalingMode +
                ", alwaysUpdateNetwork=" + alwaysUpdateNetwork +
                ", lineSearchStateVectorScalingMaxIteration=" + lineSearchStateVectorScalingMaxIteration +
                ", lineSearchStateVectorScalingStepFold=" + lineSearchStateVectorScalingStepFold +
                ", maxVoltageChangeStateVectorScalingMaxDv=" + maxVoltageChangeStateVectorScalingMaxDv +
                ", maxVoltageChangeStateVectorScalingMaxDphi=" + maxVoltageChangeStateVectorScalingMaxDphi +
                ')';
    }
}
