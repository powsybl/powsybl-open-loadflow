/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

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

    public void setMainMergedVoltageControl(VoltageControl<T> mainMergedVoltageControl) {
        this.mainMergedVoltageControl = mainMergedVoltageControl;
    }

    protected boolean isControlledBySameControlType(LfBus bus) {
        throw new IllegalStateException();
    }

    protected VoltageControl<T> getControl(LfBus bus) {
        throw new IllegalStateException();
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
        return controllerElements.stream()
                .allMatch(LfElement::isDisabled);
    }

    public MergeStatus getMergeStatus() {
        return mergeStatus;
    }

    public void updateMergeStatus() {
        LfZeroImpedanceNetwork zn = controlledBus.getZeroImpedanceNetwork(false);
        if (zn != null) { // bus is part of a zero impedance graph
            List<VoltageControl<T>> voltageControls = new ArrayList<>(1);
            for (LfBus zb : zn.getGraph().vertexSet()) { // all enabled by design
                if (isControlledBySameControlType(zb)) {
                    VoltageControl<T> zvc = getControl(zb);
                    voltageControls.add(zvc);
                }
            }
            for (VoltageControl<T> vc : voltageControls) {
                vc.getMergedDependentVoltageControls().clear();
                vc.setMainMergedVoltageControl(null);
                vc.mergeStatus = MergeStatus.MAIN;
            }
            if (voltageControls.size() > 1) {
                // we take the highest target voltage (why not...) and in case of equality the voltage control
                // with the first controlled bus ID by alpha sort
                voltageControls.sort(Comparator.<VoltageControl<?>>comparingDouble(VoltageControl::getTargetValue)
                        .reversed()
                        .thenComparing(o -> o.getControlledBus().getId()));
                VoltageControl<T> mainVc = voltageControls.get(0);
                // first one is main, the other have are dependents
                for (int i = 1; i < voltageControls.size(); i++) {
                    VoltageControl<T> vc = voltageControls.get(i);
                    vc.mergeStatus = MergeStatus.DEPENDENT;
                    mainVc.getMergedDependentVoltageControls().add(vc);
                    vc.setMainMergedVoltageControl(mainVc);
                }
            }
        } else {
            mergedDependentVoltageControls.clear();
            mainMergedVoltageControl = null;
            mergeStatus = MergeStatus.MAIN;
        }
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

    private static void addVoltageControls(List<VoltageControl<?>> voltageControls, LfBus bus) {
        if (bus.isVoltageControlled()) {
            for (VoltageControl<?> vc : bus.getVoltageControls()) {
                if (vc.isDisabled() || vc.getMergeStatus() == MergeStatus.DEPENDENT) {
                    continue;
                }
                voltageControls.add(vc);
            }
        }
    }

    public static List<VoltageControl<?>> findVoltageControlsSortedByPriority(LfBus bus) {
        List<VoltageControl<?>> voltageControls = new ArrayList<>();
        LfZeroImpedanceNetwork zn = bus.getZeroImpedanceNetwork(false);
        if (zn != null) { // bus is part of a zero impedance graph
            for (LfBus zb : zn.getGraph().vertexSet()) { // all enabled by design
                addVoltageControls(voltageControls, zb);
            }
        } else {
            addVoltageControls(voltageControls, bus);
        }
        voltageControls.sort(Comparator.comparingInt(VoltageControl::getPriority));
        return voltageControls;
    }

    public boolean isHidden() {
        // collect all voltage controls with the same controlled bus as this one and also all voltage controls coming
        // from merged ones
        List<VoltageControl<?>> voltageControls = findVoltageControlsSortedByPriority(controlledBus);
        if (voltageControls.isEmpty()) {
            return true; // means all disabled
        }
        // we should normally have max 3 voltage controls (one of each type) because already merged
        if (voltageControls.size() > 1) {
            return voltageControls.get(0) != this;
        }
        return false;
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
