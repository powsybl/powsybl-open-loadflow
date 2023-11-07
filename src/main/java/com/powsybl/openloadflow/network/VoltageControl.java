/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import java.util.*;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class VoltageControl<T extends LfElement> extends Control {

    public enum Type {
        GENERATOR,
        TRANSFORMER,
        SHUNT
    }

    public enum MergeStatus {
        MAIN,
        DEPENDENT,
        NOT_SUPPORTED
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

    /**
     * Check is the merged voltage to which this voltage control belongs is disabled. Disabled means that there is no
     * more controlled bus or no more controller element.
     * Having a disabled controlled among several controlled bus in a merge voltage control is an open question. Disabling
     * a controlled bus could also lead to removing the associated controller buses from the merge voltage control. The merge
     * status could be updated too.
     */
    public boolean isDisabled() {
        if (getMergedControlledBuses().stream().allMatch(LfElement::isDisabled)) {
            return true;
        }
        return getMergedControllerElements().stream()
                .allMatch(LfElement::isDisabled);
    }

    public void setNotSupported() {
        this.mergeStatus = MergeStatus.NOT_SUPPORTED;
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

    public List<LfBus> getMergedControlledBuses() {
        if (mergedDependentVoltageControls.isEmpty()) {
            return List.of(controlledBus);
        } else {
            List<LfBus> mergedControlledBuses = new ArrayList<>(1);
            mergedControlledBuses.add(controlledBus);
            for (var mvc : mergedDependentVoltageControls) {
                mergedControlledBuses.add(mvc.getControlledBus());
            }
            return mergedControlledBuses;
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

    /**
     * Find the list of voltage control with merge status as main, connected to a given bus (so including by traversing
     * non impedant branches).
     */
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
     * Check if the merged voltage to which this voltage control belongs is hidden by another one of a different type
     * (generator, transformer or shunt). The hidden status includes the disable status so a disable voltage control is
     * also hidden.
     *
     * FIXME: take into account controllers status to have a proper definition
     * For generator voltage control, isGeneratorVoltageControlEnabled() should be called.
     * For transformer voltage control, isVoltageControlEnabled() should be called.
     * For shunt voltage control, isVoltageControlEnabled() should be called.
     */
    public boolean isHidden() {
        // collect all main voltage controls connected the same controlled bus
        List<VoltageControl<?>> mainVoltageControls = findMainVoltageControlsSortedByPriority(controlledBus);
        if (mainVoltageControls.isEmpty()) {
            return true; // means all disabled
        } else {
            // we should normally have max 3 voltage controls (one of each type) with merge type as main
            // in order to this method work whatever the voltage control is main or dependent we check against
            // main voltage control of this
            return mainVoltageControls.get(0) != this.getMainVoltageControl();
        }
    }

    public boolean isVisible() {
        return !isHidden();
    }

    /**
     * Find controlled bus which is part of:
     *  - the visible voltage control
     *  - the main voltage control of the global merged one
     * This controlled bus is important because this is the one that will be targeted by a voltage equation in the
     * equation system.
     */
    public Optional<LfBus> findMainVisibleControlledBus() {
        List<VoltageControl<?>> mainVoltageControls = findMainVoltageControlsSortedByPriority(controlledBus);
        if (mainVoltageControls.isEmpty()) {
            return Optional.empty(); // means all disabled
        }
        List<VoltageControl<?>> visibleMainVoltageControls = mainVoltageControls.stream().filter(VoltageControl::isVisible).toList();
        if (visibleMainVoltageControls.size() == 1) {
            return Optional.of(visibleMainVoltageControls.get(0).getControlledBus());
        } else {
            throw new IllegalStateException("Several visible controlled buses, it should not happen");
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
