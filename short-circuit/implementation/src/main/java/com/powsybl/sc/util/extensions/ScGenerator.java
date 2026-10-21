/**
 * Copyright (c) 2022, Jean-Baptiste Heyberger & Geoffroy Jamgotchian
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.util.extensions;

import org.apache.commons.math3.complex.Complex;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class ScGenerator {

    public enum MachineType {
        SYNCHRONOUS_GEN,
        ASYNCHRONOUS_GEN,
        SYNCHRONOUS_MOTOR,
        ASYNCHRONOUS_MOTOR
    }

    private final Complex transZd;

    private final Complex subTransZd;

    private final Complex stepUpTfoZ;

    private final boolean grounded;
    private final Complex groundZ;

    private final Complex zo;

    private final MachineType machineType;

    public ScGenerator(Complex transZd, Complex stepUpTfoZ, MachineType machineType, Complex subTransZd,
                       boolean grounded, Complex groundZ, Complex zo) {
        this.transZd = transZd;
        this.stepUpTfoZ = stepUpTfoZ;
        this.machineType = machineType;
        this.subTransZd = subTransZd;
        this.grounded = grounded;
        this.groundZ = groundZ;
        this.zo = zo;
    }

    public boolean isGrounded() {
        return grounded;
    }

    public Complex getStepUpTfoZ() {
        return stepUpTfoZ;
    }

    public Complex getZo() {
        return zo;
    }

    public Complex getTransZd() {
        return transZd;
    }

    public Complex getSubTransZd() {
        return subTransZd;
    }
}
