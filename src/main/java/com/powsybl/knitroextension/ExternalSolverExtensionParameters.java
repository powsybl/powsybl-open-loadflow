package com.powsybl.knitroextension;

import com.powsybl.commons.extensions.AbstractExtension;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowParameters;

import java.util.Objects;

public class ExternalSolverExtensionParameters extends AbstractExtension<LoadFlowParameters> {

    private int gradientComputationMode = KnitroSolverParameters.DEFAULT_GRADIENT_COMPUTATION_MODE;
    private int gradientUserRoutine = KnitroSolverParameters.DEFAULT_GRADIENT_USER_ROUTINE;
    private double lowerVoltageBound = KnitroSolverParameters.DEFAULT_LOWER_VOLTAGE_BOUND;
    private double upperVoltageBound = KnitroSolverParameters.DEFAULT_UPPER_VOLTAGE_BOUND;
    private KnitroSolverStoppingCriteria stoppingCriteria = new KnitroSolverStoppingCriteria() {
    };

    public int getGradientComputationMode() {
        return gradientComputationMode;
    }

    public ExternalSolverExtensionParameters setGradientComputationMode(int gradientComputationMode) {
        if (gradientComputationMode < 1 || gradientComputationMode > 3) {
            throw new IllegalArgumentException("User routine must be between 1 and 3");
        }
        this.gradientComputationMode = gradientComputationMode;
        return this;
    }
    public int getGradientUserRoutine() {
        return gradientUserRoutine;
    }

    public ExternalSolverExtensionParameters setGradientUserRoutine(int gradientUserRoutine) {
        if (gradientUserRoutine < 1 || gradientUserRoutine > 2) {
            throw new IllegalArgumentException("User routine must be between 1 and 2");
        }
        this.gradientUserRoutine = gradientUserRoutine;
        return this;
    }

    public double getLowerVoltageBound() {
        return lowerVoltageBound;
    }

    public ExternalSolverExtensionParameters setLowerVoltageBound(double lowerVoltageBound) {
        if (lowerVoltageBound < 0) {
            throw new IllegalArgumentException("Realistic voltage bounds must strictly greater then 0");
        }
        this.lowerVoltageBound = lowerVoltageBound;
        return this;
    }

    public double getUpperVoltageBound() {
        return upperVoltageBound;
    }

    public ExternalSolverExtensionParameters setUpperVoltageBound(double upperVoltageBound) {
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

    public ExternalSolverExtensionParameters setStoppingCriteria(KnitroSolverStoppingCriteria stoppingCriteria) {
        this.stoppingCriteria = Objects.requireNonNull(stoppingCriteria);
        return this;
    }

    @Override
    public String getName() {
        return "external-solver-extension";
    }

}
