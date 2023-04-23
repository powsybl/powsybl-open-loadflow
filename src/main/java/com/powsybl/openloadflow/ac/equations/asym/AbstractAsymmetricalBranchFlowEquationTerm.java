/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.equations.asym;

import com.powsybl.openloadflow.ac.equations.AcEquationType;
import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.equations.AbstractElementEquationTerm;
import com.powsybl.openloadflow.network.extensions.AsymLine;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.extensions.AsymLineAdmittanceMatrix;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
abstract class AbstractAsymmetricalBranchFlowEquationTerm extends AbstractElementEquationTerm<LfBranch, AcVariableType, AcEquationType> {

    // Classical line parameters are replaced by a 12x12 admittance matrix
    protected final AsymLineAdmittanceMatrix y;

    protected AbstractAsymmetricalBranchFlowEquationTerm(LfBranch branch) {
        super(branch);
        AsymLine asymLine = (AsymLine) branch.getProperty(AsymLine.PROPERTY_ASYMMETRICAL);
        if (asymLine == null) {
            throw new IllegalStateException("Line : " + branch.getId() + " has no dissymmetric extension but is required here ");
        }
        y = asymLine.getAdmittanceMatrix();
    }

    public AsymLineAdmittanceMatrix getY() {
        return y;
    }
}
