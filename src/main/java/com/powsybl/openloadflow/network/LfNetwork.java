/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.google.common.base.Stopwatch;
import com.powsybl.commons.reporter.Reporter;
import com.powsybl.openloadflow.graph.GraphConnectivity;
import com.powsybl.openloadflow.graph.GraphConnectivityFactory;
import com.powsybl.openloadflow.util.PerUnit;
import com.powsybl.openloadflow.util.Reports;
import org.anarres.graphviz.builder.*;
import org.jgrapht.Graph;
import org.jgrapht.alg.interfaces.SpanningTreeAlgorithm;
import org.jgrapht.alg.spanning.KruskalMinimumSpanningTree;
import org.jgrapht.graph.Pseudograph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.powsybl.openloadflow.util.Markers.PERFORMANCE_MARKER;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class LfNetwork extends AbstractPropertyBag implements PropertyBag {

    private static final Logger LOGGER = LoggerFactory.getLogger(LfNetwork.class);

    private final int numCC;

    private final int numSC;

    private final SlackBusSelector slackBusSelector;

    private final int maxSlackBusCount;

    private final Map<String, LfBus> busesById = new LinkedHashMap<>();

    private final List<LfBus> busesByIndex = new ArrayList<>();

    private List<LfBus> slackBuses;

    private final List<LfBranch> branches = new ArrayList<>();

    private final Map<String, LfBranch> branchesById = new HashMap<>();

    private int shuntCount = 0;

    private final List<LfShunt> shuntsByIndex = new ArrayList<>();

    private final Map<String, LfShunt> shuntsById = new HashMap<>();

    private final Map<String, LfGenerator> generatorsById = new HashMap<>();

    private final List<LfHvdc> hvdcs = new ArrayList<>();

    private final Map<String, LfHvdc> hvdcsById = new HashMap<>();

    private final List<LfNetworkListener> listeners = new ArrayList<>();

    private boolean valid = true;

    private final GraphConnectivityFactory<LfBus, LfBranch> connectivityFactory;

    private GraphConnectivity<LfBus, LfBranch> connectivity;

    public static class LfZeroImpedanceNetwork {

        private final Graph<LfBus, LfBranch> subGraph;

        private final SpanningTreeAlgorithm.SpanningTree<LfBranch> spanningTree;

        public LfZeroImpedanceNetwork(LfNetwork network, boolean dc) {
            subGraph = createZeroImpedanceSubGraph(network, dc);
            spanningTree = createZeroImpedanceSpanningTree(subGraph, dc);
        }

        private static Graph<LfBus, LfBranch> createZeroImpedanceSubGraph(LfNetwork network, boolean dc) {
            return network.createSubGraph(branch -> branch.isZeroImpedance(dc)
                    && branch.getBus1() != null && branch.getBus2() != null);
        }

        private static SpanningTreeAlgorithm.SpanningTree<LfBranch> createZeroImpedanceSpanningTree(Graph<LfBus, LfBranch> zeroImpedanceSubGraph, boolean dc) {
            if (!zeroImpedanceSubGraph.vertexSet().isEmpty()) {
                SpanningTreeAlgorithm.SpanningTree<LfBranch> spanningTree = new KruskalMinimumSpanningTree<>(zeroImpedanceSubGraph).getSpanningTree();
                for (LfBranch branch : spanningTree.getEdges()) {
                    branch.setSpanningTreeEdge(dc, true);
                }
                return spanningTree;
            }
            return null;
        }

        public Graph<LfBus, LfBranch> getSubGraph() {
            return subGraph;
        }

        public SpanningTreeAlgorithm.SpanningTree<LfBranch> getSpanningTree() {
            return spanningTree;
        }
    }

    private LfZeroImpedanceNetwork dcLfZeroImpedanceNetwork;

    private LfZeroImpedanceNetwork acLfZeroImpedanceNetwork;

    private Reporter reporter;

    public LfNetwork(int numCC, int numSC, SlackBusSelector slackBusSelector, int maxSlackBusCount,
                     GraphConnectivityFactory<LfBus, LfBranch> connectivityFactory, Reporter reporter) {
        this.numCC = numCC;
        this.numSC = numSC;
        this.slackBusSelector = Objects.requireNonNull(slackBusSelector);
        this.maxSlackBusCount = maxSlackBusCount;
        this.connectivityFactory = Objects.requireNonNull(connectivityFactory);
        this.reporter = Objects.requireNonNull(reporter);
    }

    public LfNetwork(int numCC, int numSC, SlackBusSelector slackBusSelector, int maxSlackBusCount,
                     GraphConnectivityFactory<LfBus, LfBranch> connectivityFactory) {
        this(numCC, numSC, slackBusSelector, maxSlackBusCount, connectivityFactory, Reporter.NO_OP);
    }

    public int getNumCC() {
        return numCC;
    }

    public int getNumSC() {
        return numSC;
    }

    public Reporter getReporter() {
        return reporter;
    }

    public void setReporter(Reporter reporter) {
        this.reporter = Objects.requireNonNull(reporter);
    }

    private void invalidateSlack() {
        if (slackBuses != null) {
            for (var slackBus : slackBuses) {
                slackBus.setSlack(false);
            }
            slackBuses.get(0).setReference(false);
        }
        slackBuses = null;
    }

    public void updateSlackBuses() {
        if (slackBuses == null) {
            SelectedSlackBus selectedSlackBus = slackBusSelector.select(busesByIndex, maxSlackBusCount);
            slackBuses = selectedSlackBus.getBuses();
            LOGGER.info("Network {}, slack buses are {} (method='{}')", this, slackBuses, selectedSlackBus.getSelectionMethod());
            for (var slackBus : slackBuses) {
                slackBus.setSlack(true);
            }
            slackBuses.get(0).setReference(true);
        }
    }

    private void invalidateZeroImpedanceNetworks() {
        dcLfZeroImpedanceNetwork = null;
        acLfZeroImpedanceNetwork = null;
    }

    public void addBranch(LfBranch branch) {
        Objects.requireNonNull(branch);
        branch.setNum(branches.size());
        branches.add(branch);
        branchesById.put(branch.getId(), branch);
        invalidateSlack();
        connectivity = null;
        invalidateZeroImpedanceNetworks();

        // create bus -> branches link
        if (branch.getBus1() != null) {
            branch.getBus1().addBranch(branch);
        }
        if (branch.getBus2() != null) {
            branch.getBus2().addBranch(branch);
        }
    }

    public List<LfBranch> getBranches() {
        return branches;
    }

    public LfBranch getBranch(int num) {
        return branches.get(num);
    }

    public LfBranch getBranchById(String branchId) {
        Objects.requireNonNull(branchId);
        return branchesById.get(branchId);
    }

    public void addBus(LfBus bus) {
        Objects.requireNonNull(bus);
        bus.setNum(busesByIndex.size());
        busesByIndex.add(bus);
        busesById.put(bus.getId(), bus);
        invalidateSlack();
        connectivity = null;

        bus.getShunt().ifPresent(shunt -> {
            shunt.setNum(shuntCount++);
            shuntsByIndex.add(shunt);
            shunt.getOriginalIds().forEach(id -> shuntsById.put(id, shunt));
        });
        bus.getControllerShunt().ifPresent(shunt -> {
            shunt.setNum(shuntCount++);
            shuntsByIndex.add(shunt);
            shunt.getOriginalIds().forEach(id -> shuntsById.put(id, shunt));
        });
        bus.getGenerators().forEach(gen -> generatorsById.put(gen.getId(), gen));
    }

    public List<LfBus> getBuses() {
        return busesByIndex;
    }

    public LfBus getBusById(String id) {
        Objects.requireNonNull(id);
        return busesById.get(id);
    }

    public LfBus getBus(int num) {
        return busesByIndex.get(num);
    }

    public LfBus getSlackBus() {
        return getSlackBuses().get(0);
    }

    public List<LfBus> getSlackBuses() {
        updateSlackBuses();
        return slackBuses;
    }

    public List<LfShunt> getShunts() {
        return shuntsByIndex;
    }

    public LfShunt getShunt(int num) {
        return shuntsByIndex.get(num);
    }

    public LfShunt getShuntById(String id) {
        Objects.requireNonNull(id);
        return shuntsById.get(id);
    }

    public LfGenerator getGeneratorById(String id) {
        Objects.requireNonNull(id);
        return generatorsById.get(id);
    }

    public void addHvdc(LfHvdc hvdc) {
        Objects.requireNonNull(hvdc);
        hvdc.setNum(hvdcs.size());
        hvdcs.add(hvdc);
        hvdcsById.put(hvdc.getId(), hvdc);

        // create bus -> branches link
        if (hvdc.getBus1() != null) {
            hvdc.getBus1().addHvdc(hvdc);
        }
        if (hvdc.getBus2() != null) {
            hvdc.getBus2().addHvdc(hvdc);
        }
    }

    public List<LfHvdc> getHvdcs() {
        return hvdcs;
    }

    public LfHvdc getHvdcById(String id) {
        Objects.requireNonNull(id);
        return hvdcsById.get(id);
    }

    public void updateState(LfNetworkStateUpdateParameters parameters) {
        Stopwatch stopwatch = Stopwatch.createStarted();

        for (LfHvdc hvdc : hvdcs) {
            hvdc.updateState();
        }
        for (LfBus bus : busesById.values()) {
            bus.updateState(parameters);
            for (LfGenerator generator : bus.getGenerators()) {
                generator.updateState();
            }
            bus.getShunt().ifPresent(shunt -> shunt.updateState(parameters));
            bus.getControllerShunt().ifPresent(shunt -> shunt.updateState(parameters));
        }
        for (LfBranch branch : branches) {
            branch.updateState(parameters);
        }

        stopwatch.stop();
        LOGGER.debug(PERFORMANCE_MARKER, "Network {}, IIDM network updated in {} ms", this, stopwatch.elapsed(TimeUnit.MILLISECONDS));
    }

    public void writeJson(Path file) {
        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            writeJson(writer);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void writeJson(LfBus bus, JsonGenerator jsonGenerator) throws IOException {
        jsonGenerator.writeStringField("id", bus.getId());
        jsonGenerator.writeNumberField("num", bus.getNum());
        if (bus.getGenerationTargetQ() != 0) {
            jsonGenerator.writeNumberField("generationTargetQ", bus.getGenerationTargetQ());
        }
        if (bus.getLoadTargetP() != 0) {
            jsonGenerator.writeNumberField("loadTargetP", bus.getLoadTargetP());
        }
        if (bus.getLoadTargetQ() != 0) {
            jsonGenerator.writeNumberField("loadTargetQ", bus.getLoadTargetQ());
        }
        bus.getVoltageControl().ifPresent(vc -> {
            if (bus.isVoltageControlEnabled()) {
                try {
                    if (vc.getControlledBus() != bus) {
                        jsonGenerator.writeNumberField("remoteControlTargetBus", vc.getControlledBus().getNum());
                    }
                    jsonGenerator.writeNumberField("targetV", vc.getTargetValue());
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        });
        if (!Double.isNaN(bus.getV())) {
            jsonGenerator.writeNumberField("v", bus.getV());
        }
        if (!Double.isNaN(bus.getAngle())) {
            jsonGenerator.writeNumberField("angle", bus.getAngle());
        }
    }

    private void writeJson(LfBranch branch, JsonGenerator jsonGenerator) throws IOException {
        jsonGenerator.writeStringField("id", branch.getId());
        jsonGenerator.writeNumberField("num", branch.getNum());
        LfBus bus1 = branch.getBus1();
        LfBus bus2 = branch.getBus2();
        if (bus1 != null) {
            jsonGenerator.writeNumberField("num1", bus1.getNum());
        }
        if (bus2 != null) {
            jsonGenerator.writeNumberField("num2", bus2.getNum());
        }
        PiModel piModel = branch.getPiModel();
        jsonGenerator.writeNumberField("r", piModel.getR());
        jsonGenerator.writeNumberField("x", piModel.getX());
        if (piModel.getG1() != 0) {
            jsonGenerator.writeNumberField("g1", piModel.getG1());
        }
        if (piModel.getG2() != 0) {
            jsonGenerator.writeNumberField("g2", piModel.getG2());
        }
        if (piModel.getB1() != 0) {
            jsonGenerator.writeNumberField("b1", piModel.getB1());
        }
        if (piModel.getB2() != 0) {
            jsonGenerator.writeNumberField("b2", piModel.getB2());
        }
        if (piModel.getR1() != 1) {
            jsonGenerator.writeNumberField("r1", piModel.getR1());
        }
        if (piModel.getA1() != 0) {
            jsonGenerator.writeNumberField("a1", piModel.getA1());
        }
        branch.getDiscretePhaseControl().filter(dpc -> branch.isPhaseController()).ifPresent(dpc -> {
            try {
                jsonGenerator.writeFieldName("discretePhaseControl");
                jsonGenerator.writeStartObject();
                jsonGenerator.writeStringField("controller", dpc.getController().getId());
                jsonGenerator.writeStringField("controlled", dpc.getControlled().getId());
                jsonGenerator.writeStringField("mode", dpc.getMode().name());
                jsonGenerator.writeStringField("unit", dpc.getUnit().name());
                jsonGenerator.writeStringField("controlledSide", dpc.getControlledSide().name());
                jsonGenerator.writeNumberField("targetValue", dpc.getTargetValue());
                jsonGenerator.writeEndObject();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    private void writeJson(LfShunt shunt, JsonGenerator jsonGenerator) throws IOException {
        jsonGenerator.writeStringField("id", shunt.getId());
        jsonGenerator.writeNumberField("num", shunt.getNum());
        jsonGenerator.writeNumberField("b", shunt.getB());
    }

    private void writeJson(LfGenerator generator, JsonGenerator jsonGenerator) throws IOException {
        jsonGenerator.writeStringField("id", generator.getId());
        jsonGenerator.writeNumberField("targetP", generator.getTargetP());
        if (!Double.isNaN(generator.getTargetQ())) {
            jsonGenerator.writeNumberField("targetQ", generator.getTargetQ());
        }
        jsonGenerator.writeBooleanField("voltageControl", generator.getGeneratorControlType() == LfGenerator.GeneratorControlType.VOLTAGE);
        jsonGenerator.writeNumberField("minP", generator.getMinP());
        jsonGenerator.writeNumberField("maxP", generator.getMaxP());
    }

    public void writeJson(Writer writer) {
        Objects.requireNonNull(writer);
        updateSlackBuses();
        try (JsonGenerator jsonGenerator = new JsonFactory()
                .createGenerator(writer)
                .useDefaultPrettyPrinter()) {
            jsonGenerator.writeStartObject();

            jsonGenerator.writeFieldName("buses");
            jsonGenerator.writeStartArray();
            List<LfBus> sortedBuses = busesById.values().stream().sorted(Comparator.comparing(LfBus::getId)).collect(Collectors.toList());
            for (LfBus bus : sortedBuses) {
                jsonGenerator.writeStartObject();

                writeJson(bus, jsonGenerator);

                bus.getShunt().ifPresent(shunt -> {
                    try {
                        jsonGenerator.writeFieldName("shunt");
                        jsonGenerator.writeStartObject();

                        writeJson(shunt, jsonGenerator);

                        jsonGenerator.writeEndObject();
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });

                List<LfGenerator> sortedGenerators = bus.getGenerators().stream().sorted(Comparator.comparing(LfGenerator::getId)).collect(Collectors.toList());
                if (!sortedGenerators.isEmpty()) {
                    jsonGenerator.writeFieldName("generators");
                    jsonGenerator.writeStartArray();
                    for (LfGenerator generator : sortedGenerators) {
                        jsonGenerator.writeStartObject();

                        writeJson(generator, jsonGenerator);

                        jsonGenerator.writeEndObject();
                    }
                    jsonGenerator.writeEndArray();
                }

                jsonGenerator.writeEndObject();
            }
            jsonGenerator.writeEndArray();

            jsonGenerator.writeFieldName("branches");
            jsonGenerator.writeStartArray();
            List<LfBranch> sortedBranches = branches.stream().sorted(Comparator.comparing(LfBranch::getId)).collect(Collectors.toList());
            for (LfBranch branch : sortedBranches) {
                jsonGenerator.writeStartObject();

                writeJson(branch, jsonGenerator);

                jsonGenerator.writeEndObject();
            }
            jsonGenerator.writeEndArray();

            jsonGenerator.writeEndObject();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void reportSize(Reporter reporter) {
        Reports.reportNetworkSize(reporter, busesById.values().size(), branches.size());
        LOGGER.info("Network {} has {} buses and {} branches",
            this, busesById.values().size(), branches.size());
    }

    public void reportBalance(Reporter reporter) {
        double activeGeneration = 0;
        double reactiveGeneration = 0;
        double activeLoad = 0;
        double reactiveLoad = 0;
        for (LfBus b : busesById.values()) {
            activeGeneration += b.getGenerationTargetP() * PerUnit.SB;
            reactiveGeneration += b.getGenerationTargetQ() * PerUnit.SB;
            activeLoad += b.getLoadTargetP() * PerUnit.SB;
            reactiveLoad += b.getLoadTargetQ() * PerUnit.SB;
        }

        Reports.reportNetworkBalance(reporter, activeGeneration, activeLoad, reactiveGeneration, reactiveLoad);
        LOGGER.info("Network {} balance: active generation={} MW, active load={} MW, reactive generation={} MVar, reactive load={} MVar",
            this, activeGeneration, activeLoad, reactiveGeneration, reactiveLoad);
    }

    public void fix(boolean minImpedance, double lowImpedanceThreshold) {
        if (minImpedance) {
            for (LfBranch branch : branches) {
                branch.setMinZ(lowImpedanceThreshold);
            }
        }
    }

    private void validateBuses(boolean dc, Reporter reporter) {
        if (!dc) {
            boolean hasAtLeastOneBusVoltageControlled = false;
            for (LfBus bus : busesByIndex) {
                if (bus.isVoltageControlled()) {
                    hasAtLeastOneBusVoltageControlled = true;
                    break;
                }
            }
            if (!hasAtLeastOneBusVoltageControlled) {
                LOGGER.error("Network {} must have at least one bus voltage controlled", this);
                Reports.reportNetworkMustHaveAtLeastOneBusVoltageControlled(reporter);
                valid = false;
            }
        }
    }

    public void validate(boolean dc, Reporter reporter) {
        valid = true;
        validateBuses(dc, reporter);
    }

    public static <T> List<LfNetwork> load(T network, LfNetworkLoader<T> networkLoader, SlackBusSelector slackBusSelector) {
        return load(network, networkLoader, new LfNetworkParameters().setSlackBusSelector(slackBusSelector), Reporter.NO_OP);
    }

    public static <T> List<LfNetwork> load(T network, LfNetworkLoader<T> networkLoader, LfNetworkParameters parameters) {
        return load(network, networkLoader, parameters, Reporter.NO_OP);
    }

    public static <T> List<LfNetwork> load(T network, LfNetworkLoader<T> networkLoader, SlackBusSelector slackBusSelector, Reporter reporter) {
        return load(network, networkLoader, new LfNetworkParameters().setSlackBusSelector(slackBusSelector), reporter);
    }

    public static <T> List<LfNetwork> load(T network, LfNetworkLoader<T> networkLoader, LfNetworkParameters parameters, Reporter reporter) {
        Objects.requireNonNull(network);
        Objects.requireNonNull(networkLoader);
        Objects.requireNonNull(parameters);
        List<LfNetwork> lfNetworks = networkLoader.load(network, parameters, reporter);
        for (LfNetwork lfNetwork : lfNetworks) {
            Reporter reporterNetwork = Reports.createPostLoadingProcessingReporter(lfNetwork.getReporter());
            lfNetwork.fix(parameters.isMinImpedance(), parameters.getLowImpedanceThreshold());
            lfNetwork.validate(parameters.isDc(), reporterNetwork);
            if (lfNetwork.isValid()) {
                lfNetwork.reportSize(reporterNetwork);
                lfNetwork.reportBalance(reporterNetwork);
            } else {
                LOGGER.info("Network {} is invalid, no calculation will be done", lfNetwork);
            }
        }
        return lfNetworks;
    }

    public void updateZeroImpedanceCache(boolean dc) {
        if (dc) {
            if (dcLfZeroImpedanceNetwork == null) {
                dcLfZeroImpedanceNetwork = new LfZeroImpedanceNetwork(this, true);
            }
        } else {
            if (acLfZeroImpedanceNetwork == null) {
                acLfZeroImpedanceNetwork = new LfZeroImpedanceNetwork(this, false);
            }
        }
    }

    public LfZeroImpedanceNetwork getZeroImpedanceNetwork(boolean dc) {
        updateZeroImpedanceCache(dc);
        return dc ? dcLfZeroImpedanceNetwork : acLfZeroImpedanceNetwork;
    }

    private Graph<LfBus, LfBranch> createSubGraph(Predicate<LfBranch> branchFilter) {
        Objects.requireNonNull(branchFilter);

        List<LfBranch> filteredBranches = getBranches().stream()
                .filter(branchFilter)
                .collect(Collectors.toList());

        Graph<LfBus, LfBranch> subGraph = new Pseudograph<>(LfBranch.class);
        for (LfBranch branch : filteredBranches) {
            subGraph.addVertex(branch.getBus1());
            subGraph.addVertex(branch.getBus2());
            subGraph.addEdge(branch.getBus1(), branch.getBus2(), branch);
        }

        return subGraph;
    }

    public GraphConnectivity<LfBus, LfBranch> getConnectivity() {
        if (connectivity == null) {
            connectivity = Objects.requireNonNull(connectivityFactory.create());
            getBuses().forEach(connectivity::addVertex);
            getBranches().stream()
                    .filter(b -> b.getBus1() != null && b.getBus2() != null)
                    .forEach(b -> connectivity.addEdge(b.getBus1(), b.getBus2(), b));
            connectivity.setMainComponentVertex(getSlackBus());
        }
        return connectivity;
    }

    public void addListener(LfNetworkListener listener) {
        listeners.add(listener);
    }

    public void removeListener(LfNetworkListener listener) {
        listeners.remove(listener);
    }

    public List<LfNetworkListener> getListeners() {
        return listeners;
    }

    public boolean isValid() {
        return valid;
    }

    /**
     * Disable transformer voltage control when there is no generator controlling voltage on the connected component
     * that belong to the not controlled side of the transformer.
     */
    public void fixTransformerVoltageControls() {
        List<LfBranch> controllerBranches = new ArrayList<>(1);
        getConnectivity().startTemporaryChanges();
        for (LfBranch branch : branches) {
            if (!branch.isDisabled() && branch.isVoltageController() && branch.isVoltageControlEnabled()) {
                controllerBranches.add(branch);
            }
            if (branch.isDisabled() && branch.getBus1() != null && branch.getBus2() != null) {
                // apply contingency (in case we are inside a security analysis)
                getConnectivity().removeEdge(branch);
            }
        }
        for (LfBranch branch : controllerBranches) {
            getConnectivity().removeEdge(branch);
        }
        int disabledTransformerCount = 0;
        Map<Integer, Boolean> componentNoPVBusesMap = new HashMap<>();
        for (LfBranch branch : controllerBranches) {
            var voltageControl = branch.getVoltageControl().orElseThrow();
            LfBus notControlledSide;
            if (voltageControl.getControlled() == branch.getBus1()) {
                notControlledSide = branch.getBus2();
            } else if (voltageControl.getControlled() == branch.getBus2()) {
                notControlledSide = branch.getBus1();
            } else {
                continue;
            }
            boolean noPvBusesInComponent = componentNoPVBusesMap.computeIfAbsent(getConnectivity().getComponentNumber(notControlledSide),
                k -> getConnectivity().getConnectedComponent(notControlledSide).stream().noneMatch(LfBus::isVoltageControlled));
            if (noPvBusesInComponent) {
                branch.setVoltageControlEnabled(false);
                LOGGER.trace("Transformer {} voltage control has been disabled because no PV buses on not controlled side connected component",
                        branch.getId());
                disabledTransformerCount++;
            }
        }
        getConnectivity().undoTemporaryChanges();
        if (disabledTransformerCount > 0) {
            LOGGER.warn("{} transformer voltage controls have been disabled because no PV buses on not controlled side connected component",
                    disabledTransformerCount);
        }
    }

    public void writeGraphViz(Writer writer) {
        try {
            createGraphViz().writeTo(writer);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public GraphVizGraph createGraphViz() {
        GraphVizGraph graph = new GraphVizGraph().label(getId());
        GraphVizScope scope = new GraphVizScope.Impl();
        for (LfBus bus : busesByIndex) {
            graph.node(scope, bus.getNum())
                    .label(bus.getId())
                    .attr(GraphVizAttribute.shape, "ellipse")
                    .attr(GraphVizAttribute.style, "filled")
                    .attr(GraphVizAttribute.fontsize, "10");

        }
        for (LfBranch branch : branches) {
            LfBus bus1 = branch.getBus1();
            LfBus bus2 = branch.getBus2();
            if (bus1 != null && bus2 != null) {
                graph.edge(scope, bus1.getNum(), bus2.getNum())
                        .label().append(branch.getId());
            }
        }
        return graph;
    }

    public String getId() {
        return "{CC" + numCC + " SC" + numSC + '}';
    }

    @Override
    public String toString() {
        return getId();
    }
}
