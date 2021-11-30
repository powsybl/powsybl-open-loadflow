/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.dc;

import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.reporter.Reporter;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.openloadflow.dc.equations.DcEquationSystemCreationParameters;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfGenerator;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.LfNetworkParameters;
import com.powsybl.openloadflow.network.util.VoltageInitializer;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class DcValueVoltageInitializer implements VoltageInitializer {

    private final LfNetworkParameters networkParameters;

    private final boolean distributedSlack;

    private final LoadFlowParameters.BalanceType balanceType;

    private final boolean useTransformerRatio;

    private final MatrixFactory matrixFactory;

    private final Reporter reporter;

    public DcValueVoltageInitializer(LfNetworkParameters networkParameters, boolean distributedSlack, LoadFlowParameters.BalanceType balanceType,
                                     boolean useTransformerRatio, MatrixFactory matrixFactory, Reporter reporter) {
        this.networkParameters = Objects.requireNonNull(networkParameters);
        this.distributedSlack = distributedSlack;
        this.balanceType = Objects.requireNonNull(balanceType);
        this.useTransformerRatio = useTransformerRatio;
        this.matrixFactory = Objects.requireNonNull(matrixFactory);
        this.reporter = Objects.requireNonNull(reporter);
    }

    @Override
    public void prepare(LfNetwork network) {
        // in case of distributed slack, we need to save and restore generators target p which might have been modified
        // by slack distribution, so that AC load flow can restart from original state
        Map<String, Double> generatorsTargetP = distributedSlack ? network.getBuses().stream()
                                                                          .flatMap(bus -> bus.getGenerators().stream())
                                                                          .collect(Collectors.toMap(LfGenerator::getId, LfGenerator::getTargetP))
                                                                 : null;

        DcLoadFlowParameters parameters = new DcLoadFlowParameters(networkParameters,
                                                                   new DcEquationSystemCreationParameters(false, false, false, useTransformerRatio),
                                                                   matrixFactory,
                                                                   distributedSlack,
                                                                   balanceType,
                                                                   false);
        DcLoadFlowEngine engine = new DcLoadFlowEngine(List.of(network), parameters);
        if (engine.run(reporter, network).getStatus() != LoadFlowResult.ComponentResult.Status.CONVERGED) {
            throw new PowsyblException("DC loadflow failed, impossible to initialize voltage angle from DC values");
        }

        if (generatorsTargetP != null) {
            network.getBuses().stream()
                    .flatMap(bus -> bus.getGenerators().stream())
                    .forEach(g -> g.setTargetP(generatorsTargetP.get(g.getId())));
        }
    }

    @Override
    public double getMagnitude(LfBus bus) {
        return 1;
    }

    @Override
    public double getAngle(LfBus bus) {
        return bus.getAngle();
    }
}
