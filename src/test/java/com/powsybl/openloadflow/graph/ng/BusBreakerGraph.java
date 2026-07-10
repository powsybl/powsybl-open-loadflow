/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph.ng;

import com.powsybl.iidm.network.*;

/**
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 */
@SuppressWarnings("checkstyle:FinalClass")
public class BusBreakerGraph extends NetworkGraph<BusBreakerGraph.BBVertex> {

    public BusBreakerGraph(Network network) {
        super(network);
    }

    @Override
    public void generateGraph() {
        for (VoltageLevel vl : network.getVoltageLevels()) {
            VoltageLevel.BusBreakerView bbview = vl.getBusBreakerView();

            for (Bus bus : bbview.getBuses()) {
                addVertex(new BBVertex(bus));
            }

            for (Switch sw : bbview.getSwitches()) {
                Edge<BBVertex> edge = forSwitch(sw);
                idToEdge.put(sw.getId(), edge);
                addEdge(edge.src(), edge.dest(), edge);
            }
        }

        addInterVoltageLevelEdges(network);
    }

    @Override
    protected BBVertex vertexFromTerminal(Terminal terminal) {
        Bus bus = terminal.getBusBreakerView().getBus();
        if (bus == null) {
            return null;
        }
        return new BBVertex(bus);
    }

    @Override
    protected Edge<BBVertex> forSwitch(Switch sw) {
        VoltageLevel vl = sw.getVoltageLevel();
        VoltageLevel.BusBreakerView bbview = vl.getBusBreakerView();
        String id = sw.getId();

        return new Edge<>(new BBVertex(bbview.getBus1(id)),
                new BBVertex(bbview.getBus2(id)),
                sw,
                null);
    }

    public record BBVertex(Bus bus) implements Vertex {
        @Override
        public VoltageLevel voltageLevel() {
            return bus().getVoltageLevel();
        }
    }
}
