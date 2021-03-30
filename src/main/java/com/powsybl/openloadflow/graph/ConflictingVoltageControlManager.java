package com.powsybl.openloadflow.graph;

import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.impl.LfLegBranch;
import net.jafama.FastMath;
import org.apache.commons.lang3.tuple.Pair;
import org.jgrapht.Graph;
import org.jgrapht.alg.shortestpath.DijkstraShortestPath;
import org.jgrapht.graph.DefaultUndirectedWeightedGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public class ConflictingVoltageControlManager {

    protected static final Logger LOGGER = LoggerFactory.getLogger(ConflictingVoltageControlManager.class);

    static class VoltageLevelIndex {
        private double minV;
        private double maxV;
        private double qmax;

        public VoltageLevelIndex(double minV, double maxV, double qmax) {
            this.minV = minV;
            this.maxV = maxV;
            this.qmax = qmax;
        }

        public double getQmax() {
            return qmax;
        }

        public boolean match(LfBus lfBus) {
            return lfBus.getNominalV() >= minV && lfBus.getNominalV() < maxV;
        }
    }

    static Collection<VoltageLevelIndex> createVoltageLevels() {
        List<VoltageLevelIndex> indexes = new ArrayList<>();
        indexes.add(new VoltageLevelIndex(Double.MIN_VALUE, 50, 400d));
        indexes.add(new VoltageLevelIndex(50, 80, 400d));
        indexes.add(new VoltageLevelIndex(80, 150, 400d));
        indexes.add(new VoltageLevelIndex(150, 300, 880d));
        indexes.add(new VoltageLevelIndex(300, 500, 1600d));
        indexes.add(new VoltageLevelIndex(500, Double.MAX_VALUE, 1600d));
        return indexes;
    }

    LfNetwork lfNetwork;
    private final Collection<VoltageLevelIndex> voltageLevelIndices = createVoltageLevels();
    private final Set<LfBus> pvBuses;

    /**
     *
     * @param lfNetwork
     * @param lfBuses needed, because calling lfNetwork.getBuses() would trigger updateCache and crash in slackBus selection for non-principal components
     */
    public ConflictingVoltageControlManager(LfNetwork lfNetwork, List<LfBus> lfBuses) {
        this.lfNetwork = lfNetwork;
        this.pvBuses = getPvBuses(lfBuses);
    }

    private Map<VoltageLevelIndex, Set<LfBus>> indexByVoltageLevel(Collection<LfBus> buses) {
        Map<VoltageLevelIndex, Set<LfBus>> busesByVoltageLevel = new HashMap<>();
        for (LfBus lfBus : buses) {
            VoltageLevelIndex voltageLevelIndex = voltageLevelIndices.stream().filter(voltageLevel -> voltageLevel.match(lfBus)).findFirst().orElseThrow();
            busesByVoltageLevel.computeIfAbsent(voltageLevelIndex, x -> new HashSet<>()).add(lfBus);
        }
        return busesByVoltageLevel;
    }

    public Set<LfBus> getPvBuses(List<LfBus> lfBuses) {
        return lfBuses.stream()
            .filter(bus -> bus.isDiscreteVoltageControlled() || bus.isVoltageControlled())
            .collect(Collectors.toSet());
    }

    private static Graph<LfBus, LfBranch> createGraph(Collection<LfBus> buses) {
        Graph<LfBus, LfBranch> graph = new DefaultUndirectedWeightedGraph<>(LfBranch.class);

        // first, add all buses
        for (LfBus bus : buses) {
            graph.addVertex(bus);
        }
        // then, add all branches
        for (LfBus bus : buses) {
            bus.getBranches().stream()
                .filter(branch -> branch.getBus1() != null && branch.getBus2() != null)
                .filter(branch -> buses.contains(branch.getBus1()) && buses.contains(branch.getBus2()))
                .filter(branch -> !branch.isVoltageController()) // twt ?
                .filter(branch -> !(branch instanceof LfLegBranch)) // T3wt are LegBranches ?
                .forEach(branch -> {
                    graph.addEdge(branch.getBus1(), branch.getBus2(), branch);
                    graph.setEdgeWeight(branch.getBus1(), branch.getBus2(), FastMath.abs(branch.getPiModel().getX()));
                });
        }

        return graph;
    }

    /***
     *
     * @return Collection of pairs of lfbuses that have different voltage levels, the first element of the pair being the highest voltage
     */
    public Collection<Pair<LfBus, LfBus>> getCandidatePairs(List<LfBus> buses) {
        Collection<Pair<LfBus, LfBus>> pairs = new ArrayList<>();
        for (int i = 0; i < buses.size() - 1; i++) {
            for (int j = i + 1; j < buses.size(); j++) {
                LfBus busi = buses.get(i);
                LfBus busj = buses.get(j);
                if (busi.getV() * busi.getNominalV() - busj.getV() * busj.getNominalV() > 0) {
                    pairs.add(Pair.of(busi, busj));
                } else if (busi.getV() * busi.getNominalV() - busj.getV() * busj.getNominalV() < 0) {
                    pairs.add(Pair.of(busj, busi));
                }
            }
        }
        return pairs;
    }

    public Map<VoltageLevelIndex, Collection<Pair<LfBus, LfBus>>> getConflicts() {
        Map<VoltageLevelIndex, Collection<Pair<LfBus, LfBus>>> conflictsByVoltageLevel = new HashMap<>();
        Map<VoltageLevelIndex, Set<LfBus>> busesByVoltageLevel = indexByVoltageLevel(pvBuses);

        for (Map.Entry<VoltageLevelIndex, Set<LfBus>> entry : busesByVoltageLevel.entrySet()) {
            VoltageLevelIndex index = entry.getKey();
            Set<LfBus> buses = entry.getValue();
            Graph<LfBus, LfBranch> voltageLevelGraph = createGraph(buses);
            DijkstraShortestPath<LfBus, LfBranch> algorithm = new DijkstraShortestPath<>(voltageLevelGraph);
            Collection<Pair<LfBus, LfBus>> conflicts = new ArrayList<>();

            conflictsByVoltageLevel.put(index, conflicts);

            if (buses.size() < 2) {
                continue;
            }
            Collection<Pair<LfBus, LfBus>> candidatesPvPairs = getCandidatePairs(List.copyOf(buses));

            for (Pair<LfBus, LfBus> candidatePair : candidatesPvPairs) {
                LfBus bus1 = candidatePair.getKey();
                LfBus bus2 = candidatePair.getValue();
                double pathWeight = algorithm.getPathWeight(bus1, bus2); // todo: it may be better to compute the paths ourselves (to stop at transformer, or stop if weight > threshold)

                double conflictThreshold = bus1.getV() * bus1.getNominalV() * (bus1.getV() * bus1.getNominalV() - bus2.getV() * bus2.getNominalV()) / index.getQmax();
                if (pathWeight < conflictThreshold) {
                    conflicts.add(candidatePair);
                    LOGGER.debug("Conflict found between buses: {} and {}. Impedance is {} and voltage difference is {}",
                        candidatePair.getLeft().getId(), candidatePair.getRight().getId(), pathWeight, conflictThreshold);
                }
            }

        }
        return conflictsByVoltageLevel;
    }

    public static void fixConflicts(LfNetwork network, List<LfBus> lfBuses) {
        ConflictingVoltageControlManager manager = new ConflictingVoltageControlManager(network, lfBuses);
        Map<VoltageLevelIndex, Collection<Pair<LfBus, LfBus>>> conflictsByVoltageLevel = manager.getConflicts();
        Set<LfBus> busesToSwitch = new HashSet<>();
        for (Map.Entry<VoltageLevelIndex, Collection<Pair<LfBus, LfBus>>> entry : conflictsByVoltageLevel.entrySet()) {
            Collection<Pair<LfBus, LfBus>> conflicts = entry.getValue();

            for (Pair<LfBus, LfBus> conflict : conflicts) {
                LfBus bus1 = conflict.getKey();
                LfBus bus2 = conflict.getValue();

                // One of the buses has already been switched because it was part of another conflict
                if (busesToSwitch.contains(bus1) || busesToSwitch.contains(bus2)) {
                    continue;
                }
                // todo: can you have a discreteVoltageControl and a voltageControl ? What should we do in this case ?
                busesToSwitch.add(bus1.isDiscreteVoltageControlled() ? bus1 : bus2); // delete discrete voltage level in priority
            }
        }

        for (LfBus bus : busesToSwitch) {
            if (bus.isVoltageControlled()) {
                LOGGER.debug("Voltage control of bus {} has a conflict but cannot be disabled because it comes from a generator.", bus.getId());
                continue; // this means we have a conflict between two generators, we do not want to deactivate them
            }
            DiscreteVoltageControl discreteVoltageControl = bus.getDiscreteVoltageControl();
            bus.setDiscreteVoltageControl(null);
            discreteVoltageControl.getControllers().forEach(branch -> branch.setDiscreteVoltageControl(null));
            List<String> controllersIds = discreteVoltageControl.getControllers().stream().map(LfElement::getId).collect(Collectors.toList());
            LOGGER.warn("The voltage control on branches {} is disabled because of conflict on bus {}", String.join(",", controllersIds), bus.getId());
        }
    }
}
