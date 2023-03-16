/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class VoltageControl<T extends LfElement> extends Control {

    public enum Status {
        DISABLED,
        ENABLED,
        MERGED,
        SHADOWED
    }

    protected final LfBus controlledBus;

    protected final List<T> controllerElements = new ArrayList<>();

    protected final List<VoltageControl<T>> mergedVoltageControls = new ArrayList<>();

    protected Status status = null;

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

    public boolean isControllerEnabled(T controllerElement) {
        throw new IllegalStateException();
    }

    public List<VoltageControl<T>> getMergedVoltageControls() {
        return mergedVoltageControls;
    }

    protected boolean isControlledBySameControlType(LfBus bus) {
        throw new IllegalStateException();
    }

    protected VoltageControl<T> getControl(LfBus bus) {
        throw new IllegalStateException();
    }

    protected int getPriority() {
        throw new IllegalStateException();
    }

    private boolean isDisabled() {
        if (controlledBus.isDisabled()) {
            return true;
        }
        return controllerElements.stream()
                .filter(e -> !e.isDisabled())
                .noneMatch(this::isControllerEnabled);
    }

    public Status getStatus() {
        updateStatus();
        return status;
    }

    public void invalidateStatus() {
        status = null;
        for (var mvc : mergedVoltageControls) {
            mvc.status = null;
        }
    }

    public void updateStatus() {
        if (status != null) {
            return;
        }

        // init all statuses
        LfZeroImpedanceNetwork zn = controlledBus.getZeroImpedanceNetwork(false);
        if (zn != null) { // bus is part of a zero impedance graph
            for (LfBus zb : zn.getGraph().vertexSet()) { // all enabled by design
                initStatus(zb);
            }
        } else {
            initStatus(controlledBus);
        }

        merge();
        shadow();
    }

    private static void initStatus(LfBus bus) {
        bus.getGeneratorVoltageControl().ifPresent(VoltageControl::initStatus);
        bus.getShuntVoltageControl().ifPresent(VoltageControl::initStatus);
        bus.getTransformerVoltageControl().ifPresent(VoltageControl::initStatus);
    }

    public void initStatus() {
        // update status from local checks
        status = isDisabled() ? Status.DISABLED : Status.ENABLED;
    }

    public void merge() {
        LfZeroImpedanceNetwork zn = controlledBus.getZeroImpedanceNetwork(false);
        if (zn != null) { // bus is part of a zero impedance graph
            List<VoltageControl<T>> voltageControls = new ArrayList<>(1);
            for (LfBus zb : zn.getGraph().vertexSet()) { // all enabled by design
                if (isControlledBySameControlType(zb)) {
                    VoltageControl<T> zvc = getControl(zb);
                    if (zvc.status == Status.ENABLED) {
                        voltageControls.add(zvc);
                    }
                }
            }
            if (voltageControls.size() > 1) {
                voltageControls.sort(Comparator.comparing(o -> o.getControlledBus().getId()));
                VoltageControl<T> vc0 = voltageControls.get(0);
                vc0.getMergedVoltageControls().clear();
                // first one is enabled, the other have merged status
                for (int i = 1; i < voltageControls.size(); i++) {
                    VoltageControl<T> vc = voltageControls.get(i);
                    vc.status = Status.MERGED;
                    vc0.getMergedVoltageControls().add(vc);
                }
            }
        }
    }

    public void shadow() {
        LfZeroImpedanceNetwork zn = controlledBus.getZeroImpedanceNetwork(false);
        if (zn != null) { // bus is part of a zero impedance graph
            List<VoltageControl<?>> voltageControls = new ArrayList<>();
            for (LfBus zb : zn.getGraph().vertexSet()) { // all enabled by design
                // only keep ENABLED and discard DISABLED and MERGED
                if (zb.isGeneratorVoltageControlled()) {
                    GeneratorVoltageControl gvc = zb.getGeneratorVoltageControl().orElseThrow();
                    if (gvc.status == Status.ENABLED) {
                        voltageControls.add(gvc);
                    }
                }
                if (zb.isShuntVoltageControlled()) {
                    ShuntVoltageControl svc = zb.getShuntVoltageControl().orElseThrow();
                    if (svc.status == Status.ENABLED) {
                        voltageControls.add(svc);
                    }
                }
                if (zb.isTransformerVoltageControlled()) {
                    TransformerVoltageControl tvc = zb.getTransformerVoltageControl().orElseThrow();
                    if (tvc.status == Status.ENABLED) {
                        voltageControls.add(tvc);
                    }
                }
            }
            voltageControls.sort(Comparator.comparingInt(VoltageControl::getPriority));
            // we should normally have max 3 voltage controls (one of each type) because already merged
            if (voltageControls.size() > 1) {
                // just keep most prioritary one and flag the other one as SHADOWED
                for (int i = 1; i < voltageControls.size(); i++) {
                    voltageControls.get(i).status = Status.SHADOWED;
                }
            }
        }
    }
}
