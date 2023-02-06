/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.iidm.network.Country;
import com.powsybl.iidm.network.ThreeWindingsTransformer;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.LfNetworkStateUpdateParameters;

import java.util.List;
import java.util.Optional;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class LfStarBus extends AbstractLfBus {

    private final Ref<ThreeWindingsTransformer> t3wtRef;

    private final double nominalV;
    private final Country country;

    public LfStarBus(LfNetwork network, ThreeWindingsTransformer t3wt, Country country) {
        super(network, Networks.getPropertyV(t3wt), Networks.getPropertyAngle(t3wt), false);
        this.t3wtRef = new Ref<>(t3wt);
        nominalV = t3wt.getRatedU0();
        this.country = country;
    }

    private ThreeWindingsTransformer getT3wt() {
        return t3wtRef.get();
    }

    public static String getId(String id) {
        return id + "_BUS0";
    }

    @Override
    public String getId() {
        return getId(getT3wt().getId());
    }

    @Override
    public List<String> getOriginalIds() {
        return List.of(getT3wt().getId());
    }

    @Override
    public String getVoltageLevelId() {
        return getT3wt().getLeg1().getTerminal().getVoltageLevel().getId();
    }

    @Override
    public boolean isFictitious() {
        return true;
    }

    @Override
    public double getNominalV() {
        return nominalV;
    }

    @Override
    public void updateState(LfNetworkStateUpdateParameters parameters) {
        var t3wt = getT3wt();
        Networks.setPropertyV(t3wt, v);
        Networks.setPropertyAngle(t3wt, angle);

        super.updateState(parameters);
    }

    @Override
    public Optional<Country> getCountry() {
        return Optional.ofNullable(country);
    }
}
