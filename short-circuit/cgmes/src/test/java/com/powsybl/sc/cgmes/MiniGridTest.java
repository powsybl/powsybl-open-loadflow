/**
 * Copyright (c) 2022, Jean-Baptiste Heyberger & Geoffroy Jamgotchian
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.cgmes;

import com.powsybl.cgmes.conformity.CgmesConformity1Catalog;
import com.powsybl.iidm.network.Network;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at gmail.com>
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
class MiniGridTest {

    private Map<String, String> busNameToId;

    @BeforeEach
    void setUp() {
        busNameToId = new HashMap<>();
        busNameToId.put("Bus1", "adee76cd-b2b9-48ac-8fd4-0d205a435f59");
        busNameToId.put("Bus9", "87c0d153-e308-4b2b-92a4-4fad53ab1ff9");
        busNameToId.put("Bus2", "b3d3b4ad-02af-4490-8748-70f6c9a23734");
        busNameToId.put("Bus8", "03163ede-7eec-457f-8641-365982227d7c");
        busNameToId.put("Bus3", "c8726716-e182-4373-b83e-8f60070078cb");
        busNameToId.put("Bus5", "37edd845-456f-4c3e-98d5-19af0c1cef1e");
        busNameToId.put("Bus6", "764e0b8a-f2af-4092-b6aa-b4a19e55db98");
        busNameToId.put("Bus7", "cd84fa40-ef63-422d-8ee0-d0a0f806719e");
        busNameToId.put("Bus10", "c7eda3d2-e92d-4935-8166-5e045d3de045");
        busNameToId.put("Bus11", "7f5515b2-ca6b-45af-93ee-f196686f0c66");
        busNameToId.put("Bus4", "c0adab49-d445-4609-a1a3-ebe4ef297cc8");
    }

    @Test
    void triphasedTest() {
        Properties parameters = new Properties();
        parameters.setProperty("iidm.import.cgmes.post-processors", CgmesShortCircuitImportPostProcessor.NAME);
        //TestGridModelResources testCgm = CgmesConformity1Catalog.miniBusBranch();
        Network network = Network.read(CgmesConformity1Catalog.miniBusBranch().dataSource(), parameters);
    }

    @Test
    void monophasedTest() {
        Properties parameters = new Properties();
        parameters.setProperty("iidm.import.cgmes.post-processors", CgmesShortCircuitImportPostProcessor.NAME);
        //TestGridModelResources testCgm = CgmesConformity1Catalog.miniBusBranch();
        Network network = Network.read(CgmesConformity1Catalog.miniBusBranch().dataSource(), parameters);
    }
}
