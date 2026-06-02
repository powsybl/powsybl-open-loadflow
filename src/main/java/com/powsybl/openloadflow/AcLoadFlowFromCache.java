/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.iidm.network.*;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.openloadflow.ac.AcLoadFlowContext;
import com.powsybl.openloadflow.ac.AcLoadFlowParameters;
import com.powsybl.openloadflow.ac.AcLoadFlowResult;
import com.powsybl.openloadflow.ac.AcloadFlowEngine;
import com.powsybl.openloadflow.ac.solver.AcSolverStatus;
import com.powsybl.openloadflow.lf.outerloop.OuterLoopResult;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.LfTopoConfig;
import com.powsybl.openloadflow.network.impl.LfNetworkList;
import com.powsybl.openloadflow.network.impl.Networks;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class AcLoadFlowFromCache extends AbstractLoadFlowFromCache<AcLoadFlowParameters> {

    public AcLoadFlowFromCache(Network network, LoadFlowParameters parameters, OpenLoadFlowParameters parametersExt,
                               AcLoadFlowParameters acParameters, ReportNode reportNode) {
        super(network, parameters, parametersExt, acParameters, reportNode);
    }

    private List<NetworkCache.AcLfValue> initValues(NetworkCache.Entry<NetworkCache.LfInput, NetworkCache.AcLfValue> entry) {
        List<NetworkCache.AcLfValue> values;
        LfTopoConfig topoConfig = new LfTopoConfig();
        configureTopoConfig(topoConfig);

        // Because of caching, we only need to switch back to working variant but not to remove the variant, thus
        // WorkingVariantReverter is used instead of DefaultVariantCleaner
        try (LfNetworkList lfNetworkList = Networks.loadWithReconnectableElements(network, topoConfig, acOrDcParameters.getNetworkParameters(),
                LfNetworkList.WorkingVariantReverter::new, reportNode)) {
            values = lfNetworkList.getList()
                    .stream()
                    .map(n -> new NetworkCache.AcLfValue(new AcLoadFlowContext(n, acOrDcParameters)))
                    .toList();
            entry.setValues(values);
            LfNetworkList.VariantCleaner variantCleaner = lfNetworkList.getVariantCleaner();
            if (variantCleaner != null) {
                entry.setVariantCleaner(new LfNetworkList.DefaultVariantCleaner(network, entry.getWorkingVariantId(), variantCleaner.getTmpVariantId()));
            }
        }
        return values;
    }

    private static AcLoadFlowResult run(NetworkCache.AcLfValue value) {
        if (value.getNetwork().getValidity() != LfNetwork.Validity.VALID) {
            return AcLoadFlowResult.createNoCalculationResult(value.getNetwork());
        }
        if (value.isNetworkUpdated()) {
            AcLoadFlowResult result = new AcloadFlowEngine(value.getContext())
                    .run();
            value.setNetworkUpdated(false);
            return result;
        }
        return new AcLoadFlowResult(value.getNetwork(), 0, 0, AcSolverStatus.CONVERGED, OuterLoopResult.stable(), 0d, 0d);
    }

    public List<AcLoadFlowResult> run() {
        NetworkCache.Entry<NetworkCache.LfInput, NetworkCache.AcLfValue> entry = NetworkCache.AC_LF_INSTANCE.get(network, new NetworkCache.LfInput(parameters));
        List<NetworkCache.AcLfValue> values = entry.getValues();
        if (values == null) {
            values = initValues(entry);
        }
        return values.stream()
                .map(AcLoadFlowFromCache::run)
                .collect(Collectors.toList());
    }
}
