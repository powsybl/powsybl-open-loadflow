/**
 * Copyright (c) 2026, Kickstage (https://kickstage.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.se;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.ieeecdf.converter.IeeeCdfNetworkFactory;
import com.powsybl.iidm.network.Network;
import com.powsybl.openloadflow.ac.equations.measurement.MeasuredQuantity;
import com.powsybl.openloadflow.network.FirstSlackBusSelector;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.impl.Networks;
import com.powsybl.openloadflow.network.measurement.LfMeasurementProjection;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the estimator on a frozen measurement set and compares the answer against
 * pandapower's, printing the deviations. Run with:
 *
 * <pre>    mvn test -Dtest=WlsStateEstimatorDemo</pre>
 *
 * <p>The measurements were made by running a power flow on IEEE 14-bus and adding Gaussian
 * noise to its results, one standard deviation per measurement class. That set and
 * pandapower's estimate of it are read from {@code src/test/resources/se} and are
 * byte-for-byte what both implementations consumed.</p>
 *
 * @author Šime Pavlić {@literal <sime.pavlic at kickstage.com>}
 */
class WlsStateEstimatorDemo {

    /** Per-unit base power that open-loadflow works in, in MVA. */
    private static final double SB = 100.0;

    private static final double TOLERANCE_V_PU = 1e-5;
    private static final double TOLERANCE_ANGLE_DEGREE = 1e-4;
    private static final double TOLERANCE_FLOW_MW = 1e-2;

    @Test
    void compareAgainstPandapower() {
        JsonNode scenario = readJson("/se/scenario_ieee14.json");
        JsonNode reference = readJson("/se/results_pandapower.json");

        Network network = IeeeCdfNetworkFactory.create14();
        LfNetwork lfNetwork = Networks.load(network, new FirstSlackBusSelector()).get(0);
        List<Measurement> measurements = readMeasurements(scenario, lfNetwork);

        explain(scenario, measurements, lfNetwork);

        WlsStateEstimator estimator = new WlsStateEstimator(lfNetwork, measurements);
        estimator.initFlat();
        WlsResult result = estimator.estimate();

        System.out.println();
        System.out.println("Result");
        System.out.println("------");
        System.out.printf("  converged: %s, after %d iterations (pandapower: %d)%n",
                result.converged(), result.iterations(), reference.get("iterations").asInt());
        assertTrue(result.converged());
        assertEquals(reference.get("iterations").asInt(), result.iterations(),
                "both implementations should take the same number of iterations");

        System.out.println();
        System.out.println("Agreement with pandapower");
        System.out.println("-------------------------");
        System.out.println("Tolerances are proposed to be below the smallest difference a real meter could");
        System.out.println("detect, and above the numerical noise of two different solvers landing on the");
        System.out.println("same optimum.");
        System.out.println();
        System.out.printf("  %-28s %14s %13s%n", "quantity", "max deviation", "tolerance");

        double maxV = 0;
        double maxAngle = 0;
        for (JsonNode bus : reference.get("bus")) {
            LfBus lfBus = busOf(lfNetwork, bus.get("ieee_bus").asInt());
            int i = lfBus.getNum();
            maxV = Math.max(maxV, Math.abs(result.busV()[i] - bus.get("vm_pu").asDouble()));
            maxAngle = Math.max(maxAngle,
                    Math.abs(Math.toDegrees(result.busAngleRadian()[i]) - bus.get("va_degree").asDouble()));
        }
        report("bus voltage magnitude", maxV, TOLERANCE_V_PU, "pu");
        report("bus voltage angle", maxAngle, TOLERANCE_ANGLE_DEGREE, "deg");

        double[] flowDeviation = compareLineFlows(lfNetwork, reference, result);
        report("line active flow (from)", flowDeviation[0], TOLERANCE_FLOW_MW, "MW");
        report("line reactive flow (from)", flowDeviation[1], TOLERANCE_FLOW_MW, "MVar");
        System.out.println();
    }

    private static void explain(JsonNode scenario, List<Measurement> measurements, LfNetwork lfNetwork) {
        JsonNode meta = scenario.get("meta");
        System.out.println();
        System.out.println("State estimation on IEEE 14-bus, against pandapower");
        System.out.println("==================================================");
        System.out.println();
        System.out.println("The measurements were made by running a power flow on IEEE 14-bus and adding");
        System.out.println("Gaussian noise to its results, one standard deviation per measurement class.");
        System.out.println("The same readings go to this estimator and to pandapower's, and the two answers");
        System.out.println("are compared below.");
        System.out.println();
        System.out.println("Inputs");
        System.out.println("------");
        System.out.printf("  network:      %s%n", meta.get("network").asText());
        System.out.printf("  reference:    pandapower %s, plain weighted least squares%n",
                meta.get("pandapower_version").asText());
        System.out.printf("  measurements: %d readings, random seed %d%n",
                measurements.size(), meta.get("seed").asLong());
        System.out.printf("  unknowns:     %d (two per bus, less the one angle held as reference)%n",
                2 * lfNetwork.getBuses().size() - 1);
        System.out.printf("  start:        flat, %s%n", "1 pu at every bus, every angle zero");
    }

