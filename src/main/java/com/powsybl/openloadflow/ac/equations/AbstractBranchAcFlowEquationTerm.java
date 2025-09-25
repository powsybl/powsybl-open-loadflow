/*
 * Copyright (c) 2019-2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.equations;

import com.powsybl.openloadflow.ac.equations.vector.AcVectorEngine;
import com.powsybl.openloadflow.equations.AbstractElementEquationTerm;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.PiModel;
import net.jafama.FastMath;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
abstract class AbstractBranchAcFlowEquationTerm extends AbstractElementEquationTerm<LfBranch, AcVariableType, AcEquationType> {

    protected final AcVectorEngine acVectorEngine;
    protected final int branchNum;

    protected AbstractBranchAcFlowEquationTerm(LfBranch branch, AcVectorEngine acVectorEngine) {
        super(branch);
        PiModel piModel = branch.getPiModel();
        if (piModel.getR() == 0 && piModel.getX() == 0) {
            throw new IllegalArgumentException("Non impedant branch not supported: " + branch.getId());
        }
        branchNum = branch.getNum();
        this.acVectorEngine = acVectorEngine;
        if (!acVectorEngine.networkDataInitialized[branchNum]) {
            acVectorEngine.b1[branchNum] = piModel.getB1();
            acVectorEngine.b2[branchNum] = piModel.getB2();
            acVectorEngine.g1[branchNum] = piModel.getG1();
            acVectorEngine.g2[branchNum] = piModel.getG2();
            acVectorEngine.y[branchNum] = piModel.getY();
            acVectorEngine.ksi[branchNum] = piModel.getKsi();
            acVectorEngine.sinKsi[branchNum] = FastMath.sin(piModel.getKsi());
            acVectorEngine.cosKsi[branchNum] = FastMath.cos(piModel.getKsi());
            // y12 = g12+j.b12 = 1/(r+j.x)
            acVectorEngine.g12[branchNum] = piModel.getR() * y() * y();
            acVectorEngine.b12[branchNum] = -piModel.getX() * y() * y();
            acVectorEngine.networkDataInitialized[branchNum] = true;
        }
    }

    protected double b1() {
        return acVectorEngine.b1[branchNum];
    }

    protected double b2() {
        return acVectorEngine.b2[branchNum];
    }

    protected double g1() {
        return acVectorEngine.g1[branchNum];
    }

    protected double g2() {
        return acVectorEngine.g2[branchNum];
    }

    protected double y() {
        return acVectorEngine.y[branchNum];
    }

    protected double ksi() {
        return acVectorEngine.ksi[branchNum];
    }

    protected double g12() {
        return acVectorEngine.g12[branchNum];
    }

    protected double b12() {
        return acVectorEngine.b12[branchNum];
    }

}
