/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import com.powsybl.commons.PowsyblException;

import java.util.*;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class VoltageControl<T extends LfElement> extends Control {

    public enum Type {
        GENERATOR,
        TRANSFORMER,
        SHUNT
    }

    public enum MergeStatus {
        MAIN,
        DEPENDENT
    }

    protected final Type type;

    protected int priority;

    protected final LfBus controlledBus;

    protected final List<T> controllerElements = new ArrayList<>();

    protected MergeStatus mergeStatus = MergeStatus.MAIN;

    protected final List<VoltageControl<T>> mergedDependentVoltageControls = new ArrayList<>();

    protected VoltageControl<T> mainMergedVoltageControl;

    protected VoltageControl(double targetValue, Type type, int priority, LfBus controlledBus) {
        super(targetValue);
        this.type = Objects.requireNonNull(type);
        this.priority = priority;
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

    public boolean isControllerEnabled(T controllerElement) {
        throw new IllegalStateException();
    }

    public List<VoltageControl<T>> getMergedDependentVoltageControls() {
        return mergedDependentVoltageControls;
    }

    protected int getPriority() {
        return priority;
    }

    public Type getType() {
        return type;
    }

    public boolean isDisabled() {
        if (controlledBus.isDisabled()) {
            return true;
        }
        return getMergedControllerElements().stream()
                .allMatch(LfElement::isDisabled);
    }

    public MergeStatus getMergeStatus() {
        return mergeStatus;
    }

    @SuppressWarnings("unchecked")
    public <E extends VoltageControl<T>> E getMainVoltageControl() {
        switch (mergeStatus) {
            case MAIN:
                return (E) this;
            case DEPENDENT:
                return (E) mainMergedVoltageControl;
            default:
                throw new IllegalStateException("Unknown merge status: " + mergeStatus);
        }
    }

    public List<T> getMergedControllerElements() {
        if (mergedDependentVoltageControls.isEmpty()) {
            return controllerElements;
        } else {
            List<T> mergedControllerElements = new ArrayList<>(controllerElements);
            for (var mvc : mergedDependentVoltageControls) {
                mergedControllerElements.addAll(mvc.getControllerElements());
            }
            return mergedControllerElements;
        }
    }

    private static void addMainVoltageControls(List<VoltageControl<?>> voltageControls, LfBus bus) {
        if (bus.isVoltageControlled()) {
            for (VoltageControl<?> vc : bus.getVoltageControls()) {
                if (vc.isDisabled() || vc.getMergeStatus() == MergeStatus.DEPENDENT) {
                    continue;
                }
                voltageControls.add(vc);
            }
        }
    }

    public static List<VoltageControl<?>> findMainVoltageControlsSortedByPriority(LfBus bus) {
        List<VoltageControl<?>> voltageControls = new ArrayList<>();
        LfZeroImpedanceNetwork zn = bus.getZeroImpedanceNetwork(LoadFlowModel.AC);
        if (zn != null) { // bus is part of a zero impedance graph
            for (LfBus zb : zn.getGraph().vertexSet()) { // all enabled by design
                addMainVoltageControls(voltageControls, zb);
            }
        } else {
            addMainVoltageControls(voltageControls, bus);
        }
        voltageControls.sort(Comparator.comparingInt(VoltageControl::getPriority));
        return voltageControls;
    }

    /**
     * FIXME: take into account controllers status to have a proper definition
     * For generator voltage control, isGeneratorVoltageControlEnabled() should be called.
     * For transformer voltage control, isVoltageControlEnabled() should be called.
     * For shunt voltage control, isVoltageControlEnabled() should be called.
     */
    public boolean isHidden() {
        // collect all voltage controls with the same controlled bus as this one and also all voltage controls coming
        // from merged ones
        List<VoltageControl<?>> mainVoltageControls = findMainVoltageControlsSortedByPriority(controlledBus);
        if (mainVoltageControls.isEmpty()) {
            return true; // means all disabled
        } else {
            // we should normally have max 3 voltage controls (one of each type) because already merged
            return mainVoltageControls.get(0) != this.getMainVoltageControl();
        }
    }

    public Optional<LfBus> getActiveControlledBus() {
        List<VoltageControl<?>> voltageControls = findMainVoltageControlsSortedByPriority(controlledBus);
        if (voltageControls.isEmpty()) {
            return Optional.empty(); // means all disabled
        }
        List<VoltageControl<?>> activeVoltageControls = voltageControls.stream().filter(vc -> !vc.isHidden()).toList();
        if (activeVoltageControls.size() == 1) {
            return Optional.of(activeVoltageControls.get(0).getControlledBus());
        } else {
            throw new PowsyblException("Several active controlled buses in a zero impedance network");
        }
    }

    @Override
    public String toString() {
        return "VoltageControl(type=" + type
                + ", controlledBus='" + controlledBus
                + "', controllerElements=" + controllerElements
                + ", mergeStatus=" + mergeStatus
                + ", mergedDependentVoltageControlsSize=" + mergedDependentVoltageControls.size()
                + ")";
    }
}
