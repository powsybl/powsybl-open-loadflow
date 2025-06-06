/*
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow;

import com.powsybl.loadflow.AbstractLoadFlowDefaultParametersLoader;

/**
 * @author Hugo Kulesza {@literal <hugo.kulesza at rte-france.com>}
 */
public class OLFDefaultParametersLoaderMock extends AbstractLoadFlowDefaultParametersLoader {
    private static final String RESOURCE_FILE = "/OLFParametersUpdate.json";

    OLFDefaultParametersLoaderMock(String name) {
        super(name, RESOURCE_FILE);
    }
}
