/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph.runners;

import com.powsybl.action.Action;
import com.powsybl.action.SwitchAction;
import com.powsybl.action.TerminalsConnectionAction;
import com.powsybl.computation.ComputationManager;
import com.powsybl.computation.local.LocalComputationManager;
import com.powsybl.contingency.BranchContingency;
import com.powsybl.contingency.Contingency;
import com.powsybl.contingency.ContingencyContext;
import com.powsybl.contingency.ContingencyElement;
import com.powsybl.contingency.strategy.OperatorStrategy;
import com.powsybl.contingency.strategy.condition.TrueCondition;
import com.powsybl.iidm.network.*;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.SparseMatrixFactory;
import com.powsybl.openloadflow.graph.GraphConnectivityFactory;
import com.powsybl.openloadflow.graph.OperatorStrategyUtils;
import com.powsybl.openloadflow.graph.RandomUtils;
import com.powsybl.openloadflow.graph.ng.BusBreakerGraph;
import com.powsybl.openloadflow.graph.utils.AverageStopWatch;
import com.powsybl.openloadflow.graph.workload.ExecutorWithException;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.sa.OpenSecurityAnalysisParameters;
import com.powsybl.openloadflow.sa.OpenSecurityAnalysisProvider;
import com.powsybl.security.SecurityAnalysis;
import com.powsybl.security.SecurityAnalysisParameters;
import com.powsybl.security.SecurityAnalysisRunParameters;
import org.apache.commons.lang3.tuple.Pair;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
public class SingleSecurityAnalysisRunner {

    public Random random = new Random(0);

    public final Network network;
    public final BusBreakerGraph busBreakerGraph;

    public List<Contingency> contingencies = new ArrayList<>();
    public List<OperatorStrategy> operatorStrategies = new ArrayList<>();
    public Set<Action> actions = new HashSet<>();
    public GraphConnectivityFactory<LfBus, LfBranch> connectivity;
    public Mode mode;
    public int threadCount;

    public SingleSecurityAnalysisRunner(Network network) {
        this.network = network;
        this.busBreakerGraph = new BusBreakerGraph(network);
    }

    public void disconnectLines(int toDisconnect) {
        List<Line> lines = randomLines();

        for (int i = 0, remaining = toDisconnect; i < lines.size() && remaining > 0; i++) {
            if (lines.get(i).disconnect()) {
                remaining--;
            }
        }
    }

    public void disconnectLinesPreserveConnectivity(int toDisconnect) {
        var con = busBreakerGraph.connectivity;
        con.startTemporaryChanges();
        int nbConnectedComponent = con.getNbConnectedComponents();

        List<Line> lines = randomLines();

        int remaining = toDisconnect;
        for (int i = 0; i < lines.size() && remaining > 0; i++) {
            Line line = lines.get(i);
            var edge = busBreakerGraph.idToEdge.get(line.getId());

            con.removeEdge(edge);

            if (con.getNbConnectedComponents() == nbConnectedComponent) {
                if (lines.get(i).disconnect()) {
                    remaining--;
                }
            }

            con.addEdge(edge.src(), edge.dest(), edge);
        }

        con.undoTemporaryChanges();

        busBreakerGraph.reset();
        busBreakerGraph.connectivity.startTemporaryChanges();
        System.out.println("Created " + (busBreakerGraph.connectivity.getNbConnectedComponents() - nbConnectedComponent) + " components");
        busBreakerGraph.connectivity.undoTemporaryChanges();
    }

    private List<Line> randomLines() {
        List<Line> lines = network.getLineStream()
                .filter(l -> l.getTerminal1().getBusView().getBus().isInMainSynchronousComponent() &&
                        l.getTerminal2().getBusView().getBus().isInMainSynchronousComponent())
                .sorted(Comparator.comparing(Line::getId))
                .collect(Collectors.toList());
        Collections.shuffle(lines, random);
        return lines;
    }

    public void setContingenciesAllLines() {
        contingencies.addAll(createContingenciesAllLines(network));
    }

    public static List<Contingency> createContingenciesAllLines(Network network) {
        return network.getLineStream()
                .filter(l -> !l.getId().contains(".") && l.getTerminal1().isConnected() && l.getTerminal2().isConnected())
                .sorted(Comparator.comparing(Line::getId))
                .map(line -> new Contingency("N-1: " + line.getId(), new BranchContingency(line.getId())))
                .collect(Collectors.toList());
    }

