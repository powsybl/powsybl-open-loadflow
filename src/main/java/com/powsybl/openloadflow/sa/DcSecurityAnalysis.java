/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.sa;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.dc.*;
import com.powsybl.openloadflow.dc.equations.DcEquationType;
import com.powsybl.openloadflow.dc.equations.DcVariableType;
import com.powsybl.openloadflow.graph.GraphConnectivityFactory;
import com.powsybl.openloadflow.lf.outerloop.config.AbstractDcOuterLoopConfig;
import com.powsybl.openloadflow.lf.outerloop.config.DcOuterLoopConfig;
import com.powsybl.openloadflow.lf.outerloop.config.DefaultDcOuterLoopConfig;
import com.powsybl.openloadflow.lf.outerloop.config.ExplicitDcOuterLoopConfig;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.sa.extensions.ContingencyLoadFlowParameters;
import com.powsybl.openloadflow.util.Reports;
import com.powsybl.security.PostContingencyComputationStatus;
import com.powsybl.security.monitor.StateMonitor;

import java.util.List;
import java.util.function.Consumer;

public class DcSecurityAnalysis extends AbstractSecurityAnalysis<DcVariableType, DcEquationType, DcLoadFlowParameters, DcLoadFlowContext, DcLoadFlowResult> {

    protected DcSecurityAnalysis(Network network, MatrixFactory matrixFactory, GraphConnectivityFactory<LfBus, LfBranch> connectivityFactory,
                                 List<StateMonitor> stateMonitors, ReportNode reportNode) {
        super(network, matrixFactory, connectivityFactory, stateMonitors, reportNode);
    }

    @Override
    protected LoadFlowModel getLoadFlowModel() {
        return LoadFlowModel.DC;
    }

    @Override
    protected ReportNode createSaRootReportNode() {
        return Reports.createDcSecurityAnalysis(reportNode, network.getId());
    }

    @Override
    protected boolean isShuntCompensatorVoltageControlOn(LoadFlowParameters lfParameters) {
        return false;
    }

    @Override
    protected DcLoadFlowParameters createParameters(LoadFlowParameters lfParameters, OpenLoadFlowParameters lfParametersExt, boolean breakers, boolean areas) {
        var dcParameters = OpenLoadFlowParameters.createDcParameters(network, lfParameters,
                lfParametersExt, matrixFactory, connectivityFactory, false);
        dcParameters.getNetworkParameters()
                .setBreakers(breakers)
                .setCacheEnabled(false) // force not caching as not supported in secu analysis
                .setReferenceBusSelector(ReferenceBusSelector.DEFAULT_SELECTOR) // not supported yet
                .setAreaInterchangeControl(areas);
        return dcParameters;
    }

    @Override
    protected DcLoadFlowContext createLoadFlowContext(LfNetwork lfNetwork, DcLoadFlowParameters parameters) {
        return new DcLoadFlowContext(lfNetwork, parameters);
    }

    @Override
    protected DcLoadFlowEngine createLoadFlowEngine(DcLoadFlowContext context) {
        return new DcLoadFlowEngine(context);
    }

    @Override
    protected PostContingencyComputationStatus postContingencyStatusFromLoadFlowResult(DcLoadFlowResult result) {
        return result.isSuccess() ? PostContingencyComputationStatus.CONVERGED : PostContingencyComputationStatus.FAILED;
    }

    @Override
    protected Consumer<DcLoadFlowParameters> createParametersResetter(DcLoadFlowParameters parameters) {
        boolean oldDistributedSlack = parameters.isDistributedSlack();
        LoadFlowParameters.BalanceType oldBalanceType = parameters.getBalanceType();
        List<DcOuterLoop> oldOuterLoops = List.copyOf(parameters.getOuterLoops());
        return p -> {
            p.setDistributedSlack(oldDistributedSlack);
            p.setBalanceType(oldBalanceType);
            p.setOuterLoops(oldOuterLoops);
        };
    }

    @Override
    protected void applyContingencyParameters(DcLoadFlowParameters parameters, ContingencyLoadFlowParameters contingencyParameters, LoadFlowParameters loadFlowParameters, OpenLoadFlowParameters openLoadFlowParameters) {
        DcOuterLoopConfig outerLoopConfig = AbstractDcOuterLoopConfig.getOuterLoopConfig()
                .orElseGet(() -> contingencyParameters.getOuterLoopNames().isPresent() ? new ExplicitDcOuterLoopConfig()
                        : new DefaultDcOuterLoopConfig());
        parameters.setOuterLoops(outerLoopConfig.configure(loadFlowParameters, openLoadFlowParameters, contingencyParameters));
        contingencyParameters.isDistributedSlack().ifPresent(parameters::setDistributedSlack);
        contingencyParameters.getBalanceType().ifPresent(parameters::setBalanceType);
    }
}
