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
import com.powsybl.math.matrix.Matrix;
import com.powsybl.openloadflow.ac.equations.AcEquationType;
import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.equations.*;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.util.VoltageInitializer;
import com.powsybl.openloadflow.util.MatrixUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static com.google.common.primitives.Doubles.toArray;

/**
 * @author Pierre Arvy {@literal <pierre.arvy at artelys.com>}
 * @author Jeanne Archambault {@literal <jeanne.archambault at artelys.com>}
 */
public class KnitroSolver extends AbstractNonLinearExternalSolver {

    private static final Logger LOGGER = LoggerFactory.getLogger(KnitroSolver.class);

    protected final KnitroSolverParameters knitroParameters;

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
    INPUT_OR_NON_STANDARD_ERROR }

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

            private final List<Equation<AcVariableType, AcEquationType>> sortedEquationsToSolve;
            private final LfNetwork lfNetwork;
            private final List<Integer> listNonLinearConsts;

            private CallbackEvalFC(List<Equation<AcVariableType, AcEquationType>> sortedEquationsToSolve, LfNetwork lfNetwork, List<Integer> listNonLinearConsts) {
                this.sortedEquationsToSolve = sortedEquationsToSolve;
                this.lfNetwork = lfNetwork;
                this.listNonLinearConsts = listNonLinearConsts;
            }

            @Override
            public void evaluateFC(final List<Double> x, final List<Double> obj, final List<Double> c) {
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("============ Knitro evaluating callback function ============");
                }

                // =============== Objective ===============

                // =============== Non-linear constraints in P and Q ===============
                // Update current state
                StateVector currentState = new StateVector(toArray(x));
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("Current state vector {}", currentState.get());
                    LOGGER.trace("Evaluating {} non-linear constraints", listNonLinearConsts.size());
                }
                // Add non-linear constraints
                int indexNonLinearCst = 0;
                for (int equationId : listNonLinearConsts) {
                    Equation<AcVariableType, AcEquationType> equation = sortedEquationsToSolve.get(equationId);
                    AcEquationType typeEq = equation.getType();
                    double valueConst = 0;

                    if (typeEq == AcEquationType.BUS_TARGET_P || typeEq == AcEquationType.BUS_TARGET_Q) {
                        for (EquationTerm term : equation.getTerms()) {
                            term.setStateVector(currentState);
                            if (term.isActive()) {
                                valueConst += term.eval();
                                //if (LOGGER.isTraceEnabled()) {
                                    //LOGGER.trace("Term of equation n° {} was evaluated at {}", equationId, term.eval()); //TODO a reprendre pour log le nom du terme également
                                //}
                            }
                        }
                        try {
                            c.set(indexNonLinearCst, valueConst);
                            if (LOGGER.isTraceEnabled()) {
                                LOGGER.trace("Adding non-linear constraint n° {}, of type {} and of value {}", equationId, typeEq, valueConst);
                            }
                        } catch (Exception e) {
                            LOGGER.error("Exception found while trying to add non-linear constraint n° {}", equationId);
                            LOGGER.error(e.getMessage());
                            throw new PowsyblException("Exception found while trying to add non-linear constraint");
                        }
                    }
                    indexNonLinearCst += 1;
                }
            }
        }


        /*------------------------------------------------------------------*/
        /*     FUNCTION callbackEvalG                                       */
        /*------------------------------------------------------------------*/
        private class CallbackEvalG extends KNEvalGACallback {
            private final JacobianMatrix<AcVariableType, AcEquationType> oldMatrix;
            private final List<Integer> listNonZerosCts;
            private final List<Integer> listNonZerosVars;
            private final List<Integer> listNonLinearConsts;
            private final List<Integer> listVarChecker;
            private final LfNetwork network;
            private final EquationSystem<AcVariableType, AcEquationType> equationSystem;

            private CallbackEvalG(JacobianMatrix<AcVariableType, AcEquationType> oldMatrix, List<Integer> listNonZerosCts, List<Integer> listNonZerosVars, List<Integer> listNonLinearConsts, List<Integer> listVarChecker, LfNetwork network, EquationSystem<AcVariableType, AcEquationType> equationSystem) {
                this.oldMatrix = oldMatrix;
                this.listNonZerosCts = listNonZerosCts;
                this.listNonZerosVars = listNonZerosVars;
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
                LOGGER.error("Ici : " + getNumVars());

                // Get non-linear Jacobian from original Jacobian
//                int id = 0 ;
//                for (int ct : listNonLinearConsts) {
//                    for (int var=0; var<getNumVars(); var++) { //TODO CHANGER
//                        try {
//                            jac.set(id, denseOldMatrix.get(var, ct));  // Jacobian needs to be transposed
//                            id += 1;
//                        } catch (Exception e) {
//                            LOGGER.error("Exception found while trying to add Jacobian term {} in non-linear constraint n° {}", var, ct);
//                            LOGGER.error(e.getMessage());
//                            throw new PowsyblException("Exception found while trying to add Jacobian term in non-linear constraint");
//                        }
//                    }
//                }

                for (int index = 0; index < listNonZerosCts.size(); index++) {
                    try {
                        double value = denseOldMatrix.get(listNonZerosVars.get(index), listNonZerosCts.get(index));
                        jac.set(index, value);  // Jacobian needs to be transposed
//                        jac.set(index, denseOldMatrix.get(listNonZerosCts.get(index),listNonZerosVars.get(index)));  // Jacobian needs to be transposed
                    } catch (Exception e) {
                        LOGGER.error("Exception found while trying to add Jacobian term {} in non-linear constraint n° {}", listNonZerosVars.get(index), listNonZerosCts.get(index));
                        LOGGER.error(e.getMessage());
                        throw new PowsyblException("Exception found while trying to add Jacobian term in non-linear constraint");
                    }
                }


                List<Double> jac1 = new ArrayList<>();
                List<Double> jac2 = new ArrayList<>();
                List<Double> jacDiff = new ArrayList<>();

                // JAC 1
                for (int ct : listNonLinearConsts) {
                    for (int var = 0; var < getNumVars(); var++) { //TODO CHANGER
                        try {
                            jac1.add(denseOldMatrix.get(var, ct));  // Jacobian needs to be transposed
                        } catch (Exception e) {
                            LOGGER.error("Exception found while trying to add Jacobian term {} in non-linear constraint n° {}", var, ct);
                            LOGGER.error(e.getMessage());
                            throw new PowsyblException("Exception found while trying to add Jacobian term in non-linear constraint");
                        }
                    }
                }

                // JAC 2
                for (int index = 0; index < listNonZerosCts.size(); index++) {
                    try {
                        double value = denseOldMatrix.get(listNonZerosVars.get(index), listNonZerosCts.get(index));
                        jac2.add(value);  // Jacobian needs to be transposed
//                        jac.set(index, denseOldMatrix.get(listNonZerosCts.get(index),listNonZerosVars.get(index)));  // Jacobian needs to be transposed
                    } catch (Exception e) {
                        LOGGER.error("Exception found while trying to add Jacobian term {} in non-linear constraint n° {}", listNonZerosVars.get(index), listNonZerosCts.get(index));
                        LOGGER.error(e.getMessage());
                        throw new PowsyblException("Exception found while trying to add Jacobian term in non-linear constraint");
                    }
                }

                // JAC DIFF
                int id2 = 0;
                for (int i = 0; i < jac1.size(); i++) {
                    if (listVarChecker.get(i) != -1) {
                        jacDiff.add(jac1.get(i) - jac2.get(id2));
                        id2 += 1;
                    } else {
                        jacDiff.add(jac1.get(i));
                    }
                }

                for (double value : jacDiff) {
                    if (value >= 0.00001) {
                        System.out.println("Les deux Jacobiennes sont censées être équivalentes, mais elles le sont pas!!!");
                    }
                }
                //TODO a reprendre car valeurs de la jacobienne visiblement fausses
            }
        }

        /*------------------------------------------------------------------*/
        /*     FUNCTION callbackEvalH                                       */
        /*------------------------------------------------------------------*/
        private static class CallbackEvalH extends KNEvalHCallback {
            @Override
            public void evaluateH(final List<Double> x, final double sigma, final List<Double> lambda, List<Double> hess) {
                // TODO
                // A voir si obligatoire de passer qqc ou si par défaut la hessienne est null
            }
        }

        // Get Jacobian matrix of Non-Linear constraints only

        private static Matrix getNonLinearJacobian(JacobianMatrix<AcVariableType, AcEquationType> oldMatrix, List<Integer> listNonLinearConsts) {
            Matrix allCstrsJacobian = oldMatrix.getMatrix(); //get and update old matrix
            return MatrixUtil.extractRowsAndColumns(allCstrsJacobian,listNonLinearConsts);
        }

