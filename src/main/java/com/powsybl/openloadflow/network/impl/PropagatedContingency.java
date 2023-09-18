/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.commons.PowsyblException;
import com.powsybl.contingency.Contingency;
import com.powsybl.contingency.ContingencyElement;
import com.powsybl.iidm.network.*;
import com.powsybl.iidm.network.extensions.HvdcAngleDroopActivePowerControl;
import com.powsybl.iidm.network.extensions.LoadDetail;
import com.powsybl.openloadflow.graph.GraphConnectivity;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.util.PerUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 * @author Gaël Macherel <gael.macherel@artelys.com>
 */
public class PropagatedContingency {

    protected static final Logger LOGGER = LoggerFactory.getLogger(PropagatedContingency.class);

    private final Contingency contingency;

    private final int index;

    private final Set<Switch> switchesToOpen;

    private final Set<Terminal> terminalsToDisconnect;

    private final Set<String> busIdsToLose;

    private final Set<String> branchIdsToOpen = new LinkedHashSet<>();

    private final Set<String> hvdcIdsToOpen = new HashSet<>(); // for HVDC in AC emulation

    private final Set<String> generatorIdsToLose = new HashSet<>();

    private final Map<String, PowerShift> busIdsToShift = new HashMap<>();

    private final Map<String, AdmittanceShift> shuntIdsToShift = new HashMap<>();

    private final Set<String> originalPowerShiftIds = new LinkedHashSet<>();

    public Contingency getContingency() {
        return contingency;
    }

    public int getIndex() {
        return index;
    }

    public Set<String> getBusIdsToLose() {
        return busIdsToLose;
    }

