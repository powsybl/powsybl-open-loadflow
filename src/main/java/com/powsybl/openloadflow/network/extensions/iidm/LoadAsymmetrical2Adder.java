/**
 * Copyright (c) 2023, Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 * Copyright (c) 2023, Geoffroy Jamgotchian <geoffroy.jamgotchian at gmail.com>
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network.extensions.iidm;

import com.powsybl.commons.extensions.AbstractExtensionAdder;
import com.powsybl.iidm.network.Load;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class LoadAsymmetrical2Adder extends AbstractExtensionAdder<Load, LoadAsymmetrical2> {

    private LoadType loadType = LoadType.CONSTANT_POWER;

    public LoadAsymmetrical2Adder(Load load) {
        super(load);
    }

    @Override
    public Class<? super LoadAsymmetrical2> getExtensionClass() {
        return LoadAsymmetrical2.class;
    }

    @Override
    protected LoadAsymmetrical2 createExtension(Load load) {
        return new LoadAsymmetrical2(load, loadType);
    }

    public LoadAsymmetrical2Adder withLoadType(LoadType loadType) {
        this.loadType = loadType;
        return this;
    }
}
