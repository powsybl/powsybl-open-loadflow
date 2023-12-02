/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.dc;

import com.powsybl.commons.PowsyblException;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.openloadflow.dc.equations.DcApproximationType;
import com.powsybl.openloadflow.dc.equations.DcEquationSystemCreationParameters;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.util.VoltageInitializer;

import java.util.List;
import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class DcValueVoltageInitializer implements VoltageInitializer {

    private final LfNetworkParameters networkParameters;

    private final boolean distributedSlack;

    private final LoadFlowParameters.BalanceType balanceType;

    private final boolean useTransformerRatio;

    private final DcApproximationType dcApproximationType;

    private final MatrixFactory matrixFactory;

    private final int maxOuterLoopIterations;

    public DcValueVoltageInitializer(LfNetworkParameters networkParameters, boolean distributedSlack, LoadFlowParameters.BalanceType balanceType,
                                     boolean useTransformerRatio, DcApproximationType dcApproximationType, MatrixFactory matrixFactory, int maxOuterLoopIterations) {
        this.networkParameters = Objects.requireNonNull(networkParameters);
        this.distributedSlack = distributedSlack;
        this.balanceType = Objects.requireNonNull(balanceType);
        this.useTransformerRatio = useTransformerRatio;
        this.dcApproximationType = Objects.requireNonNull(dcApproximationType);
        this.matrixFactory = Objects.requireNonNull(matrixFactory);
        this.maxOuterLoopIterations = maxOuterLoopIterations;
    }

    @Override
    public void prepare(LfNetwork network) {
        // in case of distributed slack, we need to save and restore generators and loads target p which might have been
        // modified by slack distribution, so that AC load flow can restart from original state
        List<BusDcState> busStates = distributedSlack ? ElementState.save(network.getBuses(), BusDcState::save) : null;

        LfNetworkParameters networkParametersDcInit = new LfNetworkParameters(networkParameters)
                .setPhaseControl(false); // not supported yet.

        DcEquationSystemCreationParameters creationParameters = new DcEquationSystemCreationParameters()
                .setUpdateFlows(false)
                .setForcePhaseControlOffAndAddAngle1Var(false)
                .setUseTransformerRatio(useTransformerRatio)
                .setDcApproximationType(dcApproximationType);
        DcLoadFlowParameters parameters = new DcLoadFlowParameters()
                .setNetworkParameters(networkParametersDcInit)
                .setEquationSystemCreationParameters(creationParameters)
                .setMatrixFactory(matrixFactory)
                .setDistributedSlack(distributedSlack)
                .setBalanceType(balanceType)
                .setSetVToNan(false)
                .setMaxOuterLoopIterations(maxOuterLoopIterations);

        try (DcLoadFlowContext context = new DcLoadFlowContext(network, parameters)) {
            DcLoadFlowEngine engine = new DcLoadFlowEngine(context);
            if (!engine.run().isSucceeded()) {
                throw new PowsyblException("DC loadflow failed, impossible to initialize voltage angle from DC values");
            }
        }

        if (busStates != null) {
            ElementState.restore(busStates);
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
