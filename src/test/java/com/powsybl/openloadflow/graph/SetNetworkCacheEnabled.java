/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph;

import com.powsybl.iidm.network.Line;
import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.SparseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

/**
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 */
public class SetNetworkCacheEnabled {

    @Test
    void setNetworkCacheEnabled() {
        LoadFlowParameters params = new LoadFlowParameters()
                .setDc(true)
                .setComponentMode(LoadFlowParameters.ComponentMode.MAIN_SYNCHRONOUS);
        params.addExtension(OpenLoadFlowParameters.class, new OpenLoadFlowParameters()
                .setNetworkCacheEnabled(true));

        Network network = Network.read(Path.of("/home/carrezval/networks/case_SyntheticUSA.mat"));

        OpenLoadFlowProvider olfp = new OpenLoadFlowProvider(new SparseMatrixFactory(), new DTreeStandaloneFactory<>());
        LoadFlow.Runner runner = new LoadFlow.Runner(olfp);
        runner.run(network, params);

        long time = System.currentTimeMillis();
        int i = 0;
        for (Line line : network.getLines()) {
            System.out.println(i++);
            if (line.getTerminal1() != null && line.getTerminal2() != null && !line.getId().contains(".")) {
                line.disconnect();
                runner.run(network, params);
                line.connect();
            }

            if (System.currentTimeMillis() - time > 0.25 * 60 * 1000) {
                break;
            }
        }
    }
}
