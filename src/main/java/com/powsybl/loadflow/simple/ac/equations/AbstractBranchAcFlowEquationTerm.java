/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.loadflow.simple.ac.equations;

import com.powsybl.loadflow.simple.equations.EquationTerm;
import com.powsybl.loadflow.simple.equations.Variable;
import com.powsybl.loadflow.simple.network.LfBranch;
import net.jafama.FastMath;

import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
abstract class AbstractBranchAcFlowEquationTerm implements EquationTerm {

    protected final LfBranch branch;

    protected final double r1;
    protected final double r2;
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
        r1 = this.branch.r1();
        r2 = this.branch.r2();
        b1 = this.branch.b1();
        b2 = this.branch.b2();
        g1 = this.branch.g1();
        g2 = this.branch.g2();
        y = this.branch.y();
        ksi = this.branch.ksi();
        sinKsi = FastMath.sin(ksi);
        cosKsi = FastMath.cos(ksi);
    }

    @Override
    public boolean hasRhs() {
        return false;
    }

    @Override
    public double rhs(Variable variable) {
        return 0;
    }
}
