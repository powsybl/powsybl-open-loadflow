/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph.benchmark.runners;

import com.powsybl.computation.ComputationManager;
import com.powsybl.computation.local.LocalComputationManager;
import com.powsybl.contingency.BranchContingency;
import com.powsybl.contingency.Contingency;
import com.powsybl.contingency.ContingencyElement;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.Line;
import com.powsybl.iidm.network.Network;
import com.powsybl.openloadflow.graph.benchmark.RandomUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 */
public final class SimulationUtils {

    private SimulationUtils() { }

    private static final Lock LOCK = new ReentrantLock();
    private static ComputationManager computationManager;

    public static ComputationManager getDefaultComputationManager() {
        LOCK.lock();
        try {
            if (computationManager == null) {
                try {
                    ExecutorWithException executor = (ExecutorWithException) ExecutorWithException.newCachedThreadPool(); // let openloadflow use the appropriate number of threads
                    executor.allowCoreThreadTimeOut(true);
                    executor.setKeepAliveTime(250, TimeUnit.MILLISECONDS);
                    computationManager = new LocalComputationManager(executor);
                    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                        computationManager.close();
                        executor.shutdown();
                    }));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
            return computationManager;
        } finally {
            LOCK.unlock();
        }
    }

    public static List<Contingency> createContingenciesAllLines(Network network) {
        return network.getLineStream()
                .filter(l -> !l.getId().contains(".") && l.getTerminal1().isConnected() && l.getTerminal2().isConnected())
                .sorted(Comparator.comparing(Line::getId))
                .map(line -> new Contingency("N-1: " + line.getId(), new BranchContingency(line.getId())))
                .collect(Collectors.toList());
    }

    public static List<Contingency> createContingencies(String prefix, Network network, Random random, int contingencyCount, int minLine, int maxLine) {
        List<String> lineIds = network.getLineStream()
                .filter(l -> !l.getId().contains(".") && l.getTerminal1().isConnected() && l.getTerminal2().isConnected())
                .map(Identifiable::getId)
                .sorted() // force the initial ordering to be constant, such that shuffle will always lead the same result
                .collect(Collectors.toList());

        return createContingencies(prefix, lineIds, random, contingencyCount, minLine, maxLine);
    }

    public static List<Contingency> createContingencies(String prefix, List<String> lineIds, Random random, int contingencyCount, int minLine, int maxLine) {
        AtomicInteger index = new AtomicInteger();
        return RandomUtils.distinctSubsets(random, lineIds, contingencyCount, minLine, maxLine)
                .stream()
                .map(lines -> lines.stream()
                        .map(line -> (ContingencyElement) new BranchContingency(line))
                        .toList())
                .map(lines -> new Contingency(prefix + "-" + index.getAndIncrement(), lines))
                .toList();
    }
}
