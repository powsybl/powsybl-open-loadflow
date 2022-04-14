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
import com.powsybl.iidm.network.extensions.LoadDetail;
import com.powsybl.openloadflow.graph.GraphDecrementalConnectivity;
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

    private static final Logger LOGGER = LoggerFactory.getLogger(PropagatedContingency.class);

    private final Contingency contingency;

    private final int index;

    private final Set<String> branchIdsToOpen;

    private final Set<Switch> switchesToOpen;

    private final Set<String> hvdcIdsToOpen;

    private final Set<String> generatorIdsToLose;

    private final Map<String, PowerShift> loadIdsToShift;

    private final Map<String, Double> shuntIdsToShift;

    public Contingency getContingency() {
        return contingency;
    }

    public int getIndex() {
        return index;
    }

    public Set<String> getBranchIdsToOpen() {
        return branchIdsToOpen;
    }

    public Set<String> getHvdcIdsToOpen() {
        return hvdcIdsToOpen;
    }

    public Set<String> getGeneratorIdsToLose() {
        return generatorIdsToLose;
    }

    public Map<String, PowerShift> getLoadIdsToShift() {
        return loadIdsToShift;
    }

    public Map<String, Double> getShuntIdsToShift() {
        return shuntIdsToShift;
    }

    public PropagatedContingency(Contingency contingency, int index, Set<String> branchIdsToOpen, Set<String> hvdcIdsToOpen,
                                 Set<Switch> switchesToOpen, Set<String> generatorIdsToLose,
                                 Map<String, PowerShift> loadIdsToShift, Map<String, Double> shuntIdsToShift) {
        this.contingency = Objects.requireNonNull(contingency);
        this.index = index;
        this.branchIdsToOpen = Objects.requireNonNull(branchIdsToOpen);
        this.hvdcIdsToOpen = Objects.requireNonNull(hvdcIdsToOpen);
        this.switchesToOpen = Objects.requireNonNull(switchesToOpen);
        this.generatorIdsToLose = Objects.requireNonNull(generatorIdsToLose);
        this.loadIdsToShift = Objects.requireNonNull(loadIdsToShift);
        this.shuntIdsToShift = Objects.requireNonNull(shuntIdsToShift);

        // WTF?????
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
                              load.getQ0() / PerUnit.SB);
    }

    public static List<PropagatedContingency> createListForSensitivityAnalysis(Network network, List<Contingency> contingencies,
                                                                               boolean slackDistributionOnConformLoad) {
        List<PropagatedContingency> propagatedContingencies = new ArrayList<>();
        for (int index = 0; index < contingencies.size(); index++) {
            Contingency contingency = contingencies.get(index);
            PropagatedContingency propagatedContingency = PropagatedContingency.create(network, contingency, index, false, false, slackDistributionOnConformLoad);
            Optional<Switch> coupler = propagatedContingency.switchesToOpen.stream().filter(PropagatedContingency::isCoupler).findFirst();
            if (coupler.isEmpty()) {
                propagatedContingencies.add(propagatedContingency);
            } else {
                // Sensitivity analysis works in bus view, it cannot deal (yet)  with contingencies whose propagation encounters a coupler
                LOGGER.warn("Propagated contingency '{}' not processed: coupler '{}' has been encountered while propagating the contingency",
                    contingency.getId(), coupler.get().getId());
            }
        }
        return propagatedContingencies;
    }

    public static List<PropagatedContingency> createListForSecurityAnalysis(Network network, List<Contingency> contingencies,
                                                                            Set<Switch> allSwitchesToOpen, boolean shuntCompensatorVoltageControlOn,
                                                                            boolean slackDistributionOnConformLoad) {
        List<PropagatedContingency> propagatedContingencies = new ArrayList<>();
        for (int index = 0; index < contingencies.size(); index++) {
            Contingency contingency = contingencies.get(index);
            PropagatedContingency propagatedContingency =
                    PropagatedContingency.create(network, contingency, index, shuntCompensatorVoltageControlOn, true, slackDistributionOnConformLoad);
            propagatedContingencies.add(propagatedContingency);
            allSwitchesToOpen.addAll(propagatedContingency.switchesToOpen);
        }
        return propagatedContingencies;
    }

    private static PropagatedContingency create(Network network, Contingency contingency, int index, boolean shuntCompensatorVoltageControlOn,
                                                boolean withBreakers, boolean slackDistributionOnConformLoad) {
        Set<Switch> switchesToOpen = new HashSet<>();
        Set<Terminal> terminalsToDisconnect =  new HashSet<>();
        Set<String> branchIdsToOpen = new LinkedHashSet<>();
        Set<String> hvdcIdsToOpen = new HashSet<>();
        Set<Load> loadsToLose = new HashSet<>();
        Set<Generator> generatorsToLose = new HashSet<>();
        Set<ShuntCompensator> shuntsToLose = new HashSet<>();

        // process elements of the contingency
        for (ContingencyElement element : contingency.getElements()) {
            switch (element.getType()) {
                case BRANCH:
                case LINE:
                case TWO_WINDINGS_TRANSFORMER:
                    // branch check is done inside tripping
                    ContingencyTripping.createBranchTripping(network, element.getId())
                        .traverse(switchesToOpen, terminalsToDisconnect);
                    break;
                case HVDC_LINE:
                    HvdcLine hvdcLine = network.getHvdcLine(element.getId());
                    if (hvdcLine == null) {
                        throw new PowsyblException("HVDC line '" + element.getId() + "' not found in the network");
                    }
                    hvdcIdsToOpen.add(element.getId());
                    break;
                case DANGLING_LINE:
                    // dangling line check is done inside tripping
                    ContingencyTripping.createDanglingLineTripping(network, element.getId())
                        .traverse(switchesToOpen, terminalsToDisconnect);
                    break;
                case GENERATOR:
                    Generator generator = network.getGenerator(element.getId());
                    if (generator == null) {
                        throw new PowsyblException("Generator '" + element.getId() + "' not found in the network");
                    }
                    generatorsToLose.add(generator);
                    break;
                case LOAD:
                    Load load = network.getLoad(element.getId());
                    if (load == null) {
                        throw new PowsyblException("Load '" + element.getId() + "' not found in the network");
                    }
                    loadsToLose.add(load);
                    break;
                case SHUNT_COMPENSATOR:
                    ShuntCompensator shuntCompensator = network.getShuntCompensator(element.getId());
                    if (shuntCompensator == null) {
                        throw new PowsyblException("Shunt compensator '" + element.getId() + "' not found in the network");
                    }
                    if (shuntCompensatorVoltageControlOn && shuntCompensator.isVoltageRegulatorOn()) {
                        throw new UnsupportedOperationException("Shunt compensator '" + element.getId() + "' with voltage control on: not supported yet");
                    }
                    shuntsToLose.add(shuntCompensator);
                    break;
                default:
                    throw new UnsupportedOperationException("Unsupported contingency element type: " + element.getType());
            }
        }

        // then process other elements resulting of propagation of initial elements
        for (Terminal terminal : terminalsToDisconnect) {
            Connectable<?> connectable = terminal.getConnectable();
            switch (connectable.getType()) {
                case LINE:
                case TWO_WINDINGS_TRANSFORMER:
                case DANGLING_LINE:
                    branchIdsToOpen.add(connectable.getId());
                    break;

                case GENERATOR:
                    generatorsToLose.add((Generator) connectable);
                    break;

                case LOAD:
                    loadsToLose.add((Load) connectable);
                    break;

                case SHUNT_COMPENSATOR:
                    shuntsToLose.add((ShuntCompensator) connectable);
                    break;

                case BUSBAR_SECTION:
                    // we don't care
                    break;

                default:
                    throw new UnsupportedOperationException("Unsupported by propagation contingency element type: "
                            + connectable.getType());
            }
        }

        // then process injection power shift
        Set<String> generatorIdsToLose = new HashSet<>();
        Map<String, PowerShift> loadIdsToShift = new HashMap<>();
        Map<String, Double> shuntIdsToShift = new HashMap<>();
        for (Generator generator : generatorsToLose) {
            generatorIdsToLose.add(generator.getId());
        }
        for (Load load : loadsToLose) {
            Bus bus = withBreakers ? load.getTerminal().getBusBreakerView().getBus()
                                   : load.getTerminal().getBusView().getBus();
            if (bus != null) {
                loadIdsToShift.computeIfAbsent(bus.getId(), k -> new PowerShift())
                        .add(getLoadPowerShift(load, slackDistributionOnConformLoad));
            }
        }
        for (ShuntCompensator shunt : shuntsToLose) {
            double nominalV = shunt.getTerminal().getVoltageLevel().getNominalV();
            shuntIdsToShift.put(shunt.getId(), shunt.getB() * nominalV * nominalV / PerUnit.SB);
        }

        return new PropagatedContingency(contingency, index, branchIdsToOpen, hvdcIdsToOpen, switchesToOpen,
                                         generatorIdsToLose, loadIdsToShift, shuntIdsToShift);
    }

    private static boolean isCoupler(Switch s) {
        VoltageLevel.NodeBreakerView nbv = s.getVoltageLevel().getNodeBreakerView();
        Terminal terminal1 = nbv.getTerminal1(s.getId());
        Terminal terminal2 = nbv.getTerminal2(s.getId());
        if (terminal1 == null || terminal2 == null) {
            return false; // FIXME: this can be a coupler, a traverser could be used to detect it
        }
        Connectable<?> c1 = terminal1.getConnectable();
        Connectable<?> c2 = terminal2.getConnectable();
        return c1 != c2 && c1.getType() == IdentifiableType.BUSBAR_SECTION && c2.getType() == IdentifiableType.BUSBAR_SECTION;
    }

    public Optional<LfContingency> toLfContingency(LfNetwork network, boolean useSmallComponents) {
        // find contingency branches that are part of this network
        Set<LfBranch> branches = branchIdsToOpen.stream()
                .map(network::getBranchById)
                .filter(Objects::nonNull) // could be in another component
                .collect(Collectors.toSet());

        // update connectivity with triggered branches
        GraphDecrementalConnectivity<LfBus> connectivity = network.getConnectivity();
        for (LfBranch branch : branches) {
            connectivity.cut(branch.getBus1(), branch.getBus2());
        }

        // add to contingency description buses and branches that won't be part of the main connected
        // component in post contingency state
        Set<LfBus> buses;
        if (useSmallComponents) {
            buses = connectivity.getSmallComponents().stream().flatMap(Set::stream).collect(Collectors.toSet());
        } else {
            int slackBusComponent = connectivity.getComponentNumber(network.getSlackBus());
            buses = network.getBuses().stream().filter(b -> connectivity.getComponentNumber(b) != slackBusComponent).collect(Collectors.toSet());
        }
        buses.forEach(b -> branches.addAll(b.getBranches()));

        // reset connectivity to discard triggered branches
        connectivity.reset();

        if (!hvdcIdsToOpen.isEmpty()) {
            throw new UnsupportedOperationException("HVDC line contingency not supported");
        }

        Map<LfShunt, Double> shunts = new HashMap<>(1);
        for (var e : shuntIdsToShift.entrySet()) {
            LfShunt shunt = network.getShuntById(e.getKey());
            if (shunt != null) { // could be in another component
                double oldB = shunts.getOrDefault(shunt, 0d);
                shunts.put(shunt, oldB + e.getValue());
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
        for (var e : loadIdsToShift.entrySet()) {
            String busId = e.getKey();
            PowerShift shift = e.getValue();
            LfBus bus = network.getBusById(busId);
            if (bus != null) { // could be in another component
                busesLoadShift.put(bus, shift);
            }
        }

        if (branches.isEmpty()
                && buses.isEmpty()
                && shunts.isEmpty()
                && busesLoadShift.isEmpty()
                && generators.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new LfContingency(contingency.getId(), index, buses, branches, shunts, busesLoadShift, generators));
    }
}
