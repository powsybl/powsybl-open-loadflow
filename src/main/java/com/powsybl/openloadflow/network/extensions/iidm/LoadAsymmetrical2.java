/**
 * Copyright (c) 2023, Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 * Copyright (c) 2023, Geoffroy Jamgotchian <geoffroy.jamgotchian at gmail.com>
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network.extensions.iidm;

import com.powsybl.commons.extensions.AbstractExtension;
import com.powsybl.iidm.network.Load;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class LoadAsymmetrical2 extends AbstractExtension<Load> {

    // This class is used as an extension of a "classical" balanced direct load
    private final LoadType loadType;

    public static final String NAME = "loadAsymmetrical2";

    @Override
    public String getName() {
        return NAME;
    }

    public LoadAsymmetrical2(Load load, LoadType loadType) {
        super(load);
        this.loadType = loadType;
    }

    public LoadType getLoadType() {
        return loadType;
    }
}
