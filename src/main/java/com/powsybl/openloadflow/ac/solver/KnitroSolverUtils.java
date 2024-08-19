/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */

package com.powsybl.openloadflow.ac.solver;
import com.artelys.knitro.api.KNConstants;
import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.ac.equations.AcEquationType;
import com.powsybl.openloadflow.equations.*;
import com.powsybl.openloadflow.equations.Equation;

import java.util.*;

/**
 * @author Jeanne Archambault {@literal <jeanne.archambault at artelys.com>}
 */

public class KnitroSolverUtils {

    public static int getBinaryVariableCorrespondingToEquation(List<EquationTerm<AcVariableType, AcEquationType>> terms, AcEquationType typeEq, EquationSystem equationSystem, KnitroSolverParameters knitroParameters){
        int correspondingBinaryVariable = 0 ;
        switch (typeEq) {
            case BUS_TARGET_V:
                // For equations setting V, the equation becomes V_i x_i = Vref_i x_i
                // We need to get the variable x_i
                int varVIndex = ((VariableEquationTerm) terms.get(0)).getElementNum(); // index of V variable
                // build list of buses having an equation setting V
                List<Integer> listVarVIndexes = new ArrayList<>(); // for every equation setting V, we store the index of the corresponding V variable
                for (Equation equation : getSortedEquations(equationSystem, knitroParameters)) {
                    if (equation.getType() == AcEquationType.BUS_TARGET_V) {
                        listVarVIndexes.add(((VariableEquationTerm) equation.getTerms().get(0)).getElementNum());
                    }
                }
                int indexOfVarInList = listVarVIndexes.indexOf(varVIndex);
                correspondingBinaryVariable = getXVar(indexOfVarInList, equationSystem);
                break;
        }
        return correspondingBinaryVariable;
    }

    public static int getXVar(int varId, EquationSystem equationSystem){
        // varId is the index of the bus in the list of all buses that have an equation setting V
        int numVar = equationSystem.getVariableSet().getVariables().size();
        return numVar + 2*varId ;
    }

    public static int getYVar(int varId, EquationSystem equationSystem){
        // varId is the index of the bus in the list of all buses that have an equation setting V
        int numVar = equationSystem.getVariableSet().getVariables().size();
        return numVar + 2*varId + 1 ;
    }

    public static int getNumAllVar(EquationSystem equationSystem, KnitroSolverParameters knitroParameters){
        int numNonBinaryVar = getNumNonBinaryVar(equationSystem, knitroParameters);
        if (knitroParameters.isDirectOuterLoopsFormulation()) {
            return numNonBinaryVar + getNumBinaryVar(equationSystem, knitroParameters);
        } else {
            return numNonBinaryVar;
        }
    }

    public static int getNumBinaryVar(EquationSystem equationSystem, KnitroSolverParameters knitroParameters) {
        int numBinaryVar = 0;
        List<Equation<AcVariableType, AcEquationType>> sortedEquations = getSortedEquations(equationSystem, knitroParameters); //TODO reprendre de manière + compacte
        for (Equation equation:sortedEquations){
            if (equation.getType()==AcEquationType.BUS_TARGET_V){
                // For initially PV buses, we define one binary variable to allow the bus to be PV, and another one to allow the bus to switch to PQ
                numBinaryVar += 2;
            }
        }
        return numBinaryVar;
    }

    public static int getNumNonBinaryVar(EquationSystem equationSystem, KnitroSolverParameters knitroParameters) {
        return equationSystem.getVariableSet().getVariables().size();
    }

    public static int getNumConstraints(EquationSystem equationSystem, KnitroSolverParameters knitroParameters){
        int numInitialEquations = equationSystem.getIndex().getSortedEquationsToSolve().size(); //initial number of equations, for undirect formulation
        if (knitroParameters.isDirectOuterLoopsFormulation()) {
            return numInitialEquations + getNumOuterLoopConstraints(equationSystem, knitroParameters);
        } else {
            return numInitialEquations;
        }
    }

