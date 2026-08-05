/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network;

import com.google.common.escape.Escaper;
import com.google.common.escape.Escapers;
import com.powsybl.openloadflow.util.PerUnit;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.nio.Attribute;
import org.jgrapht.nio.DefaultAttribute;
import org.jgrapht.nio.dot.DOTSubgraph;

import java.io.Writer;
import java.security.SecureRandom;
import java.util.*;

import static com.powsybl.iidm.network.dot.IidmDOTUtils.*;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class GraphVizGraphBuilder {

    private static final Escaper GV_ESCAPER = Escapers.builder()
        .addEscape('\\', "\\\\")
        .addEscape('\"', "\\\"")
        .addEscape('{', "\\{")
        .addEscape('}', "\\}")
        .addEscape('<', "&lt;")
        .addEscape('>', "&gt;")
        .addEscape('&', "&amp;")
        .addEscape('\n', "\\l")
        .addEscape('\r', "")
        .build();

    private final LfNetwork network;
    private final Random random = new SecureRandom();

    public GraphVizGraphBuilder(LfNetwork network) {
        this.network = Objects.requireNonNull(network);
    }

    private static String getNetworkLabel(LfNetwork network) {
        return "\"" + GV_ESCAPER.escape(network.getId()) + "\"";
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

    public void write(Writer writer, LoadFlowModel loadFlowModel) {
        Objects.requireNonNull(writer);
        Map<String, Attribute> graphAttributes = new HashMap<>();
        graphAttributes.put(LABEL, DefaultAttribute.createAttribute(getNetworkLabel(network)));
        exportGraph(writer, random, this::exportVertices,
            (edgesAttributes, jGraph) -> exportEdges(edgesAttributes, jGraph, loadFlowModel),
            graphAttributes);
    }

    private void exportVertices(Map<String, Map<String, Attribute>> verticesAttributes,
                                Map<DefaultEdge, Map<String, Attribute>> edgeAttributes,
                                Random random,
                                Graph<String, DefaultEdge> jGraph,
                                Map<String, DOTSubgraph<String, DefaultEdge>> subgraphs) {
        for (LfBus bus : network.getBuses()) {
            // Id
            String busId = String.valueOf(bus.getNum());

            // Vertex
            jGraph.addVertex(busId);

            // Attributes
            Map<String, Attribute> vertexAttributes = new LinkedHashMap<>();
            vertexAttributes.put(LABEL, DefaultAttribute.createAttribute(GV_ESCAPER.escape(getNodeLabel(bus))));
            vertexAttributes.put(SHAPE, DefaultAttribute.createAttribute("box"));
            vertexAttributes.put(STYLE, DefaultAttribute.createAttribute("filled,rounded"));
            vertexAttributes.put(FONT_SIZE, DefaultAttribute.createAttribute("10"));
            vertexAttributes.put(FILL_COLOR, DefaultAttribute.createAttribute("grey"));
            verticesAttributes.put(busId, vertexAttributes);
        }
    }

    private void exportEdges(Map<DefaultEdge, Map<String, Attribute>> edgesAttributes,
                             Graph<String, DefaultEdge> jGraph,
                             LoadFlowModel loadFlowModel) {
        // draw voltage controller -> controlled links
        for (LfBus bus : network.getBuses()) {
            if (bus.isGeneratorVoltageControlled()) {
                GeneratorVoltageControl vc = bus.getGeneratorVoltageControl().orElseThrow();
                for (LfBus controllerBus : vc.getControllerElements()) {
                    String bus1Id = String.valueOf(controllerBus.getNum());
                    String bus2Id = String.valueOf(bus.getNum());
                    DefaultEdge edge = jGraph.addEdge(bus1Id, bus2Id);
                    Map<String, Attribute> edgeAttributes = new LinkedHashMap<>();
                    edgeAttributes.put("color", DefaultAttribute.createAttribute("lightgray"));
                    edgeAttributes.put(STYLE, DefaultAttribute.createAttribute("dotted"));
                    edgesAttributes.put(edge, edgeAttributes);
                }
            }
        }

        for (LfBranch branch : network.getBranches()) {
            LfBus bus1 = branch.getBus1();
            LfBus bus2 = branch.getBus2();
            if (bus1 != null && bus2 != null) {
                String bus1Id = String.valueOf(bus1.getNum());
                String bus2Id = String.valueOf(bus2.getNum());
                DefaultEdge edge = jGraph.addEdge(bus1Id, bus2Id);
                Map<String, Attribute> edgeAttributes = new LinkedHashMap<>();
                edgeAttributes.put(LABEL, DefaultAttribute.createAttribute(GV_ESCAPER.escape(getEdgeLabel(branch))));
                edgeAttributes.put("color", DefaultAttribute.createAttribute(getEdgeColor(branch, loadFlowModel)));
                edgeAttributes.put(STYLE, DefaultAttribute.createAttribute(branch.isDisabled() ? "dashed" : ""));
                edgeAttributes.put("dir", DefaultAttribute.createAttribute("none"));
                edgesAttributes.put(edge, edgeAttributes);
            }
        }
    }
}
