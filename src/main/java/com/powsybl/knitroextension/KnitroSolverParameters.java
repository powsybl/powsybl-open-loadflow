/**
 * Copyright (c) 2024, Artelys (https://www.artelys.com/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.knitroextension;

import com.powsybl.openloadflow.ac.solver.*;

import java.util.Objects;

/**
 * @author Pierre Arvy {@literal <pierre.arvy at artelys.com>}
 * @author Jeanne Archambault {@literal <jeanne.archambault at artelys.com>}
 */

public class KnitroSolverParameters implements AcSolverParameters {

    public static final int DEFAULT_GRADIENT_COMPUTATION_MODE = 1; // Specifies how the Jacobian matrix is computed
    public static final int DEFAULT_GRADIENT_USER_ROUTINE = 2; // If the user chooses to pass the exact Jacobian to knitro, specifies the sparsity pattern for the Jacobian matrix.
    public static final double DEFAULT_LOWER_VOLTAGE_BOUND = 0.5; // Lower bound for voltage magnitude
    public static final double DEFAULT_UPPER_VOLTAGE_BOUND = 1.5; // Upper bound for voltage magnitude
    public static final int DEFAULT_MAX_ITERATIONS = 200;
    public static final StateVectorScalingMode DEFAULT_STATE_VECTOR_SCALING_MODE = StateVectorScalingMode.NONE;
    public static final boolean ALWAYS_UPDATE_NETWORK_DEFAULT_VALUE = false;

//    public KnitroSolverParameters() {
//        super(DEFAULT_MAX_ITERATIONS);
//    }

    private StateVectorScalingMode stateVectorScalingMode = DEFAULT_STATE_VECTOR_SCALING_MODE;

    private int lineSearchStateVectorScalingMaxIteration = LineSearchStateVectorScaling.DEFAULT_MAX_ITERATION;

    private double lineSearchStateVectorScalingStepFold = LineSearchStateVectorScaling.DEFAULT_STEP_FOLD;

    private double maxVoltageChangeStateVectorScalingMaxDv = MaxVoltageChangeStateVectorScaling.DEFAULT_MAX_DV;

    private double maxVoltageChangeStateVectorScalingMaxDphi = MaxVoltageChangeStateVectorScaling.DEFAULT_MAX_DPHI;

    private int gradientComputationMode = DEFAULT_GRADIENT_COMPUTATION_MODE;

    private int gradientUserRoutine = DEFAULT_GRADIENT_USER_ROUTINE;

    private double lowerVoltageBound = DEFAULT_LOWER_VOLTAGE_BOUND;

    private double upperVoltageBound = DEFAULT_UPPER_VOLTAGE_BOUND;

    private KnitroSolverStoppingCriteria stoppingCriteria = new DefaultKnitroSolverStoppingCriteria();

    private boolean alwaysUpdateNetwork = ALWAYS_UPDATE_NETWORK_DEFAULT_VALUE;

    private int maxIterations = DEFAULT_MAX_ITERATIONS;

    public int getGradientComputationMode() {
        return gradientComputationMode;
    }

    public KnitroSolverParameters setGradientComputationMode(int gradientComputationMode) {
        if (gradientComputationMode < 1 || gradientComputationMode > 3) {
            throw new IllegalArgumentException("Knitro gradient computation mode must be between 1 and 3");
        }
        this.gradientComputationMode = gradientComputationMode;
        return this;
    }

    public int getGradientUserRoutine() {
        return gradientUserRoutine;
    }

    public KnitroSolverParameters setGradientUserRoutine(int gradientUserRoutine) {
        if (gradientUserRoutine < 1 || gradientUserRoutine > 2) {
            throw new IllegalArgumentException("User routine must be between 1 and 2");
        }
        this.gradientUserRoutine = gradientUserRoutine;
        return this;
    }

    public double getLowerVoltageBound() {
        return lowerVoltageBound;
    }

    public KnitroSolverParameters setLowerVoltageBound(double lowerVoltageBound) {
        if (lowerVoltageBound < 0) {
            throw new IllegalArgumentException("Realistic voltage bounds must strictly greater then 0");
        }
        this.lowerVoltageBound = lowerVoltageBound;
        return this;
    }

