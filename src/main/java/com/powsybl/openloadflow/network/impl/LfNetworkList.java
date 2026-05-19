/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.iidm.network.Network;
import com.powsybl.openloadflow.NetworkVariantPool;
import com.powsybl.openloadflow.network.LfNetwork;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class LfNetworkList implements AutoCloseable {

    public interface VariantCleaner {

        String getTmpVariantId();

        void clean();
    }

    public abstract static class AbstractVariantCleaner implements VariantCleaner {

        protected final Network network;

        protected final String workingVariantId;

        protected final String tmpVariantId;

        protected AbstractVariantCleaner(Network network, String workingVariantId, String tmpVariantId) {
            this.network = Objects.requireNonNull(network);
            this.workingVariantId = Objects.requireNonNull(workingVariantId);
            this.tmpVariantId = Objects.requireNonNull(tmpVariantId);
        }

        @Override
        public String getTmpVariantId() {
            return tmpVariantId;
        }
    }

    public static class DefaultVariantCleaner extends AbstractVariantCleaner {

        public DefaultVariantCleaner(Network network, String workingVariantId, String tmpVariantId) {
            super(network, workingVariantId, tmpVariantId);
        }

        @Override
        public void clean() {
            network.getVariantManager().removeVariant(tmpVariantId);
            network.getVariantManager().setWorkingVariant(workingVariantId);
        }
    }

    public static class WorkingVariantReverter extends AbstractVariantCleaner {

        public WorkingVariantReverter(Network network, String workingVariantId, String tmpVariantId) {
            super(network, workingVariantId, tmpVariantId);
        }

        @Override
        public void clean() {
            network.getVariantManager().setWorkingVariant(workingVariantId);
        }
    }

    public static class PoolVariantReleaser extends AbstractVariantCleaner {

        public PoolVariantReleaser(Network network, String workingVariantId, String tmpVariantId) {
            super(network, workingVariantId, tmpVariantId);
        }

        @Override
        public void clean() {
            NetworkVariantPool.INSTANCE.release(network, tmpVariantId);
            network.getVariantManager().setWorkingVariant(workingVariantId);
        }
    }

    @FunctionalInterface
    public interface VariantCleanerFactory {
        VariantCleaner create(Network network, String workingVariantId, String tmpVariantId);
    }

    public interface VariantProvider {

        String getTmpVariantId(String workingVariantId);
    }

    public static class VariantCloner implements VariantProvider {

        private final Network network;

        public VariantCloner(Network network) {
            this.network = Objects.requireNonNull(network);
        }

        @Override
        public String getTmpVariantId(String workingVariantId) {
            String tmpVariantId = "olf-tmp-" + UUID.randomUUID();
            network.getVariantManager().cloneVariant(workingVariantId, tmpVariantId);
            network.getVariantManager().setWorkingVariant(tmpVariantId);
            return tmpVariantId;
        }
    }

    public static class PoolVariantAcquirer implements VariantProvider {
        private final Network network;
        private final int networkVariantPoolSize;

        public PoolVariantAcquirer(Network network, int networkVariantPoolSize) {
            this.network = Objects.requireNonNull(network);
            this.networkVariantPoolSize = networkVariantPoolSize;
        }

        @Override
        public String getTmpVariantId(String workingVariantId) {
            String tmpVariantId = NetworkVariantPool.INSTANCE.acquire(network, workingVariantId, networkVariantPoolSize);
            network.getVariantManager().cloneVariant(workingVariantId, tmpVariantId, true);
            network.getVariantManager().setWorkingVariant(tmpVariantId);
            return tmpVariantId;
        }
    }

    // list of networks sorted by descending size
    private final List<LfNetwork> list;

    private final VariantCleaner variantCleaner;

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

    @Override
    public void close() {
        if (variantCleaner != null) {
            variantCleaner.clean();
        }
    }

    public VariantCleaner getVariantCleaner() {
        return variantCleaner;
    }
}
