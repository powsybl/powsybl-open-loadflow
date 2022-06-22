/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.openloadflow.util.PerUnit;
import org.apache.commons.lang3.tuple.Pair;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.*;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class LfContingency {

    private final String id;

    private final int index;

    private final Set<LfBus> disabledBuses;

    private final Set<LfBranch> disabledBranches;

    private final Set<LfHvdc> disabledHvdcs;

    private final Map<LfShunt, Pair<Double, Double>> shuntsShift;

    private final Map<LfBus, PowerShift> busesLoadShift;

    private final Set<LfGenerator> lostGenerators;

    private double activePowerLoss = 0;

    public LfContingency(String id, int index, Set<LfBus> disabledBuses, Set<LfBranch> disabledBranches, Map<LfShunt, Pair<Double, Double>> shuntsShift,
                         Map<LfBus, PowerShift> busesLoadShift, Set<LfGenerator> lostGenerators, Set<LfHvdc> disabledHvdcs) {
        this.id = Objects.requireNonNull(id);
        this.index = index;
        this.disabledBuses = Objects.requireNonNull(disabledBuses);
        this.disabledBranches = Objects.requireNonNull(disabledBranches);
        this.disabledHvdcs = Objects.requireNonNull(disabledHvdcs);
        this.shuntsShift = Objects.requireNonNull(shuntsShift);
        this.busesLoadShift = Objects.requireNonNull(busesLoadShift);
        this.lostGenerators = Objects.requireNonNull(lostGenerators);
        for (LfBus bus : disabledBuses) {
            activePowerLoss += bus.getGenerationTargetP() - bus.getLoadTargetP();
        }
        for (Map.Entry<LfBus, PowerShift> e : busesLoadShift.entrySet()) {
            activePowerLoss -= e.getValue().getActive();
        }
        for (LfGenerator generator : lostGenerators) {
            activePowerLoss += generator.getTargetP();
        }
    }

    public String getId() {
        return id;
    }

    public int getIndex() {
        return index;
    }

    public Set<LfBus> getDisabledBuses() {
        return disabledBuses;
    }

    public Set<LfBranch> getDisabledBranches() {
        return disabledBranches;
    }

    public Map<LfShunt, Pair<Double, Double>> getShuntsShift() {
        return shuntsShift;
    }

    public Map<LfBus, PowerShift> getBusesLoadShift() {
        return busesLoadShift;
    }

    public Set<LfGenerator> getLostGenerators() {
        return lostGenerators;
    }

    public double getActivePowerLoss() {
        return activePowerLoss;
    }

    public void apply(LoadFlowParameters.BalanceType balanceType) {
        for (LfBranch branch : disabledBranches) {
            branch.setDisabled(true);
        }
        for (LfHvdc hvdc : disabledHvdcs) {
            hvdc.setDisabled(true);
        }
        for (LfBus bus : disabledBuses) {
            bus.setDisabled(true);
        }
        for (var e : shuntsShift.entrySet()) {
            LfShunt shunt = e.getKey();
            shunt.setB(shunt.getB() - e.getValue().getLeft());
            shunt.setG(shunt.getG() - e.getValue().getRight());
        }
        for (var e : busesLoadShift.entrySet()) {
            LfBus bus = e.getKey();
            PowerShift shift = e.getValue();
            bus.setLoadTargetP(bus.getLoadTargetP() - getUpdatedLoadP0(bus, balanceType, shift.getActive(), shift.getVariableActive()));
            bus.setLoadTargetQ(bus.getLoadTargetQ() - shift.getReactive());
            bus.getLoads().setAbsVariableLoadTargetP(bus.getLoads().getAbsVariableLoadTargetP() - Math.abs(shift.getVariableActive()) * PerUnit.SB);
        }
        Set<LfBus> generatorBuses = new HashSet<>();
        for (LfGenerator generator : lostGenerators) {
            generator.setTargetP(0);
            LfBus bus = generator.getBus();
            generatorBuses.add(bus);
            generator.setParticipating(false);
            if (generator.getGeneratorControlType() != LfGenerator.GeneratorControlType.OFF) {
                generator.setGeneratorControlType(LfGenerator.GeneratorControlType.OFF);
            } else {
                bus.setGenerationTargetQ(bus.getGenerationTargetQ() - generator.getTargetQ());
            }
        }
        for (LfBus bus : generatorBuses) {
            if (bus.getGenerators().stream().noneMatch(gen -> gen.getGeneratorControlType() == LfGenerator.GeneratorControlType.VOLTAGE)) {
                bus.setVoltageControlEnabled(false);
            }
        }
    }

    public static double getUpdatedLoadP0(LfBus bus, LoadFlowParameters.BalanceType balanceType, double initialP0, double initialVariableActivePower) {
        double factor = 0.0;
        if (bus.getLoads().getLoadCount() > 0) {
            if (balanceType == LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD) {
                factor = Math.abs(initialP0) / (bus.getLoads().getAbsVariableLoadTargetP() / PerUnit.SB);
            } else if (balanceType == LoadFlowParameters.BalanceType.PROPORTIONAL_TO_CONFORM_LOAD) {
                factor = initialVariableActivePower / (bus.getLoads().getAbsVariableLoadTargetP() / PerUnit.SB);
            }
        }
        return initialP0 + (bus.getLoadTargetP() - bus.getInitialLoadTargetP()) * factor;
    }

    public Set<LfBus> getLoadAndGeneratorBuses() {
        Set<LfBus> buses = new HashSet<>();
        for (var e : busesLoadShift.entrySet()) {
            LfBus bus = e.getKey();
            if (bus != null) {
                buses.add(bus);
            }
        }
        for (LfGenerator generator : lostGenerators) {
            LfBus bus = generator.getBus();
            if (bus != null) {
                buses.add(bus);
            }
        }
        return buses;
    }

    public void writeJson(Writer writer) {
        Objects.requireNonNull(writer);
        try (JsonGenerator jsonGenerator = new JsonFactory()
                .createGenerator(writer)
                .useDefaultPrettyPrinter()) {
            jsonGenerator.writeStartObject();

            jsonGenerator.writeStringField("id", id);

            jsonGenerator.writeFieldName("buses");
            int[] sortedBuses = disabledBuses.stream().mapToInt(LfBus::getNum).sorted().toArray();
            jsonGenerator.writeArray(sortedBuses, 0, sortedBuses.length);

            jsonGenerator.writeFieldName("branches");
            int[] sortedBranches = disabledBranches.stream().mapToInt(LfBranch::getNum).sorted().toArray();
            jsonGenerator.writeArray(sortedBranches, 0, sortedBranches.length);

            jsonGenerator.writeEndObject();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
