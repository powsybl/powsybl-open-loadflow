/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.openloadflow.lf.AbstractLoadFlowParameters;
import com.powsybl.openloadflow.ac.equations.AcEquationSystemCreationParameters;
import com.powsybl.openloadflow.ac.nr.NewtonRaphsonParameters;
import com.powsybl.openloadflow.network.LfNetworkParameters;
import com.powsybl.openloadflow.network.util.VoltageInitializer;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class AcLoadFlowParameters extends AbstractLoadFlowParameters {

    public static final int DEFAULT_MAX_OUTER_LOOP_ITERATIONS = 20;

    private final AcEquationSystemCreationParameters equationSystemCreationParameters;

    private final NewtonRaphsonParameters newtonRaphsonParameters;

    private final List<OuterLoop> outerLoops;

    private final int maxOuterLoopIterations;

    private VoltageInitializer voltageInitializer;

    private final boolean isAsymmetrical;

    public AcLoadFlowParameters(LfNetworkParameters networkParameters, AcEquationSystemCreationParameters equationSystemCreationParameters,
                                NewtonRaphsonParameters newtonRaphsonParameters, List<OuterLoop> outerLoops, int maxOuterLoopIterations,
                                MatrixFactory matrixFactory, VoltageInitializer voltageInitializer, boolean isAsymmetrical) {
        super(networkParameters, matrixFactory);
        this.equationSystemCreationParameters = Objects.requireNonNull(equationSystemCreationParameters);
        this.newtonRaphsonParameters = Objects.requireNonNull(newtonRaphsonParameters);
        this.outerLoops = Objects.requireNonNull(outerLoops);
        this.maxOuterLoopIterations = maxOuterLoopIterations;
        this.voltageInitializer = Objects.requireNonNull(voltageInitializer);
        this.isAsymmetrical = isAsymmetrical;
    }

    public AcEquationSystemCreationParameters getEquationSystemCreationParameters() {
        return equationSystemCreationParameters;
    }

    public NewtonRaphsonParameters getNewtonRaphsonParameters() {
        return newtonRaphsonParameters;
    }

    public List<OuterLoop> getOuterLoops() {
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
        return isAsymmetrical;
    }

    @Override
    public String toString() {
        return "AcLoadFlowParameters(" +
                "networkParameters=" + networkParameters +
                ", equationSystemCreationParameters=" + equationSystemCreationParameters +
                ", newtonRaphsonParameters=" + newtonRaphsonParameters +
                ", outerLoops=" + outerLoops.stream().map(outerLoop -> outerLoop.getClass().getSimpleName()).collect(Collectors.toList()) +
                ", maxOuterLoopIterations=" + maxOuterLoopIterations +
                ", matrixFactory=" + matrixFactory.getClass().getSimpleName() +
                ", voltageInitializer=" + voltageInitializer.getClass().getSimpleName() +
                ", isAsymmetrical=" + isAsymmetrical +
                ')';
    }
}
