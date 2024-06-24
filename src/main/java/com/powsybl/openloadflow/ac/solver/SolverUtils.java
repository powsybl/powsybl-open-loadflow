/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */

package com.powsybl.openloadflow.ac.solver;
import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.ac.equations.AcEquationType;
import com.powsybl.openloadflow.equations.EquationTerm;
import com.powsybl.openloadflow.equations.VariableEquationTerm;

import java.util.*;

/**
 * @author Jeanne Archambault {@literal <jeanne.archambault at artelys.com>}
 */

public final class SolverUtils {

    public VarAndCoefList addConstraint(AcEquationType typeEq, int equationId, List<EquationTerm<AcVariableType, AcEquationType>> terms) {
        List<Integer> listVar = new ArrayList<>();
        List<Double> listCoef = new ArrayList<>();

        switch (typeEq) {
            case BUS_TARGET_V:
            case BUS_TARGET_PHI:
            case DUMMY_TARGET_P:
            case DUMMY_TARGET_Q:
                listVar = addConstraintTargetVPhiDummyPQ(typeEq, equationId, terms).listIdVar;
                listCoef = addConstraintTargetVPhiDummyPQ(typeEq, equationId, terms).listCoef;
                break;
            case ZERO_V:
            case ZERO_PHI:
                listVar = addConstraintZero(typeEq, equationId, terms).listIdVar;
                listCoef = addConstraintZero(typeEq, equationId, terms).listCoef;
                break;
            case DISTR_Q:
                listVar = addConstraintDistrQ(typeEq, equationId, terms).listIdVar;
                listCoef = addConstraintDistrQ(typeEq, equationId, terms).listCoef;
                break;
        }
        return new VarAndCoefList(listVar,listCoef);
    }

    public class VarAndCoefList {
        private List<Integer> listIdVar;
        private List<Double> listCoef;

        public VarAndCoefList(List<Integer> listIdVar,  List<Double> listCoef) {
            this.listIdVar = listIdVar;
            this.listCoef = listCoef;
        }

        public List<Integer> getIdVar() {
            return listIdVar;
        }

        public List<Double> getCoef() {
            return listCoef;
        }
    }

    public VarAndCoefList addConstraintTargetVPhiDummyPQ(AcEquationType typeEq, int equationId, List<EquationTerm<AcVariableType, AcEquationType>> terms) {
        // get the variable V/Theta/DummyP/DummyQ corresponding to the constraint
        int idVar = terms.get(0).getVariables().get(0).getRow();
        double coef = 1.0;
        return new VarAndCoefList(Arrays.asList(idVar), Arrays.asList(coef));
    }

    public VarAndCoefList addConstraintZero(AcEquationType typeEq, int equationId, List<EquationTerm<AcVariableType, AcEquationType>> terms) {
        // get the variables Vi and Vj / Thetai and Thetaj corresponding to the constraint
        int idVari = terms.get(0).getVariables().get(0).getRow();
        int idVarj = terms.get(1).getVariables().get(0).getRow();
        return new VarAndCoefList(Arrays.asList(idVari, idVarj), Arrays.asList(1.0,-1.0));
    }

    public VarAndCoefList addConstraintDistrQ(AcEquationType typeEq, int equationId, List<EquationTerm<AcVariableType, AcEquationType>> terms) {
        // get the variables corresponding to the constraint
        List<Integer> listVar = new ArrayList();
        List<Double> listCoef = new ArrayList<>();
        for (EquationTerm<AcVariableType, AcEquationType> equationTerm : terms) {
            double scalar = 0.0;
            if (((EquationTerm.MultiplyByScalarEquationTerm) equationTerm).getTerm() instanceof VariableEquationTerm<?,?>) {
                scalar = ((EquationTerm.MultiplyByScalarEquationTerm) equationTerm).getScalarSupplier();
            } else if (((EquationTerm.MultiplyByScalarEquationTerm) equationTerm).getTerm() instanceof EquationTerm.MultiplyByScalarEquationTerm<?,?>) {
                scalar = ((EquationTerm.MultiplyByScalarEquationTerm) equationTerm).getScalarSupplier();
                scalar *= ((EquationTerm.MultiplyByScalarEquationTerm) ((EquationTerm.MultiplyByScalarEquationTerm) equationTerm).getTerm()).getScalarSupplier();
            }
            listVar.add(((EquationTerm.MultiplyByScalarEquationTerm<AcVariableType, AcEquationType>) equationTerm).getTerm().getVariables().get(0).getRow());
            listCoef.add(scalar);
        }
        return new VarAndCoefList(listVar,listCoef);
    }
}
