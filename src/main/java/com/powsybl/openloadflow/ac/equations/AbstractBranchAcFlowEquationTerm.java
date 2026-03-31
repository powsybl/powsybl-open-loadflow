/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
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

    // These values are not final because they can be modified by tap position changes
    protected double b1;
    protected double b2;
    protected double g1;
    protected double g2;
    protected double y;
    protected double ksi;
    protected double g12;
    protected double b12;

    protected AbstractBranchAcFlowEquationTerm(LfBranch branch) {
        super(branch);
        PiModel piModel = branch.getPiModel();
        if (piModel.getR() == 0 && piModel.getX() == 0) {
            throw new IllegalArgumentException("Non impedant branch not supported: " + branch.getId());
        }
        b1 = piModel.getB1();
        b2 = piModel.getB2();
        g1 = piModel.getG1();
        g2 = piModel.getG2();
        y = piModel.getY();
        ksi = piModel.getKsi();
        // y12 = g12+j.b12 = 1/(r+j.x)
        g12 = piModel.getR() * y * y;
        b12 = -piModel.getX() * y * y;
    }

    public void setB1(double b1) {
        this.b1 = b1;
    }

    public void setB2(double b2) {
        this.b2 = b2;
    }

    public void setG1(double g1) {
        this.g1 = g1;
    }

    public void setG2(double g2) {
        this.g2 = g2;
    }

    public void setB12(double b12) {
        this.b12 = b12;
    }

    public void setG12(double g12) {
        this.g12 = g12;
    }

    public void setY(double y) {
        this.y = y;
    }

    public void setKsi(double ksi) {
        this.ksi = ksi;
    }

    public double b1() {
        return b1;
    }

    public double b2() {
        return b2;
    }

    public double g1() {
        return g1;
    }

    public double g2() {
        return g2;
    }

    public double y() {
        return y;
    }

    public double ksi() {
        return ksi;
    }
}
