/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.util;

import java.lang.ref.WeakReference;
import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public final class WeakReferenceUtil {

    private WeakReferenceUtil() {
    }

    public static <T> T get(WeakReference<T> ref) {
        return Objects.requireNonNull(Objects.requireNonNull(ref).get(), "Reference has been garbage collected");
    }
}
