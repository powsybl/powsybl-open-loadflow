/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author Florian Dupuy {@literal <florian.dupuy at rte-france.com>}
 */
public class BusState extends BusDcState {

    private final double angle;
    private final double voltage;
    private final double generationTargetQ;
    private final boolean isGenerationTargetQFrozen;
    private final boolean voltageControlEnabled;
    private final Boolean shuntVoltageControlEnabled;
    private final Boolean reactiveControlEnabled;
    private final double shuntB;
    private final double shuntG;
    private final double controllerShuntB;
    private final double controllerShuntG;
    private final double svcShuntB;
    private final int[] controllerShuntPositions;
    private final int[] shuntPositions;
    private final Map<String, LfGenerator.GeneratorControlType> generatorsControlType;
    private final LfBus.QLimitType qLimitType;

    private static final class LoadState extends LoadDcState {

        private double loadTargetQ;

        @Override
        protected LoadDcState save(LfLoad load) {
            super.save(load);
            loadTargetQ = load.getTargetQ();
            return this;
        }

        @Override
        protected void restore(LfLoad load) {
            super.restore(load);
            load.setTargetQ(loadTargetQ);
        }
    }

    public BusState(LfBus bus) {
        super(bus);
        this.angle = bus.getAngle();
        this.voltage = bus.getV();
        this.generationTargetQ = bus.getGenerationTargetQ();
        this.isGenerationTargetQFrozen = bus.isGenerationTargetQFrozen();
        this.voltageControlEnabled = bus.isGeneratorVoltageControlEnabled();
        this.reactiveControlEnabled = bus.isGeneratorReactivePowerControlEnabled();
        LfShunt controllerShunt = bus.getControllerShunt().orElse(null);
        shuntVoltageControlEnabled = controllerShunt != null ? controllerShunt.isVoltageControlEnabled() : null;
        controllerShuntB = controllerShunt != null ? controllerShunt.getB() : Double.NaN;
        controllerShuntG = controllerShunt != null ? controllerShunt.getG() : Double.NaN;
        controllerShuntPositions = controllerShunt != null ? savePositions(controllerShunt) : null;
        LfShunt shunt = bus.getShunt().orElse(null);
        shuntB = shunt != null ? shunt.getB() : Double.NaN;
        shuntG = shunt != null ? shunt.getG() : Double.NaN;
        shuntPositions = shunt != null ? savePositions(shunt) : null;
        LfShunt svcShunt = bus.getSvcShunt().orElse(null);
        svcShuntB = svcShunt != null ? svcShunt.getB() : Double.NaN;
        this.generatorsControlType = bus.getGenerators().stream().collect(Collectors.toMap(LfGenerator::getId, LfGenerator::getGeneratorControlType));
        qLimitType = bus.getQLimitType().orElse(null);
    }

    @Override
    protected LoadDcState createLoadState() {
        return new LoadState();
    }

    @Override
    public void restore() {
        super.restore();
        element.setAngle(angle);
        element.setV(voltage);
        element.setGeneratorVoltageControlEnabledAndRecomputeTargetQ(voltageControlEnabled);
        if (isGenerationTargetQFrozen) {
            element.freezeGenerationTargetQAndDisableGeneratorVoltageControl(generationTargetQ);
        }
        element.setGeneratorReactivePowerControlEnabled(reactiveControlEnabled);
        if (shuntVoltageControlEnabled != null) {
            element.getControllerShunt().orElseThrow().setVoltageControlEnabled(shuntVoltageControlEnabled);
        }
        if (controllerShuntPositions != null) {
            restorePositions(element.getControllerShunt().orElseThrow(), controllerShuntPositions);
        }
        if (!Double.isNaN(controllerShuntB)) {
            element.getControllerShunt().orElseThrow().setB(controllerShuntB);
        }
        if (!Double.isNaN(controllerShuntG)) {
            element.getControllerShunt().orElseThrow().setG(controllerShuntG);
        }
        if (shuntPositions != null) {
            restorePositions(element.getShunt().orElseThrow(), shuntPositions);
        }
        if (!Double.isNaN(shuntB)) {
            element.getShunt().orElseThrow().setB(shuntB);
        }
        if (!Double.isNaN(shuntG)) {
            element.getShunt().orElseThrow().setG(shuntG);
        }
        if (!Double.isNaN(svcShuntB)) {
            element.getSvcShunt().orElseThrow().setB(svcShuntB);
        }
        element.getGenerators().forEach(g -> g.setGeneratorControlType(generatorsControlType.get(g.getId())));
        element.setQLimitType(qLimitType);
    }

    private static int[] savePositions(LfShunt shunt) {
        List<LfShunt.Controller> controllers = shunt.getControllers();
        if (controllers.isEmpty()) {
            return null;
        }
        int[] positions = new int[controllers.size()];
        for (int i = 0; i < positions.length; i++) {
            positions[i] = controllers.get(i).getPosition();
        }
        return positions;
    }

    private static void restorePositions(LfShunt shunt, int[] positions) {
        List<LfShunt.Controller> controllers = shunt.getControllers();
        for (int i = 0; i < positions.length; i++) {
            controllers.get(i).setPosition(positions[i]);
        }
    }

    public static BusState save(LfBus bus) {
        return new BusState(bus);
    }
}