    public double getUpperVoltageBound() {
        return upperVoltageBound;
    }

    public KnitroSolverParameters setUpperVoltageBound(double upperVoltageBound) {
        if (upperVoltageBound < 0) {
            throw new IllegalArgumentException("Realistic voltage bounds must strictly greater then 0");
        }
        if (upperVoltageBound <= lowerVoltageBound) {
            throw new IllegalArgumentException("Realistic voltage upper bounds must greater then lower bounds");
        }
        this.upperVoltageBound = upperVoltageBound;
        return this;
    }

    public KnitroSolverStoppingCriteria getStoppingCriteria() {
        return stoppingCriteria;
    }

    public KnitroSolverParameters setStoppingCriteria(KnitroSolverStoppingCriteria stoppingCriteria) {
        this.stoppingCriteria = Objects.requireNonNull(stoppingCriteria);
        return this;
    }

    public StateVectorScalingMode getStateVectorScalingMode() {
        return stateVectorScalingMode;
    }

    public KnitroSolverParameters setStateVectorScalingMode(StateVectorScalingMode stateVectorScalingMode) {
        this.stateVectorScalingMode = Objects.requireNonNull(stateVectorScalingMode);
        return this;
    }

    public boolean isAlwaysUpdateNetwork() {
        return alwaysUpdateNetwork;
    }

    public KnitroSolverParameters setAlwaysUpdateNetwork(boolean alwaysUpdateNetwork) {
        this.alwaysUpdateNetwork = alwaysUpdateNetwork;
        return this;
    }

    public int getLineSearchStateVectorScalingMaxIteration() {
        return lineSearchStateVectorScalingMaxIteration;
    }

    public KnitroSolverParameters setLineSearchStateVectorScalingMaxIteration(int lineSearchStateVectorScalingMaxIteration) {
        this.lineSearchStateVectorScalingMaxIteration = lineSearchStateVectorScalingMaxIteration;
        return this;

    }

    public double getLineSearchStateVectorScalingStepFold() {
        return lineSearchStateVectorScalingStepFold;
    }

    public KnitroSolverParameters setLineSearchStateVectorScalingStepFold(double lineSearchStateVectorScalingStepFold) {
        this.lineSearchStateVectorScalingStepFold = lineSearchStateVectorScalingStepFold;
        return this;
    }

    public double getMaxVoltageChangeStateVectorScalingMaxDv() {
        return maxVoltageChangeStateVectorScalingMaxDv;
    }

    public KnitroSolverParameters setMaxVoltageChangeStateVectorScalingMaxDv(double maxVoltageChangeStateVectorScalingMaxDv) {
        this.maxVoltageChangeStateVectorScalingMaxDv = maxVoltageChangeStateVectorScalingMaxDv;
        return this;
    }

    public double getMaxVoltageChangeStateVectorScalingMaxDphi() {
        return maxVoltageChangeStateVectorScalingMaxDphi;
    }

    public KnitroSolverParameters setMaxVoltageChangeStateVectorScalingMaxDphi(double maxVoltageChangeStateVectorScalingMaxDphi) {
        this.maxVoltageChangeStateVectorScalingMaxDphi = maxVoltageChangeStateVectorScalingMaxDphi;
        return this;
    }

    public int getMaxIterations() {
        return maxIterations;
    }

    public KnitroSolverParameters setMaxIterations(int maxIterations) {
        this.maxIterations = maxIterations;
        return this;
    }

    @Override
    public String toString() {
        return "KnitroSolverParameters(" +
                "gradientComputationMode=" + gradientComputationMode +
                ", stoppingCriteria=" + stoppingCriteria.getClass().getSimpleName() +
                ", minRealisticVoltage=" + lowerVoltageBound +
                ", maxRealisticVoltage=" + upperVoltageBound +
                ", alwaysUpdateNetwork=" + alwaysUpdateNetwork +
                ", maxIterations=" + maxIterations +
                ')';
    }
}
