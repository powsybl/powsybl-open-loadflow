/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph.generators;

import com.powsybl.contingency.Contingency;
import com.powsybl.contingency.strategy.ConditionalActions;
import com.powsybl.contingency.strategy.OperatorStrategy;
import com.powsybl.contingency.strategy.condition.TrueCondition;
import com.powsybl.iidm.network.Network;
import com.powsybl.openloadflow.graph.GraphConnectivity;
import com.powsybl.openloadflow.graph.GraphConnectivityFactory;
import com.powsybl.openloadflow.graph.NaiveGraphConnectivityFactory;
import com.powsybl.openloadflow.graph.runners.SingleSecurityAnalysisRunner;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

import static com.powsybl.openloadflow.graph.utils.GraphConnectivityMethod.*;

/**
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 */
public class SpyWorkloadGenerator implements IGenerateWorkload {

    public SingleSecurityAnalysisRunner sar;

    public SpyWorkloadGenerator(Network network) {
        sar = new SingleSecurityAnalysisRunner(network);
        sar.connectivity = new NaiveGraphConnectivityFactory<>(LfBus::getNum);
    }

    @Override
    public String getName() {
        int minC = Integer.MAX_VALUE;
        int maxC = 0;

        for (Contingency contingency : sar.contingencies) {
            minC = Math.min(contingency.getElements().size(), minC);
            maxC = Math.max(contingency.getElements().size(), maxC);
        }

        String name = "spy_" + sar.contingencies.size() + "_" + minC + "_" + maxC;

        if (sar.operatorStrategies != null) {
            int minA = Integer.MAX_VALUE;
            int maxA = 0;
            for (OperatorStrategy operatorStrategy : sar.operatorStrategies) {
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

            if (!sar.operatorStrategies.isEmpty()) {
                name += "_" + sar.operatorStrategies.size() + "_" + minA + "_" + maxA;
            }
        }

        return name;
    }

    @Override
    public void generate(Path folder) throws IOException {
        String filename = getName() + "_" + Instant.now();

        SpyGraphConnectivityFactory factory = new SpyGraphConnectivityFactory(this.sar.connectivity);
        sar.run(factory);

        // write workload
        List<Path> files = factory.finish();

        if (files.size() == 1) {
            Files.copy(files.getFirst(),
                    folder.resolve(filename + ".txt"),
                    StandardCopyOption.REPLACE_EXISTING);

        } else if (files.size() > 1) {
            Path zipFile = folder.resolve(filename + ".zip");

            Map<String, String> env = new HashMap<>();
            env.put("create", String.valueOf(Files.notExists(zipFile)));

            try (FileSystem zipfs = FileSystems.newFileSystem(zipFile, env)) {
                for (int i = 0; i < files.size(); i++) {
                    Path zipInternalPath = zipfs.getPath(i + ".txt");
                    Files.copy(files.get(i), zipInternalPath, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    @Override
    public void generateOperations(BufferedWriter bw) {
        throw new UnsupportedOperationException();
    }

    private static final class SpyGraphConnectivityFactory implements GraphConnectivityFactory<LfBus, LfBranch> {

        private final GraphConnectivityFactory<LfBus, LfBranch> delegate;
        private final Map<Thread, Context> contextMap = new LinkedHashMap<>();

        SpyGraphConnectivityFactory(GraphConnectivityFactory<LfBus, LfBranch> delegate) {
            this.delegate = delegate;
        }

        @Override
        public synchronized GraphConnectivity<LfBus, LfBranch> create() {
            Context context = contextMap.computeIfAbsent(Thread.currentThread(), t -> {
                try {
                    Path temp = Files.createTempFile("SpyGraphConnectivity", ".txt");
                    System.out.println("Creating new GraphConnectivity input file: " + temp);

                    return new Context(temp, Files.newBufferedWriter(temp), new ArrayList<>());
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });

            SpyGraphConnectivity spy = new SpyGraphConnectivity(context.bw, delegate.create());
            context.connectivities.add(spy);
            return spy;
        }

        public List<Path> finish() throws IOException {
            List<Path> paths = new ArrayList<>();
            for (Context context : contextMap.values()) {
                context.bw.close();
                if (context.connectivities.stream().anyMatch(s -> s.useful)) {
                    paths.add(context.file);
                }
            }
            return paths;
        }

        private record Context(Path file, BufferedWriter bw, List<SpyGraphConnectivity> connectivities) { }

        @Override
        public String toString() {
            return super.toString() + "[" + delegate.toString() + "]";
        }
    }

    private static final class SpyGraphConnectivity implements GraphConnectivity<LfBus, LfBranch> {

        private final BufferedWriter bw;
        private final GraphConnectivity<LfBus, LfBranch> delegate;
        private boolean useful = false;

        SpyGraphConnectivity(BufferedWriter bw, GraphConnectivity<LfBus, LfBranch> delegate) {
            this.bw = bw;
            this.delegate = delegate;

            try {
                WorkloadUtils.newConnectivity(bw);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public void addVertex(LfBus vertex) {
            useful = true;
            try {
                WorkloadUtils.write(bw, ADD_VERTEX, vertex.getNum());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            delegate.addVertex(vertex);
        }

        @Override
        public void addEdge(LfBus vertex1, LfBus vertex2, LfBranch edge) {
            useful = true;
            try {
                WorkloadUtils.insert(bw, vertex1.getNum(), vertex2.getNum(), edge.getNum());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            delegate.addEdge(vertex1, vertex2, edge);
        }

        @Override
        public void removeEdge(LfBranch edge) {
            useful = true;
            try {
                WorkloadUtils.remove(bw, edge.getNum());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            delegate.removeEdge(edge);
        }

        @Override
        public boolean supportTemporaryChangesNesting() {
            return delegate.supportTemporaryChangesNesting();
        }

        @Override
        public void startTemporaryChanges(boolean quick) {
            useful = true;
            try {
                WorkloadUtils.write(bw, START_TEMPORARY_CHANGES, quick);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            delegate.startTemporaryChanges();
        }

        @Override
        public void undoTemporaryChanges() {
            useful = true;
            try {
                WorkloadUtils.write(bw, UNDO_TEMPORARY_CHANGES);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            delegate.undoTemporaryChanges();
        }

        @Override
        public int getComponentNumber(LfBus vertex) {
            useful = true;
            try {
                WorkloadUtils.write(bw, GET_COMPONENT_NUMBER, vertex.getNum());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return delegate.getComponentNumber(vertex);
        }

        @Override
        public void setMainComponentVertex(LfBus mainComponentVertex) {
            useful = true;
            try {
                WorkloadUtils.write(bw, SET_MAIN_COMPONENT_VERTEX, mainComponentVertex.getNum());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            delegate.setMainComponentVertex(mainComponentVertex);
        }

        @Override
        public int getNbConnectedComponents() {
            useful = true;
            try {
                WorkloadUtils.write(bw, GET_NB_CONNECTED_COMPONENTS);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return delegate.getNbConnectedComponents();
        }

        @Override
        public Set<LfBus> getConnectedComponent(LfBus vertex) {
            useful = true;
            try {
                WorkloadUtils.write(bw, GET_CONNECTED_COMPONENT, vertex.getNum());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return delegate.getConnectedComponent(vertex);
        }

        @Override
        public Set<LfBus> getLargestConnectedComponent() {
            useful = true;
            try {
                WorkloadUtils.write(bw, GET_LARGEST_CONNECTED_COMPONENT);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return delegate.getLargestConnectedComponent();
        }

        @Override
        public Set<LfBus> getVerticesRemovedFromMainComponent() {
            useful = true;
            try {
                WorkloadUtils.write(bw, GET_VERTICES_REMOVED_FROM_MAIN_COMPONENT);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return delegate.getVerticesRemovedFromMainComponent();
        }

        @Override
        public Set<LfBranch> getEdgesRemovedFromMainComponent() {
            useful = true;
            try {
                WorkloadUtils.write(bw, GET_EDGES_REMOVED_FROM_MAIN_COMPONENT);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return delegate.getEdgesRemovedFromMainComponent();
        }

        @Override
        public Set<LfBus> getVerticesAddedToMainComponent() {
            useful = true;
            try {
                WorkloadUtils.write(bw, GET_VERTICES_ADDED_TO_MAIN_COMPONENT);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return delegate.getVerticesAddedToMainComponent();
        }

        @Override
        public Set<LfBranch> getEdgesAddedToMainComponent() {
            useful = true;
            try {
                WorkloadUtils.write(bw, GET_EDGES_ADDED_TO_MAIN_COMPONENT);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return delegate.getEdgesAddedToMainComponent();
        }

        @Override
        public String toString() {
            return super.toString() + "[" + delegate.toString() + "]";
        }
    }
}
