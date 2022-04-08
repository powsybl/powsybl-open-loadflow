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
import com.powsybl.commons.reporter.TypedValue;
import com.powsybl.openloadflow.graph.GraphDecrementalConnectivity;
import com.powsybl.openloadflow.graph.GraphDecrementalConnectivityFactory;
import com.powsybl.openloadflow.util.PerUnit;
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
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.powsybl.openloadflow.util.Markers.PERFORMANCE_MARKER;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class LfNetwork {

    private static final Logger LOGGER = LoggerFactory.getLogger(LfNetwork.class);

    private static final double TARGET_VOLTAGE_EPSILON = Math.pow(10, -6);

    private final int numCC;

    private final int numSC;

    private final SlackBusSelector slackBusSelector;

    private final Map<String, LfBus> busesById = new LinkedHashMap<>();

    private final List<LfBus> busesByIndex = new ArrayList<>();

    private LfBus slackBus;

    private final List<LfBranch> branches = new ArrayList<>();

    private final Map<String, LfBranch> branchesById = new HashMap<>();

    private int shuntCount = 0;

    private final List<LfShunt> shuntsByIndex = new ArrayList<>();

    private final Map<String, LfShunt> shuntsById = new HashMap<>();

    private final Map<String, LfGenerator> generatorsById = new HashMap<>();

    private final List<LfHvdc> hvdcs = new ArrayList<>();

    private final List<LfNetworkListener> listeners = new ArrayList<>();

    private boolean valid = true;

    private final Map<String, Object> userObjects = new HashMap<>();

    private final GraphDecrementalConnectivityFactory<LfBus> connectivityFactory;

    private GraphDecrementalConnectivity<LfBus> connectivity;

    public LfNetwork(int numCC, int numSC, SlackBusSelector slackBusSelector,
                     GraphDecrementalConnectivityFactory<LfBus> connectivityFactory) {
        this.numCC = numCC;
        this.numSC = numSC;
        this.slackBusSelector = Objects.requireNonNull(slackBusSelector);
        this.connectivityFactory = Objects.requireNonNull(connectivityFactory);
    }

    public int getNumCC() {
        return numCC;
    }

    public int getNumSC() {
        return numSC;
    }

    private void invalidateSlack() {
        slackBus = null;
    }

    public void updateSlack() {
        if (slackBus == null) {
            SelectedSlackBus selectedSlackBus = slackBusSelector.select(busesByIndex);
            slackBus = selectedSlackBus.getBus();
            LOGGER.info("Network {}, slack bus is '{}' (method='{}')", this, slackBus.getId(), selectedSlackBus.getSelectionMethod());
            slackBus.setSlack(true);
        }
    }

    public void addBranch(LfBranch branch) {
        Objects.requireNonNull(branch);
        branch.setNum(branches.size());
        branches.add(branch);
        branchesById.put(branch.getId(), branch);
        invalidateSlack();
        connectivity = null;

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
        updateSlack();
        return slackBus;
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

    public void updateState(boolean reactiveLimits, boolean writeSlackBus, boolean phaseShifterRegulationOn,
                            boolean transformerVoltageControlOn, boolean distributedOnConformLoad, boolean loadPowerFactorConstant,
                            boolean dc) {
        Stopwatch stopwatch = Stopwatch.createStarted();

        for (LfHvdc hvdc : hvdcs) {
            hvdc.updateState();
        }
        for (LfBus bus : busesById.values()) {
            bus.updateState(reactiveLimits, writeSlackBus, distributedOnConformLoad, loadPowerFactorConstant);
            for (LfGenerator generator : bus.getGenerators()) {
                generator.updateState();
            }
            bus.getShunt().ifPresent(LfShunt::updateState);
            bus.getControllerShunt().ifPresent(LfShunt::updateState);
        }
        for (LfBranch branch : branches) {
            branch.updateState(phaseShifterRegulationOn, transformerVoltageControlOn, dc);
        }

        stopwatch.stop();
        LOGGER.debug(PERFORMANCE_MARKER, "IIDM network updated in {} ms", stopwatch.elapsed(TimeUnit.MILLISECONDS));
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
        jsonGenerator.writeBooleanField("voltageControl", generator.hasVoltageControl());
        jsonGenerator.writeNumberField("minP", generator.getMinP());
        jsonGenerator.writeNumberField("maxP", generator.getMaxP());
    }

    public void writeJson(Writer writer) {
        Objects.requireNonNull(writer);
        updateSlack();
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
        reporter.report(Report.builder()
            .withKey("networkSize")
            .withDefaultMessage("Network CC${numNetworkCc} SC${numNetworkSc} has ${busCount} buses and ${branchCount} branches")
            .withValue("numNetworkCc", numCC)
            .withValue("numNetworkSc", numSC)
            .withValue("busCount", busesById.values().size())
            .withValue("branchCount", branches.size())
            .build());
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

        reporter.report(Report.builder()
            .withKey("networkBalance")
            .withDefaultMessage("Network CC${numNetworkCc} SC${numNetworkSc} balance: active generation=${activeGeneration} MW, active load=${activeLoad} MW, reactive generation=${reactiveGeneration} MVar, reactive load=${reactiveLoad} MVar")
            .withValue("numNetworkCc", numCC)
            .withValue("numNetworkSc", numSC)
            .withValue("activeGeneration", activeGeneration)
            .withValue("activeLoad", activeLoad)
            .withValue("reactiveGeneration", reactiveGeneration)
            .withValue("reactiveLoad", reactiveLoad)
            .build());
        LOGGER.info("Network {} balance: active generation={} MW, active load={} MW, reactive generation={} MVar, reactive load={} MVar",
            this, activeGeneration, activeLoad, reactiveGeneration, reactiveLoad);
    }

    public void fix(boolean minImpedance, boolean dc) {
        if (minImpedance) {
            for (LfBranch branch : branches) {
                branch.setMinZ(dc);
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
                reporter.report(Report.builder()
                        .withKey("networkMustHaveAtLEastOneBusVoltageControlled")
                        .withDefaultMessage("Network CC${numNetworkCc} SC${numNetworkSc} must have at least one bus voltage controlled")
                        .withValue("numNetworkCc", numCC)
                        .withValue("numNetworkSc", numSC)
                        .build());
                valid = false;
            }
        }
    }

    private void validateBranches(boolean minImpedance, boolean dc) {
        if (minImpedance) {
            return;
        }
        for (LfBranch branch : branches) {
            if (branch.isZeroImpedanceBranch(dc)) { // will be transformed to non impedant branch
                PiModel piModel = branch.getPiModel();
                LfBus bus1 = branch.getBus1();
                LfBus bus2 = branch.getBus2();
                // ensure target voltages are consistent
                if (bus1 != null && bus2 != null) {
                    Optional<VoltageControl> vc1 = bus1.getVoltageControl();
                    Optional<VoltageControl> vc2 = bus2.getVoltageControl();
                    if (vc1.isPresent() && vc2.isPresent() && bus1.isVoltageControlEnabled() && bus2.isVoltageControlEnabled()
                        && FastMath.abs((vc1.get().getTargetValue() / vc2.get().getTargetValue()) - piModel.getR1() / PiModel.R2) > TARGET_VOLTAGE_EPSILON) {
                        throw new PowsyblException("Non impedant branch '" + branch.getId() + "' is connected to PV buses '"
                                + bus1.getId() + "' and '" + bus2.getId() + "' with inconsistent target voltages: "
                                + vc1.get().getTargetValue() + " and " + vc2.get().getTargetValue());
                    }
                }
            }
        }
    }

    public void validate(boolean minImpedance, boolean dc, Reporter reporter) {
        valid = true;
        validateBuses(dc, reporter);
        validateBranches(minImpedance, dc);
    }

    public static <T> List<LfNetwork> load(T network, LfNetworkLoader<T> networkLoader, SlackBusSelector slackBusSelector) {
        return load(network, networkLoader, new LfNetworkParameters(slackBusSelector), Reporter.NO_OP);
    }

    public static <T> List<LfNetwork> load(T network, LfNetworkLoader<T> networkLoader, LfNetworkParameters parameters) {
        return load(network, networkLoader, parameters, Reporter.NO_OP);
    }

    public static <T> List<LfNetwork> load(T network, LfNetworkLoader<T> networkLoader, SlackBusSelector slackBusSelector, Reporter reporter) {
        return load(network, networkLoader, new LfNetworkParameters(slackBusSelector), reporter);
    }

    public static <T> List<LfNetwork> load(T network, LfNetworkLoader<T> networkLoader, LfNetworkParameters parameters, Reporter reporter) {
        Objects.requireNonNull(network);
        Objects.requireNonNull(networkLoader);
        Objects.requireNonNull(parameters);
        List<LfNetwork> lfNetworks = networkLoader.load(network, parameters, reporter);
        for (LfNetwork lfNetwork : lfNetworks) {
            Reporter reporterNetwork = reporter.createSubReporter("postLoading", "Post loading process on network CC${numNetworkCc} SC${numNetworkSc}",
                Map.of("numNetworkCc", new TypedValue(lfNetwork.getNumCC(), TypedValue.UNTYPED),
                    "numNetworkSc", new TypedValue(lfNetwork.getNumSC(), TypedValue.UNTYPED)));
            lfNetwork.fix(parameters.isMinImpedance(), parameters.isDc());
            lfNetwork.validate(parameters.isMinImpedance(), parameters.isDc(), reporterNetwork);
            if (lfNetwork.isValid()) {
                lfNetwork.reportSize(reporterNetwork);
                lfNetwork.reportBalance(reporterNetwork);
            }
        }
        return lfNetworks;
    }

    /**
     * Create the subgraph of zero-impedance LfBranches and their corresponding LfBuses
     * The graph is intentionally not cached as a parameter so far, to avoid the complexity of invalidating it if changes occur
     * @return the zero-impedance subgraph
     */
    public Graph<LfBus, LfBranch> createZeroImpedanceSubGraph(boolean dc) {
        return createSubGraph(branch -> branch.isZeroImpedanceBranch(dc)
                && branch.getBus1() != null && branch.getBus2() != null);
    }

    public Graph<LfBus, LfBranch> createSubGraph(Predicate<LfBranch> branchFilter) {
        Objects.requireNonNull(branchFilter);

        List<LfBranch> zeroImpedanceBranches = getBranches().stream()
                .filter(branchFilter)
                .collect(Collectors.toList());

        Graph<LfBus, LfBranch> subGraph = new Pseudograph<>(LfBranch.class);
        for (LfBranch branch : zeroImpedanceBranches) {
            subGraph.addVertex(branch.getBus1());
            subGraph.addVertex(branch.getBus2());
            subGraph.addEdge(branch.getBus1(), branch.getBus2(), branch);
        }

        return subGraph;
    }

    public GraphDecrementalConnectivity<LfBus> getConnectivity() {
        if (connectivity == null) {
            connectivity = Objects.requireNonNull(connectivityFactory.create());
            getBuses().forEach(connectivity::addVertex);
            getBranches().forEach(b -> connectivity.addEdge(b.getBus1(), b.getBus2()));
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

    public Object getUserObject(String name) {
        Objects.requireNonNull(name);
        return userObjects.get(name);
    }

    public void setUserObject(String name, Object userObject) {
        Objects.requireNonNull(name);
        userObjects.put(name, userObject);
    }

    @Override
    public String toString() {
        return "{CC" + numCC +
            " SC" + numSC + '}';
    }
}
