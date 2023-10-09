/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * @author Bertrand Rix <bertrand.rix at artelys.com>
 */
public class ReactivePowerControl<T extends LfElement> extends Control {

    public enum Type {
        GENERATOR,
    }

    public enum MergeStatus {
        MAIN,
        DEPENDENT
    }

    protected final ReactivePowerControl.Type type;

    protected int priority;

    protected final LfBranch controlledBranch;

    protected final ControlledSide controlledSide;

    protected final List<T> controllerElements = new ArrayList<>();

    protected MergeStatus mergeStatus = MergeStatus.MAIN;

    protected final List<ReactivePowerControl<T>> mergedDependentReactivePowerControls = new ArrayList<>();

    protected ReactivePowerControl<T> mainMergedReactivePowerControl;

    public ReactivePowerControl(double targetValue, Type type, int priority, LfBranch controlledBranch, ControlledSide controlledSide) {
        super(targetValue);
        this.type = Objects.requireNonNull(type);
        this.priority = priority;
        this.controlledBranch = Objects.requireNonNull(controlledBranch);
        this.controlledSide = Objects.requireNonNull(controlledSide);
    }

    public LfBranch getControlledBranch() {
        return controlledBranch;
    }

    public ControlledSide getControlledSide() {
        return controlledSide;
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

    public List<ReactivePowerControl<T>> getMergedDependentReactivePowerControls() {
        return mergedDependentReactivePowerControls;
    }

    protected int getPriority() {
        return priority;
    }

    public ReactivePowerControl.Type getType() {
        return type;
    }

    public List<T> getMergedControllerElements() { // TODO: add merged elements
        return controllerElements;
    }

    public ReactivePowerControl.MergeStatus getMergeStatus() {
        return mergeStatus;
    }

    public boolean isHidden() {
        return false;
    }

    public boolean isVisible() {
        return !isHidden();
    }
}
