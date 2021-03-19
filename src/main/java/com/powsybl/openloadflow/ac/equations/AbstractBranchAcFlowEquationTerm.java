/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.equations;

import com.powsybl.openloadflow.equations.AbstractNamedEquationTerm;
import com.powsybl.openloadflow.network.ElementType;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.PiModel;
import net.jafama.FastMath;

import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
abstract class AbstractBranchAcFlowEquationTerm extends AbstractNamedEquationTerm {

    public static final double CURRENT_NORMALIZATION_FACTOR = 1000d / Math.sqrt(3d);

    protected final LfBranch branch;

    protected final double b1;
    protected final double b2;
    protected final double g1;
    protected final double g2;
    protected final double y;
    protected final double ksi;
    protected final double sinKsi;
    protected final double cosKsi;

    protected AbstractBranchAcFlowEquationTerm(LfBranch branch) {
        this.branch = Objects.requireNonNull(branch);
        PiModel piModel = branch.getPiModel();
        if (piModel.getR() == 0 && piModel.getX() == 0) {
            throw new IllegalArgumentException("Non impedant branch not supported: " + branch.getId());
        }
        b1 = piModel.getB1();
        b2 = piModel.getB2();
        g1 = piModel.getG1();
        g2 = piModel.getG2();
        y = 1 / piModel.getZ();
        ksi = piModel.getKsi();
        sinKsi = FastMath.sin(ksi);
        cosKsi = FastMath.cos(ksi);
    }

    @Override
    public ElementType getElementType() {
        return ElementType.BRANCH;
    }

    @Override
    public int getElementNum() {
        return branch.getNum();
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
