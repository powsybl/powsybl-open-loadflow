/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Terminal;
import org.apache.commons.lang3.mutable.MutableInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Nominal voltage mapping. All nominal voltages that are closed (10% threshold) are merged together so that we only
 * keep ones containing the biggest number of buses, resulting a set of common nominal voltages and discarding unusual
 * and rare ones. The purpose of this is to avoid the addition a voltage ratio to line with a small difference of
 * nominal voltage between side 1 and 2.
 *
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class SimpleNominalVoltageMapping implements NominalVoltageMapping {

    private static final Logger LOGGER = LoggerFactory.getLogger(SimpleNominalVoltageMapping.class);

    public static final SimpleNominalVoltageMapping NONE = new SimpleNominalVoltageMapping();

    private final Map<Double, Double> mapping;

    public SimpleNominalVoltageMapping() {
        this(new HashMap<>());
    }

    public SimpleNominalVoltageMapping(Map<Double, Double> mapping) {
        this.mapping = Objects.requireNonNull(mapping);
    }

    public Map<Double, Double> get() {
        return mapping;
    }

    public double get(double nominalV) {
        return mapping.getOrDefault(nominalV, nominalV);
    }

    @Override
    public double get(Terminal terminal) {
        return get(Objects.requireNonNull(terminal).getVoltageLevel().getNominalV());
    }

    @Override
    public double get(Bus bus) {
        return get(Objects.requireNonNull(bus).getVoltageLevel().getNominalV());
    }

    private static Map<Double, MutableInt> createHistogram(Network network) {
        Map<Double, MutableInt> nominalVoltageHistogram = new TreeMap<>();
        for (var vl : network.getVoltageLevels()) {
            nominalVoltageHistogram.computeIfAbsent(vl.getNominalV(), k -> new MutableInt())
                    .increment();
        }
        return nominalVoltageHistogram;
    }

    public static SimpleNominalVoltageMapping create(Network network, double resolution) {
        Objects.requireNonNull(network);

        Map<Double, Double> nominalVoltageMapping = new HashMap<>();

        Map<Double, MutableInt> nominalVoltageHistogram = createHistogram(network);
        boolean change = true;
        while (change) {
            change = false;
            Set<Double> nominalVoltages = new HashSet<>(nominalVoltageHistogram.keySet());
            for (double nominalV1 : nominalVoltages) {
                var it = nominalVoltageHistogram.entrySet().iterator();
                while (it.hasNext() && !change) {
                    var e = it.next();
                    double nominalV2 = e.getKey();
                    if (nominalV1 != nominalV2 && Math.abs(nominalV1 - nominalV2) / nominalV1 < resolution) {
                        MutableInt count1 = nominalVoltageHistogram.get(nominalV1);
                        MutableInt count2 = e.getValue();
                        if (count1.getValue() > count2.getValue()) {
                            nominalVoltageMapping.put(nominalV2, nominalV1);
                            count1.add(count2.getValue());
                            it.remove();
                            change = true;
                        }
                    }
                }
            }
        }

        if (!nominalVoltageMapping.isEmpty()) {
            LOGGER.debug("Nominal voltage mapping: {}", nominalVoltageMapping);
        }

        return new SimpleNominalVoltageMapping(nominalVoltageMapping);
    }
}
