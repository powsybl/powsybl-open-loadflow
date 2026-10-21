/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph.benchmark.generators;

import com.powsybl.openloadflow.graph.*;
import com.powsybl.openloadflow.graph.benchmark.runners.Simulation;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

import static com.powsybl.openloadflow.graph.benchmark.BenchmarkMethod.*;

/**
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 */
public class SpyWorkloadGenerator implements IGenerateWorkload {

    public Simulation simulation;
    public GraphConnectivityFactory<LfBus, LfBranch> connectivity = new NaiveGraphConnectivityFactory<>(LfBus::getNum);

    public SpyWorkloadGenerator(Simulation simulation) {
        this.simulation = simulation;
    }

    @Override
    public String getName() {
        return simulation.fileName();
    }

    @Override
    public void generate(Path folder) throws IOException {
        String filename = getName() + "_" + Instant.now();

        SpyGraphConnectivityFactory factory = new SpyGraphConnectivityFactory(connectivity);
        simulation.run(factory);

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

        @Override
        public boolean supportsIncremental() {
            return delegate.supportsIncremental();
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
        public void startTemporaryChanges(boolean computeComparisons) {
            useful = true;
            try {
                WorkloadUtils.write(bw, START_TEMPORARY_CHANGES, computeComparisons);
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
        public boolean connected(LfBus vertex1, LfBus vertex2) {
            useful = true;
            try {
                WorkloadUtils.write(bw, CONNECTED, vertex1.getNum(), vertex2.getNum());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return delegate.connected(vertex1, vertex2);
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
        public Component<LfBus> getConnectedComponent(LfBus vertex) {
            useful = true;
            String name = ComponentSpy.newUniqueName();
            try {
                WorkloadUtils.write(bw, GET_CONNECTED_COMPONENT, name, vertex.getNum());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return new ComponentSpy(bw, name, delegate.getConnectedComponent(vertex));
        }

        @Override
        public Component<LfBus> getLargestConnectedComponent() {
            useful = true;
            String name = ComponentSpy.newUniqueName();
            try {
                WorkloadUtils.write(bw, GET_LARGEST_CONNECTED_COMPONENT, name);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return new ComponentSpy(bw, name, delegate.getLargestConnectedComponent());
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
