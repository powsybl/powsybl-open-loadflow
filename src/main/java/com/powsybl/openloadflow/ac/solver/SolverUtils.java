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

    // List of always linear constraints
    private static List<AcEquationType> linearConstraintsTypes = new ArrayList<>(Arrays.asList(
            AcEquationType.BUS_TARGET_PHI,
            AcEquationType.DUMMY_TARGET_P,
            AcEquationType.DUMMY_TARGET_Q,
            AcEquationType.ZERO_V,
            AcEquationType.ZERO_PHI,
            AcEquationType.DISTR_SHUNT_B,
            AcEquationType.DISTR_RHO,
            AcEquationType.SHUNT_TARGET_B,
            AcEquationType.BRANCH_TARGET_ALPHA1,
            AcEquationType.BRANCH_TARGET_RHO1
    ));

    // List of always non-linear constraints
    private static List<AcEquationType> nonLinearConstraintsTypes = new ArrayList<>(Arrays.asList(
            AcEquationType.BUS_TARGET_P,
            AcEquationType.BUS_TARGET_Q,
            AcEquationType.BRANCH_TARGET_P,
            AcEquationType.BRANCH_TARGET_Q,
            AcEquationType.BUS_DISTR_SLACK_P,
            AcEquationType.DISTR_Q
    ));

    public static List<AcEquationType> getLinearConstraintsTypes() {
        return linearConstraintsTypes;
    }

    public static List<AcEquationType> getNonLinearConstraintsTypes() {
        return nonLinearConstraintsTypes;
    }

    // Classifies a constraint as linear or non-linear based on its type and terms
    public static boolean isLinear(AcEquationType typeEq, List<EquationTerm<AcVariableType, AcEquationType>> terms) {
        // Check if the constraint type is BUS_TARGET_V
        if (typeEq == AcEquationType.BUS_TARGET_V) {
            return terms.size() == 1; // If there's only one term, it is linear
        }
        return linearConstraintsTypes.contains(typeEq);
    }

    // Return lists of variables and coefficients to pass to Knitro for a linear constraint
    public VarAndCoefList getLinearConstraint(AcEquationType typeEq, int equationId, List<EquationTerm<AcVariableType, AcEquationType>> terms) {
        VarAndCoefList varAndCoefList = null;

        // Check if the constraint is linear
        if (isLinear(typeEq, terms)) {
            switch (typeEq) {
                case BUS_TARGET_V: // BUS_TARGET_V should be treated as linear
                case BUS_TARGET_PHI:
                case DUMMY_TARGET_P:
                case DUMMY_TARGET_Q:
                case SHUNT_TARGET_B:
                case BRANCH_TARGET_ALPHA1:
                case BRANCH_TARGET_RHO1:
                    varAndCoefList = addConstraintConstantTarget(typeEq, equationId, terms);
                    break;
                case DISTR_SHUNT_B:
                case DISTR_RHO:
                    varAndCoefList = addConstraintDistrQ(typeEq, equationId, terms);
                    break;
                case ZERO_V:
                case ZERO_PHI:
                    varAndCoefList = addConstraintZero(typeEq, equationId, terms);
                    break;
            }
        }
        return varAndCoefList;
    }

    public class VarAndCoefList {
        private List<Integer> listIdVar;
        private List<Double> listCoef;

        public VarAndCoefList(List<Integer> listIdVar, List<Double> listCoef) {
            this.listIdVar = listIdVar;
            this.listCoef = listCoef;
        }

        public List<Integer> getListIdVar() {
            return listIdVar;
        }

        public List<Double> getListCoef() {
            return listCoef;
        }
    }

    public VarAndCoefList addConstraintConstantTarget(AcEquationType typeEq, int equationId, List<EquationTerm<AcVariableType, AcEquationType>> terms) {
        // get the variable V/Theta/DummyP/DummyQ/... corresponding to the constraint
        int idVar = terms.get(0).getVariables().get(0).getRow();
        return new VarAndCoefList(List.of(idVar), List.of(1.0));
    }

    public VarAndCoefList addConstraintZero(AcEquationType typeEq, int equationId, List<EquationTerm<AcVariableType, AcEquationType>> terms) {
        // get the variables Vi and Vj / Thetai and Thetaj corresponding to the constraint
        int idVari = terms.get(0).getVariables().get(0).getRow();
        int idVarj = terms.get(1).getVariables().get(0).getRow();
        return new VarAndCoefList(Arrays.asList(idVari, idVarj), Arrays.asList(1.0, -1.0));
    }

    public VarAndCoefList addConstraintDistrQ(AcEquationType typeEq, int equationId, List<EquationTerm<AcVariableType, AcEquationType>> terms) {
        // get the variables corresponding to the constraint
        List<Integer> listVar = new ArrayList();
        List<Double> listCoef = new ArrayList<>();
        for (EquationTerm<AcVariableType, AcEquationType> equationTerm : terms) {
            double scalar = 0.0;
            if (((EquationTerm.MultiplyByScalarEquationTerm) equationTerm).getTerm() instanceof VariableEquationTerm<?, ?>) {
                scalar = ((EquationTerm.MultiplyByScalarEquationTerm) equationTerm).getScalarSupplier();
            } else if (((EquationTerm.MultiplyByScalarEquationTerm) equationTerm).getTerm() instanceof EquationTerm.MultiplyByScalarEquationTerm<?, ?>) {
                scalar = ((EquationTerm.MultiplyByScalarEquationTerm) equationTerm).getScalarSupplier();
                scalar *= ((EquationTerm.MultiplyByScalarEquationTerm) ((EquationTerm.MultiplyByScalarEquationTerm) equationTerm).getTerm()).getScalarSupplier();
            }
            listVar.add(((EquationTerm.MultiplyByScalarEquationTerm<AcVariableType, AcEquationType>) equationTerm).getTerm().getVariables().get(0).getRow());
            listCoef.add(scalar);
        }
        return new VarAndCoefList(listVar, listCoef);
    }
}
