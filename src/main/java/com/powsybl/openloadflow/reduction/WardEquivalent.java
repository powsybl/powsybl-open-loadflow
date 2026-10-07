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
import com.powsybl.openloadflow.adm.*;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.impl.LfNetworkLoaderImpl;
import com.powsybl.openloadflow.reduction.WardReductionParameters.ReductionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * @author Romain Courtier {@literal <romain.courtier at rte-france.com>}
 */
public class WardEquivalent {

    private static final Logger LOGGER = LoggerFactory.getLogger(WardEquivalent.class);

    //TODO check if constructor needed
    //TODO javadoc
    public WardEquivalent() {
        // empty constructor
    }

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
        ZoneDecomposition zones = new ZoneDecomposition(lfNetwork, wardParameters.getInternalVoltageLevelIds());

        // Create the ward admittance matrix
        AdmittanceEquationSystem yEquationSystem = createEquationSystem(lfNetwork, wardParameters.getReductionType());
        WardAdmittance wardAdmittance = new WardAdmittance(yEquationSystem, zones, wardParameters.getMatrixFactory());
    }

    private AdmittanceEquationSystem createEquationSystem(LfNetwork lfNetwork, ReductionType reductionType) {
        //TODO depending on the reduction type, init the equation system differently
        return AdmittanceEquationSystem.create(lfNetwork, new VariableSet<>());
    }

}
