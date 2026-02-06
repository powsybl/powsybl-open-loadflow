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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Denis Bonnand {@literal <denis.bonnand at supergrid-institute.com>}
 */
public class LfAcDcNetwork extends LfNetwork {
    private final List<LfBus> acDcReferenceBuses = new ArrayList<>();
    private List<LfNetwork> acSubNetworks = new ArrayList<>();
    private List<LfNetwork> dcSubNetworks = new ArrayList<>();

    public LfAcDcNetwork(List<LfNetwork> acNetworks, List<LfNetwork> dcNetworks) {
        //TODO : find a better way to implement super class
        super(acNetworks.getFirst());

        if (acNetworks.size() > 1) {
            throw new PowsyblException("AC-DC load flow does not support multiple AC island for the moment");
        }

        for (LfNetwork network : acNetworks) {
            network.getBuses().forEach(this::addBus);
            network.getBranches().forEach(this::addBranch);
            network.getAreas().forEach(this::addArea);
            network.getHvdcs().forEach(this::addHvdc);
        }
        for (LfNetwork network : dcNetworks) {
            network.getDcNodes().forEach(this::addDcNode);
            network.getDcLines().forEach(this::addDcLine);

            // Check all DC nodes have the same nominal voltage
            Set<Double> dcVoltages = new HashSet<>();
            network.getDcNodes().forEach(dc_node -> dcVoltages.add(dc_node.getNominalV()));
            if (dcVoltages.size() > 1) {
                throw new PowsyblException("DC nodes in the same DC network must have the same nominal voltage but voltages " + dcVoltages + " were found");
            }
        }
        acSubNetworks = acNetworks;
        dcSubNetworks = dcNetworks;
    }

    @Override
    protected void invalidateSlackAndReference() {
        acSubNetworks.forEach(LfNetwork::invalidateSlackAndReference);
        dcSubNetworks.forEach(LfNetwork::invalidateSlackAndReference);
    }

    @Override
    public void updateSlackBusesAndReferenceBus() {
        if (!acSubNetworks.isEmpty()) {
            for (LfNetwork acSubNetwork : acSubNetworks) {
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
        //FIXME: which bus do we return ?
        return acDcReferenceBuses.getFirst();
    }

    public List<LfNetwork> getAcSubNetworks() {
        return acSubNetworks;
    }
}
