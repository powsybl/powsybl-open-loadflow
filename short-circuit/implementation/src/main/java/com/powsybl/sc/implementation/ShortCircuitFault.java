/**
 * Copyright (c) 2022, Jean-Baptiste Heyberger & Geoffroy Jamgotchian
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.implementation;

import com.powsybl.sc.util.CalculationLocation;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class ShortCircuitFault {

    /**
     * Single bus fault.
     */
    public ShortCircuitFault(String busLocation, String faultId, String elementId, ShortCircuitFaultImpedance zf, ShortCircuitType type) {
        this.location = new CalculationLocation(busLocation);
        this.zf = zf;
        this.type = type;
        this.faultId = faultId;
        this.elementId = elementId;
        this.shortCircuitFaultType = ShortCircuitFaultType.BUS;
    }

    /**
     * Biphased common support fault, tying together two buses.
     */
    public ShortCircuitFault(String busLocation, String secondBiphasedBusLocation, String faultId, String elementId, ShortCircuitFaultImpedance zf,
            ShortCircuitType type, ShortCircuitBiphasedType biphasedType) {
        if (type != ShortCircuitType.BIPHASED_COMMON_SUPPORT) {
            throw new IllegalArgumentException("ShortCircuitType must be BIPHASED_COMMON_SUPPORT for a bi-phased common support fault, got" + type);
        }
        this.location = new CalculationLocation(busLocation, secondBiphasedBusLocation);
        this.zf = zf;
        this.type = type;
        this.faultId = faultId;
        this.elementId = elementId;
        this.biphasedType = biphasedType;
        this.shortCircuitFaultType = ShortCircuitFaultType.BUS;
    }

    /**
     * Branch fault between its two terminal buses.
     */
    public ShortCircuitFault(CalculationLocation location, String faultId, String elementId, ShortCircuitFaultImpedance zf, ShortCircuitType type) {
        this.location = location;
        this.zf = zf;
        this.type = type;
        this.faultId = faultId;
        this.elementId = elementId;
        this.shortCircuitFaultType = ShortCircuitFaultType.BRANCH;
    }

    public enum ShortCircuitFaultType {
        BUS,
        BRANCH
    }

    public enum ShortCircuitType {
        TRIPHASED_GROUND,
        BIPHASED,
        BIPHASED_GROUND,
        BIPHASED_COMMON_SUPPORT,
        MONOPHASED
    }

    public enum ShortCircuitBiphasedType {
        C1_C2,
        C1_B2,
        C1_A2
    }

    private final CalculationLocation location;

    private final String faultId;

    private final String elementId;

    private final ShortCircuitFaultImpedance zf; // the short circuit impedance Zf

    private final ShortCircuitType type;

    private final ShortCircuitFaultType shortCircuitFaultType;

    private ShortCircuitBiphasedType biphasedType;

    public ShortCircuitType getType() {
        return type;
    }

    public ShortCircuitFaultImpedance getZf() {
        return zf;
    }

    public ShortCircuitBiphasedType getBiphasedType() {
        return biphasedType;
    }

    public String getFaultId() {
        return faultId;
    }

    public ShortCircuitFaultType getShortCircuitFaultType() {
        return shortCircuitFaultType;
    }

    public CalculationLocation getCalculationLocation() {
        return location;
    }

    public String getElementId() {
        return elementId;
    }
}
