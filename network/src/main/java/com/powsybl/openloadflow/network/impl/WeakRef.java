/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network.impl;

import java.lang.ref.WeakReference;
import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
class WeakRef<T> implements Ref<T> {

    private final WeakReference<T> value;

    WeakRef(T identifiable) {
        this.value = new WeakReference<>(Objects.requireNonNull(identifiable));
    }

    @Override
    public T get() {
        return Objects.requireNonNull(value.get(), "Reference has been garbage collected");
    }
}
