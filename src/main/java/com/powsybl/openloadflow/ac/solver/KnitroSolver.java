/**
 * Copyright (c) 2024, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.solver;

import com.artelys.knitro.api.*;
import com.artelys.knitro.api.callbacks.*;
import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.report.ReportNode;
import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.openloadflow.ac.equations.AcEquationType;
import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.equations.*;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.util.VoltageInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.google.common.primitives.Doubles.toArray;
import static com.powsybl.openloadflow.ac.solver.KnitroSolverUtils.*;
//import static com.powsybl.openloadflow.ac.solver.ExternalSolverUtils.getNumVariables;

/**
 * @author Pierre Arvy {@literal <pierre.arvy at artelys.com>}
 * @author Jeanne Archambault {@literal <jeanne.archambault at artelys.com>}
 */
public class KnitroSolver extends AbstractNonLinearExternalSolver {

    private static final Logger LOGGER = LoggerFactory.getLogger(KnitroSolver.class);

    protected static KnitroSolverParameters knitroParameters = new KnitroSolverParameters();

    public KnitroSolver(LfNetwork network, KnitroSolverParameters knitroParameters,
                        EquationSystem<AcVariableType, AcEquationType> equationSystem, JacobianMatrix<AcVariableType, AcEquationType> j,
                        TargetVector<AcVariableType, AcEquationType> targetVector, EquationVector<AcVariableType, AcEquationType> equationVector,
                        boolean detailedReport) {
        super(network, equationSystem, j, targetVector, equationVector, detailedReport);
        this.knitroParameters = knitroParameters;
    }

    @Override
    public String getName() {
        return "Knitro Solver";
    }

    // List of all possible Knitro status
    public enum KnitroStatus {
        CONVERGED_TO_LOCAL_OPTIMUM,
        CONVERGED_TO_FEASIBLE_APPROXIMATE_SOLUTION,
        TERMINATED_AT_INFEASIBLE_POINT,
        PROBLEM_UNBOUNDED,
        TERMINATED_DUE_TO_PRE_DEFINED_LIMIT,
        INPUT_OR_NON_STANDARD_ERROR
    }

    // Get AcStatus equivalent from Knitro Status, and log Knitro Status
    public AcSolverStatus getAcStatusAndKnitroStatus(int knitroStatus) {
        if (knitroStatus == 0) {
            LOGGER.info("Knitro Status : {}", KnitroStatus.CONVERGED_TO_LOCAL_OPTIMUM);
            return AcSolverStatus.CONVERGED;
        } else if (-199 <= knitroStatus & knitroStatus <= -100) {
            LOGGER.info("Knitro Status : {}", KnitroStatus.CONVERGED_TO_FEASIBLE_APPROXIMATE_SOLUTION);
            return AcSolverStatus.CONVERGED;
        } else if (-299 <= knitroStatus & knitroStatus <= -200) {
            LOGGER.info("Knitro Status : {}", KnitroStatus.TERMINATED_AT_INFEASIBLE_POINT);
            return AcSolverStatus.SOLVER_FAILED;
        } else if (-399 <= knitroStatus & knitroStatus <= -300) {
            LOGGER.info("Knitro Status : {}", KnitroStatus.PROBLEM_UNBOUNDED);
            return AcSolverStatus.SOLVER_FAILED;
        } else if (-499 <= knitroStatus & knitroStatus <= -400) {
            LOGGER.info("Knitro Status : {}", KnitroStatus.TERMINATED_DUE_TO_PRE_DEFINED_LIMIT);
            return AcSolverStatus.MAX_ITERATION_REACHED;
        } else if (-599 <= knitroStatus & knitroStatus <= -500) {
            LOGGER.info("Knitro Status : {}", KnitroStatus.INPUT_OR_NON_STANDARD_ERROR);
            return AcSolverStatus.NO_CALCULATION;
        } else {
            LOGGER.info("Knitro Status : unknown");
            throw new IllegalArgumentException("Unknown Knitro Status");
        }
    }

    private static final class KnitroProblem extends KNProblem {
        /*------------------------------------------------------------------*/
        /*     FUNCTION callbackEvalF                                       */
        /*------------------------------------------------------------------*/

        private final class CallbackEvalFC extends KNEvalFCCallback {

            private final EquationSystem equationSystem;
            private final LfNetwork lfNetwork;
            private final List<Integer> listNonLinearConstsInnerLoop;
            private final List<Integer> listNonLinearConstsOuterLoop;

            private CallbackEvalFC(EquationSystem equationSystem, LfNetwork lfNetwork, List<Integer> listNonLinearConstsInnerLoop, List<Integer> listNonLinearConstsOuterLoop) {
                this.equationSystem = equationSystem;
                this.lfNetwork = lfNetwork;
                this.listNonLinearConstsInnerLoop = listNonLinearConstsInnerLoop;
                this.listNonLinearConstsOuterLoop = listNonLinearConstsOuterLoop;
            }

