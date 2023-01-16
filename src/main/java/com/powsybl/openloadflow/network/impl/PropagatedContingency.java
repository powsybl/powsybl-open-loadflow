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

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 * @author Gaël Macherel <gael.macherel@artelys.com>
 */
public class PropagatedContingency {

    private final Contingency contingency;

    private final int index;

    private final Set<String> branchIdsToOpen;

    private final Set<Switch> switchesToOpen;

    private final Set<String> hvdcIdsToOpen; // for HVDC in AC emulation

    private final Set<String> generatorIdsToLose;

    private final Map<String, PowerShift> busIdsToShift;

    private final Map<String, AdmittanceShift> shuntIdsToShift;

    private final Set<String> originalPowerShiftIds;

    public Contingency getContingency() {
        return contingency;
    }

    public int getIndex() {
        return index;
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

    public PropagatedContingency(Contingency contingency, int index, Set<String> branchIdsToOpen, Set<String> hvdcIdsToOpen,
                                 Set<Switch> switchesToOpen, Set<String> generatorIdsToLose,
                                 Map<String, PowerShift> busIdsToShift, Map<String, AdmittanceShift> shuntIdsToShift,
                                 Set<String> originalPowerShiftIds) {
        this.contingency = Objects.requireNonNull(contingency);
        this.index = index;
        this.branchIdsToOpen = Objects.requireNonNull(branchIdsToOpen);
        this.hvdcIdsToOpen = Objects.requireNonNull(hvdcIdsToOpen);
        this.switchesToOpen = Objects.requireNonNull(switchesToOpen);
        this.generatorIdsToLose = Objects.requireNonNull(generatorIdsToLose);
        this.busIdsToShift = Objects.requireNonNull(busIdsToShift);
        this.shuntIdsToShift = Objects.requireNonNull(shuntIdsToShift);
        this.originalPowerShiftIds = Objects.requireNonNull(originalPowerShiftIds);

        for (Switch sw : switchesToOpen) {
            branchIdsToOpen.add(sw.getId());
        }
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

    public static List<PropagatedContingency> createList(Network network, List<Contingency> contingencies,
                                                         Set<Switch> allSwitchesToOpen, boolean shuntCompensatorVoltageControlOn,
                                                         boolean slackDistributionOnConformLoad, boolean hvdcAcEmulation,
                                                         boolean contingencyPropagation) {
        List<PropagatedContingency> propagatedContingencies = new ArrayList<>();
        for (int index = 0; index < contingencies.size(); index++) {
            Contingency contingency = contingencies.get(index);
            PropagatedContingency propagatedContingency =
                    PropagatedContingency.create(network, contingency, index, shuntCompensatorVoltageControlOn, slackDistributionOnConformLoad, hvdcAcEmulation, contingencyPropagation);
            propagatedContingencies.add(propagatedContingency);
            allSwitchesToOpen.addAll(propagatedContingency.switchesToOpen);
        }
        return propagatedContingencies;
    }

    private static PropagatedContingency create(Network network, Contingency contingency, int index, boolean shuntCompensatorVoltageControlOn,
                                                boolean slackDistributionOnConformLoad, boolean hvdcAcEmulation, boolean contingencyPropagation) {
        Set<Switch> switchesToOpen = new HashSet<>();
        Set<Terminal> terminalsToDisconnect = new HashSet<>();

        // process elements of the contingency
        for (ContingencyElement element : contingency.getElements()) {
            Identifiable<?> identifiable = getIdentifiable(network, element);
            if (contingencyPropagation) {
                ContingencyTripping.createContingencyTripping(network, identifiable).traverse(switchesToOpen, terminalsToDisconnect);
            } else if (identifiable instanceof BusbarSection) {
                ContingencyTripping.createBusbarSectionMinimalTripping(network, (BusbarSection) identifiable).traverse(switchesToOpen, terminalsToDisconnect);
            }
            terminalsToDisconnect.addAll(getTerminals(identifiable));
            if (identifiable instanceof Switch) {
                switchesToOpen.add((Switch) identifiable);
            }
        }

        boolean breakers = !switchesToOpen.isEmpty();

        Set<String> branchIdsToOpen = new LinkedHashSet<>();
        Set<String> hvdcIdsToOpen = new HashSet<>();
        Set<String> generatorIdsToLose = new HashSet<>();
        Map<String, PowerShift> busIdsToShift = new HashMap<>();
        Map<String, AdmittanceShift> shuntIdsToShift = new HashMap<>();
        Set<String> originalPowerShiftIds = new LinkedHashSet<>();

        // process terminals disconnected, in particular process injection power shift
        for (Terminal terminal : terminalsToDisconnect) {
            Connectable<?> connectable = terminal.getConnectable();
            switch (connectable.getType()) {
                case LINE:
                case TWO_WINDINGS_TRANSFORMER:
                case DANGLING_LINE:
                    branchIdsToOpen.add(connectable.getId());
                    break;

                case GENERATOR:
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
                    double nominalV = shunt.getTerminal().getVoltageLevel().getNominalV();
                    shuntIdsToShift.put(shunt.getId(), new AdmittanceShift(-shunt.getG() * nominalV * nominalV / PerUnit.SB,
                            shunt.getB() * nominalV * nominalV / PerUnit.SB));
                    break;

                case HVDC_CONVERTER_STATION:
                    HvdcConverterStation<?> station = (HvdcConverterStation<?>) connectable;
                    HvdcAngleDroopActivePowerControl control = station.getHvdcLine().getExtension(HvdcAngleDroopActivePowerControl.class);
                    if (control != null && control.isEnabled() && hvdcAcEmulation) {
                        hvdcIdsToOpen.add(station.getHvdcLine().getId());
                    }
                    if (connectable instanceof VscConverterStation) {
                        generatorIdsToLose.add(connectable.getId());
                    } else {
                        LccConverterStation lcc = (LccConverterStation) connectable;
                        PowerShift lccPowerShift = new PowerShift(HvdcConverterStations.getConverterStationTargetP(lcc) / PerUnit.SB, 0,
                                HvdcConverterStations.getLccConverterStationLoadTargetQ(lcc) / PerUnit.SB);
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

        return new PropagatedContingency(contingency, index, branchIdsToOpen, hvdcIdsToOpen, switchesToOpen,
                                         generatorIdsToLose, busIdsToShift, shuntIdsToShift, originalPowerShiftIds);
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
        if (identifiable instanceof HvdcLine) {
            HvdcLine hvdcLine = (HvdcLine) identifiable;
            return Arrays.asList(hvdcLine.getConverterStation1().getTerminal(), hvdcLine.getConverterStation2().getTerminal());
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
            case BRANCH:
            case LINE:
            case TWO_WINDINGS_TRANSFORMER:
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
            default:
                throw new UnsupportedOperationException("Unsupported contingency element type: " + element.getType());
        }
        if (identifiable == null) {
            throw new PowsyblException(identifiableType + " '" + element.getId() + "' not found in the network");
        }
        return identifiable;
    }

    public Optional<LfContingency> toLfContingency(LfNetwork network) {
        // update connectivity with triggered branches of this network
        GraphConnectivity<LfBus, LfBranch> connectivity = network.getConnectivity();
        connectivity.startTemporaryChanges();
        branchIdsToOpen.stream()
                .map(network::getBranchById)
                .filter(Objects::nonNull) // could be in another component
                .filter(b -> b.getBus1() != null && b.getBus2() != null)
                .forEach(connectivity::removeEdge);

        // add to contingency description buses and branches that won't be part of the main connected
        // component in post contingency state
        Set<LfBus> buses = connectivity.getVerticesRemovedFromMainComponent();
        Set<LfBranch> branches = new HashSet<>(connectivity.getEdgesRemovedFromMainComponent());

        // we should manage branches open at one side.
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

        if (branches.isEmpty()
                && buses.isEmpty()
                && shunts.isEmpty()
                && busesLoadShift.isEmpty()
                && generators.isEmpty()
                && hvdcs.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new LfContingency(contingency.getId(), index, buses, branches, shunts, busesLoadShift, generators, hvdcs, originalPowerShiftIds));
    }
}
