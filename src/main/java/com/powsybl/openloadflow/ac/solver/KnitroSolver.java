/**
 * Copyright (c) 2024, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.solver;

import com.artelys.knitro.api.KNConstants;
import com.artelys.knitro.api.KNException;
import com.artelys.knitro.api.KNProblem;
import com.artelys.knitro.api.KNSolver;
import com.artelys.knitro.api.callbacks.KNEvalFCCallback;
import com.artelys.knitro.api.callbacks.KNEvalGACallback;
import com.artelys.knitro.api.callbacks.KNEvalHCallback;
import com.powsybl.commons.report.ReportNode;
import com.powsybl.openloadflow.ac.equations.AcEquationType;
import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.equations.EquationVector;
import com.powsybl.openloadflow.equations.JacobianMatrix;
import com.powsybl.openloadflow.equations.TargetVector;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.util.VoltageInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * @author Pierre Arvy {@literal <pierre.arvy at artelys.com>}
 */
public class KnitroSolver extends AbstractNonLinearExternalSolver {

    private static final Logger LOGGER = LoggerFactory.getLogger(KnitroSolver.class);

    protected final KnitroSolverParameters parameters;

    protected KnitroSolver(LfNetwork network, KnitroSolverParameters parameters,
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
        private static class CallbackEvalF extends KNEvalFCCallback {
            @Override
            public void evaluateFC(final List<Double> x, final List<Double> obj, final List<Double> c) {
                // TODO
            }
        }

        /*------------------------------------------------------------------*/
        /*     FUNCTION callbackEvalG                                       */
        /*------------------------------------------------------------------*/
        private static class CallbackEvalG extends KNEvalGACallback {
            @Override
            public void evaluateGA(final List<Double> x, final List<Double> objGrad, final List<Double> jac) {
                // TODO
            }
        }

        /*------------------------------------------------------------------*/
        /*     FUNCTION callbackEvalH                                       */
        /*------------------------------------------------------------------*/
        private static class CallbackEvalH extends KNEvalHCallback {
            @Override
            public void evaluateH(final List<Double> x, final double sigma, final List<Double> lambda, List<Double> hess) {
                // TODO
            }
        }

        private KnitroProblem() {
            // TODO
        }
    }

    private void setSolverParameters(KNSolver solver, KnitroSolverParameters parameters) throws KNException {
        solver.setParam(KNConstants.KN_PARAM_GRADOPT, parameters.getGradientComputationMode());
    }

    @Override
    public AcSolverResult run(VoltageInitializer voltageInitializer, ReportNode reportNode) {

        try {
            KnitroProblem instance = new KnitroProblem();
            KNSolver solver = new KNSolver(instance);
            solver.initProblem();
            setSolverParameters(solver, parameters);
            // TODO
            solver.solve();
        } catch (KNException e) {
            // TODO
        }

        return null;
    }
}
