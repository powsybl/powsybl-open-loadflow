/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.impl;

import com.google.common.collect.Sets;
import com.powsybl.commons.PowsyblException;
import com.powsybl.contingency.Contingency;
import com.powsybl.contingency.ContingencyElement;
import com.powsybl.iidm.network.*;
import com.powsybl.iidm.network.extensions.HvdcAngleDroopActivePowerControl;
import com.powsybl.iidm.network.extensions.LoadDetail;
import com.powsybl.iidm.network.util.HvdcUtils;
import com.powsybl.openloadflow.graph.GraphConnectivity;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.util.PerUnit;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 * @author Gaël Macherel {@literal <gael.macherel@artelys.com>}
 */
public class PropagatedContingency {

    protected static final Logger LOGGER = LoggerFactory.getLogger(PropagatedContingency.class);

    private final Contingency contingency;

    private final int index;

    private final Set<Switch> switchesToOpen;

    private final Set<Terminal> terminalsToDisconnect;

    private final Set<String> busIdsToLose;

    private final Map<String, DisabledBranchStatus> branchIdsToOpen = new LinkedHashMap<>();

    private final Set<String> hvdcIdsToOpen = new HashSet<>(); // for HVDC in AC emulation

    private final Set<String> generatorIdsToLose = new HashSet<>();

    private final Map<String, PowerShift> loadIdsToLoose = new HashMap<>();

    private final Map<String, AdmittanceShift> shuntIdsToShift = new HashMap<>();

    public Contingency getContingency() {
        return contingency;
    }

    public int getIndex() {
        return index;
    }

    public Set<String> getBusIdsToLose() {
        return busIdsToLose;
    }

    public Map<String, DisabledBranchStatus> getBranchIdsToOpen() {
        return branchIdsToOpen;
    }

    public Set<Switch> getSwitchesToOpen() {
        return switchesToOpen;
    }

    public Set<String> getHvdcIdsToOpen() {
        return hvdcIdsToOpen;
    }

    public Set<String> getGeneratorIdsToLose() {
        return generatorIdsToLose;
    }

    public Map<String, PowerShift> getLoadIdsToLoose() {
        return loadIdsToLoose;
    }

    public Map<String, AdmittanceShift> getShuntIdsToShift() {
        return shuntIdsToShift;
    }

    public PropagatedContingency(Contingency contingency, int index, Set<Switch> switchesToOpen, Set<Terminal> terminalsToDisconnect,
                                 Set<String> busIdsToLose) {
        this.contingency = Objects.requireNonNull(contingency);
        this.index = index;
        this.switchesToOpen = Objects.requireNonNull(switchesToOpen);
        this.terminalsToDisconnect = Objects.requireNonNull(terminalsToDisconnect);
        this.busIdsToLose = Objects.requireNonNull(busIdsToLose);
    }

    private static PowerShift getLoadPowerShift(Load load, boolean slackDistributionOnConformLoad) {
        double variableActivePower;
        if (slackDistributionOnConformLoad) {
            LoadDetail loadDetail = load.getExtension(LoadDetail.class);
            variableActivePower = loadDetail == null ? 0.0 : Math.abs(loadDetail.getVariableActivePower());
        } else {
            variableActivePower = Math.abs(load.getP0());
        }
        return new PowerShift(load.getP0() / PerUnit.SB,
                              variableActivePower / PerUnit.SB,
                              load.getQ0() / PerUnit.SB); // ensurePowerFactorConstant is not supported.
    }

    public static List<PropagatedContingency> createList(Network network, List<Contingency> contingencies, LfTopoConfig topoConfig,
                                                         PropagatedContingencyCreationParameters creationParameters) {
        List<PropagatedContingency> propagatedContingencies = new ArrayList<>();
        for (int index = 0; index < contingencies.size(); index++) {
            Contingency contingency = contingencies.get(index);
            PropagatedContingency propagatedContingency =
                    PropagatedContingency.create(network, contingency, index, topoConfig, creationParameters);
            propagatedContingencies.add(propagatedContingency);
            topoConfig.getSwitchesToOpen().addAll(propagatedContingency.switchesToOpen);
            topoConfig.getBusIdsToLose().addAll(propagatedContingency.busIdsToLose);
        }
        return propagatedContingencies;
    }