            @Override
            public void evaluateFC(final List<Double> x, final List<Double> obj, final List<Double> c) {
                LOGGER.trace("============ Knitro evaluating callback function ============");

                // =============== Objective ===============

                // =============== Add non-linear constraints in P and Q ===============

                // --- Update current state ---
                StateVector currentState = new StateVector(toArray(x));
                LOGGER.trace("Current state vector {}", currentState.get());
                LOGGER.trace("Evaluating {} non-linear inner loop constraints", listNonLinearConstsInnerLoop.size());

                // --- Utils ---
                // Sorted equations to solve
                List<Equation<AcVariableType, AcEquationType>> sortedInnerLoopEquationsToSolve = getSortedEquations(equationSystem, knitroParameters);
                // List of buses that have an equation setting V
                List<Integer> listVarVIndexes = new ArrayList<>(); // for every equation setting V, we store the index of the corresponding V variable
                for (Equation equation : sortedInnerLoopEquationsToSolve) {
                    if (equation.getType() == AcEquationType.BUS_TARGET_V) {
                        int varVIndex = ((VariableEquationTerm) equation.getTerms().get(0)).getElementNum();
                        listVarVIndexes.add(varVIndex);
                    }
                }

                // --- Add non-linear constraints ---
                // Inner loop constraints
                int indexNonLinearInnerLoopCst = 0;
                for (int equationId : listNonLinearConstsInnerLoop) {
                    Equation<AcVariableType, AcEquationType> equation = sortedInnerLoopEquationsToSolve.get(equationId);
                    AcEquationType typeEq = equation.getType();
                    double valueConst = 0;
                    if (!KnitroEquationsUtils.getNonLinearConstraintsTypes(knitroParameters).contains(typeEq)) {
                        LOGGER.debug("Equation of type {} is linear or quadratic, and should be considered in the main function of Knitro, not in the callback function", typeEq);
                        throw new IllegalArgumentException("Equation of type " + typeEq + " is linear or quadratic, and should be considered in the main function of Knitro, not in the callback function");
                    } else {
                        for (EquationTerm term : equation.getTerms()) {
                            term.setStateVector(currentState);
                            if (term.isActive()) {
                                valueConst += term.eval();
                            }
                        }
                        try {
                            c.set(indexNonLinearInnerLoopCst, valueConst);
                            LOGGER.trace("Adding non-linear constraint n° {}, of type {} and of value {}", equationId, typeEq, valueConst);
                        } catch (Exception e) {
                            LOGGER.error("Exception found while trying to add non-linear constraint n° {}", equationId);
                            LOGGER.error(e.getMessage());
                            throw new PowsyblException("Exception found while trying to add non-linear constraint");
                        }
                    }
                    indexNonLinearInnerLoopCst += 1;
                }

                // Outer loop constraints
                LOGGER.trace("Evaluating {} non-linear outer loop constraints", listNonLinearConstsOuterLoop.size());
                int currentCbEqIndex = listNonLinearConstsInnerLoop.size(); // index of current equation to add in the callback structure (we start at the last constraint added in the inner loop +1)
                for (int indexNonLinearOuterLoopCst : listNonLinearConstsOuterLoop) {
                    int busId = listVarVIndexes.get((indexNonLinearOuterLoopCst - sortedInnerLoopEquationsToSolve.size())/5);

                    // Compute sum of reactive power at bus
                    double valueSumReactivePower = 0;
                    Equation equation = (Equation) equationSystem.getEquation(busId, AcEquationType.BUS_TARGET_Q).get();
                    List<EquationTerm> equationTerms = equation.getTerms();
                    for (EquationTerm term : equationTerms) {
                        if (term.isActive()) {
                            valueSumReactivePower += term.eval();
                        }
                    }
                    try {
                        if ((currentCbEqIndex - listNonLinearConstsInnerLoop.size()) % 3 == 0) {
                            // 1) Q is within its bounds Q_lo and Q_up for all PV and PQ nodes
                            // Inequality
                            c.set(currentCbEqIndex, valueSumReactivePower);
                        } else if ((currentCbEqIndex - listNonLinearConstsInnerLoop.size()) % 3 == 1) {
                            // 2) The node becomes PQ and q is set to its upper bound Q_up
                            // Equality
                            double valueConst = 0; // value of the constraint
                            int indexOfVarInList = listVarVIndexes.indexOf(busId);
                            int indexBinaryVarY = getYVar(indexOfVarInList, equationSystem);
                            // term in valueSumReactivePower*y[i]
                            valueConst = valueSumReactivePower*x.get(indexBinaryVarY);
                            // term in Qi_up*y[i]
                            valueConst -= lfNetwork.getBus(busId).getMaxQ()*x.get(indexBinaryVarY);
                            // add equation
                            c.set(currentCbEqIndex, valueConst);
                        } else if ((currentCbEqIndex - listNonLinearConstsInnerLoop.size()) % 3 == 2) {
                            // 4) The node becomes PQ and q is set to its lower bound Q_lo
                            // Equality
                            double valueConst = 0; // value of the constraint
                            int indexOfVarInList = listVarVIndexes.indexOf(busId);
                            int indexBinaryVarX = getXVar(indexOfVarInList, equationSystem);
                            int indexBinaryVarY = getYVar(indexOfVarInList, equationSystem);
                            // term in valueSumReactivePower*(1- x[i] - y[i])
                            valueConst = valueSumReactivePower*(1- x.get(indexBinaryVarX)- x.get(indexBinaryVarY));
                            // term in Q_lo*(1- x[i] - y[i])
                            valueConst -= lfNetwork.getBus(busId).getMaxQ()*(1- x.get(indexBinaryVarX)- x.get(indexBinaryVarY));
                            // add equation
                            c.set(currentCbEqIndex, valueConst);
                        }
                        LOGGER.trace("Adding non-linear outer loop constraint n° {}", indexNonLinearOuterLoopCst);
                    } catch (Exception e) {
                        LOGGER.error("Exception found while trying to add non-linear outer loop constraint n° {}", indexNonLinearOuterLoopCst);
                        LOGGER.error(e.getMessage());
                        throw new PowsyblException("Exception found while trying to add non-linear constraint");
                    }
                    currentCbEqIndex += 1;
                }
            }
        }

