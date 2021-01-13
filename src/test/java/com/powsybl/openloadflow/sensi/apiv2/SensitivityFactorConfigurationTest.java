/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.sensi.apiv2;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.iidm.network.Network;
import com.powsybl.openloadflow.network.FourBusNetworkFactory;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;

import static java.util.Arrays.asList;

/**
 *
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
class SensitivityFactorConfigurationTest {

    @Test
    void test() throws IOException {
        Network network = FourBusNetworkFactory.create();
        SensitivityFactorConfiguration factorConfiguration = SensitivityFactorConfiguration.create();
        factorConfiguration.getSimpleFactors().add(SimpleSensitivityFactor.createBranchFlowWithRespectToInjectionFactor("l14", "g1"));
        factorConfiguration.getSimpleFactors().add(SimpleSensitivityFactor.createBranchFlowWithRespectToInjectionFactor("l12", "g1"));
        factorConfiguration.getMatrixFactors().add(MatrixSensitivityFactor.createBranchFlowWithRespectToInjectionFactors("m", asList("l14", "l12", "l23"), asList("g1", "g2")));
        factorConfiguration.getMultiVarsFactors().add(MultiVariablesSensitivityFactor.createBranchFlowWithRespectToWeightedInjectionsFactor("l14", Arrays.asList(new WeightedVariable("g1", 1), new WeightedVariable("g2", 2))));
        String str = new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(factorConfiguration);
        System.out.println(str);
    }
}
