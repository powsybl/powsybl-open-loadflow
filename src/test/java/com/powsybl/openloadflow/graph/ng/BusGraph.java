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
public class BusGraph extends NetworkGraph<BusGraph.BVertex> {

    public BusGraph(Network network) {
        super(network);
    }

    @Override
    public void generateGraph() {
        for (Bus bus : network.getBusView().getBuses()) {
            addVertex(new BusGraph.BVertex(bus));
        }

        addInterVoltageLevelEdges(network);
    }

    @Override
    protected BVertex vertexFromTerminal(Terminal terminal) {
        Bus bus = terminal.getBusView().getBus();
        if (bus == null) {
            return null;
        }
        return new BVertex(bus);
    }

    @Override
    protected Edge<BVertex> forSwitch(Switch sw) {
        throw new UnsupportedOperationException();
    }

    public record BVertex(Bus bus) implements Vertex {

        @Override
        public VoltageLevel voltageLevel() {
            return bus.getVoltageLevel();
        }
    }
}