    private static void report(String quantity, double deviation, double tolerance, String unit) {
        System.out.printf("  %-28s %11.3e %-4s %10.0e %-4s%n", quantity, deviation, unit, tolerance, unit);
        assertTrue(deviation <= tolerance, quantity + " deviates by " + deviation + ", tolerance " + tolerance);
    }

    /**
     * Flows are not part of the state, so they are recomputed from the estimated voltages the
     * same way any other quantity would be: by evaluating the branch flow terms at that state.
     */
    private static double[] compareLineFlows(LfNetwork lfNetwork, JsonNode reference, WlsResult result) {
        List<Measurement> probes = new ArrayList<>();
        List<JsonNode> lines = new ArrayList<>();
        for (JsonNode line : reference.get("line")) {
            LfBus from = busOf(lfNetwork, line.get("ieee_from_bus").asInt());
            LfBranch branch = branchBetween(lfNetwork, from, busOf(lfNetwork, line.get("ieee_to_bus").asInt()));
            int side = LfMeasurementProjection.sideOf(branch, from);
            probes.add(Measurement.branchFlow(branch, MeasuredQuantity.BRANCH_P, side, 0, 1));
            probes.add(Measurement.branchFlow(branch, MeasuredQuantity.BRANCH_Q, side, 0, 1));
            lines.add(line);
        }

        WlsStateEstimator probe = new WlsStateEstimator(lfNetwork, probes);
        probe.init(result.busV(), result.busAngleRadian());
        double[] evaluated = probe.evaluate();

        double maxP = 0;
        double maxQ = 0;
        for (int i = 0; i < lines.size(); i++) {
            JsonNode line = lines.get(i);
            maxP = Math.max(maxP, Math.abs(evaluated[2 * i] * SB - line.get("p_from_mw").asDouble()));
            maxQ = Math.max(maxQ, Math.abs(evaluated[2 * i + 1] * SB - line.get("q_from_mvar").asDouble()));
        }
        return new double[] {maxP, maxQ};
    }

    /**
     * Turns the frozen scenario into the estimator's own conventions: per unit on a 100 MVA
     * base, injections generation-positive where the file records them consumption-positive,
     * and branch sides resolved against the orientation open-loadflow gave each branch.
     */
    private static List<Measurement> readMeasurements(JsonNode scenario, LfNetwork lfNetwork) {
        List<Measurement> measurements = new ArrayList<>();
        for (JsonNode m : scenario.get("measurements")) {
            String type = m.get("type").asText();
            double measured = m.get("measured").asDouble();
            double sigma = m.get("sigma").asDouble();
            switch (type) {
                case "v" -> measurements.add(Measurement.busVoltage(
                        busOf(lfNetwork, m.get("ieee_bus").asInt()), measured, sigma));
                case "p_injection", "q_injection" -> measurements.add(Measurement.busInjection(
                        busOf(lfNetwork, m.get("ieee_bus").asInt()), type.startsWith("p"),
                        -measured / SB, sigma / SB));
                case "p_flow", "q_flow" -> {
                    LfBus from = busOf(lfNetwork, m.get("ieee_from_bus").asInt());
                    LfBranch branch = branchBetween(lfNetwork, from, busOf(lfNetwork, m.get("ieee_to_bus").asInt()));
                    measurements.add(Measurement.branchFlow(branch,
                            type.startsWith("p") ? MeasuredQuantity.BRANCH_P : MeasuredQuantity.BRANCH_Q,
                            LfMeasurementProjection.sideOf(branch, from), measured / SB, sigma / SB));
                }
                default -> throw new IllegalStateException("Unknown measurement type: " + type);
            }
        }
        return measurements;
    }

    /** The scenario numbers buses as the IEEE case does, which the network names VL{n}_0. */
    private static LfBus busOf(LfNetwork lfNetwork, int ieeeBusNumber) {
        LfBus bus = new LfMeasurementProjection(lfNetwork).findBus("VL" + ieeeBusNumber + "_0")
                .orElseThrow(() -> new IllegalStateException("No bus for IEEE bus " + ieeeBusNumber));
        return bus;
    }

    private static LfBranch branchBetween(LfNetwork lfNetwork, LfBus one, LfBus other) {
        Map<Long, LfBranch> byEndpoints = new HashMap<>();
        for (LfBranch branch : lfNetwork.getBranches()) {
            if (branch.getBus1() != null && branch.getBus2() != null) {
                byEndpoints.put(key(branch.getBus1().getNum(), branch.getBus2().getNum()), branch);
            }
        }
        LfBranch branch = byEndpoints.get(key(one.getNum(), other.getNum()));
        if (branch == null) {
            throw new IllegalStateException("No branch between " + one.getId() + " and " + other.getId());
        }
        return branch;
    }

    private static long key(int num1, int num2) {
        return Math.min(num1, num2) * 100_000L + Math.max(num1, num2);
    }

    private static JsonNode readJson(String resource) {
        try (InputStream in = WlsStateEstimatorDemo.class.getResourceAsStream(resource)) {
            return new ObjectMapper().readTree(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
