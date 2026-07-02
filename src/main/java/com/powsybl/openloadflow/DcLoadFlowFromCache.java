/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.iidm.network.*;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.openloadflow.dc.DcLoadFlowContext;
import com.powsybl.openloadflow.dc.DcLoadFlowEngine;
import com.powsybl.openloadflow.dc.DcLoadFlowParameters;
import com.powsybl.openloadflow.dc.DcLoadFlowResult;
import com.powsybl.openloadflow.lf.outerloop.OuterLoopResult;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.LfTopoConfig;
import com.powsybl.openloadflow.network.impl.LfNetworkList;
import com.powsybl.openloadflow.network.impl.Networks;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Sylvestre Prabakaran {@literal <sylvestre.prabakaran at rte-france.com>}
 */
public class DcLoadFlowFromCache extends AbstractLoadFlowFromCache<DcLoadFlowParameters> {

    public DcLoadFlowFromCache(Network network, LoadFlowParameters parameters, OpenLoadFlowParameters parametersExt,
                               DcLoadFlowParameters dcParameters, ReportNode reportNode) {
        super(network, parameters, parametersExt, dcParameters, reportNode);
    }

    private List<NetworkCache.DcLfValue> initValues(NetworkCache.Entry<NetworkCache.LfInput, NetworkCache.DcLfValue> entry) {
        List<NetworkCache.DcLfValue> values;
        LfTopoConfig topoConfig = new LfTopoConfig();
        configureTopoConfig(topoConfig);

        // Because of caching, we only need to switch back to working variant but not to remove the variant, thus
        // WorkingVariantReverter is used instead of DefaultVariantCleaner
        try (LfNetworkList lfNetworkList = Networks.loadWithReconnectableElements(network, topoConfig, acOrDcParameters.getNetworkParameters(),
                LfNetworkList.WorkingVariantReverter::new, reportNode)) {
            values = lfNetworkList.getList()
                    .stream()
                    .map(n -> new NetworkCache.DcLfValue(new DcLoadFlowContext(n, acOrDcParameters)))
                    .toList();
            entry.setValues(values);
            LfNetworkList.VariantCleaner variantCleaner = lfNetworkList.getVariantCleaner();
            if (variantCleaner != null) {
                entry.setVariantCleaner(new LfNetworkList.DefaultVariantCleaner(network, entry.getWorkingVariantId(), variantCleaner.getTmpVariantId()));
            }
        }
        return values;
    }

    private static DcLoadFlowResult run(NetworkCache.DcLfValue value) {
        if (value.getNetwork().getValidity() != LfNetwork.Validity.VALID) {
            return DcLoadFlowResult.createNoCalculationResult(value.getNetwork());
        }
        if (value.isNetworkUpdated()) {
            DcLoadFlowResult result = new DcLoadFlowEngine(value.getContext())
                    .run();
            value.setNetworkUpdated(false);
            if (value.isTopologyUpdated()) {
                value.getNetwork().getConnectivity().undoTemporaryChanges();
                value.setTopologyUpdated(false);
            }
            return result;
        }
        return new DcLoadFlowResult(value.getNetwork(), 0, true, OuterLoopResult.stable(), 0d, 0d);
    }

    public List<DcLoadFlowResult> run() {
        NetworkCache.Entry<NetworkCache.LfInput, NetworkCache.DcLfValue> entry = NetworkCache.DC_LF_INSTANCE.get(network, new NetworkCache.LfInput(parameters));
        List<NetworkCache.DcLfValue> values = entry.getValues();
        if (values == null) {
            values = initValues(entry);
        }
        return values.stream()
                .map(DcLoadFlowFromCache::run)
                .collect(Collectors.toList());
    }
}
