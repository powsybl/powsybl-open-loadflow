/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.Line;
import com.powsybl.iidm.network.Network;
import com.powsybl.openloadflow.network.impl.Networks;
import org.jgrapht.Graph;
import org.jgrapht.alg.connectivity.ConnectivityInspector;
import org.jgrapht.graph.Pseudograph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class NominalVoltageMapper {

    private static final Logger LOGGER = LoggerFactory.getLogger(NominalVoltageMapper.class);

    private final Map<String, Double> mapping;

    public NominalVoltageMapper(Map<String, Double> mapping) {
        this.mapping = Objects.requireNonNull(mapping);
    }

    public double getNominalV(String busId) {
        return mapping.computeIfAbsent(busId, ignored -> {
            throw new IllegalArgumentException("Unknown bus '" + busId + "'");
        });
    }

    public static NominalVoltageMapper create(Network network, boolean breakers) {
        Map<String, Double> nominalVoltageMapping = new HashMap<>();

        Graph<String, Line> graph = new Pseudograph<>(Line.class);
        Map<String, Double> busIdToNomV = new HashMap<>();
        for (Bus bus : Networks.getBuses(network, breakers)) {
            graph.addVertex(bus.getId());
            busIdToNomV.put(bus.getId(), bus.getVoltageLevel().getNominalV());
        }
        for (Line line : network.getLines()) {
            Bus bus1 = Networks.getBus(line.getTerminal1(), breakers);
            if (bus1 != null) {
                Bus bus2 = Networks.getBus(line.getTerminal2(), breakers);
                if (bus2 != null) {
                    double nominalV1 = bus1.getVoltageLevel().getNominalV();
                    double nominalV2 = bus2.getVoltageLevel().getNominalV();
                    double variation = (nominalV2 - nominalV1) / nominalV1 * 100d; // in %
                    // we only accept to use same per-uniting for nominal voltages closed enough (10%)
                    if (Math.abs(variation) < 10) {
                        graph.addEdge(bus1.getId(), bus2.getId(), line);
                    }
                }
            }
        }

        List<Set<String>> busSets = new ConnectivityInspector<>(graph).connectedSets();
        for (var busSet : busSets) {
            TreeMap<Double, Integer> nominalVoltages = busSet.stream()
                    .collect(Collectors.toMap(busIdToNomV::get, busId -> 1, Integer::sum, TreeMap::new));
            double mainNominalV = nominalVoltages.entrySet().stream()
                    .sorted((o1, o2) -> Integer.compare(o2.getValue(), o1.getValue()))
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElseThrow();
            if (nominalVoltages.size() > 1) {
                LOGGER.debug("A set of {} buses connected though lines have {} different nominal voltages ({}), replaced all by main one {}",
                        busSet.size(), nominalVoltages.size(), nominalVoltages.keySet(), mainNominalV);
            }
            for (String busId : busSet) {
                nominalVoltageMapping.put(busId, mainNominalV);
            }
        }

        return new NominalVoltageMapper(nominalVoltageMapping);
    }
}
