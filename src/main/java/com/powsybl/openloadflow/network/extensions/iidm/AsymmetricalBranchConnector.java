/**
 * Copyright (c) 2023, Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 * Copyright (c) 2023, Geoffroy Jamgotchian <geoffroy.jamgotchian at gmail.com>
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network.extensions.iidm;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class AsymmetricalBranchConnector {

    private BusVariableType busVariableType; // expected variables at bus depending on the configuration of the bus: mainly wye or delta types : delta = {vab, vbc, vca} and wye = {va, vb, vc}
    private boolean isPositiveSequenceAsCurrent; // if true, the checksum at bus in the positive sequence is current and not power
    private boolean isFortescueRepresentation; // if true: we use fortescue sequence in the equation setting, if false: three phase variables

    private boolean hasPhaseA; // phases that are present at bus
    private boolean hasPhaseB;
    private boolean hasPhaseC;

    public AsymmetricalBranchConnector(BusVariableType busVariableType, boolean hasPhaseA, boolean hasPhaseB, boolean hasPhaseC, boolean isFortescueRepresentation, boolean isPositiveSequenceAsCurrent) {

        this.busVariableType = busVariableType;
        this.hasPhaseA = hasPhaseA;
        this.hasPhaseB = hasPhaseB;
        this.hasPhaseC = hasPhaseC;
        this.isFortescueRepresentation = isFortescueRepresentation;
        this.isPositiveSequenceAsCurrent = isPositiveSequenceAsCurrent;
    }

    public BusVariableType getBusVariableType() {
        return busVariableType;
    }

    public boolean isHasPhaseA() {
        return hasPhaseA;
    }

    public boolean isHasPhaseB() {
        return hasPhaseB;
    }

    public boolean isHasPhaseC() {
        return hasPhaseC;
    }

    public boolean isFortescueRepresentation() {
        return isFortescueRepresentation;
    }

    public boolean isPositiveSequenceAsCurrent() {
        return isPositiveSequenceAsCurrent;
    }
}