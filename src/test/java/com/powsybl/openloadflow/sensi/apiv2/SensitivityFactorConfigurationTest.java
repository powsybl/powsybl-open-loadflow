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
import com.powsybl.openloadflow.sensi.apiv2.json.SensitivityAnalysisJsonModule;
import org.junit.jupiter.api.Test;

import java.io.IOException;

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
        factorConfiguration.getSimpleFactors().add(SimpleSensitivityFactor.createBranchFlowWithRespectToInjection("l14", "g1"));
        factorConfiguration.getSimpleFactors().add(SimpleSensitivityFactor.createBranchFlowWithRespectToInjection("l12", "g1"));
        factorConfiguration.getMatrixFactors().add(MatrixSensitivityFactor.createBranchFlowWithRespectToInjection(asList("l14", "l12", "l23"),
                                                                                                                  asList("g1", "g2")));
        factorConfiguration.getMultiVarsFactors().add(MultiVariablesSensitivityFactor.createBranchFlowWithRespectToWeightedInjections(asList("l14"),
                                                                                                                                      asList(new WeightedVariable("g1", 1), new WeightedVariable("g2", 2))));

        factorConfiguration.getMatrixFactors().get(0).getValues();
        factorConfiguration.getMatrixFactors().get(0).getFunctionsReferences();
        factorConfiguration.getMultiVarsFactors().get(0).getValues();
        factorConfiguration.getMultiVarsFactors().get(0).getFunctionsReferences();
        String str = new ObjectMapper()
                .registerModule(new SensitivityAnalysisJsonModule())
                .writerWithDefaultPrettyPrinter().writeValueAsString(factorConfiguration);
        System.out.println(str);
    }
}
