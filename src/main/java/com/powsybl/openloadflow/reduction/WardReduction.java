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
import com.powsybl.openloadflow.ac.AcLoadFlowParameters;
import com.powsybl.openloadflow.graph.dtree.DTreeGraphConnectivityFactory;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.impl.LfNetworkLoaderImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * A network reduction by Ward equivalent.
 *
 * @author Romain Courtier {@literal <romain.courtier at rte-france.com>}
 */
public final class WardReduction {

    private static final Logger LOGGER = LoggerFactory.getLogger(WardReduction.class);

    private WardReduction() {
    }

    /**
     * Reduce the provided network using Ward equivalent based on specified reduction parameters.
     *
     * @param network the {@link Network} to be reduced
     * @param parameters the {@link WardReductionParameters} specifying the reduction process
     * @return The {@link Reducer.ReductionChanges} applied to the network
     */
    public static Reducer.ReductionChanges reduce(Network network, WardReductionParameters parameters) {
        // Create a LfNetwork
        LoadFlowParameters lfParameters = parameters.getLoadFlowParameters();
        OpenLoadFlowParameters lfParametersExt = OpenLoadFlowParameters.get(lfParameters);
        AcLoadFlowParameters acLfParameters = OpenLoadFlowParameters.createAcParameters(
            network, lfParameters, lfParametersExt, parameters.getMatrixFactory(), new DTreeGraphConnectivityFactory<>()
        );
        List<LfNetwork> lfNetworks = LfNetwork.load(network, new LfNetworkLoaderImpl(), acLfParameters.getNetworkParameters());
        if (lfNetworks.size() > 1) {
            LOGGER.warn("More than one LfNetwork found, only the first one will be used");
        }
        LfNetwork lfNetwork = lfNetworks.getFirst();

        // Compute internal, boundary and external zones
        ZoneDecomposition zones = new ZoneDecomposition(lfNetwork, parameters.getInternalVoltageLevelIds());

        // Calculate the Ward equivalent
        WardEquivalent wardEquivalent = new WardEquivalent(lfNetwork, zones, parameters.getEquivalentType(), parameters.getMatrixFactory());

        // Reduce the network with equivalents
        Reducer reducer = new Reducer(network, lfNetwork, zones);

        return reducer.reduceWithWardEquivalent(wardEquivalent);
    }

}
