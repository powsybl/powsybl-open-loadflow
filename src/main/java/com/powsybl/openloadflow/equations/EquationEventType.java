/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.equations;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public enum EquationEventType {
    EQUATION_CREATED,
    EQUATION_REMOVED,
    EQUATION_UPDATED,
    EQUATION_ACTIVATED,
    EQUATION_DEACTIVATED;
}