    public Set<String> getBranchIdsToOpen() {
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

    public Map<String, PowerShift> getBusIdsToShift() {
        return busIdsToShift;
    }

    public Map<String, AdmittanceShift> getShuntIdsToShift() {
        return shuntIdsToShift;
    }

    public Set<String> getOriginalPowerShiftIds() {
        return originalPowerShiftIds;
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
                                                         boolean contingencyPropagation) {
        List<PropagatedContingency> propagatedContingencies = new ArrayList<>();
        for (int index = 0; index < contingencies.size(); index++) {
            Contingency contingency = contingencies.get(index);
            PropagatedContingency propagatedContingency =
                    PropagatedContingency.create(network, contingency, index, contingencyPropagation);
            propagatedContingencies.add(propagatedContingency);
            topoConfig.getSwitchesToOpen().addAll(propagatedContingency.switchesToOpen);
            topoConfig.getBusIdsToLose().addAll(propagatedContingency.busIdsToLose);
        }
        return propagatedContingencies;
    }

    public static List<PropagatedContingency> completeList(List<PropagatedContingency> propagatedContingencies, boolean shuntCompensatorVoltageControlOn,
                                                           boolean slackDistributionOnConformLoad, boolean hvdcAcEmulation, boolean breakers) {
        // complete definition of contingencies after network loading
        // in order to have the good busId.
        for (PropagatedContingency propagatedContingency : propagatedContingencies) {
            propagatedContingency.complete(shuntCompensatorVoltageControlOn, slackDistributionOnConformLoad, hvdcAcEmulation, breakers);
        }
        return propagatedContingencies;
    }

    private static PropagatedContingency create(Network network, Contingency contingency, int index, boolean contingencyPropagation) {
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
                    } else {
                        throw new UnsupportedOperationException("Unsupported contingency element type " + element.getType() + ": voltage level should be in bus/breaker topology");
                    }
                    break;
                case BUSBAR_SECTION:
                    if (contingencyPropagation) {
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
                    if (contingencyPropagation) {
                        ContingencyTripping.createContingencyTripping(network, identifiable).traverse(switchesToOpen, terminalsToDisconnect);
                    }
                    terminalsToDisconnect.addAll(getTerminals(identifiable));
            }
        }
        return new PropagatedContingency(contingency, index, switchesToOpen, terminalsToDisconnect, busIdsToLose);
    }

    private void complete(boolean shuntCompensatorVoltageControlOn, boolean slackDistributionOnConformLoad,
                         boolean hvdcAcEmulation, boolean breakers) {
        for (Switch sw : switchesToOpen) {
            branchIdsToOpen.add(sw.getId());
        }

        // process terminals disconnected, in particular process injection power shift
        for (Terminal terminal : terminalsToDisconnect) {
            Connectable<?> connectable = terminal.getConnectable();
            switch (connectable.getType()) {
                case LINE, TWO_WINDINGS_TRANSFORMER:
                    branchIdsToOpen.add(connectable.getId());
                    break;
                case DANGLING_LINE:
                    DanglingLine dl = (DanglingLine) connectable;
                    if (dl.isPaired()) {
                        branchIdsToOpen.add(dl.getTieLine().orElseThrow().getId());
                    } else {
                        branchIdsToOpen.add(dl.getId());
                    }
                    break;

                case GENERATOR, STATIC_VAR_COMPENSATOR, BATTERY:
                    generatorIdsToLose.add(connectable.getId());
                    break;

                case LOAD:
                    Load load = (Load) connectable;
                    originalPowerShiftIds.add(load.getId());
                    addPowerShift(load.getTerminal(), busIdsToShift, getLoadPowerShift(load, slackDistributionOnConformLoad), breakers);
                    break;

                case SHUNT_COMPENSATOR:
                    ShuntCompensator shunt = (ShuntCompensator) connectable;
                    if (shuntCompensatorVoltageControlOn && shunt.isVoltageRegulatorOn()) {
                        throw new UnsupportedOperationException("Shunt compensator '" + shunt.getId() + "' with voltage control on: not supported yet");
                    }
                    double zb = PerUnit.zb(shunt.getTerminal().getVoltageLevel().getNominalV());
                    shuntIdsToShift.put(shunt.getId(), new AdmittanceShift(shunt.getG() * zb,
                            shunt.getB() * zb));
                    break;

                case HVDC_CONVERTER_STATION:
                    HvdcConverterStation<?> station = (HvdcConverterStation<?>) connectable;
                    HvdcAngleDroopActivePowerControl control = station.getHvdcLine().getExtension(HvdcAngleDroopActivePowerControl.class);
                    if (control != null && control.isEnabled() && hvdcAcEmulation) {
                        hvdcIdsToOpen.add(station.getHvdcLine().getId());
                    }
                    // FIXME
                    // the other converter station should be considered to if in the same synchronous component (hvdc setpoint mode).
                    if (connectable instanceof VscConverterStation) {
                        generatorIdsToLose.add(connectable.getId());
                    } else {
                        LccConverterStation lcc = (LccConverterStation) connectable;
                        PowerShift lccPowerShift = new PowerShift(HvdcConverterStations.getConverterStationTargetP(lcc, breakers) / PerUnit.SB, 0,
                                HvdcConverterStations.getLccConverterStationLoadTargetQ(lcc, breakers) / PerUnit.SB);
                        originalPowerShiftIds.add(lcc.getId());
                        addPowerShift(lcc.getTerminal(), busIdsToShift, lccPowerShift, breakers);
                    }
                    break;

                case BUSBAR_SECTION:
                    // we don't care
                    break;

                case THREE_WINDINGS_TRANSFORMER:
                    branchIdsToOpen.add(connectable.getId() + "_leg_1");
                    branchIdsToOpen.add(connectable.getId() + "_leg_2");
                    branchIdsToOpen.add(connectable.getId() + "_leg_3");
                    break;

                default:
                    throw new UnsupportedOperationException("Unsupported by propagation contingency element type: "
                            + connectable.getType());
            }
        }
    }

    private static void addPowerShift(Terminal terminal, Map<String, PowerShift> busIdsToShift, PowerShift powerShift, boolean breakers) {
        Bus bus = breakers ? terminal.getBusBreakerView().getBus() : terminal.getBusView().getBus();
        if (bus != null) {
            busIdsToShift.computeIfAbsent(bus.getId(), k -> new PowerShift()).add(powerShift);
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
            case BRANCH, LINE, TWO_WINDINGS_TRANSFORMER:
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
        return branchIdsToOpen.isEmpty() && hvdcIdsToOpen.isEmpty() && generatorIdsToLose.isEmpty()
                && busIdsToShift.isEmpty() && shuntIdsToShift.isEmpty() && busIdsToLose.isEmpty();
    }

    public Optional<LfContingency> toLfContingency(LfNetwork network) {
        // update connectivity with triggered branches of this network
        GraphConnectivity<LfBus, LfBranch> connectivity = network.getConnectivity();
        connectivity.startTemporaryChanges();

        List<LfBranch> branchesToOpen = branchIdsToOpen.stream()
                .map(network::getBranchById)
                .filter(Objects::nonNull) // could be in another component
                .collect(Collectors.toList());

        // we add the branches connected to buses to lose.
        busIdsToLose.stream().map(network::getBusById)
                .filter(Objects::nonNull)
                .forEach(bus -> {
                    if (bus.isSlack()) {
                        // slack bus disabling is not supported
                        // we keep the slack bus enabled and the connected branches
                        LOGGER.error("Contingency '{}' leads to the loss of a slack bus: slack bus kept", bus.getId());
                    } else {
                        branchesToOpen.addAll(bus.getBranches());
                    }
                });

        branchesToOpen.stream()
                .filter(LfBranch::isConnectedAtBothSides)
                .forEach(connectivity::removeEdge);

        if (connectivity.getConnectedComponent(network.getSlackBus()).size() == 1) {
            // FIXME
            // If a contingency leads to an isolated slack bus, this bus is considered as the main component.
            // In that case, we have an issue with a different number of variables and equations.
            LOGGER.error("Contingency '{}' leads to an isolated slack bus: not supported", contingency.getId());
            connectivity.undoTemporaryChanges();
            return Optional.empty();
        }

        // add to contingency description buses and branches that won't be part of the main connected
        // component in post contingency state
        int createdSynchronousComponents = connectivity.getNbConnectedComponents() - 1;
        Set<LfBus> buses = connectivity.getVerticesRemovedFromMainComponent();
        Set<LfBranch> branches = new HashSet<>(connectivity.getEdgesRemovedFromMainComponent());

        // we should manage branches open at one side
        branchesToOpen.stream()
                .filter(b -> !b.isConnectedAtBothSides())
                .forEach(branches::add);
        for (LfBus bus : buses) {
            bus.getBranches().stream().filter(b -> !b.isConnectedAtBothSides()).forEach(branches::add);
        }

        // reset connectivity to discard triggered branches
        connectivity.undoTemporaryChanges();

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

        Map<LfBus, PowerShift> busesLoadShift = new HashMap<>(1);
        for (var e : busIdsToShift.entrySet()) {
            String busId = e.getKey();
            PowerShift shift = e.getValue();
            LfBus bus = network.getBusById(busId);
            if (bus != null) { // could be in another component
                busesLoadShift.put(bus, shift);
            }
        }

        // find hvdc lines that are part of this network
        Set<LfHvdc> hvdcs = hvdcIdsToOpen.stream()
                .map(network::getHvdcById)
                .filter(Objects::nonNull) // could be in another component
                .collect(Collectors.toSet());

        for (LfHvdc hvdcLine : network.getHvdcs()) {
            // FIXME
            // if we loose a bus with a converter station, the other converter station should be considered to if in the
            // same synchronous component (hvdc setpoint mode).
            if (buses.contains(hvdcLine.getBus1()) || buses.contains(hvdcLine.getBus2())) {
                hvdcs.add(hvdcLine);
            }
        }

        if (branches.isEmpty()
                && buses.isEmpty()
                && shunts.isEmpty()
                && busesLoadShift.isEmpty()
                && generators.isEmpty()
                && hvdcs.isEmpty()) {
            LOGGER.debug("Contingency '{}' has no impact", contingency.getId());
            return Optional.empty();
        }

        return Optional.of(new LfContingency(contingency.getId(), index, createdSynchronousComponents, new DisabledNetwork(buses, branches, hvdcs), shunts, busesLoadShift, generators, originalPowerShiftIds));
    }
}
