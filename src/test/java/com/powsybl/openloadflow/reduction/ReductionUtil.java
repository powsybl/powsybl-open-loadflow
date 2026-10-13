/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.reduction;


import com.powsybl.iidm.network.*;
import com.powsybl.iidm.network.regulation.RegulationMode;

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

    public static Network create4BusNetwork() {
        Network network = NetworkFactory.findDefault().createNetwork("network", "test");

        createSubstation(network, 1);
        createSubstation(network, 2);
        createSubstation(network, 3);
        createSubstation(network, 4);

        createGenerator(network, 1);
        createLoad(network, 4);

        createLine(network, 1, 2, 0.12, -1.2);
        createLine(network, 1, 3, 0.13, -1.3);
        createLine(network, 2, 3, 0.23, -2.3);
        createLine(network, 2, 4, 0.24, -2.4);
        createLine(network, 3, 4, 0.34, -3.4);

        return network;
    }

    private static void createSubstation(Network network, int num) {
        Substation substation1 = network.newSubstation()
            .setId("ST" + num)
            .add();
        VoltageLevel voltageLevel1 = substation1.newVoltageLevel()
            .setId("VL" + num)
            .setNominalV(10.0)
            .setTopologyKind(TopologyKind.BUS_BREAKER)
            .add();
        voltageLevel1.getBusBreakerView().newBus()
            .setId("BUS" + num)
            .add();
    }

    private static void createGenerator(Network network, int num) {
        network.getVoltageLevel("VL" + num)
            .newGenerator()
            .setId("GEN" + num)
            .setBus("BUS" + num)
            .setTargetP(10.0)
            .setMinP(0.0)
            .setMaxP(200.0)
            .setLocalTargetV(10.0)
            .newVoltageRegulation()
            .withMode(RegulationMode.VOLTAGE)
            .add()
            .add();
    }

    private static void createLoad(Network network, int num) {
        network.getVoltageLevel("VL" + num).newLoad()
            .setId("LOAD" + num)
            .setBus("BUS" + num)
            .setP0(10.0)
            .setQ0(1.0)
            .add();
    }

    private static void createLine(Network network, int from, int to, double g, double b) {
        double ySquare = g * g + b * b;
        network.newLine()
            .setId("L-" + from + to)
            .setR(g / ySquare)
            .setX(-b / ySquare)
            .setG1(0.1 * g)
            .setB1(0.1 * b)
            .setG2(0.01 * g)
            .setB2(0.01 * b)
            .setVoltageLevel1("VL" + from)
            .setVoltageLevel2("VL" + to)
            .setBus1("BUS" + from)
            .setBus2("BUS" + to)
            .add();
    }

}
