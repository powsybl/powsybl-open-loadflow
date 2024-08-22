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
import com.powsybl.openloadflow.equations.*;

import java.util.*;

import static com.powsybl.openloadflow.ac.solver.KnitroSolverUtils.getBinaryVariableCorrespondingToEquation;

/**
 * @author Jeanne Archambault {@literal <jeanne.archambault at artelys.com>}
 */

public final class KnitroEquationsUtils {

    // List of linear constraints for indirect formulation (Outer Loops are external)
    public static List<AcEquationType> linearConstraintsTypesIndirectFormulation = new ArrayList<>(Arrays.asList(
            AcEquationType.BUS_TARGET_V,
            AcEquationType.BUS_TARGET_PHI,
            AcEquationType.DUMMY_TARGET_P,
            AcEquationType.DUMMY_TARGET_Q,
            AcEquationType.ZERO_V,
            AcEquationType.ZERO_PHI,
            AcEquationType.DISTR_Q,
            AcEquationType.DISTR_SHUNT_B,
            AcEquationType.DISTR_RHO,
            AcEquationType.SHUNT_TARGET_B,
            AcEquationType.BRANCH_TARGET_ALPHA1,
            AcEquationType.BRANCH_TARGET_RHO1
    ));

    public static List<AcEquationType> getLinearConstraintsTypesIndirectFormulation() {
        return linearConstraintsTypesIndirectFormulation;
    }

    // List of non-linear constraints for indirect formulation (Outer Loops are external)
    public static List<AcEquationType> nonLinearConstraintsTypesIndirectFormulation = new ArrayList<>(Arrays.asList(
            AcEquationType.BUS_TARGET_P,
            AcEquationType.BUS_TARGET_Q,
            AcEquationType.BRANCH_TARGET_P,
            AcEquationType.BRANCH_TARGET_Q,
            AcEquationType.BUS_DISTR_SLACK_P
    ));

    public static List<AcEquationType> getNonLinearConstraintsTypesIndirectFormulation() {
        return nonLinearConstraintsTypesIndirectFormulation;
    }

    // List of quadratic constraints for indirect formulation (Outer Loops are external)
    public static List<AcEquationType> quadraticConstraintsTypesIndirectFormulation = new ArrayList<>();

    public static List<AcEquationType> getQuadraticConstraintsTypesIndirectFormulation() {
        return quadraticConstraintsTypesIndirectFormulation;
    }


    // List of linear constraints for direct formulation
    public static List<AcEquationType> linearConstraintsTypesDirectFormulation = new ArrayList<>(Arrays.asList(
            AcEquationType.BUS_TARGET_PHI,
            AcEquationType.DUMMY_TARGET_P,
            AcEquationType.DUMMY_TARGET_Q,
            AcEquationType.ZERO_V,
            AcEquationType.ZERO_PHI,
            AcEquationType.DISTR_Q,
            AcEquationType.DISTR_SHUNT_B,
            AcEquationType.DISTR_RHO,
            AcEquationType.SHUNT_TARGET_B,
            AcEquationType.BRANCH_TARGET_ALPHA1,
            AcEquationType.BRANCH_TARGET_RHO1
    ));

    public static List<AcEquationType> getLinearConstraintsTypesDirectFormulation() {
        return linearConstraintsTypesDirectFormulation;
    }

    // List of non-linear constraints for direct formulation
    public static List<AcEquationType> nonLinearConstraintsTypesDirectFormulation = new ArrayList<>(Arrays.asList(
            AcEquationType.BUS_TARGET_P,
            AcEquationType.BUS_TARGET_Q,
            AcEquationType.BRANCH_TARGET_P,
            AcEquationType.BRANCH_TARGET_Q,
            AcEquationType.BUS_DISTR_SLACK_P
    ));

    public static List<AcEquationType> getNonLinearConstraintsTypesDirectFormulation() {
        return nonLinearConstraintsTypesDirectFormulation;
    }

