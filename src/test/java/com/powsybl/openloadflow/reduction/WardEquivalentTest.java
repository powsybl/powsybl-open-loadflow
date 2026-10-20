/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.reduction;

import com.powsybl.ieeecdf.converter.IeeeCdfNetworkFactory;
import com.powsybl.iidm.network.*;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.reduction.WardReductionParameters.ReductionType;
import org.junit.jupiter.api.Test;

import java.util.*;

import static com.powsybl.openloadflow.reduction.ReductionUtil.create4BusNetwork;
import static com.powsybl.openloadflow.reduction.ReductionUtil.getIEEE118InternalVlIds;

/**
 * @author Romain Courtier {@literal <romain.courtier at rte-france.com>}
 */
class WardEquivalentTest {

    @Test
    void testWithIEEE118() {
        Network network = IeeeCdfNetworkFactory.create118();
        Set<String> internalVlIds = getIEEE118InternalVlIds();
        LoadFlowParameters lfParameters = new LoadFlowParameters();
        OpenLoadFlowParameters olfParametersExt = OpenLoadFlowParameters.create(lfParameters);

        WardReductionParameters parameters = new WardReductionParameters()
            .setInternalVoltageLevelIds(internalVlIds)
            .setLoadFlowParameters(lfParameters)
            .setMatrixFactory(new DenseMatrixFactory())
            .setReductionType(ReductionType.STANDARD_WARD);

        new WardEquivalent().reduce(network, parameters);
    }

    @Test
    void testWith4BusNetwork() {
        Network network = create4BusNetwork();

        Set<String> internalVlIds = Set.of("VL1");

        LoadFlowParameters lfParameters = new LoadFlowParameters();
        OpenLoadFlowParameters olfParametersExt = OpenLoadFlowParameters.create(lfParameters);

        LoadFlow.Runner runner = LoadFlow.find();
        runner.run(network, lfParameters);

        WardReductionParameters parameters = new WardReductionParameters()
            .setInternalVoltageLevelIds(internalVlIds)
            .setLoadFlowParameters(lfParameters)
            .setReductionType(ReductionType.STANDARD_WARD);

        new WardEquivalent().reduce(network, parameters);

        runner.run(network, lfParameters);
    }
}
