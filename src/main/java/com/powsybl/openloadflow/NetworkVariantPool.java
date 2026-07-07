/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow;

import com.powsybl.commons.PowsyblException;
import com.powsybl.iidm.network.Network;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public final class NetworkVariantPool {

    public static final NetworkVariantPool INSTANCE = new NetworkVariantPool();

    private static final Logger LOGGER = LoggerFactory.getLogger(NetworkVariantPool.class);

    private NetworkVariantPool() {
    }

    private final WeakHashMap<Network, Deque<String>> variantPool = new WeakHashMap<>();

    private final Lock lock = new ReentrantLock();

    public String acquire(Network network, String initialVariantId, int poolSize) {
        Objects.requireNonNull(network, "network cannot be null");
        Objects.requireNonNull(initialVariantId, "initialVariantId cannot be null");
        if (poolSize <= 0) {
            throw new PowsyblException("poolSize must be positive");
        }
        lock.lock();
        try {
            Deque<String> variantIds = variantPool.get(network);
            if (variantIds == null) {
                // first access to the pool, create all variants
                variantIds = new ArrayDeque<>(poolSize);
                for (int i = 0; i < poolSize; i++) {
                    String variantId = "olf-tmp-" + UUID.randomUUID();
                    LOGGER.info("Creating variant '{}' for network '{}'", variantId, network.getId());
                    network.getVariantManager().cloneVariant(initialVariantId, variantId);
                    variantIds.add(variantId);
                }
                variantPool.put(network, variantIds);
            }
            if (variantIds.isEmpty()) {
                throw new PowsyblException("No variant available in the pool for network " + network.getId());
            }
            String variantId = variantIds.pollFirst();
            LOGGER.info("Acquiring variant '{}' of network '{}' ({} remaining)", variantId, network.getId(), variantIds.size());
            return variantId;
        } finally {
            lock.unlock();
        }
    }

    public void release(Network network, String variantId) {
        Objects.requireNonNull(network, "network cannot be null");
        Objects.requireNonNull(variantId, "variantId cannot be null");
        lock.lock();
        try {
            Deque<String> variantIds = variantPool.get(network);
            if (variantIds != null) {
                variantIds.add(variantId);
                LOGGER.info("Releasing variant '{}' of network '{}' ({} available)", variantId, network.getId(), variantIds.size());
            }
        } finally {
            lock.unlock();
        }
    }
}
