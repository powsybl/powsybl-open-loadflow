/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.sensi;

import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.tools.PowsyblCoreVersion;
import org.junit.jupiter.api.Test;


import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
