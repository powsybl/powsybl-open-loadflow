/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author Florian Dupuy <florian.dupuy at rte-france.com>
 */
public class BusState extends BusDcState {

    private final double angle;
    private final double voltage;
    private final double loadTargetQ;
    private final double generationTargetQ;
    private final boolean voltageControlEnabled;
    private final Boolean shuntVoltageControlEnabled;
    private final double shuntB;
    private final double shuntG;
    private final double controllerShuntB;
    private final double controllerShuntG;
    private final Map<String, LfGenerator.GeneratorControlType> generatorsControlType;

    public BusState(LfBus bus) {
        super(bus);
        this.angle = bus.getAngle();
        this.voltage = bus.getV();
        this.loadTargetQ = bus.getLoadTargetQ();
        this.generationTargetQ = bus.getGenerationTargetQ();
        this.voltageControlEnabled = bus.isVoltageControlEnabled();
        LfShunt controllerShunt = bus.getControllerShunt().orElse(null);
        shuntVoltageControlEnabled = controllerShunt != null ? controllerShunt.isVoltageControlEnabled() : null;
        controllerShuntB = controllerShunt != null ? controllerShunt.getB() : Double.NaN;
        controllerShuntG = controllerShunt != null ? controllerShunt.getG() : Double.NaN;
        LfShunt shunt = bus.getShunt().orElse(null);
        shuntB = shunt != null ? shunt.getB() : Double.NaN;
        shuntG = shunt != null ? shunt.getG() : Double.NaN;
        this.generatorsControlType = bus.getGenerators().stream().collect(Collectors.toMap(LfGenerator::getId, LfGenerator::getGeneratorControlType));
    }

    @Override
    public void restore() {
        super.restore();
        element.setAngle(angle);
        element.setV(voltage);
        element.setLoadTargetQ(loadTargetQ);
        element.setGenerationTargetQ(generationTargetQ);
        element.setVoltageControlEnabled(voltageControlEnabled);
        if (shuntVoltageControlEnabled != null) {
            element.getControllerShunt().orElseThrow().setVoltageControlEnabled(shuntVoltageControlEnabled);
        }
        if (!Double.isNaN(controllerShuntB)) {
            element.getControllerShunt().orElseThrow().setB(controllerShuntB);
        }
        if (!Double.isNaN(controllerShuntG)) {
            element.getControllerShunt().orElseThrow().setG(controllerShuntG);
        }
        if (!Double.isNaN(shuntB)) {
            element.getShunt().orElseThrow().setB(shuntB);
        }
        if (!Double.isNaN(shuntG)) {
            element.getShunt().orElseThrow().setG(shuntG);
        }
        element.getGenerators().forEach(g -> g.setGeneratorControlType(generatorsControlType.get(g.getId())));
    }

    public static BusState save(LfBus bus) {
        return new BusState(bus);
    }
}
