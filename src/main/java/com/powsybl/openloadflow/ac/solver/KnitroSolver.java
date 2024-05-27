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
import com.powsybl.commons.report.ReportNode;
import com.powsybl.openloadflow.ac.equations.AcEquationType;
import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.equations.*;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.util.VoltageInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
                for (int equationId : listNonLinearConsts) {
                    Equation<AcVariableType, AcEquationType> equation = sortedEquationsToSolve.get(equationId);
                    AcEquationType typeEq = equation.getType();
                    double valueConst = 0;

                    if (typeEq == AcEquationType.BUS_TARGET_P || typeEq == AcEquationType.BUS_TARGET_Q) {
                        for (EquationTerm term : equation.getTerms()) {
                            term.setStateVector(currentState);
                            if (term.isActive()) {
                                valueConst += term.eval();
                                if (LOGGER.isTraceEnabled()) {
                                    LOGGER.trace("Term of equation n° {} was evaluated at {}", equationId, term.eval()); //TODO a reprendre pour log le nom du terme également
                                }
                            }
                        }
                        try {
                            c.set(equationId, valueConst);
                            if (LOGGER.isTraceEnabled()) {
                                LOGGER.trace("Adding non-linear constraint n° {}, of type {} and of value {}", equationId, typeEq, valueConst);
                            }
                        } catch (Exception e) {
                            LOGGER.error("Exception found while trying to add non-linear constraint n° {}", equationId);
                        }
                    }
                }
            }
        }

        /*------------------------------------------------------------------*/
        /*     FUNCTION callbackEvalG                                       */
        /*------------------------------------------------------------------*/
        private static class CallbackEvalG extends KNEvalGACallback {
            @Override
            public void evaluateGA(final List<Double> x, final List<Double> objGrad, final List<Double> jac) {
                // TODO
                // objGrad.replaceAll(ignored -> null);
                // A voir si obligatoire de passer qqc ou si par défaut le gradient est null + idem jacobienne
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

        private KnitroProblem(LfNetwork lfNetwork, EquationSystem<AcVariableType, AcEquationType> equationSystem, TargetVector targetVector, VoltageInitializer voltageInitializer, KnitroSolverParameters knitroParameters) throws KNException {

            // =============== Variables ===============
            // Defining variables and ordering them by bus and in the order V, Phi
            super(equationSystem.getVariableSet().getVariables().size(), equationSystem.getIndex().getSortedEquationsToSolve().size());
            int numVar = equationSystem.getVariableSet().getVariables().size();
            LOGGER.info("Defining {} variables", numVar);

            // Types, bounds and inital states of variables
            // Types
            List<Integer> listVarTypes = new ArrayList<>(Collections.nCopies(numVar, KNConstants.KN_VARTYPE_CONTINUOUS));
            setVarTypes(listVarTypes);
            // Bounds
            List<Double> listVarLoBounds = new ArrayList<>(numVar);
            List<Double> listVarUpBounds = new ArrayList<>(numVar);
            double loBndV = knitroParameters.getMinRealisticVoltage();
            double upBndV = knitroParameters.getMaxRealisticVoltage();
            for (int i = 0; i < numVar; i++) {
                if (i % 2 == 0) { // Initialize V
                    listVarLoBounds.add(loBndV);
                    listVarUpBounds.add(upBndV);
                } else { // Initialize Phi
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

            // TODO faire en sorte qu'on puisse passer un état initial, sinon 1 0 1 0... par défaut
            LOGGER.info("Initialization of variables : type of initialization {}", voltageInitializer);

            // =============== Constraints ==============
            // ----- Active constraints -----
            // Get active constraints and order them in same order as targets (i.e by bus and in the order P Q V Phi)
            List<Equation<AcVariableType, AcEquationType>> sortedEquationsToSolve = equationSystem.getIndex().getSortedEquationsToSolve();
            int numConst = sortedEquationsToSolve.size();
            List<Integer> listNonLinearConsts = new ArrayList<>();
            LOGGER.info("Defining {} active constraints", numConst);

            // ----- Linear constraints in V and Phi -----
            for (int equationId = 0; equationId < numConst; equationId++) {
                Equation<AcVariableType, AcEquationType> equation = sortedEquationsToSolve.get(equationId);
                AcEquationType typeEq = equation.getType();
                LfBus bus = lfNetwork.getBus(equation.getElementNum());
                if (typeEq == AcEquationType.BUS_TARGET_V) {
                    addConstraintLinearPart(equationId, bus.getNum() * 2, 1.0);
                    if (LOGGER.isTraceEnabled()) {
                        LOGGER.trace("Adding non-linear constraint n° {}, of type {} and of value {}", equationId, typeEq, bus.getNum() * 2);
                    }
                } else if (typeEq == AcEquationType.BUS_TARGET_PHI) {
                    addConstraintLinearPart(equationId, bus.getNum() * 2 + 1, 1.0);
                    if (LOGGER.isTraceEnabled()) {
                        LOGGER.trace("Adding non-linear constraint n° {}, of type {} and of value {}", equationId, typeEq, bus.getNum() * 2 + 1);
                    }
                } else {
                    listNonLinearConsts.add(equationId); // Add constraint number to list of non-linear constraints
                }
            }

            // ----- Non-linear constraints in P and Q -----
            listNonLinearConsts = IntStream.rangeClosed(0, numConst - 1).boxed().collect(Collectors.toList()); //TODO A reprendre ca pas clair si on peut passer seulement les CTs non linéaires
            setMainCallbackCstIndexes(listNonLinearConsts);

            // ----- RHS : targets -----
            List<Double> listTarget = Arrays.stream(targetVector.getArray()).boxed().toList();
            setConEqBnds(listTarget);

            // =============== Objective and Callback ==============
            setObjConstPart(0.0);
            setObjEvalCallback(new CallbackEvalFC(sortedEquationsToSolve, lfNetwork, listNonLinearConsts));
        }
    }

    private void setSolverParameters(KNSolver solver, KnitroSolverParameters knitroParameters) throws KNException {
        solver.setParam(KNConstants.KN_PARAM_GRADOPT, knitroParameters.getGradientComputationMode());
        DefaultKnitroSolverStoppingCriteria knitroSolverStoppingCriteria = (DefaultKnitroSolverStoppingCriteria) knitroParameters.getStoppingCriteria();
        solver.setParam(KNConstants.KN_PARAM_FEASTOL, knitroSolverStoppingCriteria.convEpsPerEq);
    }

    @Override
    public AcSolverResult run(VoltageInitializer voltageInitializer, ReportNode reportNode) {
        AcSolverStatus status;
        int nbIter = -1;

        AcSolverStatus acStatus = null;
        try {
            // Create instance of problem
            KnitroProblem instance = new KnitroProblem(network, equationSystem, targetVector, voltageInitializer, knitroParameters);
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
            equationSystem.getStateVector().set(toArray(solution.getX()));
            AcSolverUtil.updateNetwork(network, equationSystem);

        } catch (KNException e) {
            LOGGER.error("Exception found while trying to solve with Knitro");
            LOGGER.error(e.toString(), e);
            acStatus = AcSolverStatus.NO_CALCULATION;
        }
        double slackBusActivePowerMismatch = network.getSlackBuses().stream().mapToDouble(LfBus::getMismatchP).sum();
        return new AcSolverResult(acStatus, nbIter, slackBusActivePowerMismatch);
    }
}
