/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.dc;

import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.graph.NaiveGraphConnectivityFactory;
import com.powsybl.openloadflow.network.LfElement;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.LfNetworkParameters;
import com.powsybl.openloadflow.network.impl.LfNetworkLoaderImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
class DcLoadFlowEngineTest {

    @Test
    void contextTest() {
        Network network = EurostagTutorialExample1Factory.create();
        LoadFlowParameters parameters = new LoadFlowParameters();
        OpenLoadFlowParameters olfParameters = OpenLoadFlowParameters.create(parameters);
        DcLoadFlowParameters dcParameters = OpenLoadFlowParameters.createDcParameters(parameters,
                                                                                      olfParameters,
                                                                                      new DenseMatrixFactory(),
                                                                                      new NaiveGraphConnectivityFactory<>(LfElement::getNum),
                                                                                      false);
        LfNetwork lfNetwork = LfNetwork.load(network, new LfNetworkLoaderImpl(), new LfNetworkParameters()).get(0);
        try (DcLoadFlowContext context = new DcLoadFlowContext(lfNetwork, dcParameters)) {
            DcLoadFlowEngine engine = new DcLoadFlowEngine(context);
            assertSame(context, engine.getContext());
        }
    }
}
