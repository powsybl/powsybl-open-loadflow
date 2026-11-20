/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.equations;

import com.powsybl.iidm.network.TwoSides;
import com.powsybl.openloadflow.equations.AbstractElementEquationTerm;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.PiModel;

import java.util.Objects;

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

    /**
     * Side whose flow or current the term evaluates. A non-reciprocal branch has a different series impedance for each side
     * (see {@link PiModel#getR21()}), so the side selects the impedance the term is built with.
     */
    protected final TwoSides side;

    protected AbstractBranchAcFlowEquationTerm(LfBranch branch, TwoSides side) {
        super(branch);
        this.side = Objects.requireNonNull(side);
        PiModel piModel = branch.getPiModel();
        if (piModel.getR() == 0 && piModel.getX() == 0) {
            throw new IllegalArgumentException("Non impedant branch not supported: " + branch.getId());
        }
        b1 = piModel.getB1();
        b2 = piModel.getB2();
        g1 = piModel.getG1();
        g2 = piModel.getG2();
        updateSeriesImpedance(piModel);
    }

    /**
     * Set y, ksi, g12 and b12 from the series impedance of the pi model on the side of this term.
     */
    public final void updateSeriesImpedance(PiModel piModel) {
        double r;
        double x;
        if (side == TwoSides.ONE) {
            r = piModel.getR();
            x = piModel.getX();
            y = piModel.getY();
            ksi = piModel.getKsi();
        } else {
            r = piModel.getR21();
            x = piModel.getX21();
            y = piModel.getY21();
            ksi = piModel.getKsi21();
        }
        // y12 = g12+j.b12 = 1/(r+j.x)
        g12 = r * y * y;
        b12 = -x * y * y;
    }

    public TwoSides getSide() {
        return side;
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