    private static PropagatedContingency create(Network network, Contingency contingency, int index, LfTopoConfig topoConfig,
                                                PropagatedContingencyCreationParameters creationParameters) {
        Set<Switch> switchesToOpen = new HashSet<>();
        Set<Terminal> terminalsToDisconnect = new HashSet<>();
        Set<String> busIdsToLose = new HashSet<>();
        // process elements of the contingency
        for (ContingencyElement element : contingency.getElements()) {
            Identifiable<?> identifiable = getIdentifiable(network, element);
            switch (identifiable.getType()) {
                case BUS:
                    Bus bus = (Bus) identifiable;
                    if (bus.getVoltageLevel().getTopologyKind() == TopologyKind.BUS_BREAKER) {
                        busIdsToLose.add(identifiable.getId());
                        bus.visitConnectedEquipments(new DefaultTopologyVisitor() {
                            public void visitBranch(Branch<?> branch, TwoSides side) {
                                if (side == TwoSides.ONE) {
                                    topoConfig.getBranchIdsOpenableSide1().add(branch.getId());
                                } else {
                                    topoConfig.getBranchIdsOpenableSide2().add(branch.getId());
                                }
                            }

                            @Override
                            public void visitLine(Line line, TwoSides side) {
                                visitBranch(line, side);
                            }

                            @Override
                            public void visitTwoWindingsTransformer(TwoWindingsTransformer transformer, TwoSides side) {
                                visitBranch(transformer, side);
                            }

                            @Override
                            public void visitThreeWindingsTransformer(ThreeWindingsTransformer transformer, ThreeSides side) {
                                topoConfig.getBranchIdsOpenableSide1().add(LfLegBranch.getId(transformer.getId(), side.getNum()));
                            }
                        });
                    } else {
                        throw new UnsupportedOperationException("Unsupported contingency element type " + element.getType() + ": voltage level should be in bus/breaker topology");
                    }
                    break;
                case BUSBAR_SECTION:
                    if (creationParameters.isContingencyPropagation()) {
                        ContingencyTripping.createContingencyTripping(network, identifiable).traverse(switchesToOpen, terminalsToDisconnect);
                    } else {
                        ContingencyTripping.createBusbarSectionMinimalTripping(network, (BusbarSection) identifiable).traverse(switchesToOpen, terminalsToDisconnect);
                    }
                    terminalsToDisconnect.addAll(getTerminals(identifiable));
                    break;
                case SWITCH:
                    switchesToOpen.add((Switch) identifiable);
                    break;
                default:
                    if (creationParameters.isContingencyPropagation()) {
                        ContingencyTripping.createContingencyTripping(network, identifiable).traverse(switchesToOpen, terminalsToDisconnect);
                    }
                    terminalsToDisconnect.addAll(getTerminals(identifiable));
            }
        }
        PropagatedContingency propagatedContingency = new PropagatedContingency(contingency, index, switchesToOpen, terminalsToDisconnect, busIdsToLose);
        propagatedContingency.complete(topoConfig, creationParameters);
        return propagatedContingency;
    }

    private <K> void addBranchToOpen(K branchId, DisabledBranchStatus status, Map<K, DisabledBranchStatus> branchIdsToOpen) {
        DisabledBranchStatus oldStatus = branchIdsToOpen.get(branchId);
        if (oldStatus == null) {
            branchIdsToOpen.put(branchId, status);
        } else if (status == DisabledBranchStatus.BOTH_SIDES || status != oldStatus) {
            branchIdsToOpen.put(branchId, DisabledBranchStatus.BOTH_SIDES);
        }
    }

