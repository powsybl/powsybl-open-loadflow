package com.powsybl.openloadflow.network.extensions.iidm;

import com.powsybl.commons.extensions.AbstractExtensionAdder;
import com.powsybl.iidm.network.Bus;

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
