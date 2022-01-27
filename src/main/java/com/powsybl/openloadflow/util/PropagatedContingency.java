/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.util;

import com.powsybl.commons.PowsyblException;
import com.powsybl.contingency.Contingency;
import com.powsybl.contingency.ContingencyElement;
import com.powsybl.iidm.network.*;
import com.powsybl.iidm.network.extensions.LoadDetail;
import com.powsybl.openloadflow.network.PerUnit;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

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

    private final Set<Pair<String, Double>> generatorIdsToLose;

    private final Set<Triple<String, Pair<Double, Double>, Double>> loadIdsToLose;

    private final Set<Pair<String, Double>> shuntIdsToLose;

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

    public Set<Pair<String, Double>> getGeneratorIdsToLose() {
        return generatorIdsToLose;
    }

    public Set<Triple<String, Pair<Double, Double>, Double>> getLoadIdsToLose() {
        return loadIdsToLose;
    }

    public Set<Pair<String, Double>> getShuntIdsToLose() {
        return shuntIdsToLose;
    }

    public PropagatedContingency(Contingency contingency, int index, Set<String> branchIdsToOpen, Set<String> hvdcIdsToOpen,
                                 Set<Switch> switchesToOpen, Set<Terminal> terminalsToDisconnect, Set<Pair<String, Double>> generatorIdsToLose,
                                 Set<Triple<String, Pair<Double, Double>, Double>> loadIdsToLose, Set<Pair<String, Double>> shuntIdsToLose, boolean withBreakers,
                                 boolean slackDistributionOnConformLoad) {
        this.contingency = Objects.requireNonNull(contingency);
        this.index = index;
        this.branchIdsToOpen = Objects.requireNonNull(branchIdsToOpen);
        this.hvdcIdsToOpen = Objects.requireNonNull(hvdcIdsToOpen);
        this.switchesToOpen = Objects.requireNonNull(switchesToOpen);
        this.generatorIdsToLose = Objects.requireNonNull(generatorIdsToLose);
        this.loadIdsToLose = Objects.requireNonNull(loadIdsToLose);
        this.shuntIdsToLose = Objects.requireNonNull(shuntIdsToLose);

        for (Switch sw : switchesToOpen) {
            branchIdsToOpen.add(sw.getId());
        }

        for (Terminal terminal : terminalsToDisconnect) {
            if (terminal.getConnectable() instanceof Branch) {
                branchIdsToOpen.add(terminal.getConnectable().getId());
            }
            if (terminal.getConnectable() instanceof Generator) {
                Generator generator = (Generator) terminal.getConnectable();
                generatorIdsToLose.add(getGeneratorInfo(generator));
            }
            if (terminal.getConnectable() instanceof Load) {
                Load load = (Load) terminal.getConnectable();
                loadIdsToLose.add(getLoadInfo(load, withBreakers, slackDistributionOnConformLoad));
            }
            if (terminal.getConnectable() instanceof ShuntCompensator) {
                ShuntCompensator shuntCompensator = (ShuntCompensator) terminal.getConnectable();
                shuntIdsToLose.add(getShuntCompensatorInfo(shuntCompensator));
            }
        }
    }

    private static Pair<String, Double> getShuntCompensatorInfo(ShuntCompensator shuntCompensator) {
        double nominalV = shuntCompensator.getTerminal().getVoltageLevel().getNominalV();
        return Pair.of(shuntCompensator.getId(), shuntCompensator.getB() * nominalV * nominalV / PerUnit.SB);
    }

    private static Pair<String, Double> getGeneratorInfo(Generator generator) {
        return Pair.of(generator.getId(), generator.getTargetP());
    }

    private static Triple<String, Pair<Double, Double>, Double> getLoadInfo(Load load, boolean withBreakers, boolean slackDistributionOnConformLoad) {
        double variableActivePower = Math.abs(load.getP0());
        if (slackDistributionOnConformLoad) {
            variableActivePower = load.getExtension(LoadDetail.class) == null ? 0.0 : Math.abs(load.getExtension(LoadDetail.class).getVariableActivePower());
        }
        Bus bus = withBreakers ? load.getTerminal().getBusBreakerView().getBus() : load.getTerminal().getBusView().getBus();
        return bus != null ? Triple.of(bus.getId(), Pair.of(load.getP0() / PerUnit.SB, variableActivePower / PerUnit.SB), load.getQ0() / PerUnit.SB) : null;
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
        Set<String> branchIdsToOpen = new HashSet<>();
        Set<String> hvdcIdsToOpen = new HashSet<>();
        Set<Pair<String, Double>> generatorIdsToLose = new HashSet<>();
        Set<Triple<String, Pair<Double, Double>, Double>> loadIdsToLose = new HashSet<>();
        Set<Pair<String, Double>> shuntIdsToLose = new HashSet<>();
        for (ContingencyElement element : contingency.getElements()) {
            switch (element.getType()) {
                case BRANCH:
                case LINE:
                case TWO_WINDINGS_TRANSFORMER:
                    // branch check is done inside tripping
                    ContingencyTripping.createBranchTripping(network, element.getId())
                        .traverse(switchesToOpen, terminalsToDisconnect);
                    branchIdsToOpen.add(element.getId());
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
                    branchIdsToOpen.add(element.getId());
                    break;
                case GENERATOR:
                    Generator generator = network.getGenerator(element.getId());
                    if (generator == null) {
                        throw new PowsyblException("Generator '" + element.getId() + "' not found in the network");
                    }
                    generatorIdsToLose.add(getGeneratorInfo(generator));
                    break;
                case LOAD:
                    Load load = network.getLoad(element.getId());
                    if (load == null) {
                        throw new PowsyblException("Load '" + element.getId() + "' not found in the network");
                    }
                    loadIdsToLose.add(getLoadInfo(load, withBreakers, slackDistributionOnConformLoad));
                    break;
                case SHUNT_COMPENSATOR:
                    ShuntCompensator shuntCompensator = network.getShuntCompensator(element.getId());
                    if (shuntCompensator == null) {
                        throw new PowsyblException("Shunt compensator '" + element.getId() + "' not found in the network");
                    }
                    if (shuntCompensatorVoltageControlOn && shuntCompensator.isVoltageRegulatorOn()) {
                        throw new UnsupportedOperationException("Shunt compensator '" + element.getId() + "' with voltage control on: not supported yet");
                    }
                    shuntIdsToLose.add(getShuntCompensatorInfo(shuntCompensator));
                    break;
                default:
                    //TODO: support all kinds of contingencies
                    throw new UnsupportedOperationException("TODO");
            }
        }

        return new PropagatedContingency(contingency, index, branchIdsToOpen, hvdcIdsToOpen, switchesToOpen, terminalsToDisconnect,
                generatorIdsToLose, loadIdsToLose, shuntIdsToLose, withBreakers, slackDistributionOnConformLoad);
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
}
