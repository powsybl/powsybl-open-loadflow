/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.loadflow.simple.network;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.google.common.base.Stopwatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static com.powsybl.loadflow.simple.util.Markers.PERFORMANCE_MARKER;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class LfNetwork {

    private static final Logger LOGGER = LoggerFactory.getLogger(LfNetwork.class);

    private final List<LfBus> buses;

    private final List<LfBranch> branches;

    private final LfBus slackBus;

    public LfNetwork(List<LfBus> buses, List<LfBranch> branches, SlackBusSelector slackBusSelector) {
        this.buses = Objects.requireNonNull(buses);
        if (buses.isEmpty()) {
            throw new IllegalArgumentException("Empty bus list");
        }
        this.branches = Objects.requireNonNull(branches);
        Objects.requireNonNull(slackBusSelector);

        slackBus = slackBusSelector.select(this.buses);
        slackBus.setSlack(true);
        LOGGER.info("Selected slack bus (class={}): {}", slackBusSelector.getClass().getSimpleName(), slackBus.getId());
    }

    public List<LfBranch> getBranches() {
        return branches;
    }

    public List<LfBus> getBuses() {
        return buses;
    }

    public LfBus getBus(int num) {
        return buses.get(num);
    }

    public LfBus getSlackBus() {
        return slackBus;
    }

    public void updateState() {
        Stopwatch stopwatch = Stopwatch.createStarted();

        for (LfBus bus : buses) {
            bus.updateState();
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
        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            writeJson(writer);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void writeJson(LfBus bus, JsonGenerator jsonGenerator) throws IOException {
        jsonGenerator.writeStringField("id", bus.getId());
        jsonGenerator.writeNumberField("num", bus.getNum());
        if (bus.getGenerationTargetP() != 0) {
            jsonGenerator.writeNumberField("generationTargetP", bus.getGenerationTargetP());
        }
        if (bus.getGenerationTargetQ() != 0) {
            jsonGenerator.writeNumberField("generationTargetQ", bus.getGenerationTargetQ());
        }
        if (bus.getLoadTargetP() != 0) {
            jsonGenerator.writeNumberField("loadTargetP", bus.getLoadTargetP());
        }
        if (bus.getLoadTargetQ() != 0) {
            jsonGenerator.writeNumberField("loadTargetQ", bus.getLoadTargetQ());
        }
        if (!Double.isNaN(bus.getMinP())) {
            jsonGenerator.writeNumberField("minP", bus.getMinP());
        }
        if (!Double.isNaN(bus.getMaxP())) {
            jsonGenerator.writeNumberField("maxP", bus.getMaxP());
        }
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

    public void writeJson(LfBranch branch, JsonGenerator jsonGenerator) throws IOException {
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

    public void writeJson(LfShunt shunt, JsonGenerator jsonGenerator) throws IOException {
        jsonGenerator.writeNumberField("b", shunt.getB());
    }

    public void writeJson(Writer writer) {
        Objects.requireNonNull(writer);
        try (JsonGenerator jsonGenerator = new JsonFactory()
                .createGenerator(writer)
                .useDefaultPrettyPrinter()) {
            jsonGenerator.writeStartObject();

            jsonGenerator.writeFieldName("buses");
            jsonGenerator.writeStartArray();
            for (LfBus bus : buses) {
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
}

