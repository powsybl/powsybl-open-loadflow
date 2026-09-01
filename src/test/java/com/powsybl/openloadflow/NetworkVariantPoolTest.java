/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow;

import com.powsybl.commons.PowsyblException;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VariantManagerConstants;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
class NetworkVariantPoolTest {

    @Test
    void acquireAndReleaseTest() {
        Network network = EurostagTutorialExample1Factory.create();
        String initialVariantId = VariantManagerConstants.INITIAL_VARIANT_ID;

        // first acquire creates the pool of variants
        String variantId = NetworkVariantPool.INSTANCE.acquire(network, initialVariantId, 2);
        assertNotNull(variantId);
        assertTrue(network.getVariantManager().getVariantIds().contains(variantId));

        // second acquire returns the other variant of the pool
        String variantId2 = NetworkVariantPool.INSTANCE.acquire(network, initialVariantId, 2);
        assertNotNull(variantId2);
        assertNotEquals(variantId, variantId2);

        // pool is now empty
        PowsyblException e = assertThrows(PowsyblException.class, () -> NetworkVariantPool.INSTANCE.acquire(network, initialVariantId, 2));
        assertTrue(e.getMessage().startsWith("No variant available in the pool for network"));

        // release a variant back to the pool and acquire it again
        NetworkVariantPool.INSTANCE.release(network, variantId);
        assertEquals(variantId, NetworkVariantPool.INSTANCE.acquire(network, initialVariantId, 2));

        NetworkVariantPool.INSTANCE.release(network, variantId);
        NetworkVariantPool.INSTANCE.release(network, variantId2);
    }

    @Test
    void invalidArgumentsTest() {
        Network network = EurostagTutorialExample1Factory.create();
        String initialVariantId = VariantManagerConstants.INITIAL_VARIANT_ID;

        PowsyblException e = assertThrows(PowsyblException.class, () -> NetworkVariantPool.INSTANCE.acquire(network, initialVariantId, 0));
        assertEquals("poolSize must be positive", e.getMessage());

        assertThrows(NullPointerException.class, () -> NetworkVariantPool.INSTANCE.acquire(null, initialVariantId, 1));
        assertThrows(NullPointerException.class, () -> NetworkVariantPool.INSTANCE.acquire(network, null, 1));
        assertThrows(NullPointerException.class, () -> NetworkVariantPool.INSTANCE.release(null, initialVariantId));
        assertThrows(NullPointerException.class, () -> NetworkVariantPool.INSTANCE.release(network, null));
    }

    @Test
    void releaseOnUnknownNetworkTest() {
        // releasing a variant of a network that was never acquired must not fail (no pool created yet)
        Network network = EurostagTutorialExample1Factory.create();
        NetworkVariantPool.INSTANCE.release(network, "whatever");
    }
}