    // List of non-linear constraints for direct formulation
    public static List<AcEquationType> quadraticConstraintsTypesDirectFormulation = new ArrayList<>(Arrays.asList(
            AcEquationType.BUS_TARGET_V
    ));

    public static List<AcEquationType> getQuadraticConstraintsTypesDirectFormulation() {
        return quadraticConstraintsTypesDirectFormulation;
    }

    // Get list of linear constraints
    public static List<AcEquationType> getLinearConstraintsTypes(KnitroSolverParameters knitroParameters) {
        if (knitroParameters.isDirectOuterLoopsFormulation()) {
            return getLinearConstraintsTypesDirectFormulation();
        } else {
            return getLinearConstraintsTypesIndirectFormulation();
        }
    }

    // Get list of non-linear constraints
    public static List<AcEquationType> getNonLinearConstraintsTypes(KnitroSolverParameters knitroParameters) {
        if (knitroParameters.isDirectOuterLoopsFormulation()) {
            return getNonLinearConstraintsTypesDirectFormulation();
        } else {
            return getNonLinearConstraintsTypesIndirectFormulation();
        }
    }

    // Get list of quadratic constraints
    public static List<AcEquationType> getQuadraticConstraintsTypes(KnitroSolverParameters knitroParameters) {
        if (knitroParameters.isDirectOuterLoopsFormulation()) {
            return getQuadraticConstraintsTypesDirectFormulation();
        } else {
            return getQuadraticConstraintsTypesIndirectFormulation();
        }
    }

    //  Get variables and coefficients lists for linear constraints for indirect formulation
    public VarAndCoefList getLinearConstraintIndirectFormulation(AcEquationType typeEq, int equationId, List<EquationTerm<AcVariableType, AcEquationType>> terms) {
        VarAndCoefList varAndCoefList = null;
        switch (typeEq) {
            case BUS_TARGET_V:
            case BUS_TARGET_PHI:
            case DUMMY_TARGET_P:
            case DUMMY_TARGET_Q:
            case SHUNT_TARGET_B:
            case BRANCH_TARGET_ALPHA1:
            case BRANCH_TARGET_RHO1:
                varAndCoefList = addConstraintConstantTarget(equationId, terms);
                break;
            case DISTR_Q:
            case DISTR_SHUNT_B:
            case DISTR_RHO:
                varAndCoefList = addConstraintDistrQ(equationId, terms);
                break;
            case ZERO_V:
            case ZERO_PHI:
                varAndCoefList = addConstraintZero(equationId, terms);
                break;
        }
        return varAndCoefList;
    }

    //  Get variables and coefficients lists for linear constraints for direct formulation
    public VarAndCoefList getLinearConstraintDirectFormulation(AcEquationType typeEq, int equationId, List<EquationTerm<AcVariableType, AcEquationType>> terms) {
        VarAndCoefList varAndCoefList = null;
        switch (typeEq) {
            case BUS_TARGET_PHI:
            case DUMMY_TARGET_P:
            case DUMMY_TARGET_Q:
            case SHUNT_TARGET_B:
            case BRANCH_TARGET_ALPHA1:
            case BRANCH_TARGET_RHO1:
                varAndCoefList = addConstraintConstantTarget(equationId, terms);
                break;
            case DISTR_Q:
            case DISTR_SHUNT_B:
            case DISTR_RHO:
                varAndCoefList = addConstraintDistrQ(equationId, terms);
                break;
            case ZERO_V:
            case ZERO_PHI:
                varAndCoefList = addConstraintZero(equationId, terms);
                break;
        }
        return varAndCoefList;
    }

    // Return lists of variables and coefficients to pass to Knitro for a linear constraint
    public VarAndCoefList getLinearConstraint(KnitroSolverParameters knitroParameters, AcEquationType typeEq, int equationId, List<EquationTerm<AcVariableType, AcEquationType>> terms) {
        if (knitroParameters.isDirectOuterLoopsFormulation()) {
            return getLinearConstraintDirectFormulation(typeEq,equationId,terms);
        } else {
            return getLinearConstraintIndirectFormulation(typeEq,equationId,terms);
        }
    }