        /*------------------------------------------------------------------*/
        /*     FUNCTION callbackEvalG                                       */
        /*------------------------------------------------------------------*/
        private final class CallbackEvalG extends KNEvalGACallback {
            private final JacobianMatrix<AcVariableType, AcEquationType> oldMatrix;
            private final List<Integer> listNonZerosCts;
            private final List<Integer> listNonZerosVars;
            private final List<Integer> listNonZerosCts2;
            private final List<Integer> listNonZerosVars2;
            private final List<Integer> listNonLinearConsts;
            private final List<Integer> listVarChecker;
            private final LfNetwork network;
            private final EquationSystem<AcVariableType, AcEquationType> equationSystem;

            private CallbackEvalG(JacobianMatrix<AcVariableType, AcEquationType> oldMatrix, List<Integer> listNonZerosCts, List<Integer> listNonZerosVars, List<Integer> listNonZerosCts2, List<Integer> listNonZerosVars2, List<Integer> listNonLinearConsts, List<Integer> listVarChecker, LfNetwork network, EquationSystem<AcVariableType, AcEquationType> equationSystem) {
                this.oldMatrix = oldMatrix;
                this.listNonZerosCts = listNonZerosCts;
                this.listNonZerosVars = listNonZerosVars;
                this.listNonZerosCts2 = listNonZerosCts2;
                this.listNonZerosVars2 = listNonZerosVars2;
                this.listNonLinearConsts = listNonLinearConsts;
                this.listVarChecker = listVarChecker;
                this.network = network;
                this.equationSystem = equationSystem;
            }

            @Override
            public void evaluateGA(final List<Double> x, final List<Double> objGrad, final List<Double> jac) {
                // Update current Jacobian
                equationSystem.getStateVector().set(toArray(x));
                AcSolverUtil.updateNetwork(network, equationSystem);
                oldMatrix.forceUpdate();
                DenseMatrix denseOldMatrix = oldMatrix.getMatrix().toDense();

                // Get non-linear Jacobian from original Jacobian

                if (knitroParameters.getGradientUserRoutine() == 1) {
                    // FIRST METHOD
                    int id = 0;
                    for (int ct : listNonLinearConsts) {
                        for (int var = 0; var < getNumVars(); var++) { //TODO CHANGER getNumVars()
                            try {
                                jac.set(id, denseOldMatrix.get(var, ct)); // Jacobian needs to be transposed
                                id += 1;
                            } catch (Exception e) {
                                LOGGER.error("Exception found while trying to add Jacobian term {} in non-linear constraint n° {}", var, ct);
                                LOGGER.error(e.getMessage());
                                throw new PowsyblException("Exception found while trying to add Jacobian term in non-linear constraint");
                            }
                        }
                    }
                } else if (knitroParameters.getGradientUserRoutine() == 2) {
                    //SECOND METHOD
                    for (int index = 0; index < listNonZerosCts2.size(); index++) {
                        try {
                            int var = listNonZerosVars2.get(index);
                            int ct = listNonZerosCts2.get(index);
                            double value = denseOldMatrix.get(var, ct); // Jacobian needs to be transposed
                            jac.set(index, value);
                        } catch (Exception e) {
                            LOGGER.error("Exception found while trying to add Jacobian term {} in non-linear constraint n° {}", listNonZerosVars2.get(index), listNonZerosCts2.get(index));
                            LOGGER.error(e.getMessage());
                        }
                    }
                }

//                List<Double> jac1 = new ArrayList<>();
//                List<Double> jac2 = new ArrayList<>();
//                List<Double> jacDiff = new ArrayList<>();
//
//                // JAC 1 all non linear constraints
//                for (int ct : listNonLinearConsts) {
//                    for (int var = 0; var < equationSystem.getVariableSet().getVariables().size(); var++) { //TODO CHANGER
//                        try {
//                            jac1.add(denseOldMatrix.get(var, ct));  // Jacobian needs to be transposed
//                        } catch (Exception e) {
//                            LOGGER.error("Exception found while trying to add Jacobian term {} in non-linear constraint n° {}", var, ct);
//                            LOGGER.error(e.getMessage());
//                            throw new PowsyblException("Exception found while trying to add Jacobian term in non-linear constraint");
//                        }
//                    }
//                }
//
//                // JAC 2 only non zeros
//                for (int index = 0; index < listNonZerosCts2.size(); index++) {
//                    try {
//                        double value = denseOldMatrix.get(listNonZerosVars2.get(index), listNonZerosCts2.get(index));
//                        jac2.add(value);  // Jacobian needs to be transposed
//                    } catch (Exception e) {
//                        LOGGER.error("Exception found while trying to add Jacobian term {} in non-linear constraint n° {}", listNonZerosVars2.get(index), listNonZerosCts2.get(index));
//                        LOGGER.error(e.getMessage());
//                        throw new PowsyblException("Exception found while trying to add Jacobian term in non-linear constraint");
//                    }
//                }

//                // JAC DIFF
//                int id2 = 0;
//                for (int i = 0; i < jac1.size(); i++) {
//                    if (listVarChecker.get(i) != -1) {
//                        jacDiff.add(jac1.get(i) - jac2.get(id2));
//                        id2 += 1;
//                    } else {
//                        jacDiff.add(jac1.get(i));
//                    }
//                }
//
//                for (double value : jacDiff) {
//                    if (value >= 0.00001) {
//                        System.out.println("Les deux Jacobiennes sont censées être équivalentes, mais elles le sont pas!!! ; erreur " + value);
//                    }
//                }
            }
        }

