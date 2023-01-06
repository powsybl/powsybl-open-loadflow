/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.ieeecdf.converter.IeeeCdfNetworkFactory;
import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.network.impl.extensions.SecondaryVoltageControl.PilotPoint;
import com.powsybl.openloadflow.network.impl.extensions.SecondaryVoltageControl.Zone;
import com.powsybl.openloadflow.network.impl.extensions.SecondaryVoltageControlAdder;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
class SecondaryVoltageControlTest {

    @Test
    void test() {
        Network network = IeeeCdfNetworkFactory.create14();
        network.newExtension(SecondaryVoltageControlAdder.class)
                .addZone(new Zone(new PilotPoint("B10", 14), List.of("B1-G", "B2-G", "B3-G", "B6-G")))
                .add();

        var loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        var parameters = new LoadFlowParameters();
        OpenLoadFlowParameters.create(parameters)
                .setSecondaryVoltageControl(true);
        loadFlowRunner.run(network, parameters);
    }
}
