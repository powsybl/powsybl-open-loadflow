package com.powsybl.openloadflow.network.impl;

import com.powsybl.iidm.network.*;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.util.Evaluable;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

public class LfAreaImpl extends AbstractPropertyBag implements LfArea {

    private final LfNetwork network;
    private final Ref<Area> areaRef;

    private final double interchangeTarget;

    private final Set<LfBus> buses;

    private Set<Supplier<Evaluable>> boundariesP;

    public LfAreaImpl(Area area, LfNetwork network, LfNetworkParameters parameters) {
        this.network = network;
        this.areaRef = Ref.create(area, parameters.isCacheEnabled());
        this.interchangeTarget = area.getInterchangeTarget().orElse(Double.NaN);
        this.buses = new HashSet<>();
        this.boundariesP = new HashSet<>();
    }

    public static LfAreaImpl create(Area area, LfNetwork network, LfNetworkParameters parameters) {
        return new LfAreaImpl(area, network, parameters);
    }

    private Area getArea() {
        return areaRef.get();
    }

    @Override
    public String getId() {
        return getArea().getId();
    }

    @Override
    public double getInterchangeTarget() {
        return interchangeTarget;
    }

    @Override
    public double getInterchange() {
        return boundariesP.stream().map(Supplier::get).mapToDouble(Evaluable::eval).sum();
    }

    @Override
    public Set<LfBus> getBuses() {
        return buses;
    }

    @Override
    public LfArea addBus(LfBus bus) {
        buses.add(bus);
        return this;
    }

    @Override
    public LfArea addBoundaryP(Supplier<Evaluable> getP) {
        boundariesP.add(getP);
        return this;
    }

    @Override
    public LfNetwork getNetwork() {
        return network;
    }

}
