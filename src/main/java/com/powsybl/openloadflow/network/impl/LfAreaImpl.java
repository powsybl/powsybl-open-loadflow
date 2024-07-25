/**
 * Copyright (c) 2024, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.iidm.network.*;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.util.Evaluable;
import com.powsybl.openloadflow.util.PerUnit;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

/**
 * @author Valentin Mouradian {@literal <valentin.mouradian at artelys.com>}
 */
public class LfAreaImpl extends AbstractPropertyBag implements LfArea {

    private final LfNetwork network;
    private final Ref<Area> areaRef;

    private final double interchangeTarget;

    private final Set<LfBus> buses;

    private Set<Supplier<Evaluable>> boundariesP;

    public LfAreaImpl(Area area, LfNetwork network, LfNetworkParameters parameters) {
        this.network = network;
        this.areaRef = Ref.create(area, parameters.isCacheEnabled());
        this.interchangeTarget = area.getInterchangeTarget().orElse(Double.NaN) / PerUnit.SB;
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
    public void addBus(LfBus bus) {
        buses.add(bus);
    }

    @Override
    public void addBoundaryP(Supplier<Evaluable> getP) {
        boundariesP.add(getP);
    }

    @Override
    public LfNetwork getNetwork() {
        return network;
    }

}
