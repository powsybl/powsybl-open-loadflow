package com.powsybl.openloadflow.network.impl;

import com.powsybl.iidm.network.*;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.util.Evaluable;

import java.util.HashSet;
import java.util.Set;

public class LfControlAreaImpl extends AbstractPropertyBag implements LfControlArea {

    private final LfNetwork network;
    private final Ref<Area> areaRef;

    private final double targetAcInterchange;

    private final Set<LfBus> buses;

    private Set<Evaluable> boundariesP;

    public LfControlAreaImpl(Area area, LfNetwork network, LfNetworkParameters parameters) {
        this.network = network;
        this.areaRef = Ref.create(area, parameters.isCacheEnabled());
        this.targetAcInterchange = area.getAcInterchangeTarget().orElse(Double.NaN);
        this.buses = new HashSet<>();
        this.boundariesP = new HashSet<>();
    }

    public static LfControlAreaImpl create(Area area, LfNetwork network, LfNetworkParameters parameters) {
        return new LfControlAreaImpl(area, network, parameters);
    }

    private Area getArea() {
        return areaRef.get();
    }

    @Override
    public String getId() {
        return getArea().getId();
    }

    @Override
    public double getTargetAcInterchange() {
        return targetAcInterchange;
    }

    @Override
    public double getAcInterchange() {
        return boundariesP.stream().mapToDouble(Evaluable::eval).sum();
    }

    @Override
    public Set<LfBus> getBuses() {
        return buses;
    }

    @Override
    public LfControlArea addBus(LfBus bus) {
        buses.add(bus);
        return this;
    }

    @Override
    public LfNetwork getNetwork() {
        return network;
    }

    @Override
    public LfControlArea addBoundaryP(Evaluable p) {
        boundariesP.add(p);
        return this;
    }

}
