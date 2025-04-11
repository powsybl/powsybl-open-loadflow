/*
 * Copyright (c) 2019-2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.openloadflow.ac.equations.AcEquationSystemCreationParameters;
import com.powsybl.openloadflow.ac.outerloop.AcOuterLoop;
import com.powsybl.openloadflow.ac.solver.AcSolverFactory;
import com.powsybl.openloadflow.ac.solver.AcSolverParameters;
import com.powsybl.openloadflow.ac.solver.NewtonRaphsonFactory;
import com.powsybl.openloadflow.ac.solver.NewtonRaphsonParameters;
import com.powsybl.openloadflow.lf.AbstractLoadFlowParameters;
import com.powsybl.openloadflow.network.LfNetworkParameters;
import com.powsybl.openloadflow.network.util.UniformValueVoltageInitializer;
import com.powsybl.openloadflow.network.util.VoltageInitializer;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class AcLoadFlowParameters extends AbstractLoadFlowParameters<AcLoadFlowParameters> {

    public static final double DEFAULT_MIN_REALISTIC_VOLTAGE = 0.5;
    public static final double DEFAULT_MAX_REALISTIC_VOLTAGE = 2;
    public static final boolean DEFAULT_EXTRAPOLATE_REACTIVE_LIMITS = false;

    private AcEquationSystemCreationParameters equationSystemCreationParameters = new AcEquationSystemCreationParameters();

    private AcSolverParameters acSolverParameters = new NewtonRaphsonParameters();

    private List<AcOuterLoop> outerLoops = Collections.emptyList();

    private int maxOuterLoopIterations = DEFAULT_MAX_OUTER_LOOP_ITERATIONS;

    private VoltageInitializer voltageInitializer = new UniformValueVoltageInitializer();

    private boolean asymmetrical = LfNetworkParameters.ASYMMETRICAL_DEFAULT_VALUE;

    private AcSolverFactory solverFactory = new NewtonRaphsonFactory();

    private boolean detailedReport = false;

    private boolean voltageRemoteControlRobustMode = true;

    private double minRealisticVoltage = DEFAULT_MIN_REALISTIC_VOLTAGE;

    private double maxRealisticVoltage = DEFAULT_MAX_REALISTIC_VOLTAGE;

    private boolean extrapolateReactiveLimits = false;

    public AcEquationSystemCreationParameters getEquationSystemCreationParameters() {
        return equationSystemCreationParameters;
    }

    public AcLoadFlowParameters setEquationSystemCreationParameters(AcEquationSystemCreationParameters equationSystemCreationParameters) {
        this.equationSystemCreationParameters = Objects.requireNonNull(equationSystemCreationParameters);
        return this;
    }

    public AcSolverParameters getAcSolverParameters() {
        return acSolverParameters;
    }

    public List<AcOuterLoop> getOuterLoops() {
        return outerLoops;
    }

    public AcLoadFlowParameters setOuterLoops(List<AcOuterLoop> outerLoops) {
        this.outerLoops = Objects.requireNonNull(outerLoops);
        return this;
    }

    public int getMaxOuterLoopIterations() {
        return maxOuterLoopIterations;
    }

    public AcLoadFlowParameters setMaxOuterLoopIterations(int maxOuterLoopIterations) {
        this.maxOuterLoopIterations = maxOuterLoopIterations;
        return this;
    }

    public VoltageInitializer getVoltageInitializer() {
        return voltageInitializer;
    }

    public AcLoadFlowParameters setVoltageInitializer(VoltageInitializer voltageInitializer) {
        this.voltageInitializer = Objects.requireNonNull(voltageInitializer);
        return this;
    }

    public boolean isAsymmetrical() {
        return asymmetrical;
    }

    public AcLoadFlowParameters setAsymmetrical(boolean asymmetrical) {
        this.asymmetrical = asymmetrical;
        return this;
    }

    public AcSolverFactory getSolverFactory() {
        return solverFactory;
    }

    public AcLoadFlowParameters setSolverFactory(AcSolverFactory solverFactory, LoadFlowParameters parameters) {
        this.solverFactory = Objects.requireNonNull(solverFactory);
        this.acSolverParameters = solverFactory.createParameters(parameters);
        return this;
    }

    public boolean isDetailedReport() {
        return detailedReport;
    }

    public AcLoadFlowParameters setDetailedReport(boolean detailedReport) {
        this.detailedReport = detailedReport;
        return this;
    }

    public boolean isVoltageRemoteControlRobustMode() {
        return voltageRemoteControlRobustMode;
    }

    public AcLoadFlowParameters setVoltageRemoteControlRobustMode(boolean voltageRemoteControlRobustMode) {
        this.voltageRemoteControlRobustMode = voltageRemoteControlRobustMode;
        return this;
    }

    public double getMinRealisticVoltage() {
        return minRealisticVoltage;
    }

    public AcLoadFlowParameters setMinRealisticVoltage(double minRealisticVoltage) {
        this.minRealisticVoltage = minRealisticVoltage;
        return this;
    }

    public double getMaxRealisticVoltage() {
        return maxRealisticVoltage;
    }

    public AcLoadFlowParameters setMaxRealisticVoltage(double maxRealisticVoltage) {
        this.maxRealisticVoltage = maxRealisticVoltage;
        return this;
    }

    public boolean isExtrapolateReactiveLimits() { return extrapolateReactiveLimits; }

    public AcLoadFlowParameters setExtrapolateReactiveLimits(boolean extrapolateReactiveLimits) {
        this.extrapolateReactiveLimits = extrapolateReactiveLimits;
        return this;
    }

    @Override
    public String toString() {
        return "AcLoadFlowParameters(" +
                "networkParameters=" + networkParameters +
                ", equationSystemCreationParameters=" + equationSystemCreationParameters +
                ", acSolverParameters=" + acSolverParameters +
                ", outerLoops=" + outerLoops.stream().map(outerLoop -> outerLoop.getClass().getSimpleName()).toList() +
                ", maxOuterLoopIterations=" + maxOuterLoopIterations +
                ", matrixFactory=" + matrixFactory.getClass().getSimpleName() +
                ", voltageInitializer=" + voltageInitializer.getClass().getSimpleName() +
                ", asymmetrical=" + asymmetrical +
                ", slackDistributionFailureBehavior=" + slackDistributionFailureBehavior.name() +
                ", solverFactory=" + solverFactory.getClass().getSimpleName() +
                ", detailedReport=" + detailedReport +
                ", voltageRemoteControlRobustMode=" + voltageRemoteControlRobustMode +
                ", minRealisticVoltage=" + minRealisticVoltage +
                ", maxRealisticVoltage=" + maxRealisticVoltage +
                ", extrapolateReactiveLimits= " + extrapolateReactiveLimits +
                ')';
    }
}
