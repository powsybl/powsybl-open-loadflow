/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph.benchmark.runners;

import com.powsybl.computation.local.LocalComputationManager;
import com.powsybl.contingency.Contingency;
import com.powsybl.contingency.strategy.ConditionalActions;
import com.powsybl.contingency.strategy.OperatorStrategy;
import com.powsybl.contingency.strategy.condition.TrueCondition;
import com.powsybl.iidm.network.ImportConfig;
import com.powsybl.iidm.network.Network;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.Properties;

/**
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 */
public record SAInput(String network,
                      String name,
                      int lineToDisconnect,
                      int contingencyCount,
                      int linePerContingency,
                      int actionPerOp,
                      ParametrizedSecurityAnalysis.Mode mode,
                      int threadCount) implements SimulationInput {

    @Override
    public Simulation createSimulation() {
        Properties parameters = new Properties();
        parameters.setProperty("iidm.import.repair-invalid-reactive-curve-limits", "true");

        Network network = Network.read(Path.of(this.network), LocalComputationManager.getDefault(), ImportConfig.CACHE.get(), parameters);
        ParametrizedSecurityAnalysis psa = new ParametrizedSecurityAnalysis(network);

        if (lineToDisconnect > 0) {
            psa.disconnectLinesPreserveConnectivity(lineToDisconnect);
        }
        psa.generateContingenciesAndActions(contingencyCount, linePerContingency, actionPerOp);
        psa.threadCount = threadCount;
        psa.mode = mode;
        psa.directoryName = toDirectoryName(psa);
        psa.fileName = toFileName(psa);

        return psa;
    }

    public String toDirectoryName(ParametrizedSecurityAnalysis psa) {
        StringBuilder sb = new StringBuilder();
        sb.append(name).append("_").append(contingencyCount < 0 ? psa.contingencies.size() : contingencyCount).append("_").append(linePerContingency);

        if (actionPerOp > 0) {
            sb.append("_1_").append(actionPerOp);
        }

        sb.append("/").append(mode.name().toLowerCase(Locale.ROOT)).append("_").append(threadCount);

        return sb.toString();
    }

    public String toFileName(ParametrizedSecurityAnalysis psa) {

        int minC = Integer.MAX_VALUE;
        int maxC = 0;

        for (Contingency contingency : psa.contingencies) {
            minC = Math.min(contingency.getElements().size(), minC);
            maxC = Math.max(contingency.getElements().size(), maxC);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("spy_").append(psa.contingencies.size()).append("_").append(minC).append("_").append(maxC);

        if (psa.operatorStrategies != null) {
            int minA = Integer.MAX_VALUE;
            int maxA = 0;
            for (OperatorStrategy operatorStrategy : psa.operatorStrategies) {
                int min = 0;
                int max = 0;
                for (ConditionalActions conditionalActions : operatorStrategy.getConditionalActions()) {
                    if (conditionalActions.getCondition() instanceof TrueCondition) {
                        min += conditionalActions.getActionIds().size();
                    }
                    max += conditionalActions.getActionIds().size();
                }

                minA = Math.min(min, minA);
                maxA = Math.max(max, maxA);
            }

            if (!psa.operatorStrategies.isEmpty()) {
                sb.append("_").append(psa.operatorStrategies.size()).append("_").append(minA).append("_").append(maxA);
            }
        }

        return sb.toString();
    }

    public static final class Builder {
        private String network = null;
        private String name = null;
        private int lineToDisconnect = 0;
        private int contingencyCount = 0;
        private int linePerContingency = 0;
        private int actionPerOp = 0;
        private ParametrizedSecurityAnalysis.Mode mode = ParametrizedSecurityAnalysis.Mode.DC;
        private int threadCount = 1;

        public Builder setNetwork(String network) {
            this.network = Objects.requireNonNull(network);
            return this;
        }

        public Builder setName(String name) {
            this.name = name;
            return this;
        }

        public Builder setLineToDisconnect(int lineToDisconnect) {
            this.lineToDisconnect = lineToDisconnect;
            return this;
        }

        public Builder setContingencyCount(int contingencyCount) {
            this.contingencyCount = contingencyCount;
            return this;
        }

        public Builder setLinePerContingency(int linePerContingency) {
            this.linePerContingency = linePerContingency;
            return this;
        }

        public Builder setActionPerOp(int actionPerOp) {
            this.actionPerOp = actionPerOp;
            return this;
        }

        public Builder setMode(ParametrizedSecurityAnalysis.Mode mode) {
            this.mode = Objects.requireNonNull(mode);
            return this;
        }

        public Builder setThreadCount(int threadCount) {
            if (threadCount <= 0) {
                throw new IllegalArgumentException("threadCount must be > 0");
            }
            this.threadCount = threadCount;
            return this;
        }

        public SAInput build() {
            return new SAInput(Objects.requireNonNull(network), name, lineToDisconnect, contingencyCount, linePerContingency, actionPerOp, mode, threadCount);
        }
    }
}
