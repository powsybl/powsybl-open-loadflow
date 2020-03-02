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

import static com.powsybl.openloadflow.util.Markers.PERFORMANCE_MARKER;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class LfNetwork {

    private static final Logger LOGGER = LoggerFactory.getLogger(LfNetwork.class);

    private final SlackBusSelector slackBusSelector;

    private final Map<String, LfBus> busesById = new LinkedHashMap<>();

    private List<LfBus> busesByIndex;

    private LfBus slackBus;

    private final List<LfBranch> branches = new ArrayList<>();

    public LfNetwork(SlackBusSelector slackBusSelector) {
        this.slackBusSelector = Objects.requireNonNull(slackBusSelector);
    }

    private void updateCache() {
        if (busesByIndex == null) {
            busesByIndex = new ArrayList<>(busesById.values());
            for (int i = 0; i < busesByIndex.size(); i++) {
                busesByIndex.get(i).setNum(i);
            }
            slackBus = slackBusSelector.select(busesByIndex);
            slackBus.setSlack(true);
            LOGGER.info("Selected slack bus: {}", slackBus.getId());
        }
    }

    private void invalidateCache() {
        busesByIndex = null;
        slackBus = null;
    }

    public void addBranch(LfBranch branch) {
        Objects.requireNonNull(branch);
        branches.add(branch);

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

    public void addBus(LfBus bus) {
        Objects.requireNonNull(bus);
        busesById.put(bus.getId(), bus);
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

    public void updateState(boolean reactiveLimits) {
        Stopwatch stopwatch = Stopwatch.createStarted();

        for (LfBus bus : busesById.values()) {
            bus.updateState(reactiveLimits);
            for (LfGenerator generator : bus.getGenerators()) {
                generator.updateState();
            }
            for (LfShunt shunt : bus.getShunts()) {
                shunt.updateState();
            }
        }
        for (LfBranch branch : branches) {
            branch.updateState();
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
        bus.getRemoteControlTargetBus().ifPresent(lfBus -> {
            try {
                jsonGenerator.writeNumberField("remoteControlTargetBus", lfBus.getNum());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        if (!Double.isNaN(bus.getTargetV())) {
            jsonGenerator.writeNumberField("targetV", bus.getTargetV());
        }
        if (!Double.isNaN(bus.getV())) {
            jsonGenerator.writeNumberField("v", bus.getV());
        }
        if (!Double.isNaN(bus.getAngle())) {
            jsonGenerator.writeNumberField("angle", bus.getAngle());
        }
    }

    private void writeJson(LfBranch branch, JsonGenerator jsonGenerator) throws IOException {
        LfBus bus1 = branch.getBus1();
        LfBus bus2 = branch.getBus2();
        if (bus1 != null) {
            jsonGenerator.writeNumberField("num1", bus1.getNum());
        }
        if (bus2 != null) {
            jsonGenerator.writeNumberField("num2", bus2.getNum());
        }
        jsonGenerator.writeNumberField("x", branch.x());
        jsonGenerator.writeNumberField("y", branch.y());
        jsonGenerator.writeNumberField("ksi", branch.ksi());
        if (branch.g1() != 0) {
            jsonGenerator.writeNumberField("g1", branch.g1());
        }
        if (branch.g2() != 0) {
            jsonGenerator.writeNumberField("g2", branch.g2());
        }
        if (branch.b1() != 0) {
            jsonGenerator.writeNumberField("b1", branch.b1());
        }
        if (branch.b2() != 0) {
            jsonGenerator.writeNumberField("b2", branch.b2());
        }
        if (branch.r1() != 1) {
            jsonGenerator.writeNumberField("r1", branch.r1());
        }
        if (branch.r2() != 1) {
            jsonGenerator.writeNumberField("r2", branch.r2());
        }
        if (branch.a1() != 0) {
            jsonGenerator.writeNumberField("a1", branch.a1());
        }
        if (branch.a2() != 0) {
            jsonGenerator.writeNumberField("a2", branch.a2());
        }
    }

    private void writeJson(LfShunt shunt, JsonGenerator jsonGenerator) throws IOException {
        jsonGenerator.writeNumberField("b", shunt.getB());
    }

    private void writeJson(LfGenerator generator, JsonGenerator jsonGenerator) throws IOException {
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
            for (LfBus bus : busesById.values()) {
                jsonGenerator.writeStartObject();

                writeJson(bus, jsonGenerator);

                List<LfShunt> shunts = bus.getShunts();
                if (!shunts.isEmpty()) {
                    jsonGenerator.writeFieldName("shunts");
                    jsonGenerator.writeStartArray();
                    for (LfShunt shunt : shunts) {
                        jsonGenerator.writeStartObject();

                        writeJson(shunt, jsonGenerator);

                        jsonGenerator.writeEndObject();
                    }
                    jsonGenerator.writeEndArray();
                }

                List<LfGenerator> generators = bus.getGenerators();
                if (!generators.isEmpty()) {
                    jsonGenerator.writeFieldName("generators");
                    jsonGenerator.writeStartArray();
                    for (LfGenerator generator : generators) {
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
            for (LfBranch branch : branches) {
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

    private void logSize() {
        int remoteControlSources = 0;
        int remoteControlTargets = 0;
        for (LfBus bus : busesById.values()) {
            if (bus.getRemoteControlTargetBus().isPresent()) {
                remoteControlSources++;
            }
            if (!bus.getRemoteControlSourceBuses().isEmpty()) {
                remoteControlTargets++;
            }
        }
        LOGGER.info("Network has {} buses (voltage remote control: {} sources, {} targets) and {} branches",
                busesById.values().size(), remoteControlSources, remoteControlTargets, branches.size());
    }

    public void logBalance() {
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

        LOGGER.info("Active generation={} Mw, active load={} Mw, reactive generation={} MVar, reactive load={} MVar",
                activeGeneration, activeLoad, reactiveGeneration, reactiveLoad);
    }

    public void fix() {
        branches.removeIf(branch -> {
            boolean connectedToSameBus = branch.getBus1() == branch.getBus2();
            if (connectedToSameBus) {
                LOGGER.warn("Discard branch '{}' because connected to same bus at both ends", branch.getId());
            }
            return connectedToSameBus;
        });
    }

    public static List<LfNetwork> load(Object network, SlackBusSelector slackBusSelector) {
        return load(network, slackBusSelector, false);
    }

    public static List<LfNetwork> load(Object network, SlackBusSelector slackBusSelector, boolean voltageRemoteControl) {
        Objects.requireNonNull(network);
        Objects.requireNonNull(slackBusSelector);
        for (LfNetworkLoader importer : ServiceLoader.load(LfNetworkLoader.class)) {
            List<LfNetwork> lfNetworks = importer.load(network, slackBusSelector, voltageRemoteControl).orElse(null);
            if (lfNetworks != null) {
                LfNetwork lfNetwork = lfNetworks.get(0); // main component
                lfNetwork.fix();
                lfNetwork.logSize();
                lfNetwork.logBalance();
                return lfNetworks;
            }
        }
        throw new PowsyblException("Cannot importer network of type: " + network.getClass().getName());
    }
}
