/**
 * Copyright (c) 2024, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.sa;

import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.report.ReportNode;
import com.powsybl.contingency.Contingency;
import com.powsybl.openloadflow.LoadFlowParametersOverride;
import com.powsybl.openloadflow.network.LfContingency;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.security.SecurityAnalysisParameters;
import org.apache.commons.compress.utils.Lists;

import java.util.List;
import java.util.Objects;
import java.util.ServiceLoader;

/**
 * Handling of active power injection lost by contingency.
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public interface ContingencyActivePowerLossDistribution {

    static List<ContingencyActivePowerLossDistribution> findAll() {
        return Lists.newArrayList(ServiceLoader.load(ContingencyActivePowerLossDistribution.class, ContingencyActivePowerLossDistribution.class.getClassLoader()).iterator());
    }

    static ContingencyActivePowerLossDistribution find(String name) {
        Objects.requireNonNull(name);
        return findAll().stream().filter(asf -> name.equals(asf.getName()))
                .findFirst().orElseThrow(() -> new PowsyblException("ContingencyActivePowerLossDistribution '" + name + "' not found"));
    }

    String getName();

    /**
     * Called by the security analysis engine for each contingency. Pre-distributes active power imbalances created by contingencies (disconnection of loads, of generators, ...).
     * @param network the network
     * @param lfContingency the contingency in open-loadflow representation, including among others information about disconnected network elements, and how much active power has been lost
     * @param contingency the contingency definition
     * @param securityAnalysisParameters the security analysis parameters
     * @param loadFlowParametersOverride the contingency load flow parameters overrides if any
     * @param reportNode the contingency report node - so that the plugin may add any report message needed
     * @return the amount of distributed active power in per-unit
     */
    double run(LfNetwork network, LfContingency lfContingency, Contingency contingency, SecurityAnalysisParameters securityAnalysisParameters, LoadFlowParametersOverride loadFlowParametersOverride, ReportNode reportNode);

}
