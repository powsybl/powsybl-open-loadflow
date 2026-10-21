/**
 * Copyright (c) 2022, Jean-Baptiste Heyberger & Geoffroy Jamgotchian
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.extensions;

import com.powsybl.commons.extensions.AbstractExtensionAdder;
import com.powsybl.iidm.network.Load;

import java.util.Objects;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class LoadShortCircuitAdder extends AbstractExtensionAdder<Load, LoadShortCircuit> {

    private LoadShortCircuit.LoadShortCircuitType loadShortCircuitType = LoadShortCircuit.LoadShortCircuitType.UNKNOWN;
    private LoadShortCircuit.AsynchronousMachineLoadData asynchronousMachineLoadData;

    public LoadShortCircuitAdder(Load load) {
        super(load);
    }

    @Override
    public Class<? super LoadShortCircuit> getExtensionClass() {
        return LoadShortCircuit.class;
    }

    public LoadShortCircuitAdder withLoadShortCircuitType(LoadShortCircuit.LoadShortCircuitType loadShortCircuitType) {
        this.loadShortCircuitType = Objects.requireNonNull(loadShortCircuitType);
        return this;
    }

    public LoadShortCircuitAdder withAsynchronousMachineLoadData(LoadShortCircuit.AsynchronousMachineLoadData asynchronousMachineLoadData) {
        this.asynchronousMachineLoadData = asynchronousMachineLoadData;
        return this;
    }

    @Override
    protected LoadShortCircuit createExtension(Load load) {
        return new LoadShortCircuit(load, loadShortCircuitType, asynchronousMachineLoadData);
    }

}
