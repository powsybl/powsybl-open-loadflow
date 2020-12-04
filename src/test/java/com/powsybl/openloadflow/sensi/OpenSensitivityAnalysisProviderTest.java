/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.sensi;

import com.powsybl.computation.local.LocalComputationManager;
import com.powsybl.contingency.BranchContingency;
import com.powsybl.contingency.ContingenciesProvider;
import com.powsybl.contingency.Contingency;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VariantManagerConstants;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.sensitivity.SensitivityAnalysisParameters;
import com.powsybl.sensitivity.SensitivityFactorsProvider;
import com.powsybl.tools.PowsyblCoreVersion;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
class OpenSensitivityAnalysisProviderTest extends AbstractSensitivityAnalysisTest {

    @Test
    void testGeneralInfos() {
        OpenSensitivityAnalysisProvider provider = new OpenSensitivityAnalysisProvider(new DenseMatrixFactory());
        assertEquals("OpenSensitivityAnalysis", provider.getName());
        assertEquals(new PowsyblCoreVersion().getMavenProjectVersion(), provider.getVersion());
    }

    @Test
    void testContingenciesNotSupported() {
        Network network = EurostagTutorialExample1Factory.create();
        runAcLf(network);

        SensitivityAnalysisParameters sensiParameters = createParameters(true, "VLLOAD_0");
        SensitivityFactorsProvider factorsProvider = n -> Collections.emptyList();
        ContingenciesProvider contingenciesProvider = n -> Collections.singletonList(new Contingency("c", new BranchContingency("NHV1_NHV2_1")));
        UnsupportedOperationException e = assertThrows(UnsupportedOperationException.class, () -> sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID,
                                                                                                                    factorsProvider, contingenciesProvider,
                                                                                                                    sensiParameters, LocalComputationManager.getDefault())
                                                                                                               .join());
        assertEquals("Contingencies not yet supported", e.getMessage());
    }
}
