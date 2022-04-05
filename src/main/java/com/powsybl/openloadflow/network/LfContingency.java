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

    private final Set<LfBus> buses;

    private final Set<LfBranch> branches;

    private final Map<LfShunt, Double> shuntsShift;

    private final Map<LfBus, PowerShift> busesLoadShift;

    private final Set<LfGenerator> generators;

    private final Set<LfGenerator> participatingGeneratorsToBeRemoved = new HashSet<>();

    private double activePowerLoss = 0;

    public LfContingency(String id, int index, Set<LfBus> buses, Set<LfBranch> branches, Map<LfShunt, Double> shuntsShift,
                         Map<LfBus, PowerShift> busesLoadShift, Set<LfGenerator> generators) {
        this.id = Objects.requireNonNull(id);
        this.index = index;
        this.buses = Objects.requireNonNull(buses);
        this.branches = Objects.requireNonNull(branches);
        this.shuntsShift = Objects.requireNonNull(shuntsShift);
        this.busesLoadShift = Objects.requireNonNull(busesLoadShift);
        this.generators = Objects.requireNonNull(generators);
        for (LfBus bus : buses) {
            activePowerLoss += bus.getGenerationTargetP() - bus.getLoadTargetP();
        }
        for (Map.Entry<LfBus, PowerShift> e : busesLoadShift.entrySet()) {
            activePowerLoss -= e.getValue().getActive();
        }
        for (LfGenerator generator : generators) {
            activePowerLoss += generator.getTargetP();
        }
    }

    public String getId() {
        return id;
    }

    public int getIndex() {
        return index;
    }

    public Set<LfBus> getBuses() {
        return buses;
    }

    public Set<LfBranch> getBranches() {
        return branches;
    }

    public Map<LfShunt, Double> getShuntsShift() {
        return shuntsShift;
    }

    public Map<LfBus, PowerShift> getBusesLoadShift() {
        return busesLoadShift;
    }

    public Set<LfGenerator> getGenerators() {
        return generators;
    }

    public double getActivePowerLoss() {
        return activePowerLoss;
    }

    public void apply(LoadFlowParameters.BalanceType balanceType) {
        for (LfBranch branch : branches) {
            branch.setDisabled(true);
        }
        for (LfBus bus : buses) {
            bus.setDisabled(true);
        }
        for (var e : shuntsShift.entrySet()) {
            LfShunt shunt = e.getKey();
            shunt.setB(shunt.getB() - e.getValue());
        }
        for (var e : busesLoadShift.entrySet()) {
            LfBus bus = e.getKey();
            PowerShift shift = e.getValue();
            bus.setLoadTargetP(bus.getLoadTargetP() - getUpdatedLoadP0(bus, balanceType, shift.getActive(), shift.getVariableActive()));
            bus.setLoadTargetQ(bus.getLoadTargetQ() - shift.getReactive());
            bus.getLfLoads().setAbsVariableLoadTargetP(bus.getLfLoads().getAbsVariableLoadTargetP() - Math.abs(shift.getVariableActive()) * PerUnit.SB);
        }
        for (LfGenerator generator : generators) {
            LfBus bus = generator.getBus();
            generator.setTargetP(0);
            generator.setParticipating(false);
            if (generator.getGeneratorControlType() != LfGenerator.GeneratorControlType.OFF) {
                generator.setGeneratorControlType(LfGenerator.GeneratorControlType.OFF);
                participatingGeneratorsToBeRemoved.add(generator);
            } else {
                bus.setGenerationTargetQ(bus.getGenerationTargetQ() - generator.getTargetQ());
            }
        }
    }

    public static double getUpdatedLoadP0(LfBus bus, LoadFlowParameters.BalanceType balanceType, double initialP0, double initialVariableActivePower) {
        double factor = 0.0;
        if (bus.getLfLoads().getLoadCount() > 0) {
            if (balanceType == LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD) {
                factor = Math.abs(initialP0) / (bus.getLfLoads().getAbsVariableLoadTargetP() / PerUnit.SB);
            } else if (balanceType == LoadFlowParameters.BalanceType.PROPORTIONAL_TO_CONFORM_LOAD) {
                factor = initialVariableActivePower / (bus.getLfLoads().getAbsVariableLoadTargetP() / PerUnit.SB);
            }
        }
        return initialP0 + (bus.getLoadTargetP() - bus.getInitialLoadTargetP()) * factor;
    }

    public Set<LfGenerator> getParticipatingGeneratorsToBeRemoved() {
        return participatingGeneratorsToBeRemoved;
    }

    public void writeJson(Writer writer) {
        Objects.requireNonNull(writer);
        try (JsonGenerator jsonGenerator = new JsonFactory()
                .createGenerator(writer)
                .useDefaultPrettyPrinter()) {
            jsonGenerator.writeStartObject();

            jsonGenerator.writeStringField("id", id);

            jsonGenerator.writeFieldName("buses");
            int[] sortedBuses = buses.stream().mapToInt(LfBus::getNum).sorted().toArray();
            jsonGenerator.writeArray(sortedBuses, 0, sortedBuses.length);

            jsonGenerator.writeFieldName("branches");
            int[] sortedBranches = branches.stream().mapToInt(LfBranch::getNum).sorted().toArray();
            jsonGenerator.writeArray(sortedBranches, 0, sortedBranches.length);

            jsonGenerator.writeEndObject();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
