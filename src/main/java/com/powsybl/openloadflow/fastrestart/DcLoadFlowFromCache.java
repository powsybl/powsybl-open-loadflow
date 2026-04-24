/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
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
import com.powsybl.openloadflow.dc.DcLoadFlowContext;
import com.powsybl.openloadflow.dc.DcLoadFlowEngine;
import com.powsybl.openloadflow.dc.DcLoadFlowParameters;
import com.powsybl.openloadflow.dc.DcLoadFlowResult;
import com.powsybl.openloadflow.lf.outerloop.OuterLoopResult;
import com.powsybl.openloadflow.network.LfNetwork;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Sylvestre Prabakaran {@literal <sylvestre.prabakaran at rte-france.com>}
 */
public class DcLoadFlowFromCache extends AbstractLoadFlowFromCache<DcLoadFlowParameters, DcLoadFlowContext> {

    public DcLoadFlowFromCache(Network network, LoadFlowParameters parameters, OpenLoadFlowParameters parametersExt,
                               DcLoadFlowParameters dcParameters, ReportNode reportNode) {
        super(network, parameters, parametersExt, dcParameters, reportNode);
    }

    private static DcLoadFlowResult run(DcLoadFlowContext context) {
        if (context.getNetwork().getValidity() != LfNetwork.Validity.VALID) {
            return DcLoadFlowResult.createNoCalculationResult(context.getNetwork());
        }
        if (context.getNetwork().isNetworkUpdated()) {
            DcLoadFlowResult result = new DcLoadFlowEngine(context)
                    .run();
            context.getNetwork().setNetworkUpdated(false);
            return result;
        }
        return new DcLoadFlowResult(context.getNetwork(), 0, true, OuterLoopResult.stable(), 0d, 0d);
    }

    public List<DcLoadFlowResult> run() {
        NetworkCache.Entry<DcLoadFlowContext> entry = NetworkCache.INSTANCE.getDc(network, parameters);
        List<DcLoadFlowContext> contexts = entry.getContexts();
        if (contexts == null) {
            contexts = initContexts(entry, n -> new DcLoadFlowContext(n, acOrDcParameters));
        }
        return contexts.stream()
                .map(DcLoadFlowFromCache::run)
                .collect(Collectors.toList());
    }
}
