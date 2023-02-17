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

    private final AcEquationSystemCreationParameters equationSystemCreationParameters;

    private final NewtonRaphsonParameters newtonRaphsonParameters;

    private final List<OuterLoop> outerLoops;

    private VoltageInitializer voltageInitializer;

    public AcLoadFlowParameters(LfNetworkParameters networkParameters, AcEquationSystemCreationParameters equationSystemCreationParameters,
                                NewtonRaphsonParameters newtonRaphsonParameters, List<OuterLoop> outerLoops, MatrixFactory matrixFactory,
                                VoltageInitializer voltageInitializer) {
        super(networkParameters, matrixFactory);
        this.equationSystemCreationParameters = Objects.requireNonNull(equationSystemCreationParameters);
        this.newtonRaphsonParameters = Objects.requireNonNull(newtonRaphsonParameters);
        this.outerLoops = Objects.requireNonNull(outerLoops);
        this.voltageInitializer = Objects.requireNonNull(voltageInitializer);
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

    public VoltageInitializer getVoltageInitializer() {
        return voltageInitializer;
    }

    public void setVoltageInitializer(VoltageInitializer voltageInitializer) {
        this.voltageInitializer = Objects.requireNonNull(voltageInitializer);
    }

    @Override
    public String toString() {
        return "AcLoadFlowParameters(" +
                "networkParameters=" + networkParameters +
                ", equationSystemCreationParameters=" + equationSystemCreationParameters +
                ", newtonRaphsonParameters=" + newtonRaphsonParameters +
                ", outerLoops=" + outerLoops.stream().map(outerLoop -> outerLoop.getClass().getSimpleName()).collect(Collectors.toList()) +
                ", matrixFactory=" + matrixFactory.getClass().getSimpleName() +
                ", voltageInitializer=" + voltageInitializer.getClass().getSimpleName() +
                ')';
    }
}