//        private static Matrix getNonLinearJacobian(JacobianMatrix<AcVariableType, AcEquationType> oldMatrix, List<Integer> listNonLinearConsts) {
//            Matrix allCstrsJacobian = oldMatrix.getMatrix().transpose(); //get, transpose and update old matrix
//            return MatrixUtil.extractRowsAndColumns(allCstrsJacobian,listNonLinearConsts);
//        }


        private KnitroProblem(LfNetwork lfNetwork, EquationSystem<AcVariableType, AcEquationType> equationSystem, TargetVector targetVector, VoltageInitializer voltageInitializer, JacobianMatrix<AcVariableType, AcEquationType> jacobianMatrix, KnitroSolverParameters knitroParameters) throws KNException {

            // =============== Variables ===============
            // Defining variables
            super(equationSystem.getVariableSet().getVariables().size(), equationSystem.getIndex().getSortedEquationsToSolve().size());
            int numVar = equationSystem.getVariableSet().getVariables().size();
            List<Variable<AcVariableType>> sortedVariables = equationSystem.getIndex().getSortedVariablesToFind(); // ordering variables
            LOGGER.info("Defining {} variables", numVar);

            // Types, bounds and initial states of variables
            // Types
            List<Integer> listVarTypes = new ArrayList<>(Collections.nCopies(numVar, KNConstants.KN_VARTYPE_CONTINUOUS));
            setVarTypes(listVarTypes);

            // Bounds
            List<Double> listVarLoBounds = new ArrayList<>(numVar);
            List<Double> listVarUpBounds = new ArrayList<>(numVar);
            double loBndV = knitroParameters.getMinRealisticVoltage();
            double upBndV = knitroParameters.getMaxRealisticVoltage();
            for (int var = 0; var < sortedVariables.size(); var++) {
                Enum<AcVariableType> typeVar = sortedVariables.get(var).getType();
                if (typeVar == AcVariableType.BUS_V) {
                    listVarLoBounds.add(loBndV);
                    listVarUpBounds.add(upBndV);
                } else {
                    listVarLoBounds.add(-KNConstants.KN_INFINITY);
                    listVarUpBounds.add(KNConstants.KN_INFINITY);
                }
            }
            setVarLoBnds(listVarLoBounds);
            setVarUpBnds(listVarUpBounds);

            // Initial state
            List<Double> listXInitial = new ArrayList<>(numVar);
            AcSolverUtil.initStateVector(lfNetwork, equationSystem, voltageInitializer); // Initialize state vector
            for (int i = 0; i < numVar; i++) {
                listXInitial.add(equationSystem.getStateVector().get(i));
            }
            setXInitial(listXInitial);
            LOGGER.info("Initialization of variables : type of initialization {}", voltageInitializer);

            // =============== Constraints ==============
            // ----- Active constraints -----
            // Get active constraints and order them in same order as targets
            List<Equation<AcVariableType, AcEquationType>> sortedEquationsToSolve = equationSystem.getIndex().getSortedEquationsToSolve();

            int numConst = sortedEquationsToSolve.size();
            List<Integer> listNonLinearConsts = new ArrayList<>();
            LOGGER.info("Defining {} active constraints", numConst);

            // ----- Linear constraints in V and Phi -----
            for (int equationId = 0; equationId < numConst; equationId++) {
                Equation<AcVariableType, AcEquationType> equation = sortedEquationsToSolve.get(equationId);
                AcEquationType typeEq = equation.getType();
                List<EquationTerm<AcVariableType, AcEquationType>> terms = equation.getTerms();

                if (typeEq == AcEquationType.BUS_TARGET_V || typeEq == AcEquationType.BUS_TARGET_PHI || typeEq == AcEquationType.DUMMY_TARGET_P || typeEq == AcEquationType.DUMMY_TARGET_Q) {
                    // get the variable V/Theta corresponding to the constraint
                    int idVar = terms.get(0).getVariables().get(0).getRow();
                    addConstraintLinearPart(equationId, idVar, 1.0);
                    if (LOGGER.isTraceEnabled()) {
                        LOGGER.trace("Adding linear constraint n° {} of type {}, with variable {}", equationId, typeEq, idVar);
                    }
                } else if (typeEq == AcEquationType.ZERO_V || typeEq == AcEquationType.ZERO_PHI) {
                    // get the variables Vi and Vj / Thetai and Thetaj corresponding to the constraint
                    int idVari = terms.get(0).getVariables().get(0).getRow();
                    int idVarj = terms.get(1).getVariables().get(0).getRow();
                    addConstraintLinearPart(equationId, idVari, 1.0);
                    addConstraintLinearPart(equationId, idVarj, -1.0);
                    if (LOGGER.isTraceEnabled()) {
                        LOGGER.trace("Adding linear constraint n° {} of type {}, with variables {} and {}", equationId, typeEq, idVari, idVarj);
                    }
                } else if (typeEq == AcEquationType.DISTR_Q) {
                    // get the variables corresponding to the constraint
                    for (EquationTerm<AcVariableType, AcEquationType> equationTerm : terms) {
                        EquationTerm.MultiplyByScalarEquationTerm<AcVariableType, AcEquationType> term = (EquationTerm.MultiplyByScalarEquationTerm<AcVariableType, AcEquationType>) equationTerm;
                        addConstraintLinearPart(equationId, term.getVariables().get(0).getRow(), term.getScalarSupplier().getAsDouble());
                    }
                    if (LOGGER.isTraceEnabled()) {
                        LOGGER.trace("Adding linear constraint n° {} of type {}", equationId, typeEq);
                    }
                } else {
                    listNonLinearConsts.add(equationId); // Add constraint number to list of non-linear constraints
                }
            }

            // ----- Non-linear constraints in P and Q -----
            setMainCallbackCstIndexes(listNonLinearConsts);

            // ----- RHS : targets -----
            List<Double> listTarget = Arrays.stream(targetVector.getArray()).boxed().toList();
            setConEqBnds(listTarget);

            // =============== Objective ==============
            setObjConstPart(0.0);

            // =============== Callback ==============
            // ----- Constraints -----
            setObjEvalCallback(new CallbackEvalFC(sortedEquationsToSolve, lfNetwork, listNonLinearConsts));

            // ----- Jacobian matrix -----
            // Non zero pattern
//            List<Integer> listNonZerosCts = new ArrayList<>();
//            for (Integer value : listNonLinearConsts) {
//                for (int i = 0; i < numVar; i++) {
//                    listNonZerosCts.add(value);
//                }
//            }
//
//            List<Integer> listNonZerosVars = new ArrayList<>();
//            List<Integer> listVars = new ArrayList<>();
//            for (int i = 0; i < numVar; i++) {
//                listVars.add(i);
//            }
//            for (int i = 0; i < listNonLinearConsts.size(); i++) {
//                listNonZerosVars.addAll(listVars);
//            }

            List<Integer> listNonZerosCts = new ArrayList<>();
            List<Integer> listNonZerosVars = new ArrayList<>();
            List<Integer> listVarChecker = new ArrayList<>();
            for (Integer ct : listNonLinearConsts) {
                Equation<AcVariableType, AcEquationType> equation = sortedEquationsToSolve.get(ct);
                List<EquationTerm<AcVariableType, AcEquationType>> terms = equation.getTerms();
                List<Integer> listNonZerosVarsCurrentCt = new ArrayList<>(); //list of variables involved in current constraint
//                for (EquationTerm<AcVariableType, AcEquationType> term : terms) {
//                    for (Variable variable : term.getVariables()) {
//                        listNonZerosVarsCurrentCt.add(variable.getRow());
//                    }
//                }
//                List<Integer> uniqueListVarsCurrentCt = listNonZerosVarsCurrentCt.stream().distinct().sorted().toList(); // remove duplicate elements from the list
//                listNonZerosVars.addAll(uniqueListVarsCurrentCt);

                for (EquationTerm<AcVariableType, AcEquationType> term : terms) {
                    for (Variable variable : term.getVariables()) {
                        listNonZerosVarsCurrentCt.add(variable.getRow());
                    }
                }
                List<Integer> uniqueListVarsCurrentCt = listNonZerosVarsCurrentCt.stream().distinct().sorted().toList(); // remove duplicate elements from the list
                listNonZerosVars.addAll(uniqueListVarsCurrentCt);
                for (int var=0; var<sortedVariables.size(); var++) {
                    if (uniqueListVarsCurrentCt.contains(var)) {
                        listVarChecker.add(var);
                    } else {
                        listVarChecker.add(-1);
                    }
                }

//                for (int i=0; i<uniqueListVarsCurrentCt.size(); i++) {
//                    listNonZerosCts.add(ct);
//                }
                listNonZerosCts.addAll(new ArrayList<>(Collections.nCopies(uniqueListVarsCurrentCt.size(), ct)));
            }

            setJacNnzPattern(listNonZerosCts,listNonZerosVars);
            setGradEvalCallback(new CallbackEvalG(jacobianMatrix,listNonZerosCts, listNonZerosVars, listNonLinearConsts, listVarChecker, lfNetwork, equationSystem));
        }

    }

    private void setSolverParameters(KNSolver solver, KnitroSolverParameters knitroParameters) throws KNException {
        solver.setParam(KNConstants.KN_PARAM_GRADOPT, knitroParameters.getGradientComputationMode());
        DefaultKnitroSolverStoppingCriteria knitroSolverStoppingCriteria = (DefaultKnitroSolverStoppingCriteria) knitroParameters.getStoppingCriteria();
        solver.setParam(KNConstants.KN_PARAM_FEASTOL, knitroSolverStoppingCriteria.convEpsPerEq);
//        solver.setParam(KNConstants.KN_PARAM_DERIVCHECK,1);
//        solver.setParam(KNConstants.KN_PARAM_DERIVCHECK_TOL,0.00001);
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
            if (acStatus == AcSolverStatus.CONVERGED) { //TODO add always update network condition
                equationSystem.getStateVector().set(toArray(solution.getX()));
                AcSolverUtil.updateNetwork(network, equationSystem);
            }

            if (acStatus == AcSolverStatus.CONVERGED || knitroParameters.isAlwaysUpdateNetwork()) {
                equationSystem.getStateVector().set(toArray(solution.getX()));
                AcSolverUtil.updateNetwork(network, equationSystem);
            }
//
//            // update network state variable
//            if (acStatus == AcSolverStatus.CONVERGED && isStateUnrealistic(reportNode)) {
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
