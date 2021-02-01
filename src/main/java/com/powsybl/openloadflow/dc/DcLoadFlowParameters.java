/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.dc;

import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.openloadflow.network.SlackBusSelector;

import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class DcLoadFlowParameters {

    private final SlackBusSelector slackBusSelector;

    private final MatrixFactory matrixFactory;

    private final boolean updateFlows;

    boolean useTransformerRatio;

    private boolean distributedSlack;

    private LoadFlowParameters.BalanceType balanceType;

    private final double plausibleActivePowerLimit;

    public DcLoadFlowParameters(SlackBusSelector slackBusSelector, MatrixFactory matrixFactory, boolean updateFlows,
                                boolean useTransformerRatio, boolean distributedSlack, LoadFlowParameters.BalanceType balanceType,
                                double plausibleActivePowerLimit) {
        this.slackBusSelector = Objects.requireNonNull(slackBusSelector);
        this.matrixFactory = Objects.requireNonNull(matrixFactory);
        this.updateFlows = updateFlows;
        this.useTransformerRatio = useTransformerRatio;
        this.distributedSlack = distributedSlack;
        this.balanceType = balanceType;
        this.plausibleActivePowerLimit = plausibleActivePowerLimit;
    }

    public SlackBusSelector getSlackBusSelector() {
        return slackBusSelector;
    }

    public MatrixFactory getMatrixFactory() {
        return matrixFactory;
    }

    public boolean isUpdateFlows() {
        return updateFlows;
    }

    public boolean isDistributedSlack() {
        return distributedSlack;
    }

    public LoadFlowParameters.BalanceType getBalanceType() {
        return balanceType;
    }

    public boolean isUseTransformerRatio() {
        return useTransformerRatio;
    }

    public double getPlausibleActivePowerLimit() {
        return plausibleActivePowerLimit;
    }
}