    private void complete(LfTopoConfig topoConfig, PropagatedContingencyCreationParameters creationParameters) {
        for (Switch sw : switchesToOpen) {
            addBranchToOpen(sw.getId(), DisabledBranchStatus.BOTH_SIDES, branchIdsToOpen); // we open both sides
        }

        // process terminals disconnected, in particular process injection power shift
        for (Terminal terminal : terminalsToDisconnect) {
            Connectable<?> connectable = terminal.getConnectable();
            switch (connectable.getType()) {
                case LINE,
                     TWO_WINDINGS_TRANSFORMER:
                    Branch<?> branch = (Branch<?>) connectable;
                    if (terminal == branch.getTerminal1()) {
                        addBranchToOpen(connectable.getId(), DisabledBranchStatus.SIDE_1, branchIdsToOpen);
                        topoConfig.getBranchIdsOpenableSide1().add(connectable.getId());
                    } else {
                        addBranchToOpen(connectable.getId(), DisabledBranchStatus.SIDE_2, branchIdsToOpen);
                        topoConfig.getBranchIdsOpenableSide2().add(connectable.getId());
                    }
                    break;
                case DANGLING_LINE:
                    DanglingLine dl = (DanglingLine) connectable;
                    // as we terminal is only on network side, we open both sides in LF network
                    if (dl.isPaired()) {
                        addBranchToOpen(dl.getTieLine().orElseThrow().getId(), DisabledBranchStatus.BOTH_SIDES, branchIdsToOpen);
                    } else {
                        addBranchToOpen(dl.getId(), DisabledBranchStatus.BOTH_SIDES, branchIdsToOpen);
                    }
                    break;

                case GENERATOR,
                     STATIC_VAR_COMPENSATOR,
                     BATTERY:
                    generatorIdsToLose.add(connectable.getId());
                    break;

                case LOAD:
                    Load load = (Load) connectable;
                    loadIdsToLoose.put(load.getId(), getLoadPowerShift(load, creationParameters.isSlackDistributionOnConformLoad()));
                    break;

                case SHUNT_COMPENSATOR:
                    ShuntCompensator shunt = (ShuntCompensator) connectable;
                    if (creationParameters.isShuntCompensatorVoltageControlOn() && shunt.isVoltageRegulatorOn()) {
                        throw new UnsupportedOperationException("Shunt compensator '" + shunt.getId() + "' with voltage control on: not supported yet");
                    }
                    double zb = PerUnit.zb(shunt.getTerminal().getVoltageLevel().getNominalV());
                    shuntIdsToShift.put(shunt.getId(), new AdmittanceShift(shunt.getG() * zb,
                            shunt.getB() * zb));
                    break;

                case HVDC_CONVERTER_STATION:
                    HvdcConverterStation<?> station = (HvdcConverterStation<?>) connectable;
                    HvdcAngleDroopActivePowerControl control = station.getHvdcLine().getExtension(HvdcAngleDroopActivePowerControl.class);
                    if (control != null && control.isEnabled() && creationParameters.isHvdcAcEmulation()) {
                        hvdcIdsToOpen.add(station.getHvdcLine().getId());
                    }
                    // FIXME
                    // the other converter station should be considered to if in the same synchronous component (hvdc setpoint mode).
                    if (connectable instanceof VscConverterStation) {
                        generatorIdsToLose.add(connectable.getId());
                    } else {
                        LccConverterStation lcc = (LccConverterStation) connectable;
                        PowerShift lccPowerShift = new PowerShift(HvdcUtils.getConverterStationTargetP(lcc) / PerUnit.SB, 0,
                                HvdcUtils.getLccConverterStationLoadTargetQ(lcc) / PerUnit.SB);
                        loadIdsToLoose.put(lcc.getId(), lccPowerShift);
                    }
                    break;

                case BUSBAR_SECTION:
                    // we don't care
                    break;

                case THREE_WINDINGS_TRANSFORMER:
                    // terminal in always by construction the side 1 of the LF branch
                    ThreeWindingsTransformer twt = (ThreeWindingsTransformer) connectable;
                    for (ThreeSides side : ThreeSides.values()) {
                        if (twt.getTerminal(side) == terminal) {
                            addBranchToOpen(LfLegBranch.getId(side, connectable.getId()), DisabledBranchStatus.SIDE_1, branchIdsToOpen);
                            topoConfig.getBranchIdsOpenableSide1().add(LfLegBranch.getId(connectable.getId(), side.getNum()));
                            break;
                        }
                    }
                    break;

                default:
                    throw new UnsupportedOperationException("Unsupported by propagation contingency element type: "
                            + connectable.getType());
            }
        }
    }

    private static List<? extends Terminal> getTerminals(Identifiable<?> identifiable) {
        if (identifiable instanceof Connectable<?>) {
            return ((Connectable<?>) identifiable).getTerminals();
        }
        if (identifiable instanceof HvdcLine hvdcLine) {
            return List.of(hvdcLine.getConverterStation1().getTerminal(), hvdcLine.getConverterStation2().getTerminal());
        }
        if (identifiable instanceof TieLine line) {
            return List.of(line.getDanglingLine1().getTerminal(), line.getDanglingLine2().getTerminal());
        }
        if (identifiable instanceof Switch) {
            return Collections.emptyList();
        }
        throw new UnsupportedOperationException("Unsupported contingency element type: " + identifiable.getType());
    }

