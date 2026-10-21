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
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.graph.EvenShiloachGraphDecrementalConnectivityFactory;
import com.powsybl.openloadflow.graph.GraphConnectivityFactory;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.SlackBusSelectionMode;
import com.powsybl.openloadflow.test.AbstractSecurityAnalysisTest;
import com.powsybl.openloadflow.test.CommonTestConfig;
import com.powsybl.openloadflow.test.ServiceParameterResolver;
import com.powsybl.security.SecurityAnalysisProvider;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * @author Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
@ExtendWith(ServiceParameterResolver.class)
public abstract class AbstractOpenSecurityAnalysisTest extends AbstractSecurityAnalysisTest {

    protected CommonTestConfig commonTestConfig;

    private final GraphConnectivityFactory<LfBus, LfBranch> connectivityFactory = new EvenShiloachGraphDecrementalConnectivityFactory<>();

    protected AbstractOpenSecurityAnalysisTest(CommonTestConfig commonTestConfig) {
        this.commonTestConfig = commonTestConfig;
    }

    @Override
    protected SecurityAnalysisProvider createSecurityAnalysisProvider() {
        return new OpenSecurityAnalysisProvider(commonTestConfig.matrixFactory(), connectivityFactory);
    }

    @Override
    protected LoadFlowProvider createLoadFlowProvider() {
        return new OpenLoadFlowProvider(commonTestConfig.matrixFactory(), connectivityFactory);
    }

    protected static void setSlackBusId(LoadFlowParameters lfParameters, String slackBusId) {
        OpenLoadFlowParameters.create(lfParameters)
                .setSlackBusSelectionMode(SlackBusSelectionMode.NAME)
                .setSlackBusId(slackBusId);
    }
}
