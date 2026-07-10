/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph.log;

/**
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 */
@SuppressWarnings("checkstyle:ClassTypeParameterName")
public class TProgress<THIS extends TProgress<THIS>> {

    private ProgressManager<THIS> manager;

    protected void notifyProgressManager() {
        if (manager != null) {
            manager.printProgress();
        }
    }

    void setManager(ProgressManager<THIS> manager) {
        this.manager = manager;
    }
}
