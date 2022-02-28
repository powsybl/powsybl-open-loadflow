/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.equations;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public interface EquationSystemIndexListener {

    /**
     * Called when a new variable has been added to the system.
     */
    void onVariablesIndexUpdate();

    /**
     * Called when a new equation has been added to the system.
     */
    void onEquationsIndexUpdate();
}
