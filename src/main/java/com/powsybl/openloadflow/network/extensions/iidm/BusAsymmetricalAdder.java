/**
 * Copyright (c) 2023, Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 * Copyright (c) 2023, Geoffroy Jamgotchian <geoffroy.jamgotchian at gmail.com>
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network.extensions.iidm;

import com.powsybl.commons.extensions.AbstractExtensionAdder;
import com.powsybl.iidm.network.Bus;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class BusAsymmetricalAdder extends AbstractExtensionAdder<Bus, BusAsymmetrical> {

    private BusVariableType busVariableType = BusVariableType.WYE; // expected variables at bus depending on the configuration of the bus

    private boolean hasPhaseA = true; // phases that are present at bus
    private boolean hasPhaseB = true;
    private boolean hasPhaseC = true;
    private boolean isFortescueRepresentation = true;
    private boolean isPositiveSequenceAsCurrent = false;

    public BusAsymmetricalAdder(Bus bus) {
        super(bus);
    }

    @Override
    public Class<? super BusAsymmetrical> getExtensionClass() {
        return BusAsymmetrical.class;
    }

    @Override
    protected BusAsymmetrical createExtension(Bus bus) {
        return new BusAsymmetrical(bus, busVariableType, hasPhaseA, hasPhaseB, hasPhaseC, isFortescueRepresentation, isPositiveSequenceAsCurrent);
    }

    public BusAsymmetricalAdder withHasPhaseA(boolean hasPhaseA) {
        this.hasPhaseA = hasPhaseA;
        return this;
    }

    public BusAsymmetricalAdder withHasPhaseB(boolean hasPhaseB) {
        this.hasPhaseB = hasPhaseB;
        return this;
    }

    public BusAsymmetricalAdder withHasPhaseC(boolean hasPhaseC) {
        this.hasPhaseC = hasPhaseC;
        return this;
    }

    public BusAsymmetricalAdder withBusVariableType(BusVariableType busVariableType) {
        this.busVariableType = busVariableType;
        return this;
    }

    public BusAsymmetricalAdder withFortescueRepresentation(boolean isFortescueRepresentation) {
        this.isFortescueRepresentation = isFortescueRepresentation;
        return this;
    }

    public BusAsymmetricalAdder withPositiveSequenceAsCurrent(boolean isPositiveSequenceAsCurrent) {
        this.isPositiveSequenceAsCurrent = isPositiveSequenceAsCurrent;
        return this;
    }

}
