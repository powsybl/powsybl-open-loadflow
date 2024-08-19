/**
 * Copyright (c) 2024, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.solver;

import java.util.Objects;

/**
 * @author Pierre Arvy {@literal <pierre.arvy at artelys.com>}
 * @author Jeanne Archambault {@literal <jeanne.archambault at artelys.com>}
 */

public class KnitroSolverParameters {

    public static final int DEFAULT_GRADIENT_COMPUTATION_MODE = 1; // user provides a routine for computing the exact gradients
    public static final int DEFAULT_GRADIENT_USER_ROUTINE = 2; // user routine for computing the exact gradients
    public static final double DEFAULT_MIN_REALISTIC_VOLTAGE = 0.5;
    public static final double DEFAULT_MAX_REALISTIC_VOLTAGE = 1.5;
    public static final boolean ALWAYS_UPDATE_NETWORK_DEFAULT_VALUE = false;
    public static final boolean DIRECT_OUTER_LOOPS_FORMULATION = true; // knitro is used as a replacement for NR inner loops

    public KnitroSolverParameters() {
    }

    private int gradientComputationMode = DEFAULT_GRADIENT_COMPUTATION_MODE;

    private int gradientUserRoutine = DEFAULT_GRADIENT_USER_ROUTINE;

    private double minRealisticVoltage = DEFAULT_MIN_REALISTIC_VOLTAGE;

    private double maxRealisticVoltage = DEFAULT_MAX_REALISTIC_VOLTAGE;

    private KnitroSolverStoppingCriteria stoppingCriteria = new DefaultKnitroSolverStoppingCriteria();

    private boolean alwaysUpdateNetwork = ALWAYS_UPDATE_NETWORK_DEFAULT_VALUE;

    private boolean directOuterLoopsFormulation = DIRECT_OUTER_LOOPS_FORMULATION;

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
        if (gradientUserRoutine < 1 || gradientUserRoutine > 2){
            throw new IllegalArgumentException("User routine must be between 1 and 2");
        }
        this.gradientUserRoutine = gradientUserRoutine;
        return this;
    }


    public double getMinRealisticVoltage() {
        return minRealisticVoltage;
    }

    public KnitroSolverParameters setMinRealisticVoltage(double minRealisticVoltage) {
        if (minRealisticVoltage < 0) {
            throw new IllegalArgumentException("Realistic voltage bounds must strictly greater then 0");
        }
        this.minRealisticVoltage = minRealisticVoltage;
        return this;
    }

    public double getMaxRealisticVoltage() {
        return maxRealisticVoltage;
    }

    public KnitroSolverParameters setMaxRealisticVoltage(double maxRealisticVoltage) {
        if (maxRealisticVoltage < 0) {
            throw new IllegalArgumentException("Realistic voltage bounds must strictly greater then 0");
        }
        if (maxRealisticVoltage <= minRealisticVoltage) {
            throw new IllegalArgumentException("Realistic voltage upper bounds must greater then lower bounds");
        }
        this.maxRealisticVoltage = maxRealisticVoltage;
        return this;
    }

    public KnitroSolverStoppingCriteria getStoppingCriteria() {
        return stoppingCriteria;
    }

    public KnitroSolverParameters setStoppingCriteria(KnitroSolverStoppingCriteria stoppingCriteria) {
        this.stoppingCriteria = Objects.requireNonNull(stoppingCriteria);
        return this;
    }

    public boolean isAlwaysUpdateNetwork() {
        return alwaysUpdateNetwork;
    }

    public KnitroSolverParameters setAlwaysUpdateNetwork(boolean alwaysUpdateNetwork) {
        this.alwaysUpdateNetwork = alwaysUpdateNetwork;
        return this;
    }

    public boolean isDirectOuterLoopsFormulation() {
        return directOuterLoopsFormulation;
    }

    public KnitroSolverParameters setDirectOuterLoopsFormulation(boolean directOuterLoopsFormulation) {
        this.directOuterLoopsFormulation = directOuterLoopsFormulation;
        return this;
    }

    @Override
    public String toString() {
        return "KnitroSolverParameters(" +
                "gradientComputationMode=" + gradientComputationMode +
                ", stoppingCriteria=" + stoppingCriteria.getClass().getSimpleName() +
                ", minRealisticVoltage=" + minRealisticVoltage +
                ", maxRealisticVoltage=" + maxRealisticVoltage +
                ", alwaysUpdateNetwork=" + alwaysUpdateNetwork +
                ", directOuterLoopsFormulationType=" + directOuterLoopsFormulation +
                ')';
    }
}
