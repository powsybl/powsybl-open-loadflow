/**
 * Copyright (c) 2023, Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 * Copyright (c) 2023, Geoffroy Jamgotchian <geoffroy.jamgotchian at gmail.com>
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network.extensions.iidm;

import com.powsybl.commons.extensions.AbstractExtension;
import com.powsybl.iidm.network.Bus;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class BusAsymmetrical extends AbstractExtension<Bus> {

    public static final String NAME = "busAsymmetrical";

    private BusVariableType busVariableType; // expected variables at bus depending on the configuration of the bus: mainly wye or delta types : delta = {vab, vbc, vca} and wye = {va, vb, vc}
    private boolean isPositiveSequenceAsCurrent; // if true, the checksum at bus in the positive sequence is current and not power
    private boolean isFortescueRepresentation; // if true: we use fortescue sequence in the equation setting, if false: three phase variables

    private boolean hasPhaseA; // phases that are present at bus
    private boolean hasPhaseB;
    private boolean hasPhaseC;

    @Override
    public String getName() {
        return NAME;
    }

    public BusAsymmetrical(Bus bus, BusVariableType busVariableType, boolean hasPhaseA, boolean hasPhaseB, boolean hasPhaseC, boolean isFortescueRepresentation, boolean isPositiveSequenceAsCurrent) {

        super(bus);
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

    public void setPositiveSequenceAsCurrent(boolean positiveSequenceAsCurrent) {
        isPositiveSequenceAsCurrent = positiveSequenceAsCurrent;
    }

    public void setFortescueRepresentation(boolean fortescueRepresentation) {
        isFortescueRepresentation = fortescueRepresentation;
    }

    public void setHasPhaseA(boolean hasPhaseA) {
        this.hasPhaseA = hasPhaseA;
    }

    public void setHasPhaseB(boolean hasPhaseB) {
        this.hasPhaseB = hasPhaseB;
    }

    public void setHasPhaseC(boolean hasPhaseC) {
        this.hasPhaseC = hasPhaseC;
    }
}
