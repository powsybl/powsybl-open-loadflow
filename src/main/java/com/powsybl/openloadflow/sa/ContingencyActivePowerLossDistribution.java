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
import com.powsybl.openloadflow.network.LfContingency;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.security.SecurityAnalysisParameters;
import org.apache.commons.compress.utils.Lists;

import java.util.List;
import java.util.Objects;
import java.util.ServiceLoader;

/**
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

    void run(LfNetwork network, LfContingency lfContingency, SecurityAnalysisParameters securityAnalysisParameters, ReportNode reportNode);

}
