/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.ac.equations.AcEquationSystemCreationParameters;
import com.powsybl.openloadflow.ac.solver.NewtonRaphsonFactory;
import com.powsybl.openloadflow.ac.solver.NewtonRaphsonParameters;
import com.powsybl.openloadflow.ac.solver.SolverFactory;
import com.powsybl.openloadflow.ac.outerloop.AcOuterLoop;
import com.powsybl.openloadflow.lf.AbstractLoadFlowParameters;
import com.powsybl.openloadflow.network.LfNetworkParameters;
import com.powsybl.openloadflow.network.util.VoltageInitializer;

import java.util.List;
import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class AcLoadFlowParameters extends AbstractLoadFlowParameters {

    public static final int DEFAULT_MAX_OUTER_LOOP_ITERATIONS = 20;

    private final AcEquationSystemCreationParameters equationSystemCreationParameters;

    private final NewtonRaphsonParameters newtonRaphsonParameters;

    private final List<AcOuterLoop> outerLoops;

    private final int maxOuterLoopIterations;

    private VoltageInitializer voltageInitializer;

    private final boolean asymmetrical;

    private OpenLoadFlowParameters.SlackDistributionFailureBehavior slackDistributionFailureBehavior;

    private SolverFactory solverFactory;

    public AcLoadFlowParameters(LfNetworkParameters networkParameters, AcEquationSystemCreationParameters equationSystemCreationParameters,
                                NewtonRaphsonParameters newtonRaphsonParameters, List<AcOuterLoop> outerLoops, int maxOuterLoopIterations,
                                MatrixFactory matrixFactory, VoltageInitializer voltageInitializer, boolean asymmetrical,
                                OpenLoadFlowParameters.SlackDistributionFailureBehavior slackDistributionFailureBehavior) {
        this(networkParameters, equationSystemCreationParameters, newtonRaphsonParameters, outerLoops, maxOuterLoopIterations,
                matrixFactory, voltageInitializer, asymmetrical, slackDistributionFailureBehavior, new NewtonRaphsonFactory());
    }

    public AcLoadFlowParameters(LfNetworkParameters networkParameters, AcEquationSystemCreationParameters equationSystemCreationParameters,
                                NewtonRaphsonParameters newtonRaphsonParameters, List<AcOuterLoop> outerLoops, int maxOuterLoopIterations,
                                MatrixFactory matrixFactory, VoltageInitializer voltageInitializer, boolean asymmetrical,
                                OpenLoadFlowParameters.SlackDistributionFailureBehavior slackDistributionFailureBehavior,
                                SolverFactory solverFactory) {
        super(networkParameters, matrixFactory);
        this.equationSystemCreationParameters = Objects.requireNonNull(equationSystemCreationParameters);
        this.newtonRaphsonParameters = Objects.requireNonNull(newtonRaphsonParameters);
        this.outerLoops = Objects.requireNonNull(outerLoops);
        this.maxOuterLoopIterations = maxOuterLoopIterations;
        this.voltageInitializer = Objects.requireNonNull(voltageInitializer);
        this.asymmetrical = asymmetrical;
        this.slackDistributionFailureBehavior = Objects.requireNonNull(slackDistributionFailureBehavior);
        this.solverFactory = Objects.requireNonNull(solverFactory);
    }

    public AcEquationSystemCreationParameters getEquationSystemCreationParameters() {
        return equationSystemCreationParameters;
    }

    public NewtonRaphsonParameters getNewtonRaphsonParameters() {
        return newtonRaphsonParameters;
    }

    public List<AcOuterLoop> getOuterLoops() {
        return outerLoops;
    }

    public int getMaxOuterLoopIterations() {
        return maxOuterLoopIterations;
    }

    public VoltageInitializer getVoltageInitializer() {
        return voltageInitializer;
    }

    public void setVoltageInitializer(VoltageInitializer voltageInitializer) {
        this.voltageInitializer = Objects.requireNonNull(voltageInitializer);
    }

    public boolean isAsymmetrical() {
        return asymmetrical;
    }

    public OpenLoadFlowParameters.SlackDistributionFailureBehavior getSlackDistributionFailureBehavior() {
        return slackDistributionFailureBehavior;
    }

    public void setSlackDistributionFailureBehavior(OpenLoadFlowParameters.SlackDistributionFailureBehavior slackDistributionFailureBehavior) {
        this.slackDistributionFailureBehavior = Objects.requireNonNull(slackDistributionFailureBehavior);
    }

    public SolverFactory getSolverFactory() {
        return solverFactory;
    }

    public void setSolverFactory(SolverFactory solverFactory) {
        this.solverFactory = Objects.requireNonNull(solverFactory);
    }

    @Override
    public String toString() {
        return "AcLoadFlowParameters(" +
                "networkParameters=" + networkParameters +
                ", equationSystemCreationParameters=" + equationSystemCreationParameters +
                ", newtonRaphsonParameters=" + newtonRaphsonParameters +
                ", outerLoops=" + outerLoops.stream().map(outerLoop -> outerLoop.getClass().getSimpleName()).toList() +
                ", maxOuterLoopIterations=" + maxOuterLoopIterations +
                ", matrixFactory=" + matrixFactory.getClass().getSimpleName() +
                ", voltageInitializer=" + voltageInitializer.getClass().getSimpleName() +
                ", asymmetrical=" + asymmetrical +
                ", slackDistributionFailureBehavior=" + slackDistributionFailureBehavior.name() +
                ')';
    }
}
