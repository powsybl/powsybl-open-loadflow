/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.dc;

import com.powsybl.openloadflow.dc.equations.DcEquationType;
import com.powsybl.openloadflow.dc.equations.DcVariableType;
import com.powsybl.openloadflow.equations.Equation;
import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.equations.EquationTerm;
import com.powsybl.openloadflow.equations.TargetVector;
import com.powsybl.openloadflow.network.*;

/**
 * @author Jean-Luc Bouchot (Artelys) <jlbouchot at gmail.com>
 */
public class DcTargetVector extends TargetVector<DcVariableType, DcEquationType> {

    public static void init(Equation<DcVariableType, DcEquationType> equation, LfNetwork network, double[] targets) {
        switch (equation.getType()) {
            case BUS_TARGET_P:
                targets[equation.getColumn()] = network.getBus(equation.getElementNum()).getTargetP();
                break;

            case BUS_TARGET_PHI:
                targets[equation.getColumn()] = 0;
                break;

            case BRANCH_TARGET_P:
                targets[equation.getColumn()] = LfBranch.getDiscretePhaseControlTarget(network.getBranch(equation.getElementNum()), DiscretePhaseControl.Unit.MW);
                break;

            case BRANCH_TARGET_ALPHA1:
                targets[equation.getColumn()] = network.getBranch(equation.getElementNum()).getPiModel().getA1();
                break;

            case ZERO_PHI:
                targets[equation.getColumn()] = LfBranch.getA(network.getBranch(equation.getElementNum()));
                break;

            default:
                throw new IllegalStateException("Unknown state variable type: " + equation.getType());
        }

        for (EquationTerm<DcVariableType, DcEquationType> term : equation.getTerms()) {
            if (term.isActive() && term.hasRhs()) {
                targets[equation.getColumn()] -= term.rhs();
            }
        }
    }

    public DcTargetVector(LfNetwork network, EquationSystem<DcVariableType, DcEquationType> equationSystem) {
        super(network, equationSystem, DcTargetVector::init);
    }
}
