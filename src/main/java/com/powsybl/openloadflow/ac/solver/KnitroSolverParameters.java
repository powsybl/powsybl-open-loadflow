/**
 * Copyright (c) 2024, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.solver;

import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowParameters;

/**
 * @author Pierre Arvy {@literal <pierre.arvy at artelys.com>}
 * @author Jeanne Archambault {@literal <jeanne.archambault at artelys.com>}
 */

// TODO check si c'est vraiment utile de passer un paramètre voltageInitMode (ce n'est par exemple pas fait dans NR)
public class KnitroSolverParameters {

    public static final int GRADIENT_COMPUTATION_MODE_DEFAULT = 2; // Knitro computes gradients by forward finite differences
    public static final double DEFAULT_MIN_REALISTIC_VOLTAGE = 0.5;
    public static final double DEFAULT_MAX_REALISTIC_VOLTAGE = 1.5;

    private int gradientComputationMode = GRADIENT_COMPUTATION_MODE_DEFAULT;
    private double convEpsPerEq = NewtonRaphsonStoppingCriteria.DEFAULT_CONV_EPS_PER_EQ;
//    private LoadFlowParameters.VoltageInitMode voltageInitMode = LoadFlowParameters.DEFAULT_VOLTAGE_INIT_MODE;
    private double minRealisticVoltage = DEFAULT_MIN_REALISTIC_VOLTAGE;

    private double maxRealisticVoltage = DEFAULT_MAX_REALISTIC_VOLTAGE;

    public KnitroSolverParameters() {
    }

    public int getGradientComputationMode() {
        return gradientComputationMode;
    }

    public double getConvEpsPerEq() {
        return convEpsPerEq;
    }

//    public LoadFlowParameters.VoltageInitMode getVoltageInitMode() {
//        return voltageInitMode;
//    }

    public double getMinRealisticVoltage() {
        return minRealisticVoltage;
    }

    public double getMaxRealisticVoltage() {
        return maxRealisticVoltage;
    }

    public void setGradientComputationMode(int gradientComputationMode) {
        if (gradientComputationMode < 1 || gradientComputationMode > 3) {
            throw new IllegalArgumentException( );
        }
        this.gradientComputationMode = gradientComputationMode;
    }

    public void setConvEpsPerEq(double convEpsPerEq) {
        if (convEpsPerEq<=0) {
            throw new IllegalArgumentException("Knitro final relative stopping tolerance for the feasibility error must be strictly greater than 0");
        }
        this.convEpsPerEq = convEpsPerEq;
    }

//    public void setVoltageInitMode(LoadFlowParameters.VoltageInitMode voltageInitMode) {
//        if ((voltageInitMode!=LoadFlowParameters.VoltageInitMode.UNIFORM_VALUES)&(voltageInitMode!=LoadFlowParameters.VoltageInitMode.PREVIOUS_VALUES)&(voltageInitMode!=LoadFlowParameters.VoltageInitMode.DC_VALUES)) {
//            throw new IllegalArgumentException("Knitro init mode must be UNIFORM_VALUES, PREVIOUS_VALUES or DC_VALUES");
//        }
//        this.voltageInitMode = voltageInitMode;
//    }

    public void setMinRealisticVoltage(double minRealisticVoltage) {
        this.minRealisticVoltage = minRealisticVoltage;
    }

    public void setMaxRealisticVoltage(double maxRealisticVoltage) {
        this.maxRealisticVoltage = maxRealisticVoltage;
    }

    @Override
    public String toString() {
        return "KnitroSolverParameters(" +
                "gradientComputationMode=" + gradientComputationMode +
                ", " + "convEpsPerEq=" + convEpsPerEq +
//                ", " + "voltageInitMode=" + voltageInitMode +
                ", minRealisticVoltage=" + minRealisticVoltage +
                ", maxRealisticVoltage=" + maxRealisticVoltage +
                ')';
    }
}
