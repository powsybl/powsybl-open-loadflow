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
import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.reporter.Report;
import com.powsybl.commons.reporter.Reporter;
import com.powsybl.openloadflow.graph.GraphDecrementalConnectivity;
import net.jafama.FastMath;
import org.jgrapht.Graph;
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
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.powsybl.openloadflow.util.Markers.PERFORMANCE_MARKER;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class LfNetwork {

    private static final Logger LOGGER = LoggerFactory.getLogger(LfNetwork.class);

    public static final double LOW_IMPEDANCE_THRESHOLD = Math.pow(10, -8); // in per unit
    private static final double TARGET_VOLTAGE_EPSILON = Math.pow(10, -6);

    private final int num;

    private final SlackBusSelector slackBusSelector;

    private final Map<String, LfBus> busesById = new LinkedHashMap<>();

    private List<LfBus> busesByIndex;

    private LfBus slackBus;

    private final List<LfBranch> branches = new ArrayList<>();

    private final Map<String, LfBranch> branchesById = new HashMap<>();

    private int shuntCount = 0;

    private final List<LfNetworkListener> listeners = new ArrayList<>();

    private boolean valid = true;

    public LfNetwork(int num, SlackBusSelector slackBusSelector) {
        this.num = num;
        this.slackBusSelector = Objects.requireNonNull(slackBusSelector);
    }

    public int getNum() {
        return num;
    }

    private void updateCache() {
        if (busesByIndex == null) {
            busesByIndex = new ArrayList<>(busesById.values());
            for (int i = 0; i < busesByIndex.size(); i++) {
                busesByIndex.get(i).setNum(i);
            }
            slackBus = slackBusSelector.select(busesByIndex);
            slackBus.setSlack(true);
        }
    }

    private void invalidateCache() {
        busesByIndex = null;
        slackBus = null;
    }

    public void addBranch(LfBranch branch) {
        Objects.requireNonNull(branch);
        branch.setNum(branches.size());
        branches.add(branch);
        branchesById.put(branch.getId(), branch);

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
        busesById.put(bus.getId(), bus);
        for (LfShunt shunt : bus.getShunts()) {
            shunt.setNum(shuntCount++);
        }
        invalidateCache();
    }

    public List<LfBus> getBuses() {
        updateCache();
        return busesByIndex;
    }

    public LfBus getBusById(String id) {
        Objects.requireNonNull(id);
        return busesById.get(id);
    }

    public LfBus getBus(int num) {
        updateCache();
        return busesByIndex.get(num);
    }

    public LfBus getSlackBus() {
        updateCache();
        return slackBus;
    }

    public void updateState(boolean reactiveLimits, boolean writeSlackBus, boolean phaseShifterRegulationOn,
                            boolean transformerVoltageControlOn) {
        Stopwatch stopwatch = Stopwatch.createStarted();

        for (LfBus bus : busesById.values()) {
            bus.updateState(reactiveLimits, writeSlackBus);
            for (LfGenerator generator : bus.getGenerators()) {
                generator.updateState();
            }
            for (LfShunt shunt : bus.getShunts()) {
                shunt.updateState();
            }
        }
        for (LfBranch branch : branches) {
            branch.updateState(phaseShifterRegulationOn, transformerVoltageControlOn);
        }

        stopwatch.stop();
        LOGGER.debug(PERFORMANCE_MARKER, "IIDM network updated in {} ms", stopwatch.elapsed(TimeUnit.MILLISECONDS));
    }

    public void writeJson(Path file) {
        updateCache();
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
            if (bus.isVoltageControllerEnabled()) {
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
        Double v = bus.getV().eval();
        if (!Double.isNaN(v)) {
            jsonGenerator.writeNumberField("v", v);
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
        if (branch.isPhaseController()) {
            DiscretePhaseControl phaseControl = branch.getDiscretePhaseControl();
            try {
                jsonGenerator.writeFieldName("discretePhaseControl");
                jsonGenerator.writeStartObject();
                jsonGenerator.writeStringField("controller", phaseControl.getController().getId());
                jsonGenerator.writeStringField("controlled", phaseControl.getController().getId());
                jsonGenerator.writeStringField("mode", phaseControl.getMode().name());
                jsonGenerator.writeStringField("unit", phaseControl.getUnit().name());
                jsonGenerator.writeStringField("controlledSide", phaseControl.getControlledSide().name());
                jsonGenerator.writeNumberField("targetValue", phaseControl.getTargetValue());
                jsonGenerator.writeEndObject();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
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
        jsonGenerator.writeBooleanField("voltageControl", generator.hasVoltageControl());
        jsonGenerator.writeNumberField("minP", generator.getMinP());
        jsonGenerator.writeNumberField("maxP", generator.getMaxP());
    }

    public void writeJson(Writer writer) {
        Objects.requireNonNull(writer);
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

                List<LfShunt> sortedShunts = bus.getShunts().stream().sorted(Comparator.comparing(LfShunt::getId)).collect(Collectors.toList());
                if (!sortedShunts.isEmpty()) {
                    jsonGenerator.writeFieldName("shunts");
                    jsonGenerator.writeStartArray();
                    for (LfShunt shunt : sortedShunts) {
                        jsonGenerator.writeStartObject();

                        writeJson(shunt, jsonGenerator);

                        jsonGenerator.writeEndObject();
                    }
                    jsonGenerator.writeEndArray();
                }

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
        int remoteControlledBusCount = 0;
        int remoteControllerBusCount = 0;
        for (LfBus b : busesById.values()) {
            // To avoid counting the local voltage controls we check that the voltage controller is not also voltage controlled
            if (b.isVoltageControllerEnabled() && !b.isVoltageControlled()) {
                remoteControllerBusCount++;
            }
            // Similarly, to avoid counting the local voltage controls we check that the voltage controlled is not also voltage controller
            if (b.isVoltageControlled() && !b.isVoltageControllerEnabled()) {
                remoteControlledBusCount++;
            }
        }
        reporter.report(Report.builder()
            .withKey("networkSize")
            .withDefaultMessage("Network ${numNetwork} has ${nbBuses} buses (voltage remote control: ${nbRemoteControllerBuses} controllers, ${nbRemoteControlledBuses} controlled) and ${nbBranches} branches")
            .withValue("numNetwork", num)
            .withValue("nbBuses", busesById.values().size())
            .withValue("nbRemoteControllerBuses", remoteControllerBusCount)
            .withValue("nbRemoteControlledBuses", remoteControlledBusCount)
            .withValue("nbBranches", branches.size())
            .build());
        LOGGER.info("Network {} has {} buses (voltage remote control: {} controllers, {} controlled) and {} branches",
            num, busesById.values().size(), remoteControllerBusCount, remoteControlledBusCount, branches.size());
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

        reporter.report(Report.builder()
            .withKey("networkBalance")
            .withDefaultMessage("Network ${numNetwork} balance: active generation=${activeGeneration} Mw, active load=${activeLoad} Mw, reactive generation=${reactiveGeneration} MVar, reactive load=${reactiveLoad} MVar")
            .withValue("numNetwork", num)
            .withValue("activeGeneration", activeGeneration)
            .withValue("activeLoad", activeLoad)
            .withValue("reactiveGeneration", reactiveGeneration)
            .withValue("reactiveLoad", reactiveLoad)
            .build());
        LOGGER.info("Network {} balance: active generation={} Mw, active load={} Mw, reactive generation={} MVar, reactive load={} MVar",
            num, activeGeneration, activeLoad, reactiveGeneration, reactiveLoad);
    }

    private static void fix(LfNetwork network, boolean minImpedance) {
        if (minImpedance) {
            for (LfBranch branch : network.getBranches()) {
                PiModel piModel = branch.getPiModel();
                if (Math.abs(piModel.getZ()) < LOW_IMPEDANCE_THRESHOLD) {
                    piModel.setR(0);
                    piModel.setX(LOW_IMPEDANCE_THRESHOLD);
                }
            }
        }
    }

    private static void validate(LfNetwork network, boolean minImpedance) {
        if (minImpedance) {
            return;
        }
        for (LfBranch branch : network.getBranches()) {
            PiModel piModel = branch.getPiModel();
            if (Math.abs(piModel.getZ()) < LOW_IMPEDANCE_THRESHOLD) { // will be transformed to non impedant branch
                LfBus bus1 = branch.getBus1();
                LfBus bus2 = branch.getBus2();
                // ensure target voltages are consistent
                if (bus1 != null && bus2 != null) {
                    Optional<VoltageControl> vc1 = bus1.getVoltageControl();
                    Optional<VoltageControl> vc2 = bus2.getVoltageControl();
                    if (vc1.isPresent() && vc2.isPresent() && bus1.isVoltageControllerEnabled() && bus2.isVoltageControllerEnabled()
                        && FastMath.abs((vc1.get().getTargetValue() / vc2.get().getTargetValue()) - piModel.getR1() / PiModel.R2) > TARGET_VOLTAGE_EPSILON) {
                        throw new PowsyblException("Non impedant branch '" + branch.getId() + "' is connected to PV buses '"
                            + bus1.getId() + "' and '" + bus2.getId() + "' with inconsistent target voltages: "
                            + vc1.get().getTargetValue() + " and " + vc2.get().getTargetValue());
                    }
                }
            }
        }
    }

    public static List<LfNetwork> load(Object network, SlackBusSelector slackBusSelector) {
        return load(network, new LfNetworkParameters(slackBusSelector), Reporter.NO_OP);
    }

    public static List<LfNetwork> load(Object network, LfNetworkParameters parameters) {
        return load(network, parameters, Reporter.NO_OP);
    }

    public static List<LfNetwork> load(Object network, SlackBusSelector slackBusSelector, Reporter reporter) {
        return load(network, new LfNetworkParameters(slackBusSelector), reporter);
    }

    public static List<LfNetwork> load(Object network, LfNetworkParameters parameters, Reporter reporter) {
        Objects.requireNonNull(network);
        Objects.requireNonNull(parameters);
        for (LfNetworkLoader importer : ServiceLoader.load(LfNetworkLoader.class)) {
            List<LfNetwork> lfNetworks = importer.load(network, parameters, reporter).orElse(null);
            if (lfNetworks != null) {
                for (LfNetwork lfNetwork : lfNetworks) {
                    Reporter reporterNetwork = reporter.createSubReporter("postLoading", "Post loading process on network ${numNetwork}", "numNetwork", lfNetwork.getNum());
                    fix(lfNetwork, parameters.isMinImpedance());
                    validate(lfNetwork, parameters.isMinImpedance());
                    lfNetwork.reportSize(reporterNetwork);
                    lfNetwork.reportBalance(reporterNetwork);
                }
                return lfNetworks;
            }
        }
        throw new PowsyblException("Cannot import network of type: " + network.getClass().getName());
    }

    /**
     * Create the subgraph of zero-impedance LfBranches and their corresponding LfBuses
     * The graph is intentionally not cached as a parameter so far, to avoid the complexity of invalidating it if changes occur
     * @return the zero-impedance subgraph
     */
    public Graph<LfBus, LfBranch> createZeroImpedanceSubGraph() {
        List<LfBranch> zeroImpedanceBranches = getBranches().stream()
            .filter(LfNetwork::isZeroImpedanceBranch)
            .filter(b -> b.getBus1() != null && b.getBus2() != null)
            .collect(Collectors.toList());

        Graph<LfBus, LfBranch> subGraph = new Pseudograph<>(LfBranch.class);
        for (LfBranch branch : zeroImpedanceBranches) {
            subGraph.addVertex(branch.getBus1());
            subGraph.addVertex(branch.getBus2());
            subGraph.addEdge(branch.getBus1(), branch.getBus2(), branch);
        }

        return  subGraph;
    }

    public static boolean isZeroImpedanceBranch(LfBranch branch) {
        PiModel piModel = branch.getPiModel();
        return piModel.getZ() < LOW_IMPEDANCE_THRESHOLD;
    }

    public GraphDecrementalConnectivity<LfBus> createDecrementalConnectivity(Supplier<GraphDecrementalConnectivity<LfBus>> connectivitySupplier) {
        GraphDecrementalConnectivity<LfBus> connectivity = connectivitySupplier.get();
        getBuses().forEach(connectivity::addVertex);
        getBranches().forEach(b -> connectivity.addEdge(b.getBus1(), b.getBus2()));
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

    public void setValid(boolean valid) {
        this.valid = valid;
    }
}
