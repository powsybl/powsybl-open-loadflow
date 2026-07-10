/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph.utils;

/**
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 */
public enum GraphConnectivityMethod {
    ADD_VERTEX("v", 1),
    ADD_EDGE("e", 3),
    REMOVE_EDGE("rm", 1),
    START_TEMPORARY_CHANGES("start", 0),
    UNDO_TEMPORARY_CHANGES("undo", 0),
    GET_COMPONENT_NUMBER("get_num", 1),
    SET_MAIN_COMPONENT_VERTEX("set_main", 1),
    GET_NB_CONNECTED_COMPONENTS("count", 0),
    GET_CONNECTED_COMPONENT("get_comp", 1),
    GET_LARGEST_CONNECTED_COMPONENT("largest", 0),
    GET_VERTICES_ADDED_TO_MAIN_COMPONENT("v_added", 0),
    GET_EDGES_ADDED_TO_MAIN_COMPONENT("e_added", 0),
    GET_VERTICES_REMOVED_FROM_MAIN_COMPONENT("v_removed", 0),
    GET_EDGES_REMOVED_FROM_MAIN_COMPONENT("e_removed", 0);

    private final String shortName;
    private final int argCount;

    GraphConnectivityMethod(String shortName, int argCount) {
        this.shortName = shortName;
        this.argCount = argCount;
    }

    public String shortName() {
        return shortName;
    }

    public int argCount() {
        return argCount;
    }
}
