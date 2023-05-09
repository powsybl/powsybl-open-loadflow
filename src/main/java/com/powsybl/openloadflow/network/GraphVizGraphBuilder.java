/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import com.powsybl.openloadflow.util.PerUnit;
import org.anarres.graphviz.builder.*;

import java.util.Locale;
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
        StringBuilder builder = new StringBuilder(Integer.toString(bus.getNum()))
                .append("\n")
                .append(bus.getId());
        if (bus.getGenerationTargetP() != 0 || bus.getGenerationTargetQ() != 0) {
            builder.append("\ngen=")
                    .append(String.format(Locale.US, "%.1f", bus.getGenerationTargetP() * PerUnit.SB)).append(" MW ")
                    .append(String.format(Locale.US, "%.1f", bus.getGenerationTargetQ() * PerUnit.SB)).append(" MVar");
        }
        if (bus.getLoadTargetP() != 0 || bus.getLoadTargetQ() != 0) {
            builder.append("\nload=")
                    .append(String.format(Locale.US, "%.1f", bus.getLoadTargetP() * PerUnit.SB)).append(" MW ")
                    .append(String.format(Locale.US, "%.1f", bus.getLoadTargetQ() * PerUnit.SB)).append(" MVar");
        }
        return builder.toString();
    }

    private static String getEdgeLabel(LfBranch branch) {
        StringBuilder builder = new StringBuilder(branch.getId());
        PiModel piModel = branch.getPiModel();
        if (piModel.getR1() != 1) {
            builder.append("\nr1=").append(String.format(Locale.US, "%.3f", piModel.getR1()));
        }
        if (piModel.getA1() != 0) {
            builder.append("\na1=").append(String.format(Locale.US, "%.3f", piModel.getA1()));
        }
        return builder.toString();
    }

    private static String getEdgeColor(LfBranch branch, LoadFlowModel loadFlowModel) {
        if (branch.isZeroImpedance(loadFlowModel)) {
            return branch.isSpanningTreeEdge(loadFlowModel) ? "red" : "orange";
        }
        return "black";
    }

    public GraphVizGraph build(LoadFlowModel loadFlowModel) {
        GraphVizGraph graph = new GraphVizGraph().label(network.getId());
        GraphVizScope scope = new GraphVizScope.Impl();
        for (LfBus bus : network.getBuses()) {
            graph.node(scope, bus.getNum())
                    .label(getNodeLabel(bus))
                    .attr(GraphVizAttribute.shape, "box")
                    .attr(GraphVizAttribute.style, "filled,rounded")
                    .attr(GraphVizAttribute.fontsize, "10")
                    .attr(GraphVizAttribute.fillcolor, "grey");
        }
        // draw voltage controller -> controlled links
        for (LfBus bus : network.getBuses()) {
            if (bus.isGeneratorVoltageControlled()) {
                GeneratorVoltageControl vc = bus.getGeneratorVoltageControl().orElseThrow();
                for (LfBus controllerBus : vc.getControllerElements()) {
                    GraphVizEdge edge = graph.edge(scope, controllerBus.getNum(), bus.getNum(), controllerBus);
                    edge.attr(GraphVizAttribute.color, "lightgray")
                            .attr(GraphVizAttribute.style, "dotted");
                }
            }
        }
        for (LfBranch branch : network.getBranches()) {
            LfBus bus1 = branch.getBus1();
            LfBus bus2 = branch.getBus2();
            if (bus1 != null && bus2 != null) {
                GraphVizEdge edge = graph.edge(scope, bus1.getNum(), bus2.getNum(), branch.getNum());
                edge.label().append(getEdgeLabel(branch));
                edge.attr(GraphVizAttribute.color, getEdgeColor(branch, loadFlowModel))
                        .attr(GraphVizAttribute.style, branch.isDisabled() ? "dashed" : "")
                        .attr(GraphVizAttribute.dir, "none");
            }
        }
        return graph;
    }
}
