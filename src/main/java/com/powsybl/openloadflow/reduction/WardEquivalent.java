/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.reduction;

import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.ac.AcLoadFlowContext;
import com.powsybl.openloadflow.ac.AcLoadFlowParameters;
import com.powsybl.openloadflow.ac.AcLoadFlowResult;
import com.powsybl.openloadflow.ac.AcloadFlowEngine;
import com.powsybl.openloadflow.adm.AdmittanceEquationSystem;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.impl.LfNetworkLoaderImpl;
import com.powsybl.openloadflow.reduction.WardReductionParameters.ReductionType;
import com.powsybl.openloadflow.reduction.ZonesDecomposition.Zone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static com.powsybl.openloadflow.reduction.ZonesDecomposition.computeZones;

/**
 * @author Romain Courtier {@literal <romain.courtier at rte-france.com>}
 */
public class WardEquivalent {

    private static final Logger LOGGER = LoggerFactory.getLogger(WardEquivalent.class);

    //TODO return status?
    //TODO javadoc
    public void reduce(Network network, WardReductionParameters wardParameters) {
        // Create a LfNetwork
        LoadFlowParameters lfParameters = wardParameters.getLoadFlowParameters();
        OpenLoadFlowParameters lfParametersExt = OpenLoadFlowParameters.get(lfParameters);
        AcLoadFlowParameters acLfParameters = OpenLoadFlowParameters.createAcParameters(network, lfParameters, lfParametersExt, wardParameters.getMatrixFactory(), wardParameters.getConnectivityFactory());
        List<LfNetwork> lfNetworks = LfNetwork.load(network, new LfNetworkLoaderImpl(), acLfParameters.getNetworkParameters());
        if (lfNetworks.size() > 1) {
            LOGGER.warn("More than one LfNetwork found, only the first one will be used");
        }
        LfNetwork lfNetwork = lfNetworks.getFirst();

        // Run an AC load flow and make sure the network is balanced
        AcLoadFlowContext acLfContext = new AcLoadFlowContext(lfNetwork, acLfParameters);
        AcLoadFlowResult acLfResult = new AcloadFlowEngine(acLfContext).run();
        if (!acLfResult.isSuccess()) {
            LOGGER.error("AC load flow failed. The network must be balanced to perform a reduction.");
            return;
        }

        // Compute internal, boundary and external zones
        Map<LfBus, Zone> busToZone = computeZones(lfNetwork, wardParameters.getInternalVoltageLevelIds());

        // Create admittance matrix
        createAdmittanceMatrix(lfNetwork, wardParameters.getReductionType());
    }

    private void createAdmittanceMatrix(LfNetwork lfNetwork, ReductionType reductionType) {
        // Create branches
        AdmittanceEquationSystem ySystem = AdmittanceEquationSystem.create(lfNetwork, new VariableSet<>());
    }
}
