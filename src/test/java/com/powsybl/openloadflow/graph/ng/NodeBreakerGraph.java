/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph.ng;

import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Switch;
import com.powsybl.iidm.network.Terminal;
import com.powsybl.iidm.network.VoltageLevel;

/**
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 */
@SuppressWarnings("checkstyle:FinalClass")
public class NodeBreakerGraph extends NetworkGraph<NodeBreakerGraph.NBVertex> {

    public NodeBreakerGraph(Network network) {
        super(network);
    }

    @Override
    public void generateGraph() {
        for (VoltageLevel vl : network.getVoltageLevels()) {
            VoltageLevel.NodeBreakerView nbview = vl.getNodeBreakerView();

            for (int node : nbview.getNodes()) {
                addVertex(new NBVertex(vl, node));
            }

            for (Switch sw : nbview.getSwitches()) {
                Edge<NBVertex> edge = forSwitch(sw);
                idToEdge.put(sw.getId(), edge);
                addEdge(edge.src(), edge.dest(), edge);
            }
        }

        addInterVoltageLevelEdges(network);
    }

    @Override
    protected NBVertex vertexFromTerminal(Terminal terminal) {
        VoltageLevel voltageLevel = terminal.getVoltageLevel();
        return new NBVertex(voltageLevel, terminal.getNodeBreakerView().getNode());
    }

    @Override
    protected Edge<NBVertex> forSwitch(Switch sw) {
        VoltageLevel vl = sw.getVoltageLevel();
        VoltageLevel.NodeBreakerView nbview = vl.getNodeBreakerView();
        String id = sw.getId();

        return new Edge<>(new NBVertex(vl, nbview.getNode1(id)),
                new NBVertex(vl, nbview.getNode2(id)),
                sw,
                null);
    }

    public record NBVertex(VoltageLevel voltageLevel, int node) implements Vertex { }
}
