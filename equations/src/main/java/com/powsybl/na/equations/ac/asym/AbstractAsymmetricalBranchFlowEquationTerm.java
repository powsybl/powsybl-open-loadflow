/**
 * Copyright (c) 2023, Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 * Copyright (c) 2023, Geoffroy Jamgotchian <geoffroy.jamgotchian at gmail.com>
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.na.equations.ac.asym;

import com.powsybl.na.equations.AbstractElementEquationTerm;
import com.powsybl.na.equations.ac.AcEquationType;
import com.powsybl.na.equations.ac.AcVariableType;
import com.powsybl.na.network.LfAsymLine;
import com.powsybl.na.network.LfAsymLineAdmittanceMatrix;
import com.powsybl.na.network.LfBranch;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at gmail.com>}
 * @author Jean-Baptiste Heyberger {@literal <jbheyberger at gmail.com>}
 */
abstract class AbstractAsymmetricalBranchFlowEquationTerm extends AbstractElementEquationTerm<LfBranch, AcVariableType, AcEquationType> {

    // Classical line parameters are replaced by a 12x12 admittance matrix
    protected final LfAsymLineAdmittanceMatrix y;

    protected AbstractAsymmetricalBranchFlowEquationTerm(LfBranch branch) {
        super(branch);
        LfAsymLine asymLine = branch.getAsymLine();
        if (asymLine == null) {
            throw new IllegalStateException("Line : " + branch.getId() + " has no asymmetric extension but is required here ");
        }
        y = asymLine.getAdmittanceMatrix();
    }
}