        /*------------------------------------------------------------------*/
        /*     FUNCTION callbackEvalH                                       */
        /*------------------------------------------------------------------*/
        private static class CallbackEvalH extends KNEvalHCallback {
            @Override
            public void evaluateH(final List<Double> x, final double sigma, final List<Double> lambda, List<Double> hess) {
                // TODO ?
            }
        }

        private KnitroProblem(LfNetwork lfNetwork, EquationSystem<AcVariableType, AcEquationType> equationSystem, TargetVector targetVector, VoltageInitializer voltageInitializer, JacobianMatrix<AcVariableType, AcEquationType> jacobianMatrix, KnitroSolverParameters knitroParameters) throws KNException {

            // =============== Variables ===============
            // ----- Defining variables -----
            super(getNumAllVar(equationSystem, knitroParameters), getNumConstraints(equationSystem, knitroParameters));
            int numVar = getNumAllVar(equationSystem, knitroParameters); // num of variables
            int numConst = getNumConstraints(equationSystem, knitroParameters); // num of constraints (inner + outer loops)
            List<Variable<AcVariableType>> sortedNonBinVar = getSortedNonBinaryVarList(equationSystem, knitroParameters); // ordering variables
            LOGGER.info("Defining {} variables, including {} inner loop variables.", numVar, sortedNonBinVar.size());

            // ----- Types, bounds and initial states of variables -----
            // Types
            List<Integer> listVarTypes = getVariablesTypes(equationSystem, knitroParameters);
            setVarTypes(listVarTypes);

            // Bounds
            // TODO A REPRENDRE -> bornes pour les variables binaires
//            List<Double> listVarLoBounds = new ArrayList<>(numVar);
//            List<Double> listVarUpBounds = new ArrayList<>(numVar);
//            double loBndV = knitroParameters.getMinRealisticVoltage();
//            double upBndV = knitroParameters.getMaxRealisticVoltage();
//            for (int var = 0; var < sortedNonBinVar.size(); var++) {
//                Enum<AcVariableType> typeVar = sortedNonBinVar.get(var).getType();
//                if (typeVar == AcVariableType.BUS_V) {
//                    listVarLoBounds.add(loBndV);
//                    listVarUpBounds.add(upBndV);
//                } else {
//                    listVarLoBounds.add(-KNConstants.KN_INFINITY);
//                    listVarUpBounds.add(KNConstants.KN_INFINITY);
//                }
//            }
//            setVarLoBnds(listVarLoBounds);
//            setVarUpBnds(listVarUpBounds);

            // Initial state
            AcSolverUtil.initStateVector(lfNetwork, equationSystem, voltageInitializer); // Initialize state vector
            List<Double> listXInitial = getInitialStateVector(equationSystem, knitroParameters);
            setXInitial(listXInitial);
            LOGGER.info("Initialization of variables : type of initialization {}", voltageInitializer);

            // =============== Constraints ==============
            // ----- Active constraints -----
            // Get active constraints and order them in same order as targets
            List<Equation<AcVariableType, AcEquationType>> sortedInnerLoopEquationsToSolve = getSortedEquations(equationSystem, knitroParameters);
            int numConstInnerLoop = sortedInnerLoopEquationsToSolve.size();
            List<Integer> listNonLinearInnerLoopConsts = new ArrayList<>(); // list of indexes of inner loop non-linear constraints
            List<Integer> listNonLinearOuterLoopConsts = new ArrayList<>(); // list of indexes of outer loop non-linear constraints
            LOGGER.info("Defining {} active constraints, including {} inner loop constraints.", numConst, numConstInnerLoop);

            // ----- Linear and quadratic constraints -----
            // -- Inner loop constraints --
            for (int equationId = 0; equationId < numConstInnerLoop; equationId++) {
                Equation<AcVariableType, AcEquationType> equation = sortedInnerLoopEquationsToSolve.get(equationId);
                AcEquationType typeEq = equation.getType();
                List<EquationTerm<AcVariableType, AcEquationType>> terms = equation.getTerms();
                KnitroEquationsUtils solverUtils = new KnitroEquationsUtils();
                if (KnitroEquationsUtils.getLinearConstraintsTypes(knitroParameters).contains(typeEq)) {
                    // Linear constraints
                    List<Integer> listVar = new ArrayList<>();
                    List<Double> listCoef = new ArrayList<>();
                    listVar = solverUtils.getLinearConstraint(knitroParameters, typeEq, equationId, terms).getListIdVar();
                    listCoef = solverUtils.getLinearConstraint(knitroParameters, typeEq, equationId, terms).getListCoef();

                    for (int i = 0; i < listVar.size(); i++) {
                        addConstraintLinearPart(equationId, listVar.get(i), listCoef.get(i));
                    }
                    LOGGER.trace("Adding linear constraint n° {} of type {}", equationId, typeEq);

                } else if (KnitroEquationsUtils.getQuadraticConstraintsTypes(knitroParameters).contains(typeEq)) {
                    // Quadratic constraints
                    List<Integer> listVarQuadra = new ArrayList<>();
                    List<Double> listCoefQuadra = new ArrayList<>();
                    List<Integer> listVarLin = new ArrayList<>();
                    List<Double> listCoefLin = new ArrayList<>();
                    listVarQuadra = solverUtils.getQuadraticConstraint(knitroParameters, typeEq, equationId, terms, targetVector, equationSystem).get(0).getListIdVar();
                    listCoefQuadra = solverUtils.getQuadraticConstraint(knitroParameters, typeEq, equationId, terms, targetVector, equationSystem).get(0).getListCoef();
                    listVarLin = solverUtils.getQuadraticConstraint(knitroParameters, typeEq, equationId, terms, targetVector, equationSystem).get(1).getListIdVar();
                    listCoefLin = solverUtils.getQuadraticConstraint(knitroParameters, typeEq, equationId, terms, targetVector, equationSystem).get(1).getListCoef();

                    // Quadratic part
                    addConstraintQuadraticPart(equationId, listVarQuadra.get(0), listVarQuadra.get(1), listCoefQuadra.get(0));
                    // Linear part
                    addConstraintLinearPart(equationId, listVarLin.get(0), listCoefLin.get(0));
                    LOGGER.trace("Adding quadratic constraint n° {} of type {}", equationId, typeEq);

                } else {
                    // Non-linear constraints
                    listNonLinearInnerLoopConsts.add(equationId); // Add constraint number to list of non-linear constraintslogi
                }
            }

            // -- Outer loop constraints --
            List<Double> targetOuterLoopEqualities = null; // target vector for outer loop equalities
            List<Integer> equalitiesIndexesOuterLoop = new ArrayList<>(); // indexes of equalities
            List<Integer> inequalitiesIndexes = new ArrayList<>(); // indexes of inequalities
            List<Double> loBndsOuterLoopInequalities = new ArrayList<>(); // lower bound vector for outer loop inequalities
            List<Double> upBndsOuterLoopInequalities = new ArrayList<>(); // upper bound vector for outer loop inequalities

            // Build list of buses indexes for buses that have an equation setting V
            List<Equation<AcVariableType, AcEquationType>> innerLoopSortedEquations = getSortedEquations(equationSystem, knitroParameters); //TODO reprendre de manière + compacte
            List<Integer> listBusesWithVEq = new ArrayList<>(); // for every equation setting V, we store the index of the corresponding V variable
            for (Equation equation : innerLoopSortedEquations) {
                if (equation.getType() == AcEquationType.BUS_TARGET_V) {
                    int varVIndex = ((VariableEquationTerm) equation.getTerms().get(0)).getElementNum();
                    listBusesWithVEq.add(varVIndex);
                }
            }

            // Adding outer loop constraints
            if (knitroParameters.isDirectOuterLoopsFormulation()) {
                int currentEquationId = innerLoopSortedEquations.size(); //we now add equations after the inner loop equations
                targetOuterLoopEqualities = new ArrayList<>();
//                loBndsOuterLoopInequalities = new ArrayList<>(); //TODO
//                upBndsOuterLoopInequalities = new ArrayList<>();

                // Browse the list of buses that have an equation setting V
                for (Equation equation : innerLoopSortedEquations) {
                    if (equation.getType() == AcEquationType.BUS_TARGET_V) {
                        // For each bus that has initially an equation setting V, we had 5 constraints

                        // 1) Q is within its bounds Q_lo and Q_up for all PV and PQ nodes
                        // Non-linear inequality
                        listNonLinearOuterLoopConsts.add(currentEquationId); // Add constraint number to list of non-linear constraints
                        // bounds
                        Double Q_lo = lfNetwork.getBus(equation.getElementNum()).getMinQ();
                        Double Q_up = lfNetwork.getBus(equation.getElementNum()).getMaxQ();
                        loBndsOuterLoopInequalities.add(Q_lo);
                        upBndsOuterLoopInequalities.add(Q_up);
                        inequalitiesIndexes.add(currentEquationId);

                        // 2) The node becomes PQ and q is set to its upper bound Q_up
                        // Non-linear equality
                        listNonLinearOuterLoopConsts.add(currentEquationId + 1); // Add constraint number to list of non-linear constraints
                        targetOuterLoopEqualities.add(0.0);
                        equalitiesIndexesOuterLoop.add(currentEquationId + 1);

                        // 3) The node becomes PQ and V_i y_i - Vref_i y_i <= 0
                        // Quadratic inequality
                        int busIndex = ((VariableEquationTerm) equation.getTerms().get(0)).getElementNum();
                        int indexOfVarInList = listBusesWithVEq.indexOf(busIndex);
                        int binaryVarYIndex = getYVar(indexOfVarInList, equationSystem);

                        // add inequality
                        addConstraintQuadraticPart(currentEquationId + 2, busIndex, binaryVarYIndex, 1.0); // V_i*y_i
                        addConstraintLinearPart(currentEquationId + 2, binaryVarYIndex, -targetVector.getArray()[equation.getColumn()]); //- Vref_i*y_i
                        // bounds
                        loBndsOuterLoopInequalities.add(-KNConstants.KN_INFINITY);
                        upBndsOuterLoopInequalities.add(0.0);
                        inequalitiesIndexes.add(currentEquationId + 2);

                        // 4) The node becomes PQ and q is set to its lower bound Q_lo
                        // Non-linear equality
                        listNonLinearOuterLoopConsts.add(currentEquationId + 3); // Add constraint number to list of non-linear constraints
                        targetOuterLoopEqualities.add(0.0);
                        equalitiesIndexesOuterLoop.add(currentEquationId + 3);

                        // 5) The node becomes PQ and Vref_i (1- x_i - y_i) - V_i (1- x_i - y_i) <= 0
                        // That is to say : - V_i - Vref_i*x_i - Vref_i*y_i + V_i*x_i + V_i*y_i <= - Vref_i
                        // where + V_i*x_i and + V_i*y_i are quadratic parts of the constraints
                        // and - V_i , - Vref_i*x_i and - Vref_i*y_i are linear parts of the constraints
                        // Quadratic inequality

                        // Add inequality
                        int binaryVarXIndex = getXVar(indexOfVarInList, equationSystem);
                        addConstraintQuadraticPart(currentEquationId + 4, busIndex, binaryVarXIndex, 1.0); // + V_i*x_i
                        addConstraintQuadraticPart(currentEquationId + 4, busIndex, binaryVarYIndex, 1.0); // + V_i*y_i
                        addConstraintLinearPart(currentEquationId + 4, busIndex, -1.0); // - V_i
                        addConstraintLinearPart(currentEquationId + 4, binaryVarXIndex, - targetVector.getArray()[equation.getColumn()]); // - Vref_i*x_i
                        addConstraintLinearPart(currentEquationId + 4, binaryVarYIndex, - targetVector.getArray()[equation.getColumn()]); // - Vref_i*y_i
                        // bounds
                        loBndsOuterLoopInequalities.add(-KNConstants.KN_INFINITY);
                        upBndsOuterLoopInequalities.add(- targetVector.getArray()[equation.getColumn()]); // - Vref_i
                        inequalitiesIndexes.add(currentEquationId + 4);

                        // Update current equation number
                        currentEquationId += 5  ;
                    }
                }
            }


//            if (knitroParameters.isDirectOuterLoopsFormulation()){
//                for (int equationId = numConstInnerLoop ; equationId < numConst; equationId++) {
//                    if ((equationId - numConstInnerLoop) % 5 == 0){
//                        // q is within its bounds Q_lo and Q_up for all PV and PQ nodes
//                        // Non-linear inequality
//                        listNonLinearOuterLoopConsts.add(equationId); // Add constraint number to list of non-linear constraints
//                    } else if ((equationId - numConstInnerLoop) % 5 == 1){
//                        // The node becomes PQ and q is set to its upper bound Q_up
//                        // Non-linear equality
//                        listNonLinearOuterLoopConsts.add(equationId); // Add constraint number to list of non-linear constraints
//                        //TODO for this equality, we need to pass a target of 0 to Knitro
//                    } else if ((equationId - numConstInnerLoop) % 5 == 2){
//                        // The node becomes PQ and V_i y_i - Vref_i y_i <= 0
//                        // Quadratic inequality
//
//                     } else if ((equationId - numConstInnerLoop) % 5 == 3){
//                        // The node becomes PQ and q is set to its lower bound Q_lo
//                        // Non-linear equality
//                        listNonLinearOuterLoopConsts.add(equationId); // Add constraint number to list of non-linear constraints
//                        //TODO for this equality, we need to pass a target of 0 to Knitro
//                    } else if ((equationId - numConstInnerLoop) % 5 == 4){
//                        // The node becomes PQ and V_i y_i - Vref_i y_i >= 0
//                        // Quadratic inequality
//                    }
//                }
//            }

            // ----- Non-linear constraints -----
            // Callback
            List<Integer> listNonLinearConsts = new ArrayList<>(listNonLinearInnerLoopConsts);
            listNonLinearConsts.addAll(listNonLinearOuterLoopConsts); // concatenate lists of non-linear constraints
            setMainCallbackCstIndexes(listNonLinearConsts);

            // ----- RHS : targets -----

            // -- Equalitites targets --
            // Modify the inner loop target vector for V equations
            List<Double> modifiedInnerLoopTargetVector = new ArrayList<>(numConstInnerLoop);
            for (Equation equation : innerLoopSortedEquations) {
                if (equation.getType() == AcEquationType.BUS_TARGET_V) {
                    modifiedInnerLoopTargetVector.add(0.0);
                } else {
                    modifiedInnerLoopTargetVector.add(targetVector.getArray()[equation.getColumn()]);
                }
            }
            // Concatenante targets for inner and outer loop
            List<Double> targetConstsEqualities = new ArrayList<>(modifiedInnerLoopTargetVector); // concatenate the two lists
            targetConstsEqualities.addAll(targetOuterLoopEqualities);
            // Set target for equalities
            List<Integer> listTargetEqualities = new ArrayList<>();
            listTargetEqualities = IntStream.range(0, numConstInnerLoop).boxed().collect(Collectors.toList());;
            listTargetEqualities.addAll(equalitiesIndexesOuterLoop);
            setConEqBnds(listTargetEqualities, targetConstsEqualities); //TODO IF DIRECT FORMULATION PASS MODIFIED VECTOR, ELSE PASS VECTOR

            // -- Inequalities bounds --
            setConLoBnds(inequalitiesIndexes, loBndsOuterLoopInequalities);
            setConUpBnds(inequalitiesIndexes, upBndsOuterLoopInequalities);

            // =============== Objective ==============
            setObjConstPart(0.0);

            // =============== Callback ==============
            // ----- Constraints -----
            setObjEvalCallback(new CallbackEvalFC(equationSystem, lfNetwork, listNonLinearInnerLoopConsts, listNonLinearOuterLoopConsts));

//            // ----- Jacobian matrix ----- //TODO
//            // Non zero pattern
//            // FIRST METHOD : all non-linear constraints
//            List<Integer> listNonZerosCts = new ArrayList<>(); //list of constraints to pass to Knitro non-zero pattern
//            for (Integer idCt : listNonLinearInnerLoopConsts) {
//                for (int i = 0; i < numVar; i++) {
//                    listNonZerosCts.add(idCt);
//                }
//            }
//
//            List<Integer> listNonZerosVars = new ArrayList<>(); //list of variables to pass to Knitro non-zero pattern
//            List<Integer> listVars = new ArrayList<>();
//            for (int i = 0; i < numVar; i++) {
//                listVars.add(i);
//            }
//            for (int i = 0; i < listNonLinearInnerLoopConsts.size(); i++) {
//                listNonZerosVars.addAll(listVars);
//            }
//
//            // SECOND METHOD : only non-zero constraints
//            List<Integer> listNonZerosCts2 = new ArrayList<>();
//            List<Integer> listNonZerosVars2 = new ArrayList<>();
//            List<Integer> listVarChecker = new ArrayList<>();
//            for (Integer ct : listNonLinearInnerLoopConsts) {
//                Equation<AcVariableType, AcEquationType> equation = sortedInnerLoopEquationsToSolve.get(ct);
//                List<EquationTerm<AcVariableType, AcEquationType>> terms = equation.getTerms();
//                List<Integer> listNonZerosVarsCurrentCt = new ArrayList<>(); //list of variables involved in current constraint
//
//                for (EquationTerm<AcVariableType, AcEquationType> term : terms) {
//                    for (Variable variable : term.getVariables()) {
//                        listNonZerosVarsCurrentCt.add(variable.getRow());
//                    }
//                }
//                List<Integer> uniqueListVarsCurrentCt = listNonZerosVarsCurrentCt.stream().distinct().sorted().toList(); // remove duplicate elements from the list
//                listNonZerosVars2.addAll(uniqueListVarsCurrentCt);
//                for (int var = 0; var < sortedNonBinVar.size(); var++) {
//                    if (uniqueListVarsCurrentCt.contains(var)) {
//                        listVarChecker.add(var);
//                    } else {
//                        listVarChecker.add(-1);
//                    }
//                }
//
//                listNonZerosCts2.addAll(new ArrayList<>(Collections.nCopies(uniqueListVarsCurrentCt.size(), ct)));
//            }
//
//            if (knitroParameters.getGradientComputationMode() == 1) { // User routine to compute the Jacobian
//                if (knitroParameters.getGradientUserRoutine() == 1) {
//                    setJacNnzPattern(listNonZerosCts, listNonZerosVars);
//                } else if (knitroParameters.getGradientUserRoutine() == 2) {
//                    setJacNnzPattern(listNonZerosCts2, listNonZerosVars2);
//                }
//                setGradEvalCallback(new CallbackEvalG(jacobianMatrix, listNonZerosCts, listNonZerosVars, listNonZerosCts2, listNonZerosVars2, listNonLinearInnerLoopConsts, listVarChecker, lfNetwork, equationSystem));
//            }
        }
    }


