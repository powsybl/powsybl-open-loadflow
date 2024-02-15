/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public abstract class AbstractPropertyBag {

    protected Map<String, Object> properties;

    public Object getProperty(String name) {
        Objects.requireNonNull(name);
        if (properties == null) {
            return null;
        }
        return properties.get(name);
    }

    public void setProperty(String name, Object value) {
        Objects.requireNonNull(name);
        if (properties == null) {
            properties = new HashMap<>();
        }
        properties.put(name, value);
    }

    public void removeProperty(String name) {
        Objects.requireNonNull(name);
        if (properties != null) {
            properties.remove(name);
        }
    }
}
