/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.fastrestart;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.iidm.network.*;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.ac.AcLoadFlowContext;
import com.powsybl.openloadflow.ac.AcLoadFlowParameters;
import com.powsybl.openloadflow.ac.AcLoadFlowResult;
import com.powsybl.openloadflow.ac.AcloadFlowEngine;
import com.powsybl.openloadflow.ac.solver.AcSolverStatus;
import com.powsybl.openloadflow.lf.outerloop.OuterLoopResult;
import com.powsybl.openloadflow.network.LfNetwork;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class AcLoadFlowFromCache extends AbstractLoadFlowFromCache<AcLoadFlowParameters, AcLoadFlowContext> {

    public AcLoadFlowFromCache(Network network, LoadFlowParameters parameters, OpenLoadFlowParameters parametersExt,
                               AcLoadFlowParameters acParameters, ReportNode reportNode) {
        super(network, parameters, parametersExt, acParameters, reportNode);
    }

    private static AcLoadFlowResult run(AcLoadFlowContext context) {
        if (context.getNetwork().getValidity() != LfNetwork.Validity.VALID) {
            return AcLoadFlowResult.createNoCalculationResult(context.getNetwork());
        }
        if (context.getNetwork().isNetworkUpdated()) {
            AcLoadFlowResult result = new AcloadFlowEngine(context)
                    .run();
            context.getNetwork().setNetworkUpdated(false);
            return result;
        }
        return new AcLoadFlowResult(context.getNetwork(), 0, 0, AcSolverStatus.CONVERGED, OuterLoopResult.stable(), 0d, 0d);
    }

    public List<AcLoadFlowResult> run() {
        NetworkCache.Entry<AcLoadFlowContext> entry = NetworkCache.INSTANCE.getAc(network, parameters);
        List<AcLoadFlowContext> contexts = entry.getContexts();
        if (contexts == null) {
            contexts = initContexts(entry, n -> new AcLoadFlowContext(n, acOrDcParameters));
        }
        return contexts.stream()
                .map(AcLoadFlowFromCache::run)
                .collect(Collectors.toList());
    }
}
