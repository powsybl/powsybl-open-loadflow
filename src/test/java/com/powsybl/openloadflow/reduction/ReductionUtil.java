/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.reduction;


import java.util.Set;

/**
 * @author Romain Courtier {@literal <romain.courtier at rte-france.com>}
 */
public class ReductionUtil {

    public static Set<String> getIEEE118InternalVlIds() {
        return Set.of(
            "VL39", "VL40", "VL41", "VL42", "VL44", "VL45", "VL46", "VL47", "VL48", "VL49",
            "VL50", "VL51", "VL52", "VL53", "VL54", "VL55", "VL56", "VL57", "VL58", "VL59", "VL60", "VL61", "VL62",
            "VL63", "VL64", "VL66", "VL67", "VL68", "VL69", "VL70", "VL71", "VL72", "VL73", "VL74", "VL75",
            "VL76", "VL77", "VL78", "VL79", "VL80", "VL81", "VL82", "VL83", "VL84", "VL85", "VL86", "VL87", "VL88",
            "VL89", "VL90", "VL91", "VL92", "VL93", "VL94", "VL95", "VL96", "VL97", "VL98", "VL99", "VL100", "VL101",
            "VL102", "VL103", "VL104", "VL105", "VL106", "VL107", "VL108", "VL109", "VL110", "VL111", "VL112", "VL116",
            "VL118"
        );
    }

    public static Set<String> getIEEE118BoundaryVlIds() {
        return Set.of("VL24", "VL37", "VL43", "VL65");
    }

}
