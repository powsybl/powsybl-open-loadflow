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

    public enum MergeStatus {
        ALONE,
        MERGED_MAIN,
        MERGED_DEPENDENT
    }

    protected final LfBus controlledBus;

    protected final List<T> controllerElements = new ArrayList<>();

    protected final List<VoltageControl<T>> mergedVoltageControls = new ArrayList<>();

    protected MergeStatus mergeStatus = MergeStatus.ALONE;

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

    public MergeStatus getMergeStatus() {
        return mergeStatus;
    }

    public boolean isDisabled() {
        if (controlledBus.isDisabled()) {
            return true;
        }
        return controllerElements.stream()
                .allMatch(LfElement::isDisabled);
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
                vc.getMergedVoltageControls().clear();
            }
            if (voltageControls.size() > 1) {
                voltageControls.sort(Comparator.comparing(o -> o.getControlledBus().getId()));
                VoltageControl<T> mainVc = voltageControls.get(0);
                mainVc.mergeStatus = MergeStatus.MERGED_MAIN;
                // first one is main, the other have are dependents
                for (int i = 1; i < voltageControls.size(); i++) {
                    VoltageControl<T> vc = voltageControls.get(i);
                    vc.mergeStatus = MergeStatus.MERGED_DEPENDENT;
                    mainVc.getMergedVoltageControls().add(vc);
                }
            }
        } else {
            mergedVoltageControls.clear();
            mergeStatus = MergeStatus.ALONE;
        }
    }

    public List<T> getMergedControllerElements() {
        if (mergedVoltageControls.isEmpty()) {
            return controllerElements;
        } else {
            List<T> mergedControllerElements = new ArrayList<>(controllerElements);
            for (var mvc : mergedVoltageControls) {
                mergedControllerElements.addAll(mvc.getControllerElements());
            }
            return mergedControllerElements;
        }
    }

    public boolean isHidden() {
        // collect all voltage controls with the same controlled bus as this one and also all voltage controls coming
        // from merged ones
        List<VoltageControl<?>> voltageControls = new ArrayList<>();
        for (VoltageControl<?> vc : controlledBus.getVoltageControls()) {
            if (vc.isDisabled() || vc.getMergeStatus() == MergeStatus.MERGED_DEPENDENT) {
                continue;
            }
            voltageControls.add(vc);
            for (VoltageControl<?> mvc : vc.getMergedVoltageControls()) {
                if (mvc.isDisabled() || vc.getMergeStatus() == MergeStatus.MERGED_DEPENDENT) {
                    continue;
                }
                voltageControls.add(mvc);
            }
        }
        if (voltageControls.isEmpty()) {
            return true; // means all disabled
        }
        voltageControls.sort(Comparator.comparingInt(VoltageControl::getPriority));
        // we should normally have max 3 voltage controls (one of each type) because already merged
        if (voltageControls.size() > 1) {
            return voltageControls.get(0) != this;
        }
        return false;
    }
}