    private static Identifiable<?> getIdentifiable(Network network, ContingencyElement element) {
        Identifiable<?> identifiable;
        String identifiableType;
        switch (element.getType()) {
            case BRANCH,
                 LINE,
                 TWO_WINDINGS_TRANSFORMER:
                identifiable = network.getBranch(element.getId());
                identifiableType = "Branch";
                break;
            case HVDC_LINE:
                identifiable = network.getHvdcLine(element.getId());
                identifiableType = "HVDC line";
                break;
            case DANGLING_LINE:
                identifiable = network.getDanglingLine(element.getId());
                identifiableType = "Dangling line";
                break;
            case GENERATOR:
                identifiable = network.getGenerator(element.getId());
                identifiableType = "Generator";
                break;
            case STATIC_VAR_COMPENSATOR:
                identifiable = network.getStaticVarCompensator(element.getId());
                identifiableType = "Static var compensator";
                break;
            case LOAD:
                identifiable = network.getLoad(element.getId());
                identifiableType = "Load";
                break;
            case SHUNT_COMPENSATOR:
                identifiable = network.getShuntCompensator(element.getId());
                identifiableType = "Shunt compensator";
                break;
            case SWITCH:
                identifiable = network.getSwitch(element.getId());
                identifiableType = "Switch";
                break;
            case THREE_WINDINGS_TRANSFORMER:
                identifiable = network.getThreeWindingsTransformer(element.getId());
                identifiableType = "Three windings transformer";
                break;
            case BUSBAR_SECTION:
                identifiable = network.getBusbarSection(element.getId());
                identifiableType = "Busbar section";
                break;
            case TIE_LINE:
                identifiable = network.getTieLine(element.getId());
                identifiableType = "Tie line";
                break;
            case BUS:
                identifiable = network.getBusBreakerView().getBus(element.getId());
                identifiableType = "Configured bus";
                break;
            default:
                throw new UnsupportedOperationException("Unsupported contingency element type: " + element.getType());
        }
        if (identifiable == null) {
            throw new PowsyblException(identifiableType + " '" + element.getId() + "' not found in the network");
        }
        return identifiable;
    }

    public boolean hasNoImpact() {
        return branchIdsToOpen.isEmpty()
                && hvdcIdsToOpen.isEmpty() && generatorIdsToLose.isEmpty()
                && loadIdsToLoose.isEmpty() && shuntIdsToShift.isEmpty() && busIdsToLose.isEmpty();
    }

    private static boolean isIsolatedBus(GraphConnectivity<LfBus, LfBranch> connectivity, LfNetwork network, LfBus bus) {
        return connectivity.getConnectedComponent(bus).size() < network.getBuses().size() / 2;
    }

    private Map<LfBranch, DisabledBranchStatus> findBranchToOpenDirectlyImpactedByContingency(LfNetwork network) {
        // we add the branches connected to buses to lose.
        Map<LfBranch, DisabledBranchStatus> branchesToOpen = branchIdsToOpen.entrySet().stream()
                .map(e -> Pair.of(network.getBranchById(e.getKey()), e.getValue()))
                .filter(e -> e.getKey() != null)
                .collect(Collectors.toMap(Pair::getKey, Pair::getValue));

        busIdsToLose.stream().map(network::getBusById)
                .filter(Objects::nonNull)
                .forEach(bus -> {
                    if (bus.isSlack()) {
                        // slack bus disabling is not supported
                        // we keep the slack bus enabled and the connected lostBranches
                        LOGGER.error("Contingency '{}' leads to the loss of a slack bus: slack bus kept", bus.getId());
                    } else {
                        bus.getBranches().forEach(branch -> {
                            DisabledBranchStatus status = branch.getBus1() == bus ? DisabledBranchStatus.SIDE_1 : DisabledBranchStatus.SIDE_2;
                            addBranchToOpen(branch, status, branchesToOpen);
                        });
                    }
                });

        return branchesToOpen;
    }

    record ContingencyConnectivityLossImpact(boolean ok, int createdSynchronousComponents, Set<LfBus> busesToLost) {
    }

    private ContingencyConnectivityLossImpact findBusesAndBranchesImpactedBecauseOfConnectivityLoss(LfNetwork network, Map<LfBranch, DisabledBranchStatus> branchesToOpen) {
        // update connectivity with triggered branches of this network
        GraphConnectivity<LfBus, LfBranch> connectivity = network.getConnectivity();
        connectivity.startTemporaryChanges();
        try {
            branchesToOpen.keySet().stream()
                    .filter(LfBranch::isConnectedAtBothSides)
                    .forEach(connectivity::removeEdge);

            if (isIsolatedBus(connectivity, network, network.getSlackBus())) {
                LOGGER.warn("Contingency '{}' leads to an isolated slack bus: relocate slack bus inside main component",
                        contingency.getId());
                // if a contingency leads to an isolated slack bus, we need to relocate the slack bus
                // we select a new slack bus excluding buses from isolated component
                Set<LfBus> excludedBuses = Sets.difference(Set.copyOf(network.getBuses()), connectivity.getVerticesRemovedFromMainComponent());
                network.setExcludedSlackBuses(excludedBuses);
                // reverse main component to the one containing the relocated slack bus
                connectivity.setMainComponentVertex(network.getSlackBus());
            }

            // add to contingency description buses and branches that won't be part of the main connected
            // component in post contingency state
            int createdSynchronousComponents = connectivity.getNbConnectedComponents() - 1;
            Set<LfBus> busesToLost = connectivity.getVerticesRemovedFromMainComponent();

            return new ContingencyConnectivityLossImpact(true, createdSynchronousComponents, busesToLost);
        } finally {
            // reset connectivity to discard triggered elements
            connectivity.undoTemporaryChanges();
        }
    }