    public void setContingencies(int contingencyCount, int minLine, int maxLine) {
        contingencies.addAll(createContingencies("ct", network, random, contingencyCount, minLine, maxLine));
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

    @Deprecated
    public void setDefaultActions() {
        Pair<List<OperatorStrategy>, List<Action>> opAndActions = OperatorStrategyUtils.operatorStrategiesFor(network, contingencies, random);
        operatorStrategies.addAll(opAndActions.getLeft());
        actions.addAll(opAndActions.getRight());
    }

    public void generateContingenciesAndActions(int contingencyCount, int linePerCt, int actionPerOp) {
        Component mainSynchronous = getMainSynchronousComponent();
        Objects.requireNonNull(mainSynchronous);

        // create contingencies
        List<String> linesInComponent = mainSynchronous.getBusStream()
                .flatMap(b -> b.getConnectedTerminalStream()
                        .filter(t -> t.getConnectable() instanceof Line)
                        .map(t -> (Line) t.getConnectable()))
                .filter(line -> !line.getId().contains(".") && line.getTerminal1().isConnected() && line.getTerminal2().isConnected())
                .map(Line::getId)
                .distinct()
                .sorted() // constant ordering
                .collect(Collectors.toList());

        List<Contingency> contingencies = createContingencies("ct", linesInComponent, random, contingencyCount, linePerCt, linePerCt);
        this.contingencies.addAll(contingencies);

        // create actions
        if (actionPerOp > 0) {
            if (network.getSwitchCount() == 0) {
                generateTerminalsConnectionActionsFor(contingencies, mainSynchronous, actionPerOp);
            } else {
                generateCloseSwitchActionsFor(contingencies, mainSynchronous, actionPerOp);
            }
        }
    }

    private void generateTerminalsConnectionActionsFor(List<Contingency> contingencies, Component mainSynchronous, int actionPerOp) {
        List<String> disconnectedLines = network.getLineStream()
                .filter(l -> l.getTerminal1() != null && !l.getTerminal1().isConnected())
                .filter(l -> l.getTerminal2() != null && !l.getTerminal2().isConnected())
                .filter(l -> l.getTerminal1().getBusView().getConnectableBus() != null
                        && l.getTerminal1().getBusView().getConnectableBus().getSynchronousComponent() == mainSynchronous)
                .filter(l -> l.getTerminal2().getBusView().getConnectableBus() != null
                        && l.getTerminal2().getBusView().getConnectableBus().getSynchronousComponent() == mainSynchronous)
                .map(Line::getId)
                .collect(Collectors.toList());

        for (Contingency ct : contingencies) {
            List<String> ids = RandomUtils.sample(random, disconnectedLines, actionPerOp, actionPerOp).toList();

            for (String lineId : ids) {
                actions.add(new TerminalsConnectionAction(lineId, lineId, false));
            }

            operatorStrategies.add(new OperatorStrategy("op-" + ct.getId(),
                    ContingencyContext.specificContingency(ct.getId()),
                    new TrueCondition(),
                    ids));
        }
    }

    private void generateCloseSwitchActionsFor(List<Contingency> contingencies, Component mainSynchronous, int actionPerOp) {
        List<Switch> switches = network.getVoltageLevelStream()
                .flatMap(vl -> vl.getBusBreakerView().getSwitchStream())
                .filter(Switch::isOpen)
                .filter(s -> {
                    VoltageLevel.BusBreakerView bbv = s.getVoltageLevel().getBusBreakerView();
                    Bus bus1 = bbv.getBus1(s.getId());
                    Bus bus2 = bbv.getBus2(s.getId());

                    return bus1.getSynchronousComponent() == mainSynchronous && bus1 != bus2;
                })
                .collect(Collectors.toList());
        System.out.println(switches.size());
        for (int i = 0; i < contingencies.size(); i++) {
            Contingency ct = contingencies.get(i);

            List<String> ids = RandomUtils.sample(random, switches, actionPerOp, actionPerOp)
                    .map(Switch::getId)
                    .toList();

            for (String switchId : ids) {
                actions.add(new SwitchAction(switchId, switchId, false));
            }

            operatorStrategies.add(new OperatorStrategy("op-ct-" + i,
                    ContingencyContext.specificContingency(ct.getId()),
                    new TrueCondition(),
                    ids));
        }
    }

    private Component getMainSynchronousComponent() {
        for (Component component : network.getBusView().getSynchronousComponents()) {
            if (component.getNum() == ComponentConstants.MAIN_NUM) {
                return component;
            }
        }

        throw new IllegalStateException();
    }

    public AverageStopWatch run() {
        return run(connectivity);
    }

    public AverageStopWatch run(GraphConnectivityFactory<LfBus, LfBranch> connectivity) {
        SecurityAnalysisParameters securityAnalysisParameters = new SecurityAnalysisParameters();
        securityAnalysisParameters.setLoadFlowParameters(new LoadFlowParameters()
                .setDc(mode != Mode.AC)
                .setComponentMode(LoadFlowParameters.ComponentMode.MAIN_SYNCHRONOUS));

        OpenSecurityAnalysisParameters osap = new OpenSecurityAnalysisParameters();
        if (threadCount > 1) {
            osap.setThreadCount(threadCount);
        }
        if (mode == Mode.FAST_DC) {
            osap.setDcFastMode(true);
        }
        securityAnalysisParameters.addExtension(OpenSecurityAnalysisParameters.class, osap);

        SecurityAnalysisRunParameters runParameters = new SecurityAnalysisRunParameters()
                .setSecurityAnalysisParameters(securityAnalysisParameters)
                .setComputationManager(getDefaultComputationManager());

        if (!actions.isEmpty() || !operatorStrategies.isEmpty()) {
            if (actions.isEmpty() || operatorStrategies.isEmpty()) {
                throw new RuntimeException("No actions or operator strategy defined");
            }

            runParameters.setActions(new ArrayList<>(actions));
            runParameters.setOperatorStrategies(operatorStrategies);
        }

        OpenSecurityAnalysisProvider provider = new OpenSecurityAnalysisProvider(
                new SparseMatrixFactory(), connectivity);

        System.out.println(Instant.now() + " - Contingency: " + contingencies.size() + " - OperatorStrategy: " + operatorStrategies.size());
        AverageStopWatch sa = new AverageStopWatch();
        sa.start();
        new SecurityAnalysis.Runner(provider).run(network, contingencies, runParameters);
        sa.stop();
        return sa;
    }

    private static final Lock LOCK = new ReentrantLock();
    private static ComputationManager computationManager;

    private static ComputationManager getDefaultComputationManager() {
        LOCK.lock();
        try {
            if (computationManager == null) {
                try {
                    ExecutorService executor = ExecutorWithException.newCachedThreadPool(); // let openloadflow use the appropriate number of threads
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

    public enum Mode {
        AC,
        DC,
        FAST_DC
    }
}
