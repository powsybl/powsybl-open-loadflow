/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class LfSecondaryVoltageControl {

    private final String zoneName;

    private final LfBus pilotBus;

    private final Set<String> participatingControlUnitIds;

    private final Set<GeneratorVoltageControl> generatorVoltageControls;

    private double targetValue;

    public LfSecondaryVoltageControl(String zoneName, LfBus pilotBus, double targetValue, Set<String> participatingControlUnitIds,
                                     Set<GeneratorVoltageControl> generatorVoltageControls) {
        this.zoneName = Objects.requireNonNull(zoneName);
        this.pilotBus = Objects.requireNonNull(pilotBus);
        this.targetValue = targetValue;
        this.participatingControlUnitIds = Objects.requireNonNull(participatingControlUnitIds);
        this.generatorVoltageControls = Objects.requireNonNull(generatorVoltageControls);
    }

    public String getZoneName() {
        return zoneName;
    }

    public double getTargetValue() {
        return targetValue;
    }

    public void setTargetValue(double targetValue) {
        this.targetValue = targetValue;
    }

    public LfBus getPilotBus() {
        return pilotBus;
    }

    public Set<String> getParticipatingControlUnitIds() {
        return participatingControlUnitIds;
    }

    public Set<GeneratorVoltageControl> getGeneratorVoltageControls() {
        return generatorVoltageControls.stream()
                .filter(this::hasAtLeastOneParticipatingControlUnit) // only keep voltage controls where there is at list one enabled control unit
                .collect(Collectors.toSet());
    }

    private boolean hasAtLeastOneParticipatingControlUnit(GeneratorVoltageControl vc) {
        for (var controllerElement : vc.getMergedControllerElements()) {
            for (LfGenerator generator : controllerElement.getGenerators()) {
                if (participatingControlUnitIds.contains(generator.getId())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static List<LfBus> findControllerBuses(LfBus controlledBus) {
        return controlledBus.getGeneratorVoltageControl().orElseThrow()
                .getMergedControllerElements().stream()
                .filter(controllerBus -> !controllerBus.isDisabled())
                .toList();
    }

    public List<LfBus> getControlledBuses() {
        return getGeneratorVoltageControls().stream()
                .filter(voltageControl -> voltageControl.isVisible() && voltageControl.getMergeStatus() == VoltageControl.MergeStatus.MAIN)
                .map(VoltageControl::getControlledBus)
                .toList();
    }

    private static Optional<LfBus> findAnyControllerBusWithVoltageControlEnabled(LfBus controlledBus) {
        return findControllerBuses(controlledBus).stream()
                .filter(LfBus::isGeneratorVoltageControlEnabled)
                .findAny();
    }

    public Optional<LfBus> findAnyControlledBusWithAtLeastOneControllerBusWithVoltageControlEnabled() {
        return getControlledBuses().stream()
                .flatMap(controlledBus -> findAnyControllerBusWithVoltageControlEnabled(controlledBus).stream())
                .findAny();
    }

    public List<LfBus> getControllerBuses() {
        return getControlledBuses().stream()
                .flatMap(controlledBus -> findControllerBuses(controlledBus).stream())
                .toList();
    }

    public List<LfBus> getEnabledControllerBuses() {
        return getControllerBuses().stream()
                .filter(LfBus::isGeneratorVoltageControlEnabled)
                .toList();
    }
}
