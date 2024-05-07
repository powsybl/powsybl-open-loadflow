/**
 * Copyright (c) 2023, Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 * Copyright (c) 2023, Geoffroy Jamgotchian <geoffroy.jamgotchian at gmail.com>
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network.extensions.iidm;

import com.google.auto.service.AutoService;
import com.powsybl.commons.extensions.ExtensionAdderProvider;
import com.powsybl.iidm.network.TwoWindingsTransformer;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
@AutoService(ExtensionAdderProvider.class)
public class Tfo3PhasesImplProvider implements ExtensionAdderProvider<TwoWindingsTransformer, Tfo3Phases, Tfo3PhasesAdder> {
    @Override
    public String getImplementationName() {
        return "Default";
    }

    @Override
    public String getExtensionName() {
        return Tfo3Phases.NAME;
    }

    @Override
    public Class<Tfo3PhasesAdder> getAdderClass() {
        return Tfo3PhasesAdder.class;
    }

    @Override
    public Tfo3PhasesAdder newAdder(TwoWindingsTransformer t2w) {
        return new Tfo3PhasesAdder(t2w);
    }
}