    private void setSolverParameters(KNSolver solver, KnitroSolverParameters knitroParameters) throws KNException {
        solver.setParam(KNConstants.KN_PARAM_GRADOPT, knitroParameters.getGradientComputationMode());
        DefaultKnitroSolverStoppingCriteria knitroSolverStoppingCriteria = (DefaultKnitroSolverStoppingCriteria) knitroParameters.getStoppingCriteria();
        solver.setParam(KNConstants.KN_PARAM_FEASTOL, knitroSolverStoppingCriteria.convEpsPerEq);
        solver.setParam(KNConstants.KN_PARAM_DERIVCHECK, 1);
        solver.setParam(KNConstants.KN_PARAM_DERIVCHECK_TOL, 0.0001);
//        solver.setParam(KNConstants.KN_PARAM_MAXIT, 30);
        solver.setParam(KNConstants.KN_PARAM_OUTLEV,4);
//        solver.setParam(KNConstants.KN_PARAM_OUTMODE,2);
//        solver.setParam(KNConstants.KN_PARAM_DEBUG ,1);

//        solver.setParam(KNConstants.KN_PARAM_MS_ENABLE, 0); // multi-start
//        solver.setParam(KNConstants.KN_PARAM_MS_NUMTHREADS, 1);
//        solver.setParam(KNConstants.KN_PARAM_CONCURRENT_EVALS, 0); //pas d'évaluations de callbacks concurrentes
//        solver.setParam(KNConstants.KN_PARAM_NUMTHREADS, 8);
    }