    //  Get variables and coefficients lists for quadratic constraints for indirect formulation
    public List<VarAndCoefList> getQuadraticConstraintIndirectFormulation(AcEquationType typeEq, int equationId, List<EquationTerm<AcVariableType, AcEquationType>> terms, TargetVector targetVector, EquationSystem equationSystem) {
        return Arrays.asList(null,null);
    }

    //  Get variables and coefficients lists for quadratic constraints for direct formulation
    public List<VarAndCoefList> getQuadraticConstraintDirectFormulation(AcEquationType typeEq, int equationId, List<EquationTerm<AcVariableType, AcEquationType>> terms, TargetVector targetVector, EquationSystem equationSystem, KnitroSolverParameters knitroParameters) {
        VarAndCoefList varAndCoefListLin = new VarAndCoefList(new ArrayList<>(), new ArrayList<>());
        VarAndCoefList varAndCoefListQuadra = new VarAndCoefList(new ArrayList<>(), new ArrayList<>());
        //TODO
        switch (typeEq) {
            case BUS_TARGET_V:
                int idVar = terms.get(0).getVariables().get(0).getRow();
                int idBinaryVar = getBinaryVariableCorrespondingToEquation(terms, typeEq, equationSystem, knitroParameters); //TODO index du x correspondant
                // Quadratic part
                varAndCoefListQuadra.listIdVar = Arrays.asList(idVar,idBinaryVar);
                varAndCoefListQuadra.listCoef = Collections.singletonList(1.0);

                // Linear part
                double coefLinear = -1.0*targetVector.getArray()[terms.get(0).getEquation().getColumn()]; //TODO pass target to argument
                varAndCoefListLin.listIdVar = Collections.singletonList(idBinaryVar);
                varAndCoefListLin.listCoef = Collections.singletonList(coefLinear);
        }
        return Arrays.asList(varAndCoefListQuadra,varAndCoefListLin);
    }

    // Return lists of variables and coefficients to pass to Knitro for a quadratic constraint
    public List<VarAndCoefList> getQuadraticConstraint(KnitroSolverParameters knitroParameters, AcEquationType typeEq, int equationId, List<EquationTerm<AcVariableType, AcEquationType>> terms, TargetVector targetVector, EquationSystem equationSystem) {
        if (knitroParameters.isDirectOuterLoopsFormulation()) {
            return getQuadraticConstraintDirectFormulation(typeEq,equationId,terms,targetVector, equationSystem, knitroParameters);
        } else {
            return getQuadraticConstraintIndirectFormulation(typeEq,equationId,terms,targetVector, equationSystem);
        }
    }

    public static class VarAndCoefList {
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

    public VarAndCoefList addConstraintConstantTarget(int equationId, List<EquationTerm<AcVariableType, AcEquationType>> terms) {
        // get the variable V/Theta/DummyP/DummyQ/... corresponding to the constraint
        int idVar = terms.get(0).getVariables().get(0).getRow();
        return new VarAndCoefList(Arrays.asList(idVar), Arrays.asList(1.0));
    }

    public VarAndCoefList addConstraintZero(int equationId, List<EquationTerm<AcVariableType, AcEquationType>> terms) {
        // get the variables Vi and Vj / Thetai and Thetaj corresponding to the constraint
        int idVari = terms.get(0).getVariables().get(0).getRow();
        int idVarj = terms.get(1).getVariables().get(0).getRow();
        return new VarAndCoefList(Arrays.asList(idVari, idVarj), Arrays.asList(1.0, -1.0));
    }

    public VarAndCoefList addConstraintDistrQ(int equationId, List<EquationTerm<AcVariableType, AcEquationType>> terms) {
        // get the variables corresponding to the constraint
        List<Integer> listVar = new ArrayList<>();
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
