/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph;

import com.powsybl.action.Action;
import com.powsybl.action.TerminalsConnectionAction;
import com.powsybl.contingency.BranchContingency;
import com.powsybl.contingency.Contingency;
import com.powsybl.contingency.ContingencyContext;
import com.powsybl.contingency.ContingencyElement;
import com.powsybl.contingency.strategy.OperatorStrategy;
import com.powsybl.contingency.strategy.condition.TrueCondition;
import com.powsybl.iidm.network.*;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.SparseMatrixFactory;
import com.powsybl.openloadflow.graph.ng.BusBreakerGraph;
import com.powsybl.openloadflow.graph.utils.AverageStopWatch;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.sa.OpenSecurityAnalysisParameters;
import com.powsybl.openloadflow.sa.OpenSecurityAnalysisProvider;
import com.powsybl.security.SecurityAnalysis;
import com.powsybl.security.SecurityAnalysisParameters;
import com.powsybl.security.SecurityAnalysisRunParameters;
import org.apache.commons.lang3.tuple.Pair;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
public class SecurityAnalysisRunner {

    public Random random = new Random(0);

    public final Network network;
    public final BusBreakerGraph busBreakerGraph;

    public List<Contingency> contingencies = new ArrayList<>();
    public List<OperatorStrategy> operatorStrategies = new ArrayList<>();
    public Set<Action> actions = new HashSet<>();
    public GraphConnectivityFactory<LfBus, LfBranch> connectivity;
    public boolean dc;
    public int threadCount;

    public SecurityAnalysisRunner(Network network) {
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
        contingencies.addAll(createContingencies(network, random, contingencyCount, minLine, maxLine));
    }

    public static List<Contingency> createContingencies(Network network, Random random, int contingencyCount, int minLine, int maxLine) {
        List<String> lines = network.getLineStream()
                .filter(l -> !l.getId().contains(".") && l.getTerminal1().isConnected() && l.getTerminal2().isConnected())
                .map(Identifiable::getId)
                .sorted() // force the initial ordering to be constant, such that shuffle will always lead the same result
                .collect(Collectors.toList());

        List<Contingency> contingencies = new ArrayList<>();
        for (int i = 0; i < contingencyCount; i++) {
            List<ContingencyElement> contingencyLine = RandomUtils.sample(random, lines, minLine, maxLine)
                    .map(id -> (ContingencyElement) new BranchContingency(id))
                    .toList();
            contingencies.add(new Contingency("Contingency " + i, contingencyLine));
        }

        return contingencies;
    }

    public void setDefaultActions(Random random) {
        Pair<List<OperatorStrategy>, List<Action>> opAndActions = OperatorStrategyUtils.operatorStrategiesFor(network, contingencies, random);
        operatorStrategies.addAll(opAndActions.getLeft());
        actions.addAll(opAndActions.getRight());
    }

    public void generateActions() {
        for (Contingency contingency : contingencies) {
            List<ContingencyElement> elements = new ArrayList<>(contingency.getElements());
            Collections.shuffle(elements, random);

            List<String> strActions = new ArrayList<>();
            for (int i = 0; i < elements.size() / 2; i++) {
                ContingencyElement element = elements.get(i);
                if (element instanceof BranchContingency branchContingency) {
                    TerminalsConnectionAction tca = new TerminalsConnectionAction(branchContingency.getId(), branchContingency.getId(), false);

                    if (actions.add(tca)) {
                        strActions.add(tca.getId());
                    }
                }
            }

            operatorStrategies.add(new OperatorStrategy("op-" + contingency.getId(),
                    ContingencyContext.specificContingency(contingency.getId()),
                    new TrueCondition(),
                    strActions));
        }
    }

    public void generateContingenciesAndActions(int contingencyCount, int linePerCt, int actionPerOp) {
        Component mainSynchronous = getMainSynchronousComponent();

        Objects.requireNonNull(mainSynchronous);
        List<String> linesInComponent = mainSynchronous.getBusStream()
                .flatMap(b -> b.getConnectedTerminalStream()
                        .filter(t -> t.getConnectable() instanceof Line)
                        .map(t -> (Line) t.getConnectable()))
                .map(Line::getId)
                .distinct()
                .collect(Collectors.toList());

        List<String> disconnectedLines = network.getLineStream()
                .filter(l -> l.getTerminal1() != null && !l.getTerminal1().isConnected())
                .filter(l -> l.getTerminal2() != null && !l.getTerminal2().isConnected())
                .filter(l -> l.getTerminal1().getBusView().getConnectableBus() != null
                        && l.getTerminal1().getBusView().getConnectableBus().getSynchronousComponent() == mainSynchronous)
                .filter(l -> l.getTerminal2().getBusView().getConnectableBus() != null
                        && l.getTerminal2().getBusView().getConnectableBus().getSynchronousComponent() == mainSynchronous)
                .map(Line::getId)
                .collect(Collectors.toList());

        for (int i = 0; i < contingencyCount; i++) {
            List<ContingencyElement> contingencyLines = RandomUtils.sample(random, linesInComponent, linePerCt, linePerCt)
                    .map(id -> (ContingencyElement) new BranchContingency(id))
                    .toList();
            Contingency ct = new Contingency("ct-" + i, contingencyLines);
            contingencies.add(ct);

            if (actionPerOp > 0) {
                List<String> ids = RandomUtils.sample(random, disconnectedLines, actionPerOp, actionPerOp).toList();

                for (String lineId : ids) {
                    actions.add(new TerminalsConnectionAction(lineId, lineId, false));
                }

                operatorStrategies.add(new OperatorStrategy("op-ct-" + i,
                        ContingencyContext.specificContingency(ct.getId()),
                        new TrueCondition(),
                        ids));
            }
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
                .setDc(dc)
                .setComponentMode(LoadFlowParameters.ComponentMode.MAIN_SYNCHRONOUS));
        // TODO: OpenLoadFlowParameters setNetworkCacheEnabled(true)

        if (threadCount > 1) {
            securityAnalysisParameters.addExtension(OpenSecurityAnalysisParameters.class,
                    new OpenSecurityAnalysisParameters().setThreadCount(threadCount));
        }

        SecurityAnalysisRunParameters runParameters = new SecurityAnalysisRunParameters()
                .setSecurityAnalysisParameters(securityAnalysisParameters);

        if (!actions.isEmpty() || !operatorStrategies.isEmpty()) {
            if (actions.isEmpty() || operatorStrategies.isEmpty()) {
                throw new RuntimeException("No actions or operator strategy defined");
            }

            runParameters.setActions(new ArrayList<>(actions));
            runParameters.setOperatorStrategies(operatorStrategies);
        }

        OpenSecurityAnalysisProvider provider = new OpenSecurityAnalysisProvider(
                new SparseMatrixFactory(), connectivity);

        System.out.println(Instant.now());
        AverageStopWatch sa = new AverageStopWatch();
        sa.start();
        new SecurityAnalysis.Runner(provider).run(network, contingencies, runParameters);
        sa.stop();
        return sa;
    }
}
