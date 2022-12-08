/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.iidm.network.Network;
import com.powsybl.openloadflow.network.LfNetwork;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class LfNetworkList implements AutoCloseable {

    public static class VariantCleaner {

        private final Network network;

        private final String workingVariantId;

        private final String tmpVariantId;

        public VariantCleaner(Network network, String workingVariantId, String tmpVariantId) {
            this.network = Objects.requireNonNull(network);
            this.workingVariantId = Objects.requireNonNull(workingVariantId);
            this.tmpVariantId = Objects.requireNonNull(tmpVariantId);
        }

        public void clean() {
            network.getVariantManager().removeVariant(tmpVariantId);
            network.getVariantManager().setWorkingVariant(workingVariantId);
        }
    }

    // list of networks sorted by descending size
    private final List<LfNetwork> list;

    private VariantCleaner variantCleaner;

    public LfNetworkList(List<LfNetwork> list, VariantCleaner variantCleaner) {
        this.list = Objects.requireNonNull(list);
        this.variantCleaner = variantCleaner;
    }

    public LfNetworkList(List<LfNetwork> list) {
        this(list, null);
    }

    public List<LfNetwork> getList() {
        return list;
    }

    public Optional<LfNetwork> getLargest() {
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public VariantCleaner release() {
        VariantCleaner variantCleanerToReturn = variantCleaner;
        variantCleaner = null;
        return variantCleanerToReturn;
    }

    @Override
    public void close() {
        if (variantCleaner != null) {
            variantCleaner.clean();
        }
    }
}
