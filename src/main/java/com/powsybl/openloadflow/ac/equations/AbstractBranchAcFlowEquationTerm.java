/*
 * Copyright (c) 2019-2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.equations;

import com.powsybl.openloadflow.equations.AbstractElementEquationTerm;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.PiModel;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
abstract class AbstractBranchAcFlowEquationTerm extends AbstractElementEquationTerm<LfBranch, AcVariableType, AcEquationType> {

    protected final BranchAcDataVector branchAcDataVector;
    private final int branchNum;

    protected AbstractBranchAcFlowEquationTerm(LfBranch branch, BranchAcDataVector branchAcDataVector) {
        super(branch);
        PiModel piModel = branch.getPiModel();
        if (piModel.getR() == 0 && piModel.getX() == 0) {
            throw new IllegalArgumentException("Non impedant branch not supported: " + branch.getId());
        }
        branchNum = branch.getNum();
        this.branchAcDataVector = branchAcDataVector;
        if (!branchAcDataVector.networkDataInitialized[branchNum]) {
            branchAcDataVector.b1[branchNum] = piModel.getB1();
            branchAcDataVector.b2[branchNum] = piModel.getB2();
            branchAcDataVector.g1[branchNum] = piModel.getG1();
            branchAcDataVector.g2[branchNum] = piModel.getG2();
            branchAcDataVector.y[branchNum] = piModel.getY();
            branchAcDataVector.ksi[branchNum] = piModel.getKsi();
            // y12 = g12+j.b12 = 1/(r+j.x)
            branchAcDataVector.g12[branchNum] = piModel.getR() * y() * y();
            branchAcDataVector.b12[branchNum] = -piModel.getX() * y() * y();
            branchAcDataVector.networkDataInitialized[branchNum] = true;
        }
    }

    public abstract void updateVectorSuppliers();

    protected double b1() {
        return branchAcDataVector.b1[branchNum];
    }

    protected double b2() {
        return branchAcDataVector.b2[branchNum];
    }

    protected double g1() {
        return branchAcDataVector.g1[branchNum];
    }

    protected double g2() {
        return branchAcDataVector.g2[branchNum];
    }

    protected double y() {
        return branchAcDataVector.y[branchNum];
    }

    protected double ksi() {
        return branchAcDataVector.ksi[branchNum];
    }

    protected double g12() {
        return branchAcDataVector.g12[branchNum];
    }

    protected double b12() {
        return branchAcDataVector.b12[branchNum];
    }

    // eval variables

    protected boolean p2Valid() {
        return branchAcDataVector.p2Valid[branchNum];
    }

    protected double p2() {
        return branchAcDataVector.p2[branchNum];
    }

    protected void setP2(double value) {
        branchAcDataVector.p2[branchNum] = value;
        branchAcDataVector.p2Valid[branchNum] = true;
    }
}
