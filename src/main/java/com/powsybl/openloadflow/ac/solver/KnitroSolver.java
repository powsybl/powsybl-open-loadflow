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

    protected final KnitroSolverParameters parameters;

    public KnitroSolver(LfNetwork network, KnitroSolverParameters parameters,
                           EquationSystem<AcVariableType, AcEquationType> equationSystem, JacobianMatrix<AcVariableType, AcEquationType> j,
                           TargetVector<AcVariableType, AcEquationType> targetVector, EquationVector<AcVariableType, AcEquationType> equationVector,
                           boolean detailedReport) {
        super(network, equationSystem, j, targetVector, equationVector, detailedReport);
        this.parameters = parameters;
    }

    @Override
    public String getName() {
        return "Knitro Solver";
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
                // =============== Objectif ===============
                obj.set(0, 0.0);

                // =============== Contraintes en P et Q non linéaires ===============
                // On récupère le nombre de contraintes et on met à jour l'état courant
//                int numConst = sortedEquationsToSolve.size();
//                int numConst = 5;
                StateVector currentState = new StateVector(toArray(x));

                for (int equationId : listNonLinearConsts) {
                    Equation<AcVariableType, AcEquationType> equation = sortedEquationsToSolve.get(equationId);
                    AcEquationType typeEq = equation.getType();
//                    LfBus bus = lfNetwork.getBus(equation.getElementNum());
                    double valueConst = 0;

                    if (typeEq == AcEquationType.BUS_TARGET_P || typeEq == AcEquationType.BUS_TARGET_Q) {
                        for (EquationTerm term : equation.getTerms()) {
                            term.setStateVector(currentState);
                            if (term.isActive()) {
                                valueConst += term.eval();
                            }
                        }
                    }
                    try {
                        c.set(equationId, valueConst);
                    } catch (Exception e) {
                        System.out.println("Exception found while trying to add non-linear constraint n° " + equationId);
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

        private KnitroProblem(LfNetwork lfNetwork, EquationSystem<AcVariableType, AcEquationType> equationSystem, TargetVector targetVector) throws KNException {

            // =============== Variables ===============
            // Définition des variables, ordonnées par bus et par V, Phi
            super(equationSystem.getVariableSet().getVariables().size(), equationSystem.getIndex().getSortedEquationsToSolve().size());
            int numVar = equationSystem.getVariableSet().getVariables().size();

            // Types, bounds et états initiaux des variables
            List<Integer> listVarTypes = new ArrayList<>(Collections.nCopies(numVar, KNConstants.KN_VARTYPE_CONTINUOUS));
            List<Double> listVarLoBounds = new ArrayList<>(numVar);
            List<Double> listVarUpBounds = new ArrayList<>(numVar);
            List<Double> listXInitial = new ArrayList<>(numVar);

            double loBndV = 0.5;
            double upBndV = 1.5;
            double vInit = 1.0;
            double phiInit = 0.0;
            for (int i = 0; i < numVar; i++) {
                if (i % 2 == 0) { //init V
                    listVarLoBounds.add(loBndV);
                    listVarUpBounds.add(upBndV);
                    listXInitial.add(vInit);
                } else { //init phi
                    listVarLoBounds.add(-KNConstants.KN_INFINITY);
                    listVarUpBounds.add(KNConstants.KN_INFINITY);
                    listXInitial.add(phiInit);
                }
            }
            setVarLoBnds(listVarLoBounds);
            setVarUpBnds(listVarUpBounds);
            setVarTypes(listVarTypes);
            setXInitial(listXInitial);
            // TODO faire en sorte qu'on puisse passer un état initial, sinon 1 0 1 0... par défaut

            // =============== Contraintes ==============
            // ----- Contraintes actives -----
            // Récupérer les contraintes actives et les ordonner dans le même ordre que les targets, par bus et dans l'ordre P Q V Phi
            List<Equation<AcVariableType, AcEquationType>> sortedEquationsToSolve = equationSystem.getIndex().getSortedEquationsToSolve();
            int numConst = sortedEquationsToSolve.size();
            List<Integer> listNonLinearConsts = new ArrayList<>() ;

            // ----- Contraintes en V et Phi linéaires -----
            for (int equationId = 0; equationId < numConst; equationId++) {
                Equation<AcVariableType, AcEquationType> equation = sortedEquationsToSolve.get(equationId);
                AcEquationType typeEq = equation.getType();
                LfBus bus = lfNetwork.getBus(equation.getElementNum());
                if (typeEq == AcEquationType.BUS_TARGET_V) {
                    addConstraintLinearPart(equationId, bus.getNum() * 2, 1.0);
                }
                else if (typeEq == AcEquationType.BUS_TARGET_PHI) {
                    addConstraintLinearPart(equationId, bus.getNum() * 2 + 1, 1.0);
                }
                else {
                    listNonLinearConsts.add(equationId);
                }
            }

            // ----- Contraintes en P et Q non linéaires -----
            listNonLinearConsts = IntStream.rangeClosed(0, numConst-1).boxed().collect(Collectors.toList()); //TODO A reprendre ca pas clair si on peut passer seulement les CTs non linéaires
            setMainCallbackCstIndexes(listNonLinearConsts);

            // ----- RHS : targets -----
            List<Double> listTarget = Arrays.stream(targetVector.getArray()).boxed().toList();
            setConEqBnds(listTarget);

            // =============== Objectif ==============
            setObjEvalCallback(new CallbackEvalFC(sortedEquationsToSolve, lfNetwork, listNonLinearConsts));
        }
    }

    private void setSolverParameters(KNSolver solver, KnitroSolverParameters parameters) throws KNException {
        solver.setParam(KNConstants.KN_PARAM_GRADOPT, parameters.getGradientComputationMode());
        solver.setParam(KNConstants.KN_PARAM_FEASTOL, parameters.getDefaultConvEpsPerEq());
    }

    @Override
    public AcSolverResult run(VoltageInitializer voltageInitializer, ReportNode reportNode) {
        AcSolverStatus status;
        int nbIter = -1;

        try {
            KnitroProblem instance = new KnitroProblem(network, equationSystem, targetVector);
            KNSolver solver = new KNSolver(instance);
            solver.initProblem();
            setSolverParameters(solver, parameters);
            solver.solve();

            KNSolution solution = solver.getSolution();
            List<Double> constraintValues = solver.getConstraintValues();

            System.out.println("Optimal objective value  = " + solution.getObjValue());
            System.out.println("Optimal x");
            for (int i=0; i<solution.getX().size(); i++)
            {
                System.out.format(" x[%d] = %f%n", i, solution.getX().get(i));
            }
            System.out.println("Optimal constraint values (with corresponding multiplier)");
            for (int i=0; i < instance.getNumCons(); i++)
            {
                System.out.format(" c[%d] = %f (lambda = %f)%n", i, constraintValues.get(i), solution.getLambda().get(i));
            }
            System.out.format("Feasibility violation    = %f%n", solver.getAbsFeasError());
            System.out.format("Optimality violation     = %f%n", solver.getAbsOptError());

            status = AcSolverStatus.CONVERGED;
            nbIter = solver.getNumberIters();

            // Load results in the network
            equationSystem.getStateVector().set(toArray(solution.getX()));
            AcSolverUtil.updateNetwork(network, equationSystem);

        } catch (KNException e) {
            System.out.println("Exception found while trying to solve with Knitro");
            status = AcSolverStatus.NO_CALCULATION;
        }
        double slackBusActivePowerMismatch = network.getSlackBuses().stream().mapToDouble(LfBus::getMismatchP).sum();
        return new AcSolverResult(status, nbIter, slackBusActivePowerMismatch);
    }
}
