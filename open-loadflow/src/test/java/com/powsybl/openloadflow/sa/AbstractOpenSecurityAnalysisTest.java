/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.sa;

import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowProvider;
import com.powsybl.na.graph.dtree.DTreeGraphConnectivityFactory;
import com.powsybl.na.network.SlackBusSelectionMode;
import com.powsybl.na.test.AbstractSecurityAnalysisTest;
import com.powsybl.na.test.CommonTestConfig;
import com.powsybl.na.test.ServiceParameterResolver;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.security.SecurityAnalysisProvider;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * @author Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
@ExtendWith(ServiceParameterResolver.class)
public abstract class AbstractOpenSecurityAnalysisTest extends AbstractSecurityAnalysisTest {

    protected CommonTestConfig commonTestConfig;

    protected AbstractOpenSecurityAnalysisTest(CommonTestConfig commonTestConfig) {
        this.commonTestConfig = commonTestConfig;
    }

    @Override
    protected SecurityAnalysisProvider createSecurityAnalysisProvider() {
        return new OpenSecurityAnalysisProvider(commonTestConfig.matrixFactory(), new DTreeGraphConnectivityFactory<>());
    }

    @Override
    protected LoadFlowProvider createLoadFlowProvider() {
        return new OpenLoadFlowProvider(commonTestConfig.matrixFactory(), new DTreeGraphConnectivityFactory<>());
    }

    protected static void setSlackBusId(LoadFlowParameters lfParameters, String slackBusId) {
        OpenLoadFlowParameters.create(lfParameters)
                .setSlackBusSelectionMode(SlackBusSelectionMode.NAME)
                .setSlackBusId(slackBusId);
    }
}
