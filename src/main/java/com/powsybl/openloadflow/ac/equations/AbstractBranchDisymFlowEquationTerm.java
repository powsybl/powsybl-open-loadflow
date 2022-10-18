/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.equations;

import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.openloadflow.equations.AbstractBranchEquationTerm;
import com.powsybl.openloadflow.network.Extensions.AsymLine;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.PiModel;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
abstract class AbstractBranchDisymFlowEquationTerm extends AbstractBranchEquationTerm<AcVariableType, AcEquationType> {

    //protected final double b1;
    //protected final double b2;
    //protected final double g1;
    //protected final double g2;
    //protected final double ksi;

    // Classical line parameters are replaced by a 12x12 admittance matrix
    protected final DenseMatrix mYodi;


    protected AbstractBranchDisymFlowEquationTerm(LfBranch branch) {
        super(branch);
        PiModel piModel = branch.getPiModel();
        if (piModel.getR() == 0 && piModel.getX() == 0) {
            throw new IllegalArgumentException("Non impedant branch not supported: " + branch.getId());
        }
        AsymLine asymLine = (AsymLine) branch.getProperty(AsymLine.PROPERTY_ASYMMETRICAL);
        if (asymLine == null) {
            throw new IllegalStateException("Line : " + branch.getId() + " has no dissymmetric extension but is required here : ");
        }
        mYodi = asymLine.getAdmittanceTerms().getmYodi();
        /*b1 = piModel.getB1();
        b2 = piModel.getB2();
        g1 = piModel.getG1();
        g2 = piModel.getG2();
        y = piModel.getY();
        ksi = piModel.getKsi();*/
    }

    public DenseMatrix getmYodi() {
        return mYodi;
    }
}