    public static int getNumOuterLoopConstraints(EquationSystem equationSystem, KnitroSolverParameters knitroParameters) {
        int numAddedEquations = 0;
        List<Equation<AcVariableType, AcEquationType>> equationsList = equationSystem.getEquations().stream().toList();
        for (Equation equation:equationsList){
            if (equation.getType()==AcEquationType.BUS_TARGET_V&&equation.isActive()){
                // For initially PV buses, we modify the equation in V and add 5 equations/inequalities :
                // 1) Q is within its bounds (valid for both buses that stay PV or that switch to PQ)
                // 2) Q is set to its lower bound (for buses that end up PQ)
                // 3) V is lower than target V (for buses that end up PQ, with Q = Q_lo)
                // 4) Q is set to its upper bound (for buses that end up PQ)
                // 5) V is larger than target V (for buses that end up PQ, with Q = Q_up)
                numAddedEquations += 5;
            }
        }
        return numAddedEquations;
    }

    public static List<Integer> getVariablesTypes(EquationSystem equationSystem, KnitroSolverParameters knitroParameters){
        // Types of inner loop variables
        int numNonBinaryVar =  getNumNonBinaryVar(equationSystem, knitroParameters);
        List<Integer> typesNonBinaryVar = new ArrayList<>(Collections.nCopies(numNonBinaryVar, KNConstants.KN_VARTYPE_CONTINUOUS));
        // Types of outer loop variables
        int numBinaryVar = getNumBinaryVar(equationSystem, knitroParameters);
        List<Integer> typesBinaryVar = new ArrayList<>(Collections.nCopies(numBinaryVar, KNConstants.KN_VARTYPE_BINARY));
        // Concatenate the lists
        typesNonBinaryVar.addAll(typesBinaryVar);
        return typesNonBinaryVar;
    }

    public static List<Variable<AcVariableType>> getSortedNonBinaryVarList(EquationSystem equationSystem, KnitroSolverParameters knitroParameters){
        return equationSystem.getIndex().getSortedVariablesToFind();
    }

    public static List<Equation<AcVariableType, AcEquationType>>  getSortedEquations(EquationSystem equationSystem, KnitroSolverParameters knitroParameters) {
        return equationSystem.getIndex().getSortedEquationsToSolve();
    }

    public static List<Double> getInitialStateVector(EquationSystem equationSystem, KnitroSolverParameters knitroParameters){
        int numNonBinaryVar =  getNumNonBinaryVar(equationSystem, knitroParameters);
        int numVar = getNumAllVar(equationSystem, knitroParameters);
        List<Double> listXInitial = new ArrayList<>(numVar);
        // Non-binary variables
        for (int i = 0; i < numNonBinaryVar; i++) {
            listXInitial.add(equationSystem.getStateVector().get(i));
        }
        // Binary variables
        for (int i = numNonBinaryVar; i < numVar; i++) {
            if (i % 2 == 0) {
                listXInitial.add(1.0);
            } else {
                listXInitial.add(0.0);
            }
        }
        return listXInitial;
    }


    public static List getTargetVector(EquationSystem equationSystem, KnitroSolverParameters knitroParameters, TargetVector targetVector) {
        List innerLoopTargetVector = Arrays.stream(targetVector.getArray()).boxed().toList();
        if (knitroParameters.isDirectOuterLoopsFormulation()) {
            // Inner loop target vector
            List<Equation<AcVariableType, AcEquationType>> equationsList = equationSystem.getEquations().stream().toList();
            for (Equation equation:equationsList){
                if (equation.getType()==AcEquationType.BUS_TARGET_V){
                    innerLoopTargetVector.set(equation.getColumn(), 0);
                }
            }
            // Outer loop target vector
            // TODO : sachant que toutes les contraintes n'ont pas de second membre, il faudra passer dans Knitro à la fois la liste des seconds memmbres et la liste des numéros d'équations correspondants
            // Concatenate vectors
            return innerLoopTargetVector;
        } else {
            return innerLoopTargetVector;
        }
    }
}
