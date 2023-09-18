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
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class LfContingency {

    private final String id;

    private final int index;

    private final int createdSynchronousComponentsCount;

    private final DisabledNetwork disabledNetwork;

    private final Map<LfShunt, AdmittanceShift> shuntsShift;

    private final Map<LfBus, PowerShift> busesLoadShift;

    private final Set<String> originalPowerShiftIds;

    private final Set<LfGenerator> lostGenerators;

    private double disconnectedLoadActivePower;

    private double disconnectedGenerationActivePower;

    private Set<String> disconnectedElementIds;

    public LfContingency(String id, int index, int createdSynchronousComponentsCount, DisabledNetwork disabledNetwork, Map<LfShunt, AdmittanceShift> shuntsShift,
                         Map<LfBus, PowerShift> busesLoadShift, Set<LfGenerator> lostGenerators, Set<String> originalPowerShiftIds) {
        this.id = Objects.requireNonNull(id);
        this.index = index;
        this.createdSynchronousComponentsCount = createdSynchronousComponentsCount;
        this.disabledNetwork = Objects.requireNonNull(disabledNetwork);
        this.shuntsShift = Objects.requireNonNull(shuntsShift);
        this.busesLoadShift = Objects.requireNonNull(busesLoadShift);
        this.lostGenerators = Objects.requireNonNull(lostGenerators);
        this.originalPowerShiftIds = Objects.requireNonNull(originalPowerShiftIds);
        this.disconnectedLoadActivePower = 0.0;
        this.disconnectedGenerationActivePower = 0.0;
        this.disconnectedElementIds = new HashSet<>();

        for (LfBus bus : disabledNetwork.getBuses()) {
            disconnectedLoadActivePower += bus.getLoadTargetP();
            disconnectedGenerationActivePower += bus.getGenerationTargetP();
            disconnectedElementIds.addAll(bus.getGenerators().stream().map(LfGenerator::getId).collect(Collectors.toList()));
            disconnectedElementIds.addAll(bus.getLoad().map(LfLoad::getOriginalIds).orElse(Collections.emptyList()));
            bus.getControllerShunt().ifPresent(shunt -> disconnectedElementIds.addAll(shunt.getOriginalIds()));
            bus.getShunt().ifPresent(shunt -> disconnectedElementIds.addAll(shunt.getOriginalIds()));
        }
        for (Map.Entry<LfBus, PowerShift> e : busesLoadShift.entrySet()) {
            disconnectedLoadActivePower += e.getValue().getActive();
        }
        for (LfGenerator generator : lostGenerators) {
            disconnectedGenerationActivePower += generator.getTargetP();
            disconnectedElementIds.add(generator.getId());
        }
        disconnectedElementIds.addAll(originalPowerShiftIds);
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

    public Map<LfBus, PowerShift> getBusesLoadShift() {
        return busesLoadShift;
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
        for (var e : busesLoadShift.entrySet()) {
            LfBus bus = e.getKey();
            PowerShift shift = e.getValue();
            bus.setLoadTargetP(bus.getLoadTargetP() - getUpdatedLoadP0(bus, balanceType, shift.getActive(), shift.getVariableActive()));
            bus.setLoadTargetQ(bus.getLoadTargetQ() - shift.getReactive());
            bus.getLoad().ifPresent(load -> { // it could be a LCC in contingency.
                Set<String> loadsIdsInContingency = originalPowerShiftIds.stream()
                        .distinct()
                        .filter(load.getOriginalIds()::contains)
                        .collect(Collectors.toSet());
                load.setAbsVariableTargetP(load.getAbsVariableTargetP() - Math.abs(shift.getVariableActive()));
                loadsIdsInContingency.forEach(loadId -> load.setOriginalLoadDisabled(loadId, true));
            });
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
            if (generator instanceof LfStaticVarCompensator lfStaticVarCompensator) {
                lfStaticVarCompensator.getStandByAutomatonShunt().ifPresent(svcShunt -> {
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
        }
    }

    private static double getUpdatedLoadP0(LfBus bus, LoadFlowParameters.BalanceType balanceType, double initialP0, double initialVariableActivePower) {
        double factor = bus.getLoad().map(load -> {
            if (load.getOriginalLoadCount() > 0) {
                if (balanceType == LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD) {
                    return Math.abs(initialP0) / load.getAbsVariableTargetP();
                } else if (balanceType == LoadFlowParameters.BalanceType.PROPORTIONAL_TO_CONFORM_LOAD) {
                    return initialVariableActivePower / load.getAbsVariableTargetP();
                }
            }
            return 0d;
        }).orElse(0d);
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
