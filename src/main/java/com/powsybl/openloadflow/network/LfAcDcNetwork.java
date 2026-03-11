/**
 * Copyright (c) 2025, SuperGrid Institute (http://www.supergrid-institute.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network;

import com.powsybl.commons.PowsyblException;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Denis Bonnand {@literal <denis.bonnand at supergrid-institute.com>}
 */
public class LfAcDcNetwork extends LfNetwork {
    private final List<LfBus> acDcReferenceBuses = new ArrayList<>();
    private final List<LfNetwork> acNetworks;
    private final List<LfNetwork> dcNetworks;

    public LfAcDcNetwork(List<LfNetwork> acNetworks, List<LfNetwork> dcNetworks) {
        // TODO : find a better way to implement super class
        super(acNetworks.getFirst());

        if (acNetworks.size() > 1) {
            throw new PowsyblException("AC-DC load flow does not support multiple synchronous components for the moment");
        }
        this.acNetworks = List.copyOf(acNetworks);
        this.dcNetworks = List.copyOf(dcNetworks);

        for (LfNetwork network : acNetworks) {
            network.getBuses().forEach(this::addBus);
            network.getBranches().forEach(this::addBranch);
            network.getAreas().forEach(this::addArea);
            network.getHvdcs().forEach(this::addHvdc);
        }
        for (LfNetwork network : dcNetworks) {
            network.getDcBuses().forEach(this::addDcBus);
            network.getDcLines().forEach(this::addDcLine);
        }

    }

    @Override
    protected void invalidateSlackAndReference() {
        acNetworks.forEach(LfNetwork::invalidateSlackAndReference);
        dcNetworks.forEach(LfNetwork::invalidateSlackAndReference);
    }

    @Override
    public void updateSlackBusesAndReferenceBus() {
        if (!acNetworks.isEmpty()) {
            for (LfNetwork acSubNetwork : acNetworks) {
                acSubNetwork.updateSlackBusesAndReferenceBus();
                for (LfBus bus : acSubNetwork.slackBuses) {
                    LfBus slackBus = this.getBusById(bus.getId());
                    if (!this.slackBuses.contains(slackBus)) {
                        slackBus.setSlack(true);
                        this.slackBuses.add(slackBus);
                    }
                }
                LfBus referenceBus = this.getBusById(acSubNetwork.referenceBus.getId());
                if (!acDcReferenceBuses.contains(referenceBus)) {
                    referenceBus.setReference(true);
                    this.acDcReferenceBuses.add(referenceBus);
                }
            }
        }
    }

    @Override
    public List<LfBus> getSlackBuses() {
        updateSlackBusesAndReferenceBus();
        return slackBuses;
    }

    @Override
    public LfBus getReferenceBus() {
        updateSlackBusesAndReferenceBus();
        // FIXME: which bus do we return ?
        return acDcReferenceBuses.getFirst();
    }
}
