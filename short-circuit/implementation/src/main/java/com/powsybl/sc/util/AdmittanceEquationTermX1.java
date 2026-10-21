/**
 * Copyright (c) 2022, Jean-Baptiste Heyberger & Geoffroy Jamgotchian
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.util;

import com.powsybl.math.matrix.ComplexMatrix;
import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.sc.util.extensions.HomopolarModel;
import com.powsybl.sc.util.extensions.ShortCircuitExtensions;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class AdmittanceEquationTermX1 extends AbstractAdmittanceEquationTerm {

    private final double g12;

    private final double b12;

    private final double g1g12sum;

    private final double b1b12sum;

    public AdmittanceEquationTermX1(LfBranch branch, LfBus bus1, LfBus bus2, VariableSet<VariableType> variableSet,
                                    AdmittanceEquationSystem.AdmittanceType admittanceType, AdmittanceEquationSystem.FrequencyType frequencyType) {
        super(branch, bus1, bus2, variableSet, frequencyType);
        // Direct component:
        // I1x = (g1 + g12)V1x - (b1 + b12)V1y - g12 * V2x + b12 * V2y
        if (admittanceType == AdmittanceEquationSystem.AdmittanceType.ADM_THEVENIN_HOMOPOLAR) {
            HomopolarModel homopolarModel = (HomopolarModel) branch.getProperty(ShortCircuitExtensions.PROPERTY_HOMOPOLAR_MODEL);
            if (frequencyType == AdmittanceEquationSystem.FrequencyType.FREQ_20_HZ) {
                homopolarModel = (HomopolarModel) branch.getProperty(ShortCircuitExtensions.PROPERTY_HOMOPOLAR_MODEL_20HZ);
            }
            if (branch.getBranchType() == LfBranch.BranchType.LINE) {
                // default if branch type is a line
                g12 = rho * homopolarModel.getZoInvSquare() * (homopolarModel.getZo().getReal() * cosA + homopolarModel.getZo().getImaginary() * sinA);
                b12 = -rho * homopolarModel.getZoInvSquare() * (homopolarModel.getZo().getImaginary() * cosA + homopolarModel.getZo().getReal() * sinA);
                g1g12sum = rho * rho * (homopolarModel.getYom().getReal() + homopolarModel.getZo().getReal() * homopolarModel.getZoInvSquare());
                b1b12sum = rho * rho * (homopolarModel.getYom().getImaginary() - homopolarModel.getZo().getImaginary() * homopolarModel.getZoInvSquare());
            } else if (branch.getBranchType() == LfBranch.BranchType.TRANSFO_2
                    || branch.getBranchType() == LfBranch.BranchType.TRANSFO_3_LEG_1
                    || branch.getBranchType() == LfBranch.BranchType.TRANSFO_3_LEG_2
                    || branch.getBranchType() == LfBranch.BranchType.TRANSFO_3_LEG_3) {
                // case where branch is part of a transformer
                ComplexMatrix mo = homopolarModel.getHomopolarAdmittanceMatrix();
                b1b12sum = mo.get(0, 0).getImaginary();
                g1g12sum = mo.get(0, 0).getReal();
                b12 = -mo.get(0, 1).getImaginary();
                g12 = -mo.get(0, 1).getReal();
            } else {
                throw new IllegalArgumentException("branch type not yet handled");
            }
        } else {
            g12 = rho * zInvSquare * (r * cosA + x * sinA);
            b12 = -rho * zInvSquare * (x * cosA + r * sinA);
            g1g12sum = rho * rho * (gPi1 + r * zInvSquare);
            b1b12sum = rho * rho * (bPi1 - x * zInvSquare);
        }
    }

    @Override
    public double getCoefficient(Variable<VariableType> variable) {
        if (variable.equals(v1rVar)) {
            return g1g12sum;
        } else if (variable.equals(v2rVar)) {
            return -g12;
        } else if (variable.equals(v1iVar)) {
            return -b1b12sum;
        } else if (variable.equals(v2iVar)) {
            return b12;
        } else {
            throw new IllegalArgumentException("Unknown variable " + variable);
        }
    }

    @Override
    protected String getName() {
        return "yr1";
    }
}
