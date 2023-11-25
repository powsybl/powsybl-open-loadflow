/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.dc;

import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.openloadflow.lf.AbstractLoadFlowParameters;
import com.powsybl.openloadflow.dc.equations.DcEquationSystemCreationParameters;
import com.powsybl.openloadflow.network.LfNetworkParameters;

import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class DcLoadFlowParameters extends AbstractLoadFlowParameters<DcLoadFlowParameters> {

    private final DcEquationSystemCreationParameters equationSystemCreationParameters;

    private final boolean distributedSlack;

    private final LoadFlowParameters.BalanceType balanceType;

    private final boolean setVToNan;

    private final int maxOuterLoopIterations;

    public DcLoadFlowParameters(LfNetworkParameters networkParameters, DcEquationSystemCreationParameters equationSystemCreationParameters,
                                MatrixFactory matrixFactory, boolean distributedSlack, LoadFlowParameters.BalanceType balanceType,
                                boolean setVToNan, int maxOuterLoopIterations) {
        super(networkParameters, matrixFactory);
        this.equationSystemCreationParameters = Objects.requireNonNull(equationSystemCreationParameters);
        this.distributedSlack = distributedSlack;
        this.balanceType = balanceType;
        this.setVToNan = setVToNan;
        this.maxOuterLoopIterations = maxOuterLoopIterations;
    }

    public DcEquationSystemCreationParameters getEquationSystemCreationParameters() {
        return equationSystemCreationParameters;
    }

    public int getMaxOuterLoopIterations() {
        return maxOuterLoopIterations;
    }

    public boolean isDistributedSlack() {
        return distributedSlack;
    }

    public LoadFlowParameters.BalanceType getBalanceType() {
        return balanceType;
    }

    public boolean isSetVToNan() {
        return setVToNan;
    }

    @Override
    public String toString() {
        return "DcLoadFlowParameters(" +
                "networkParameters=" + networkParameters +
                ", equationSystemCreationParameters=" + equationSystemCreationParameters +
                ", matrixFactory=" + matrixFactory.getClass().getSimpleName() +
                ", distributedSlack=" + distributedSlack +
                ", balanceType=" + balanceType +
                ", setVToNan=" + setVToNan +
                ", maxOuterLoopIterations=" + maxOuterLoopIterations +
                ')';
    }
}
