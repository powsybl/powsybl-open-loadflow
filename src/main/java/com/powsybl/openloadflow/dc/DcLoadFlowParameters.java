/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.dc;

import com.powsybl.iidm.network.Country;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.openloadflow.network.SlackBusSelector;
import com.powsybl.openloadflow.util.ParameterConstants;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class DcLoadFlowParameters {

    private final SlackBusSelector slackBusSelector;

    private final MatrixFactory matrixFactory;

    private final boolean updateFlows;

    private final boolean useTransformerRatio;

    private final boolean distributedSlack;

    private final LoadFlowParameters.BalanceType balanceType;

    private final boolean forcePhaseControlOffAndAddAngle1Var;

    private final double plausibleActivePowerLimit;

    private final boolean addRatioToLinesWithDifferentNominalVoltageAtBothEnds;

    private final boolean setVToNan;

    private final boolean computeMainConnectedComponentOnly;

    private final Set<Country> countriesToBalance;

    public DcLoadFlowParameters(SlackBusSelector slackBusSelector, MatrixFactory matrixFactory, boolean setVToNan) {
        this(slackBusSelector, matrixFactory, false, true, false, LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX, false,
                ParameterConstants.PLAUSIBLE_ACTIVE_POWER_LIMIT_DEFAULT_VALUE, false, setVToNan, true, LoadFlowParameters.DEFAULT_COUNTRIES_TO_BALANCE);
    }

    public DcLoadFlowParameters(SlackBusSelector slackBusSelector, MatrixFactory matrixFactory, boolean updateFlows,
                                boolean useTransformerRatio, boolean distributedSlack, LoadFlowParameters.BalanceType balanceType,
                                boolean forcePhaseControlOffAndAddAngle1Var, double plausibleActivePowerLimit,
                                boolean addRatioToLinesWithDifferentNominalVoltageAtBothEnds, boolean setVToNan, boolean computeMainConnectedComponentOnly,
                                Set<Country> countriesToBalance) {
        this.slackBusSelector = Objects.requireNonNull(slackBusSelector);
        this.matrixFactory = Objects.requireNonNull(matrixFactory);
        this.updateFlows = updateFlows;
        this.useTransformerRatio = useTransformerRatio;
        this.distributedSlack = distributedSlack;
        this.balanceType = balanceType;
        this.forcePhaseControlOffAndAddAngle1Var = forcePhaseControlOffAndAddAngle1Var;
        this.plausibleActivePowerLimit = plausibleActivePowerLimit;
        this.addRatioToLinesWithDifferentNominalVoltageAtBothEnds = addRatioToLinesWithDifferentNominalVoltageAtBothEnds;
        this.setVToNan = setVToNan;
        this.computeMainConnectedComponentOnly = computeMainConnectedComponentOnly;
        this.countriesToBalance = countriesToBalance;
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

    public boolean isForcePhaseControlOffAndAddAngle1Var() {
        return forcePhaseControlOffAndAddAngle1Var;
    }

    public double getPlausibleActivePowerLimit() {
        return plausibleActivePowerLimit;
    }

    public boolean isAddRatioToLinesWithDifferentNominalVoltageAtBothEnds() {
        return addRatioToLinesWithDifferentNominalVoltageAtBothEnds;
    }

    public boolean isSetVToNan() {
        return setVToNan;
    }

    public boolean isComputeMainConnectedComponentOnly() {
        return computeMainConnectedComponentOnly;
    }

    public Set<Country> getCountriesToBalance() {
        return Collections.unmodifiableSet(countriesToBalance);
    }

}