    @Override
    public AcSolverResult run(VoltageInitializer voltageInitializer, ReportNode reportNode) {
        AcSolverStatus status;
        int nbIter = -1;

        AcSolverStatus acStatus = null;
        try {
            // Create instance of problem
            KnitroProblem instance = new KnitroProblem(network, equationSystem, targetVector, voltageInitializer, j, knitroParameters);
            KNSolver solver = new KNSolver(instance);
            solver.initProblem();

            // Set solver parameters
            setSolverParameters(solver, knitroParameters);

            // Solve
            solver.solve();
            KNSolution solution = solver.getSolution();
            List<Double> constraintValues = solver.getConstraintValues();
            acStatus = getAcStatusAndKnitroStatus(solution.getStatus());
            nbIter = solver.getNumberIters();

            // Log solution
            LOGGER.info("Optimal objective value  = {}", solution.getObjValue());
            LOGGER.info("Feasibility violation    = {}", solver.getAbsFeasError());
            LOGGER.info("Optimality violation     = {}", solver.getAbsOptError());

            LOGGER.debug("Optimal x");
            for (int i = 0; i < solution.getX().size(); i++) {
                LOGGER.debug(" x[{}] = {}", i, solution.getX().get(i));
            }
            LOGGER.debug("Optimal constraint values (with corresponding multiplier)");
            for (int i = 0; i < instance.getNumCons(); i++) {
                LOGGER.debug(" c[{}] = {} (lambda = {} )", i, constraintValues.get(i), solution.getLambda().get(i));
            }

            // Load results in the network

            if (acStatus == AcSolverStatus.CONVERGED || knitroParameters.isAlwaysUpdateNetwork()) {
                equationSystem.getStateVector().set(toArray(solution.getX()));
                AcSolverUtil.updateNetwork(network, equationSystem);
            }
//            equationSystem.getStateVector().set(toArray(solution.getX()));
//            AcSolverUtil.updateNetwork(network, equationSystem); //TODO

//            // update network state variable //TODO later?
//            if (acStatus == AcSolverStatus.CONVERGED && knitroParameters.is(reportNode)) {
//                status = AcSolverStatus.UNREALISTIC_STATE;
//            }


        } catch (KNException e) {
            LOGGER.error("Exception found while trying to solve with Knitro");
            LOGGER.error(e.toString(), e);
            acStatus = AcSolverStatus.NO_CALCULATION;
            throw new PowsyblException("Exception found while trying to solve with Knitro");
        }

        double slackBusActivePowerMismatch = network.getSlackBuses().stream().mapToDouble(LfBus::getMismatchP).sum();
        return new AcSolverResult(acStatus, nbIter, slackBusActivePowerMismatch);
    }
}
