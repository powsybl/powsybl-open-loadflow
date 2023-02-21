/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class VoltageControl<T extends LfElement> extends Control {

    protected final LfBus controlledBus;

    protected final List<T> controllerElements = new ArrayList<>();

    protected VoltageControl(double targetValue, LfBus controlledBus) {
        super(targetValue);
        this.controlledBus = Objects.requireNonNull(controlledBus);
    }

    public LfBus getControlledBus() {
        return controlledBus;
    }

    public List<T> getControllerElements() {
        return controllerElements;
    }

    public void addControllerElement(T controllerElement) {
        controllerElements.add(Objects.requireNonNull(controllerElement));
    }

    private static void addVoltageControls(LfBus bus, List<VoltageControl<?>> voltageControls) {
        if (bus.isGeneratorVoltageControlled()) {
            voltageControls.add(bus.getGeneratorVoltageControl().orElseThrow());
        }
        if (bus.isTransformerVoltageControlled()) {
            voltageControls.add(bus.getTransformerVoltageControl().orElseThrow());
        }
        if (bus.isShuntVoltageControlled()) {
            voltageControls.add(bus.getShuntVoltageControl().orElseThrow());
        }
    }

    private static List<VoltageControl<?>> getVoltageControls(LfBus bus) {
        List<VoltageControl<?>> voltageControls = new ArrayList<>(1);
        LfZeroImpedanceNetwork zn = bus.getZeroImpedanceNetwork(false);
        if (zn != null) { // bus is part of a zero impedance graph
            for (LfBus zb : zn.getGraph().vertexSet()) { // all enabled by design
                // add controls for all buses of the zero impedance graph
                addVoltageControls(zb, voltageControls);
            }
        } else {
            addVoltageControls(bus, voltageControls);
        }
        if (voltageControls.size() > 1) {
            voltageControls.sort((vc1, vc2) -> {
                if (vc1.getClass() == vc2.getClass()) {
                    // sort by ID
                    return vc1.getControlledBus().getId().compareTo(vc2.getControlledBus().toString());
                } else {
                    // generator first, then transformer, then shunt
                    if (vc1 instanceof GeneratorVoltageControl) {
                        return -1;
                    } else if (vc1 instanceof TransformerVoltageControl) {
                        return vc2 instanceof GeneratorVoltageControl ? 1 : -1;
                    }
                    return 1;
                }
            });
        }
        return voltageControls;
    }

    private static boolean isVoltageControlled(LfBus bus, Class<? extends VoltageControl<?>> vcClass) {
        List<VoltageControl<?>> voltageControls = getVoltageControls(bus);
        if (voltageControls.isEmpty()) {
            return false;
        }
        VoltageControl<?> firstVc = voltageControls.get(0);
        return firstVc.getControlledBus() == bus && vcClass.isInstance(firstVc);
    }

    public static boolean isGeneratorVoltageControlled(LfBus bus) {
        return isVoltageControlled(bus, GeneratorVoltageControl.class);
    }

    public static boolean isTransformerVoltageControlled(LfBus bus) {
        return isVoltageControlled(bus, TransformerVoltageControl.class);
    }

    public static boolean isShuntVoltageControlled(LfBus bus) {
        return isVoltageControlled(bus, ShuntVoltageControl.class);
    }
}
