/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow;

import com.powsybl.loadflow.LoadFlowProvider;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.tools.PowsyblCoreVersion;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
class OpenLoadFlowProviderTest {

    @Test
    void test() {
        LoadFlowProvider loadFlowProvider = new OpenLoadFlowProvider(new DenseMatrixFactory());
        assertEquals("OpenLoadFlow", loadFlowProvider.getName());
        assertEquals(new PowsyblCoreVersion().getMavenProjectVersion(), loadFlowProvider.getVersion());
    }
}
