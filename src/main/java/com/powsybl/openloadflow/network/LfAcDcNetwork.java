/**
 * Copyright (c) 2025, SuperGrid Institute (http://www.supergrid-institute.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network;

import com.powsybl.commons.report.ReportNode;

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

        this.acNetworks = List.copyOf(acNetworks);
        this.dcNetworks = List.copyOf(dcNetworks);

        // Add LfElements in the LfAcDcNetwork. Their number is therefore updated to match the global LfAcDcNetwork object.
        // However their getNetwork() method still returns the original LfNetwork they belong to.
        // Having all elements in the LfAcDcNetwork allows to simulate load flow the whole connected network
        // The attributes acNetworks and dcNetworks also allow to access a "child" etwork individually.
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
    public void addListener(LfNetworkListener listener) {
        // LfElements getNetwork() method returns the original LfNetwork they belong to.
        // Therefore, any listener attached to the LfAcDcNetwork should be attached to the "children" LfNetworks in order
        // to be triggered when accessing the original LfNetwork listeners through an LfElement via the method getNetwork()
        super.addListener(listener);
        for (LfNetwork acNetwork : acNetworks) {
            acNetwork.addListener(listener);
        }
        for (LfNetwork dcNetwork : dcNetworks) {
            dcNetwork.addListener(listener);
        }
    }

    @Override
    public void validate(LoadFlowModel loadFlowModel, ReportNode reportNode) {
        validity = Validity.VALID;
        for (LfNetwork acNetwork : acNetworks) {
            acNetwork.validate(loadFlowModel, reportNode);
            Validity acNetworkValidity = acNetwork.getValidity();
            if (acNetworkValidity != Validity.VALID) {
                validity = acNetworkValidity;
                break;
            }
        }
    }

    @Override
    protected void invalidateSlackAndReference() {
        slackBuses.clear();
        acDcReferenceBuses.clear();
        acNetworks.forEach(LfNetwork::invalidateSlackAndReference);
        dcNetworks.forEach(LfNetwork::invalidateSlackAndReference);
    }

    @Override
    public void updateSlackBusesAndReferenceBus() {
        if (!acNetworks.isEmpty()) {
            for (LfNetwork acNetwork : acNetworks) {
                acNetwork.updateSlackBusesAndReferenceBus();
                for (LfBus bus : acNetwork.slackBuses) {
                    LfBus slackBus = this.getBusById(bus.getId());
                    if (!this.slackBuses.contains(slackBus)) {
                        this.slackBuses.add(slackBus);
                    }
                }
                LfBus referenceBus = this.getBusById(acNetwork.referenceBus.getId());
                if (!acDcReferenceBuses.contains(referenceBus)) {
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

    public int getSynchronousComponentCount() {
        return acNetworks.size();
    }

    public List<LfNetwork> getAcNetworks() {
        return acNetworks;
    }
}
