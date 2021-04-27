/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.sensi;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
class ContingencyContextTest {

    @Test
    void test() {
        ContingencyContext context = new ContingencyContext(ContingencyContextType.SPECIFIC, "c1");
        assertEquals("ContingencyContext(contingencyId='c1', contextType=SPECIFIC)", context.toString());
    }
}
