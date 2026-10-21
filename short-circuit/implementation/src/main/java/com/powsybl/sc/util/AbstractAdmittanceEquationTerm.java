/**
 * Copyright (c) 2022, Jean-Baptiste Heyberger & Geoffroy Jamgotchian
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.util;

import com.powsybl.openloadflow.equations.AbstractElementEquationTerm;
import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.ElementType;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.PiModel;
import com.powsybl.sc.util.extensions.ScTransfo3wKt;
import com.powsybl.sc.util.extensions.ShortCircuitExtensions;

import java.util.List;
import java.util.Objects;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public abstract class AbstractAdmittanceEquationTerm extends AbstractElementEquationTerm<LfBranch, VariableType, EquationType> implements LinearEquationTerm {

    protected final Variable<VariableType> v1rVar;

    protected final Variable<VariableType> v1iVar;

    protected final Variable<VariableType> v2rVar;

    protected final Variable<VariableType> v2iVar;

    protected final List<Variable<VariableType>> variables;

    protected double rho;

    protected double zInvSquare;

    protected double r;

    protected double x;

    protected double cosA;

    protected double sinA;

    protected double cos2A;

    protected double sin2A;

    protected double gPi1;

    protected double bPi1;

    protected double gPi2;

    protected double bPi2;

    protected double freqCoef;

    protected AbstractAdmittanceEquationTerm(LfBranch branch, LfBus bus1, LfBus bus2, VariableSet<VariableType> variableSet, AdmittanceEquationSystem.FrequencyType frequencyType) {
        super(branch);
        Objects.requireNonNull(bus1);
        Objects.requireNonNull(bus2);
        Objects.requireNonNull(variableSet);

        v1rVar = variableSet.getVariable(bus1.getNum(), VariableType.BUS_VR);
        v2rVar = variableSet.getVariable(bus2.getNum(), VariableType.BUS_VR);
        v1iVar = variableSet.getVariable(bus1.getNum(), VariableType.BUS_VI);
        v2iVar = variableSet.getVariable(bus2.getNum(), VariableType.BUS_VI);

        variables = List.of(v1rVar, v2rVar, v1iVar, v2iVar);

        freqCoef = 1.0;
        if (frequencyType == AdmittanceEquationSystem.FrequencyType.FREQ_20_HZ) {
            freqCoef = 20. / 50.;
        }

        PiModel piModel = branch.getPiModel();
        if (piModel.getX() == 0) {
            throw new IllegalArgumentException("Branch '" + branch.getId() + "' has reactance equal to zero");
        }
        rho = piModel.getR1();
        if (piModel.getZ() == 0) {
            throw new IllegalArgumentException("Branch '" + branch.getId() + "' has Z equal to zero");
        }

        double kTr = 1.;
        double kTx = 1.;
        if (branch.getBranchType() == LfBranch.BranchType.TRANSFO_2) {
            // branch is a 2 windings transformer
            if (branch.getProperty(ShortCircuitExtensions.PROPERTY_SHORT_CIRCUIT_NORM) != null) {
                kTx = (Double) branch.getProperty(ShortCircuitExtensions.PROPERTY_SHORT_CIRCUIT_NORM);
                kTr = kTx;
            }
        } else if (branch.getBranchType() == LfBranch.BranchType.TRANSFO_3_LEG_1
                || branch.getBranchType() == LfBranch.BranchType.TRANSFO_3_LEG_2
                || branch.getBranchType() == LfBranch.BranchType.TRANSFO_3_LEG_3) {
            // branch is leg1 of a 3 windings transformer and homopolar data available
            ScTransfo3wKt scTransfoKt = (ScTransfo3wKt) branch.getProperty(ShortCircuitExtensions.PROPERTY_SHORT_CIRCUIT_NORM);
            if (scTransfoKt != null) {
                if (branch.getBranchType() == LfBranch.BranchType.TRANSFO_3_LEG_1) {
                    kTr = scTransfoKt.getLeg1().getkTr();
                    kTx = scTransfoKt.getLeg1().getkTx();
                } else if (branch.getBranchType() == LfBranch.BranchType.TRANSFO_3_LEG_2) {
                    kTr = scTransfoKt.getLeg2().getkTr();
                    kTx = scTransfoKt.getLeg2().getkTx();
                } else if (branch.getBranchType() == LfBranch.BranchType.TRANSFO_3_LEG_3) {
                    kTr = scTransfoKt.getLeg3().getkTr();
                    kTx = scTransfoKt.getLeg3().getkTx();
                } else {
                    throw new IllegalArgumentException("Branch " + branch.getId() + " has unknown 3-winding leg number");
                }
            }
        }

        r = piModel.getR() * kTr;
        x = piModel.getX() * kTx * freqCoef;

        double zk = Math.sqrt(r * r + x * x);

        zInvSquare = 1 / (zk * zk);

        double alpha = piModel.getA1();
        cosA = Math.cos(Math.toRadians(alpha));
        sinA = Math.sin(Math.toRadians(alpha));
        cos2A = Math.cos(Math.toRadians(2 * alpha));
        sin2A = Math.sin(Math.toRadians(2 * alpha));

        gPi1 = piModel.getG1() / kTr;
        bPi1 = piModel.getB1() / kTx;
        gPi2 = piModel.getG2() / kTr;
        bPi2 = piModel.getB2() / kTx;
    }

    @Override
    public List<Variable<VariableType>> getVariables() {
        return variables;
    }

    @Override
    public ElementType getElementType() {
        return ElementType.BRANCH;
    }

    @Override
    public int getElementNum() {
        return element.getNum();
    }

    @Override
    public double eval() {
        throw new UnsupportedOperationException("Not needed");
    }

    @Override
    public double der(Variable<VariableType> variable) {
        throw new UnsupportedOperationException("Not needed");
    }

    @Override
    public boolean hasRhs() {
        return false;
    }

    @Override
    public double rhs() {
        return 0;
    }
}
