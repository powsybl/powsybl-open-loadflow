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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class LfContingency {

    private final String id;

    private final int index;

    private final int createdSynchronousComponentsCount;

    private final DisabledNetwork disabledNetwork;

    private final Map<LfShunt, AdmittanceShift> shuntsShift;

    private final Map<LfLoad, LfLostLoad> lostLoads;

    private final Set<LfGenerator> lostGenerators;

    private double disconnectedLoadActivePower;

    private double disconnectedGenerationActivePower;

    private final Set<String> disconnectedElementIds;

    public LfContingency(String id, int index, int createdSynchronousComponentsCount, DisabledNetwork disabledNetwork, Map<LfShunt, AdmittanceShift> shuntsShift,
                         Map<LfLoad, LfLostLoad> lostLoads, Set<LfGenerator> lostGenerators) {
        this.id = Objects.requireNonNull(id);
        this.index = index;
        this.createdSynchronousComponentsCount = createdSynchronousComponentsCount;
        this.disabledNetwork = Objects.requireNonNull(disabledNetwork);
        this.shuntsShift = Objects.requireNonNull(shuntsShift);
        this.lostLoads = Objects.requireNonNull(lostLoads);
        this.lostGenerators = Objects.requireNonNull(lostGenerators);
        this.disconnectedLoadActivePower = 0.0;
        this.disconnectedGenerationActivePower = 0.0;
        this.disconnectedElementIds = new HashSet<>();

        for (LfBus bus : disabledNetwork.getBuses()) {
            disconnectedLoadActivePower += bus.getLoadTargetP();
            disconnectedGenerationActivePower += bus.getGenerationTargetP();
            disconnectedElementIds.addAll(bus.getGenerators().stream().map(LfGenerator::getId).collect(Collectors.toList()));
            disconnectedElementIds.addAll(bus.getLoads().stream().flatMap(l -> l.getOriginalIds().stream()).collect(Collectors.toList()));
            bus.getControllerShunt().ifPresent(shunt -> disconnectedElementIds.addAll(shunt.getOriginalIds()));
            bus.getShunt().ifPresent(shunt -> disconnectedElementIds.addAll(shunt.getOriginalIds()));
        }
        for (Map.Entry<LfLoad, LfLostLoad> e : lostLoads.entrySet()) {
            LfLostLoad lostLoad = e.getValue();
            disconnectedLoadActivePower += lostLoad.getPowerShift().getActive();
            disconnectedElementIds.addAll(lostLoad.getOriginalIds());
        }
        for (LfGenerator generator : lostGenerators) {
            disconnectedGenerationActivePower += generator.getTargetP();
            disconnectedElementIds.add(generator.getOriginalId());
        }
        disconnectedElementIds.addAll(disabledNetwork.getBranches().stream().map(LfBranch::getId).collect(Collectors.toList()));
        // FIXME: shuntsShift has to be included in the disconnected elements.
    }

    public String getId() {
        return id;
    }

    public int getIndex() {
        return index;
    }

    public int getCreatedSynchronousComponentsCount() {
        return createdSynchronousComponentsCount;
    }

    public DisabledNetwork getDisabledNetwork() {
        return disabledNetwork;
    }

    public Map<LfShunt, AdmittanceShift> getShuntsShift() {
        return shuntsShift;
    }

    public Map<LfLoad, LfLostLoad> getLostLoads() {
        return lostLoads;
    }

    public Set<LfGenerator> getLostGenerators() {
        return lostGenerators;
    }

    public Set<String> getDisconnectedElementIds() {
        return disconnectedElementIds;
    }

    public double getActivePowerLoss() {
        return disconnectedGenerationActivePower - disconnectedLoadActivePower;
    }

    public double getDisconnectedLoadActivePower() {
        return disconnectedLoadActivePower;
    }

    public double getDisconnectedGenerationActivePower() {
        return disconnectedGenerationActivePower;
    }

    public void apply(LoadFlowParameters.BalanceType balanceType) {
        for (LfBranch branch : disabledNetwork.getBranches()) {
            branch.setDisabled(true);
        }
        for (LfHvdc hvdc : disabledNetwork.getHvdcs()) {
            hvdc.setDisabled(true);
        }
        for (LfBus bus : disabledNetwork.getBuses()) {
            bus.setDisabled(true);
        }
        for (var e : shuntsShift.entrySet()) {
            LfShunt shunt = e.getKey();
            shunt.setG(shunt.getG() - e.getValue().getG());
            shunt.setB(shunt.getB() - e.getValue().getB());
        }
        for (var e : lostLoads.entrySet()) {
            LfLoad load = e.getKey();
            LfLostLoad lostLoad = e.getValue();
            PowerShift shift = lostLoad.getPowerShift();
            load.setTargetP(load.getTargetP() - getUpdatedLoadP0(load, balanceType, shift.getActive(), shift.getVariableActive()));
            load.setTargetQ(load.getTargetQ() - shift.getReactive());
            load.setAbsVariableTargetP(load.getAbsVariableTargetP() - Math.abs(shift.getVariableActive()));
            lostLoad.getOriginalIds().forEach(loadId -> load.setOriginalLoadDisabled(loadId, true));
        }
        Set<LfBus> generatorBuses = new HashSet<>();
        for (LfGenerator generator : lostGenerators) {
            generator.setTargetP(0);
            LfBus bus = generator.getBus();
            generatorBuses.add(bus);
            generator.setParticipating(false);
            generator.setDisabled(true);
            if (generator.getGeneratorControlType() != LfGenerator.GeneratorControlType.OFF) {
                generator.setGeneratorControlType(LfGenerator.GeneratorControlType.OFF);
            } else {
                bus.setGenerationTargetQ(bus.getGenerationTargetQ() - generator.getTargetQ());
            }
            if (generator instanceof LfStaticVarCompensator svc) {
                svc.getStandByAutomatonShunt().ifPresent(svcShunt -> {
                    // it means that the generator in contingency is a static var compensator with an active stand by automaton shunt
                    shuntsShift.put(svcShunt, new AdmittanceShift(0, svcShunt.getB()));
                    svcShunt.setB(0);
                });
            }
        }
        for (LfBus bus : generatorBuses) {
            if (bus.getGenerators().stream().noneMatch(gen -> gen.getGeneratorControlType() == LfGenerator.GeneratorControlType.VOLTAGE)) {
                bus.setGeneratorVoltageControlEnabled(false);
            }
            if (bus.getGenerators().stream().noneMatch(gen -> gen.getGeneratorControlType() == LfGenerator.GeneratorControlType.REMOTE_REACTIVE_POWER)) {
                bus.setReactivePowerControlEnabled(false);
            }
        }
    }

    private static double getUpdatedLoadP0(LfLoad load, LoadFlowParameters.BalanceType balanceType, double initialP0, double initialVariableActivePower) {
        double factor = 0;
        if (load.getOriginalLoadCount() > 0) {
            if (balanceType == LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD) {
                factor = Math.abs(initialP0) / load.getAbsVariableTargetP();
            } else if (balanceType == LoadFlowParameters.BalanceType.PROPORTIONAL_TO_CONFORM_LOAD) {
                factor = initialVariableActivePower / load.getAbsVariableTargetP();
            }
        }
        return initialP0 + (load.getTargetP() - load.getInitialTargetP()) * factor;
    }

    public Set<LfBus> getLoadAndGeneratorBuses() {
        Set<LfBus> buses = new HashSet<>();
        for (var e : lostLoads.entrySet()) {
            LfLoad load = e.getKey();
            LfBus bus = load.getBus();
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
            int[] sortedBuses = disabledNetwork.getBuses().stream().mapToInt(LfBus::getNum).sorted().toArray();
            jsonGenerator.writeArray(sortedBuses, 0, sortedBuses.length);

            jsonGenerator.writeFieldName("branches");
            int[] sortedBranches = disabledNetwork.getBranches().stream().mapToInt(LfBranch::getNum).sorted().toArray();
            jsonGenerator.writeArray(sortedBranches, 0, sortedBranches.length);

            jsonGenerator.writeEndObject();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
