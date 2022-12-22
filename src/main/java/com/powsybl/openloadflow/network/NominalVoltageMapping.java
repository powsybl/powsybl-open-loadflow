/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.Terminal;
import org.apache.commons.lang3.mutable.MutableInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class NominalVoltageMapping {

    private static final Logger LOGGER = LoggerFactory.getLogger(NominalVoltageMapping.class);

    private static final double NOMINAL_VOLTAGE_RANGE = 0.1;

    public static final NominalVoltageMapping NONE = new NominalVoltageMapping();

    private final Map<Double, Double> mapping;

    public NominalVoltageMapping() {
        this(new HashMap<>());
    }

    public NominalVoltageMapping(Map<Double, Double> mapping) {
        this.mapping = Objects.requireNonNull(mapping);
    }

    public double get(double nominalV) {
        return mapping.getOrDefault(nominalV, nominalV);
    }

    public double get(Terminal terminal) {
        return get(Objects.requireNonNull(terminal).getVoltageLevel().getNominalV());
    }

    public double get(Bus bus) {
        return get(Objects.requireNonNull(bus).getVoltageLevel().getNominalV());
    }

    private static Map<Double, MutableInt> createHistogram(Iterable<Bus> buses) {
        Map<Double, MutableInt> nominalVoltageHistogram = new TreeMap<>();
        for (var bus : buses) {
            nominalVoltageHistogram.computeIfAbsent(bus.getVoltageLevel().getNominalV(), k -> new MutableInt())
                    .increment();
        }
        return nominalVoltageHistogram;
    }

    public static NominalVoltageMapping create(Iterable<Bus> buses) {
        Objects.requireNonNull(buses);

        Map<Double, Double> nominalVoltageMapping = new HashMap<>();

        Map<Double, MutableInt> nominalVoltageHistogram = createHistogram(buses);
        boolean change = true;
        while (change) {
            change = false;
            Set<Double> nominalVoltages = new HashSet<>(nominalVoltageHistogram.keySet());
            for (double nominalV1 : nominalVoltages) {
                var it = nominalVoltageHistogram.entrySet().iterator();
                while (it.hasNext()) {
                    var e = it.next();
                    double nominalV2 = e.getKey();
                    if (nominalV1 != nominalV2 && Math.abs(nominalV1 - nominalV2) / nominalV1 < NOMINAL_VOLTAGE_RANGE) {
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

        return new NominalVoltageMapping(nominalVoltageMapping);
    }
}
