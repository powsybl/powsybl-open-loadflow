/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import org.anarres.graphviz.builder.GraphVizAttribute;
import org.anarres.graphviz.builder.GraphVizEdge;
import org.anarres.graphviz.builder.GraphVizGraph;
import org.anarres.graphviz.builder.GraphVizScope;

import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class GraphVizGraphBuilder {

    private final LfNetwork network;

    public GraphVizGraphBuilder(LfNetwork network) {
        this.network = Objects.requireNonNull(network);
    }

    private static String getNodeLabel(LfBus bus) {
        StringBuilder builder = new StringBuilder(bus.getId());
        if (bus.getGenerationTargetP() != 0 || bus.getGenerationTargetQ() != 0) {
            builder.append("\ngen=")
                    .append(String.format("%.1f", bus.getGenerationTargetP())).append(" MW, ")
                    .append(String.format("%.1f", bus.getGenerationTargetQ())).append(" MVar");
        }
        if (bus.getLoadTargetP() != 0 || bus.getLoadTargetQ() != 0) {
            builder.append("\nload=")
                    .append(String.format("%.1f", bus.getLoadTargetP())).append(" MW, ")
                    .append(String.format("%.1f", bus.getLoadTargetQ())).append(" MVar");
        }
        return builder.toString();
    }

    private static String getEdgeLabel(LfBranch branch) {
        StringBuilder builder = new StringBuilder(branch.getId());
        PiModel piModel = branch.getPiModel();
        if (piModel.getR1() != 1) {
            builder.append("\nr1=").append(String.format("%.3f", piModel.getR1()));
        }
        if (piModel.getA1() != 0) {
            builder.append("\na1=").append(String.format("%.3f", piModel.getA1()));
        }
        return builder.toString();
    }

    private static String getNodeColor(LfBus bus) {
        return bus.isVoltageControlled() ? "red" : "";
    }

    private static String getEdgeColor(LfBranch branch, boolean dc) {
        if (branch.isZeroImpedance(dc)) {
            return branch.isSpanningTreeEdge(dc) ? "red" : "orange";
        }
        return "black";
    }

    public GraphVizGraph build(boolean dc) {
        GraphVizGraph graph = new GraphVizGraph().label(network.getId());
        GraphVizScope scope = new GraphVizScope.Impl();
        for (LfBus bus : network.getBuses()) {
            graph.node(scope, bus.getNum())
                    .label(getNodeLabel(bus))
                    .attr(GraphVizAttribute.shape, "box")
                    .attr(GraphVizAttribute.style, "filled,rounded")
                    .attr(GraphVizAttribute.fontsize, "10")
                    .attr(GraphVizAttribute.color, getNodeColor(bus))
                    .attr(GraphVizAttribute.fillcolor, "grey");
        }
        for (LfBranch branch : network.getBranches()) {
            LfBus bus1 = branch.getBus1();
            LfBus bus2 = branch.getBus2();
            if (bus1 != null && bus2 != null) {
                GraphVizEdge edge = graph.edge(scope, bus1.getNum(), bus2.getNum(), branch.getNum());
                edge.label().append(getEdgeLabel(branch));
                edge.attr(GraphVizAttribute.fillcolor, getEdgeColor(branch, dc));
            }
        }
        return graph;
    }
}