    public Optional<LfContingency> toLfContingency(LfNetwork network) {
        // find branch to open because of direct impact of the contingency (including propagation is activated)
        Map<LfBranch, DisabledBranchStatus> branchesToOpen = findBranchToOpenDirectlyImpactedByContingency(network);

        // find branches to open and buses to lost not directly from the contingency impact but as a consequence of
        // loss of connectivity once contingency applied on the network
        ContingencyConnectivityLossImpact connectivityLossImpact = findBusesAndBranchesImpactedBecauseOfConnectivityLoss(network, branchesToOpen);
        if (!connectivityLossImpact.ok) {
            return Optional.empty();
        }
        Set<LfBus> busesToLost = connectivityLossImpact.busesToLost(); // nothing else

        for (LfBus busToLost : busesToLost) {
            busToLost.getBranches()
                    .forEach(branch -> {
                        // fully disable if branch is connected to 2 buses to lost or open on the other side
                        LfBus otherSideBus;
                        boolean otherSideConnected;
                        if (branch.getBus1() == busToLost) {
                            otherSideBus = branch.getBus2();
                            otherSideConnected = branch.isConnectedSide2();
                        } else {
                            otherSideBus = branch.getBus1();
                            otherSideConnected = branch.isConnectedSide1();
                        }
                        if (busesToLost.contains(otherSideBus) || !otherSideConnected) {
                            addBranchToOpen(branch, DisabledBranchStatus.BOTH_SIDES, branchesToOpen);
                        }
                    });
        }

        Map<LfShunt, AdmittanceShift> shunts = new HashMap<>(1);
        for (var e : shuntIdsToShift.entrySet()) {
            LfShunt shunt = network.getShuntById(e.getKey());
            if (shunt != null) { // could be in another component
                shunts.computeIfAbsent(shunt, k -> new AdmittanceShift())
                        .add(e.getValue());
            }
        }

        Set<LfGenerator> generators = new HashSet<>(1);
        for (String generatorId : generatorIdsToLose) {
            LfGenerator generator = network.getGeneratorById(generatorId);
            if (generator != null) { // could be in another component
                generators.add(generator);
            }
        }

        Map<LfLoad, LfLostLoad> loads = new HashMap<>(1);
        for (var e : loadIdsToLoose.entrySet()) {
            String loadId = e.getKey();
            PowerShift powerShift = e.getValue();
            LfLoad load = network.getLoadById(loadId);
            if (load != null) { // could be in another component
                LfLostLoad lostLoad = loads.computeIfAbsent(load, k -> new LfLostLoad());
                lostLoad.getPowerShift().add(powerShift);
                lostLoad.getOriginalIds().add(loadId);
            }
        }

        // find hvdc lines that are part of this network
        Set<LfHvdc> lostHvdcs = hvdcIdsToOpen.stream()
                .map(network::getHvdcById)
                .filter(Objects::nonNull) // could be in another component
                .collect(Collectors.toSet());

        for (LfHvdc hvdcLine : network.getHvdcs()) {
            // FIXME
            // if we loose a bus with a converter station, the other converter station should be considered to if in the
            // same synchronous component (hvdc setpoint mode).
            if (busesToLost.contains(hvdcLine.getBus1()) || busesToLost.contains(hvdcLine.getBus2())) {
                lostHvdcs.add(hvdcLine);
            }
        }

        if (branchesToOpen.isEmpty()
                && busesToLost.isEmpty()
                && shunts.isEmpty()
                && loads.isEmpty()
                && generators.isEmpty()
                && lostHvdcs.isEmpty()) {
            LOGGER.debug("Contingency '{}' has no impact", contingency.getId());
            return Optional.empty();
        }

        return Optional.of(new LfContingency(contingency.getId(), index, connectivityLossImpact.createdSynchronousComponents, new DisabledNetwork(busesToLost, branchesToOpen, lostHvdcs), shunts, loads, generators));
    }
}
