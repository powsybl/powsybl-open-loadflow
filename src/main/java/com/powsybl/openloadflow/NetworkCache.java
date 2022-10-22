/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.NetworkListener;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.json.LoadFlowParametersJsonModule;
import com.powsybl.openloadflow.ac.outerloop.AcLoadFlowContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public enum NetworkCache {
    INSTANCE;

    private static final Logger LOGGER = LoggerFactory.getLogger(NetworkCache.class);

    public static class NetworkEntry implements NetworkListener {

        private final LoadFlowParameters parameters;

        private List<AcLoadFlowContext> contexts;

        public NetworkEntry(LoadFlowParameters parameters) {
            this.parameters = parameters;
        }

        public List<AcLoadFlowContext> getContexts() {
            return contexts;
        }

        public void setContexts(List<AcLoadFlowContext> contexts) {
            this.contexts = contexts;
        }

        public LoadFlowParameters getParameters() {
            return parameters;
        }

        @Override
        public void onCreation(Identifiable identifiable) {
        }

        @Override
        public void beforeRemoval(Identifiable identifiable) {
        }

        @Override
        public void afterRemoval(String s) {
        }

        @Override
        public void onUpdate(Identifiable identifiable, String attribute, Object oldValue, Object newValue) {
        }

        @Override
        public void onUpdate(Identifiable identifiable, String attribute, String variantId, Object oldValue, Object newValue) {
        }

        @Override
        public void onElementAdded(Identifiable identifiable, String attribute, Object newValue) {
        }

        @Override
        public void onElementReplaced(Identifiable identifiable, String attribute, Object oldValue, Object newValue) {
        }

        @Override
        public void onElementRemoved(Identifiable identifiable, String attribute, Object oldValue) {
        }

        @Override
        public void onVariantCreated(String sourceVariantId, String targetVariantId) {
        }

        @Override
        public void onVariantOverwritten(String sourceVariantId, String targetVariantId) {
        }

        @Override
        public void onVariantRemoved(String variantId) {
        }

        public void close() {
            for (AcLoadFlowContext context : contexts) {
                context.close();
            }
        }
    }

    private final ReferenceQueue<Network> queue = new ReferenceQueue<>();

    private final Map<WeakReference<Network>, NetworkEntry> entries = new HashMap<>();

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new LoadFlowParametersJsonModule());

    private void evictDeadNetworks() {
        Reference<? extends Network> networkRef;
        while ((networkRef = queue.poll()) != null) {
            NetworkEntry entry = entries.remove(networkRef);
            entry.close();
            LOGGER.info("Dead network removed from cache ({} remains)", entries.size());
        }
    }

    private boolean equals(LoadFlowParameters parameters1, LoadFlowParameters parameters2) {
        try {
            return objectMapper.writeValueAsString(parameters1)
                    .equals(objectMapper.writeValueAsString(parameters2));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public NetworkEntry get(Network network, LoadFlowParameters parameters) {
        Objects.requireNonNull(network);
        Objects.requireNonNull(parameters);

        evictDeadNetworks();

        var mapEntry = entries.entrySet().stream()
                .filter(e -> e.getKey().get() == network)
                .findFirst()
                .orElse(null);

        // invalid cache if parameters have changed
        // TODO to refine later
        if (mapEntry != null && !equals(parameters, mapEntry.getValue().getParameters())) {
            mapEntry.getValue().close();
            entries.remove(mapEntry.getKey());
            mapEntry = null;
        }

        if (mapEntry == null) {
            var networkEntry = new NetworkEntry(parameters);
            entries.put(new WeakReference<>(network, queue), networkEntry);
            return networkEntry;
        } else {
            return mapEntry.getValue();
        }
    }
}
