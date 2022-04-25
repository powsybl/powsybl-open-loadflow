/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public final class PropertyUtil {

    private PropertyUtil() {
    }

    public static <E extends LfElement> boolean save(String propertyName, E element, Function<E, Object> getter) {
        Objects.requireNonNull(propertyName);
        Objects.requireNonNull(element);
        Objects.requireNonNull(getter);
        if (element.getProperty(propertyName) == null) {
            Object value = getter.apply(element);
            element.setProperty(propertyName, value);
            return true;
        }
        return false;
    }

    public static <E extends LfElement, T> boolean restore(String propertyName, E element, BiConsumer<E, T> setter) {
        Objects.requireNonNull(propertyName);
        Objects.requireNonNull(element);
        Objects.requireNonNull(setter);
        Object value = element.getProperty(propertyName);
        if (value != null) {
            setter.accept(element, (T) value);
            element.removeProperty(propertyName);
            return true;
        }
        return false;
    }
}
