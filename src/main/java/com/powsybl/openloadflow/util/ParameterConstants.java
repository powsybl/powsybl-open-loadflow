/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.util;

import com.powsybl.openloadflow.network.MostMeshedSlackBusSelector;
import com.powsybl.openloadflow.network.SlackBusSelector;

import static com.powsybl.openloadflow.OpenLoadFlowParameters.*;

/**
 * @author Jérémy Labous <jlabous at silicom.fr>
 */
public final class ParameterConstants {

    public static final String SLACK_BUS_SELECTOR_PARAM_NAME = "slackBusSelector";
    public static final SlackBusSelector SLACK_BUS_SELECTOR_DEFAULT_VALUE = new MostMeshedSlackBusSelector();

    public static final String DISTRIBUTED_SLACK_PARAM_NAME = "distributedSlack";
    public static final boolean DISTRIBUTED_SLACK_DEFAULT_VALUE = true;

    public static final String THROWS_EXCEPTION_IN_CASE_OF_SLACK_DISTRIBUTION_FAILURE_PARAM_NAME = "throwsExceptionInCaseOfSlackDistributionFailure";
    public static final boolean THROWS_EXCEPTION_IN_CASE_OF_SLACK_DISTRIBUTION_FAILURE_DEFAULT_VALUE = true;

    public static final String BALANCE_TYPE_PARAM_NAME = "balanceType";
    public static final BalanceType BALANCE_TYPE_DEFAULT_VALUE = BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX;

    public static final String DC_PARAM_NAME = "dc";
    public static final boolean DC_DEFAULT_VALUE = false;

    public static final String VOLTAGE_REMOTE_CONTROLE_PARAM_NAME = "voltageRemoteControl";
    public static final boolean VOLTAGE_REMOTE_CONTROLE_DEFAULT_VALUE = false;

    public static final String LOW_IMPEDANCE_BRANCH_MODE_PARAM_NAME = "lowImpedanceBranchMode";
    public static final LowImpedanceBranchMode LOW_IMPEDANCE_BRANCH_MODE_DEFAULT_VALUE = LowImpedanceBranchMode.REPLACE_BY_ZERO_IMPEDANCE_LINE;

    private ParameterConstants() {
    }
}
