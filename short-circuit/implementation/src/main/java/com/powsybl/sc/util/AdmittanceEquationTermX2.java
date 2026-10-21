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
import com.powsybl.sc.util.extensions.AdmittanceConstants;
import com.powsybl.sc.util.extensions.HomopolarModel;
import com.powsybl.sc.util.extensions.ShortCircuitExtensions;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class AdmittanceEquationTermX2 extends AbstractAdmittanceEquationTerm {

    private final double g21;

    private final double b21;

    private final double g2g21sum;

    private final double b2b21sum;

    public AdmittanceEquationTermX2(LfBranch branch, LfBus bus1, LfBus bus2, VariableSet<VariableType> variableSet,
                                    AdmittanceEquationSystem.AdmittanceType admittanceType, AdmittanceEquationSystem.FrequencyType frequencyType) {
        super(branch, bus1, bus2, variableSet, frequencyType);
        // Direct component:
        // I2x = -g21 * V1x + b21 * V1y + (g2 + g21)V2x - (b2 + b21)V2y
        if (admittanceType == AdmittanceEquationSystem.AdmittanceType.ADM_THEVENIN_HOMOPOLAR) {
            HomopolarModel homopolarModel = (HomopolarModel) branch.getProperty(ShortCircuitExtensions.PROPERTY_HOMOPOLAR_MODEL);
            if (frequencyType == AdmittanceEquationSystem.FrequencyType.FREQ_20_HZ) {
                homopolarModel = (HomopolarModel) branch.getProperty(ShortCircuitExtensions.PROPERTY_HOMOPOLAR_MODEL_20HZ);
            }
            if (branch.getBranchType() == LfBranch.BranchType.LINE) {
                g21 = rho * homopolarModel.getZoInvSquare() * (homopolarModel.getZo().getReal() * cosA + homopolarModel.getZo().getImaginary() * sinA);
                b21 = rho * homopolarModel.getZoInvSquare() * (homopolarModel.getZo().getReal() * sinA - homopolarModel.getZo().getImaginary() * cosA);
                g2g21sum = homopolarModel.getZo().getReal() * homopolarModel.getZoInvSquare() + gPi2 * AdmittanceConstants.COEF_XO_XD;
                b2b21sum = -homopolarModel.getZo().getImaginary() * homopolarModel.getZoInvSquare() + bPi2 * AdmittanceConstants.COEF_XO_XD;
            } else if (branch.getBranchType() == LfBranch.BranchType.TRANSFO_2
                    || branch.getBranchType() == LfBranch.BranchType.TRANSFO_3_LEG_1
                    || branch.getBranchType() == LfBranch.BranchType.TRANSFO_3_LEG_2
                    || branch.getBranchType() == LfBranch.BranchType.TRANSFO_3_LEG_3) {
                // case where branch is part of a transformer
                ComplexMatrix mo = homopolarModel.getHomopolarAdmittanceMatrix();
                b2b21sum = mo.get(1, 1).getImaginary();
                g2g21sum = mo.get(1, 1).getReal();
                b21 = -mo.get(1, 0).getImaginary();
                g21 = -mo.get(1, 0).getReal();
            } else {
                throw new IllegalArgumentException("branch type not yet handled");
            }
        } else {
            double g12 = rho * zInvSquare * (r * cosA + x * sinA);
            g21 = g12;
            b21 = rho * zInvSquare * (r * sinA - x * cosA);
            g2g21sum = r * zInvSquare + gPi2;
            b2b21sum = -x * zInvSquare + bPi2;
        }
    }

    @Override
    public double getCoefficient(Variable<VariableType> variable) {
        if (variable.equals(v1rVar)) {
            return -g21;
        } else if (variable.equals(v2rVar)) {
            return g2g21sum;
        } else if (variable.equals(v1iVar)) {
            return b21;
        } else if (variable.equals(v2iVar)) {
            return -b2b21sum;
        } else {
            throw new IllegalArgumentException("Unknown variable " + variable);
        }
    }

    @Override
    protected String getName() {
        return "yr2";
    }
}
