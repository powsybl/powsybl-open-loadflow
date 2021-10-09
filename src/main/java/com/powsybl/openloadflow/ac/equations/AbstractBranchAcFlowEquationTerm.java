/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.equations;

import com.powsybl.openloadflow.equations.AbstractNamedEquationTerm;
import com.powsybl.openloadflow.network.ElementType;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
abstract class AbstractBranchAcFlowEquationTerm extends AbstractNamedEquationTerm<AcVariableType, AcEquationType> {

    protected final int num;

    protected AbstractBranchAcFlowEquationTerm(int num) {
        this.num = num;
    }

    @Override
    public ElementType getElementType() {
        return ElementType.BRANCH;
    }

    @Override
    public int getElementNum() {
        return num;
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
