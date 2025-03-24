/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * Copyright (c) 2024, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.sa;

import com.google.auto.service.AutoService;
import com.powsybl.commons.report.ReportNode;
import com.powsybl.contingency.Contingency;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.openloadflow.LoadFlowParametersOverride;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.network.LfContingency;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.util.ActivePowerDistribution;
import com.powsybl.openloadflow.util.PerUnit;
import com.powsybl.openloadflow.util.Reports;
import com.powsybl.security.SecurityAnalysisParameters;

import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
@AutoService(ContingencyActivePowerLossDistribution.class)
public class DefaultContingencyActivePowerLossDistribution implements ContingencyActivePowerLossDistribution {

    @Override
    public String getName() {
        return "Default";
    }

    @Override
    public void run(LfNetwork network, LfContingency lfContingency, Contingency contingency, SecurityAnalysisParameters securityAnalysisParameters, LoadFlowParametersOverride loadFlowParametersOverride, ReportNode reportNode) {
        Objects.requireNonNull(network);
        Objects.requireNonNull(lfContingency);
        Objects.requireNonNull(contingency);
        Objects.requireNonNull(securityAnalysisParameters);
        Objects.requireNonNull(reportNode);
        LoadFlowParametersOverride paramsOverride = Objects.requireNonNullElse(loadFlowParametersOverride, LoadFlowParametersOverride.NO_OVERRIDE);
        double mismatch = lfContingency.getActivePowerLoss();
        LoadFlowParameters loadFlowParameters = securityAnalysisParameters.getLoadFlowParameters();
        OpenLoadFlowParameters openLoadFlowParameters = OpenLoadFlowParameters.get(loadFlowParameters);

        if ((paramsOverride.isDistributedSlack(loadFlowParameters) || paramsOverride.isAreaInterchangeControl(openLoadFlowParameters)) && Math.abs(mismatch) > 0) {
            ActivePowerDistribution activePowerDistribution = ActivePowerDistribution.create(paramsOverride.getBalanceType(loadFlowParameters), openLoadFlowParameters.isLoadPowerFactorConstant(), openLoadFlowParameters.isUseActiveLimits());
            var result = activePowerDistribution.run(network, mismatch);
            Reports.reportContingencyActivePowerLossDistribution(reportNode, mismatch * PerUnit.SB, result.remainingMismatch() * PerUnit.SB);
        }
    }

}
