/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.equations;

import com.powsybl.commons.util.ServiceLoaderCache;

import java.util.Collections;
import java.util.List;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public interface EquationSystemPostProcessor {

    ServiceLoaderCache<EquationSystemPostProcessor> SERVICE_LOADER_CACHE = new ServiceLoaderCache<>(EquationSystemPostProcessor.class);

    static List<EquationSystemPostProcessor> findAll() {
        return Collections.unmodifiableList(SERVICE_LOADER_CACHE.getServices());
    }

    void onCreate(EquationSystem<?, ?> equationSystem);
}
