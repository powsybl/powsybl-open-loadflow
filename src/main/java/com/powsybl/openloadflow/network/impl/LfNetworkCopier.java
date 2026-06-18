/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.report.ReportNode;
import com.powsybl.openloadflow.network.GeneratorReactivePowerControl;
import com.powsybl.openloadflow.network.GeneratorVoltageControl;
import com.powsybl.openloadflow.network.LfArea;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfGenerator;
import com.powsybl.openloadflow.network.LfHvdc;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.LfOverloadManagementSystem;
import com.powsybl.openloadflow.network.LfSecondaryVoltageControl;
import com.powsybl.openloadflow.network.LfShunt;
import com.powsybl.openloadflow.network.LfSynchronousNetwork;
import com.powsybl.openloadflow.network.LfVscConverterStation;
import com.powsybl.openloadflow.network.LoadFlowModel;
import com.powsybl.openloadflow.network.ShuntVoltageControl;
import com.powsybl.openloadflow.network.TransformerPhaseControl;
import com.powsybl.openloadflow.network.TransformerReactivePowerControl;
import com.powsybl.openloadflow.network.TransformerVoltageControl;
import com.powsybl.openloadflow.network.VoltageSourceConverterVoltageControl;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Creates deep copies of a built {@link LfNetwork}, sharing the immutable IIDM references but
 * duplicating all the LF state, so that several copies can be simulated concurrently (each one
 * being confined to its own thread).
 *
 * <p>The copy can be taken on a freshly built network or on a solved one (the simulation state,
 * e.g. solved voltages, distributed targets and PV to PQ switches, is preserved), including
 * networks whose initial topology was restored after the load (elements built closed for a closing
 * remedial action then reopened: the disabled flags and the removed connectivity edges are
 * reproduced). Solver injected
 * evaluables are reset to their defaults and lazily computed structures (connectivity, zero
 * impedance networks, slack and reference bus selection, limits caches) are left to be recomputed
 * by the copy, exactly as on a freshly loaded network.</p>
 *
 * <p>Networks using unknown element implementations are rejected:
 * check {@link #canCopy(LfNetwork)} first and fall back to a rebuild from IIDM.</p>
 *
 * @author Gautier Bureau {@literal <gautier.bureau at rte-france.com>}
 */
public final class LfNetworkCopier {

    private LfNetworkCopier() {
    }

    /**
     * Check that the network only uses features supported by {@link #copy(LfNetwork, LoadFlowModel, ReportNode)}.
     */
    public static boolean canCopy(LfNetwork network) {
        Objects.requireNonNull(network);
        return network.getDcBuses().stream().allMatch(LfDcBusImpl.class::isInstance)
                && network.getDcLines().stream().allMatch(LfDcLineImpl.class::isInstance)
                && network.getVoltageSourceConverters().stream().allMatch(LfVoltageSourceConverterImpl.class::isInstance)
                && network.getBuses().stream().allMatch(LfNetworkCopier::isSupportedBus)
                && network.getBranches().stream().allMatch(LfNetworkCopier::isSupportedBranch)
                && network.getHvdcs().stream().allMatch(LfHvdcImpl.class::isInstance)
                && network.getAreas().stream().allMatch(LfAreaImpl.class::isInstance);
    }

    private static boolean isSupportedBus(LfBus bus) {
        if (!(bus instanceof LfBusImpl || bus instanceof LfStarBus || bus instanceof LfBoundaryLineBus)) {
            return false;
        }
        return bus.getGenerators().stream().allMatch(LfNetworkCopier::isSupportedGenerator)
                && bus.getLoads().stream().allMatch(LfLoadImpl.class::isInstance)
                && bus.getShunt().filter(s -> !(s instanceof LfShuntImpl)).isEmpty()
                && bus.getControllerShunt().filter(s -> !(s instanceof LfShuntImpl)).isEmpty();
    }

    private static boolean isSupportedGenerator(LfGenerator generator) {
        return generator instanceof LfGeneratorImpl || generator instanceof LfBatteryImpl
                || generator instanceof LfStaticVarCompensatorImpl || generator instanceof LfVscConverterStationImpl
                || generator instanceof LfBoundaryLineGenerator;
    }

    private static boolean isSupportedBranch(LfBranch branch) {
        return branch instanceof LfBranchImpl || branch instanceof LfLegBranch || branch instanceof LfSwitch
                || branch instanceof LfTieLineBranch || branch instanceof LfBoundaryLineBranch;
    }

    /**
     * Deep copy of a built network.
     *
     * @param original the network to copy, freshly built or solved (but with unmodified topology)
     * @param loadFlowModel the model used to (re)validate the copy, as in
     *                      {@link LfNetwork#validate(LoadFlowModel, ReportNode)}
     * @param reportNode the report node of the copied network
     */
    public static LfNetwork copy(LfNetwork original, LoadFlowModel loadFlowModel, ReportNode reportNode) {
        Objects.requireNonNull(original);
        Objects.requireNonNull(loadFlowModel);
        Objects.requireNonNull(reportNode);

        LfNetwork copy = copyFlat(original, reportNode);
        copy.validate(loadFlowModel, null);
        return copy;
    }

    private static LfNetwork copyFlat(LfNetwork original, ReportNode reportNode) {
        LfNetwork copy = new LfNetwork(original, reportNode);

        // buses (with their owned generators, loads and shunts); nums are reassigned in the same
        // order by addBus, so they match the original ones. addBus also lazily recreates one
        // synchronous network per synchronous component, so the copy ends up with the same set.
        for (LfBus bus : original.getBuses()) {
            copy.addBus(copyBus(bus, copy));
        }
        // reproduce the per synchronous component state (excluded slack buses); slack and reference
        // selection is left to be lazily redone on the copy
        for (LfSynchronousNetwork originalSc : original.getSynchronousNetworks()) {
            copy.getSynchronousNetwork(originalSc.getNumSC()).copyStateFrom(originalSc);
        }

        // branches: addBranch rebuilds the bus to branches links in the same order
        for (LfBranch branch : original.getBranches()) {
            copy.addBranch(copyBranch(branch, copy));
        }
        // branches whose connectivity edge was removed by the initial topology restoration (networks
        // with elements disabled at build, e.g. switches built closed for a closing remedial action):
        // the copy's lazily rebuilt connectivity excludes them, making it equivalent to the original's
        for (LfBranch branch : original.getConnectivityRemovedBranches()) {
            copy.addConnectivityRemovedBranch(copy.getBranchById(branch.getId()));
        }

        // DC part of AC/DC networks: DC buses first, then the DC lines connecting them, then the
        // voltage source converters linking a DC bus to an AC bus (all owned by the same network now)
        for (var dcBus : original.getDcBuses()) {
            copy.addDcBus(new LfDcBusImpl((LfDcBusImpl) dcBus, copy));
        }
        for (var dcLine : original.getDcLines()) {
            copy.addDcLine(new LfDcLineImpl((LfDcLineImpl) dcLine, copy,
                    copy.getDcBusById(dcLine.getDcBus1().getId()),
                    copy.getDcBusById(dcLine.getDcBus2().getId())));
        }
        for (var converter : original.getVoltageSourceConverters()) {
            copy.addVoltageSourceConverter(new LfVoltageSourceConverterImpl((LfVoltageSourceConverterImpl) converter, copy,
                    copy.getDcBusById(converter.getDcBus1().getId()),
                    copy.getDcBusById(converter.getDcBus2().getId()),
                    copy.getBusById(converter.getBus1().getId())));
        }

        for (LfHvdc hvdc : original.getHvdcs()) {
            copy.addHvdc(copyHvdc((LfHvdcImpl) hvdc, copy));
        }

        for (LfArea area : original.getAreas()) {
            copy.addArea(copyArea((LfAreaImpl) area, copy));
        }

        Map<GeneratorVoltageControl, GeneratorVoltageControl> generatorVoltageControlMap = copyControls(original, copy);

        for (LfSecondaryVoltageControl secondaryVoltageControl : original.getSecondaryVoltageControls()) {
            copy.addSecondaryVoltageControl(new LfSecondaryVoltageControl(
                    secondaryVoltageControl.getZoneName(),
                    copy.getBusById(secondaryVoltageControl.getPilotBus().getId()),
                    secondaryVoltageControl.getTargetValue(),
                    new LinkedHashSet<>(secondaryVoltageControl.getParticipatingControlUnitIds()),
                    secondaryVoltageControl.getGeneratorVoltageControls().stream()
                            .map(vc -> Objects.requireNonNull(generatorVoltageControlMap.get(vc),
                                    "Generator voltage control not copied"))
                            .collect(Collectors.toCollection(LinkedHashSet::new))));
        }

        for (LfNetwork.LfVoltageAngleLimit limit : original.getVoltageAngleLimits()) {
            copy.addVoltageAngleLimit(new LfNetwork.LfVoltageAngleLimit(limit.getId(),
                    copy.getBusById(limit.getFrom().getId()), copy.getBusById(limit.getTo().getId()),
                    limit.getHighValue(), limit.getLowValue()));
        }

        for (LfOverloadManagementSystem system : original.getOverloadManagementSystems()) {
            LfOverloadManagementSystem copiedSystem = new LfOverloadManagementSystem(
                    copy.getBranchById(system.getMonitoredBranch().getId()), system.getMonitoredSide());
            for (LfOverloadManagementSystem.LfBranchTripping tripping : system.getBranchTrippingList()) {
                copiedSystem.addLfBranchTripping(copy.getBranchById(tripping.branchToOperate().getId()),
                        tripping.branchOpen(), tripping.threshold());
            }
            copy.addOverloadManagementSystem(copiedSystem);
        }

        return copy;
    }

    private static AbstractLfBus copyBus(LfBus bus, LfNetwork network) {
        if (bus instanceof LfBusImpl busImpl) {
            return new LfBusImpl(busImpl, network);
        } else if (bus instanceof LfStarBus starBus) {
            return new LfStarBus(starBus, network);
        } else if (bus instanceof LfBoundaryLineBus boundaryLineBus) {
            return new LfBoundaryLineBus(boundaryLineBus, network);
        }
        throw new PowsyblException("Copy of bus type " + bus.getClass().getSimpleName() + " is not supported");
    }

    static LfGenerator copyGenerator(LfGenerator generator, LfNetwork network, AbstractLfBus bus) {
        Objects.requireNonNull(bus); // generator bus is set by AbstractLfBus.add
        if (generator instanceof LfGeneratorImpl generatorImpl) {
            return new LfGeneratorImpl(generatorImpl, network);
        } else if (generator instanceof LfBatteryImpl battery) {
            return new LfBatteryImpl(battery, network);
        } else if (generator instanceof LfStaticVarCompensatorImpl svc) {
            return new LfStaticVarCompensatorImpl(svc, network);
        } else if (generator instanceof LfVscConverterStationImpl station) {
            return new LfVscConverterStationImpl(station, network);
        } else if (generator instanceof LfBoundaryLineGenerator boundaryLineGenerator) {
            return new LfBoundaryLineGenerator(boundaryLineGenerator, network);
        }
        throw new PowsyblException("Copy of generator type " + generator.getClass().getSimpleName() + " is not supported");
    }

    private static LfBus copyOf(LfBus bus, LfNetwork network) {
        return bus == null ? null : network.getBusById(bus.getId());
    }

    private static LfBranch copyBranch(LfBranch branch, LfNetwork network) {
        LfBus bus1 = copyOf(branch.getBus1(), network);
        LfBus bus2 = copyOf(branch.getBus2(), network);
        if (branch instanceof LfBranchImpl branchImpl) {
            return new LfBranchImpl(branchImpl, network, bus1, bus2);
        } else if (branch instanceof LfLegBranch legBranch) {
            return new LfLegBranch(legBranch, network, bus1, bus2);
        } else if (branch instanceof LfSwitch lfSwitch) {
            return new LfSwitch(lfSwitch, network, bus1, bus2);
        } else if (branch instanceof LfTieLineBranch tieLineBranch) {
            return new LfTieLineBranch(tieLineBranch, network, bus1, bus2);
        } else if (branch instanceof LfBoundaryLineBranch boundaryLineBranch) {
            return new LfBoundaryLineBranch(boundaryLineBranch, network, bus1, bus2);
        }
        throw new PowsyblException("Copy of branch type " + branch.getClass().getSimpleName() + " is not supported");
    }

    private static LfHvdc copyHvdc(LfHvdcImpl hvdc, LfNetwork network) {
        return new LfHvdcImpl(hvdc, network,
                copyOf(hvdc.getBus1(), network),
                copyOf(hvdc.getBus2(), network),
                (LfVscConverterStation) network.getGeneratorById(hvdc.getConverterStation1().getId()),
                (LfVscConverterStation) network.getGeneratorById(hvdc.getConverterStation2().getId()));
    }

    private static LfArea copyArea(LfAreaImpl area, LfNetwork network) {
        Set<LfBus> buses = area.getBuses().stream()
                .map(b -> network.getBusById(b.getId()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<LfArea.Boundary> boundaries = area.getBoundaries().stream()
                .map(b -> (LfArea.Boundary) new LfAreaImpl.BoundaryImpl(network.getBranchById(b.getBranch().getId()), b.getSide()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        LfAreaImpl copiedArea = new LfAreaImpl(area, buses, boundaries, network);
        buses.forEach(bus -> bus.setArea(copiedArea));
        return copiedArea;
    }

    /**
     * Recreate the control objects on the copied network, mirroring the wiring done by
     * {@link LfNetworkLoaderImpl}. The voltage control merge structures (merge status, merged
     * dependent controls) are intentionally left at their initial state: they are recomputed
     * when the zero impedance networks of the copy are lazily created.
     */
    private static Map<GeneratorVoltageControl, GeneratorVoltageControl> copyControls(LfNetwork original, LfNetwork copy) {
        Map<GeneratorVoltageControl, GeneratorVoltageControl> generatorVoltageControlMap = new HashMap<>();

        for (LfBus bus : original.getBuses()) {
            copyBusControls(bus, copy, generatorVoltageControlMap);
        }

        for (LfBranch branch : original.getBranches()) {
            copyBranchControls(branch, copy);
        }

        // control wiring (addControllerElement / addControllerBus) forces some enabled flags and may
        // invalidate the reactive target state: restore the raw copied state, which also preserves the
        // simulation state of an already solved network (PV to PQ switched buses with frozen targets)
        for (LfBus bus : original.getBuses()) {
            AbstractLfBus copiedBus = (AbstractLfBus) copy.getBusById(bus.getId());
            copiedBus.copyReactiveStateFrom((AbstractLfBus) bus);
            copiedBus.setRemoteControlReactivePercent(bus.getRemoteControlReactivePercent());
        }

        return generatorVoltageControlMap;
    }

    private static void copyBusControls(LfBus bus, LfNetwork copy,
                                        Map<GeneratorVoltageControl, GeneratorVoltageControl> generatorVoltageControlMap) {
        LfBus copiedBus = copy.getBusById(bus.getId());

        bus.getGeneratorVoltageControl().filter(vc -> vc.getControlledBus() == bus).ifPresent(vc -> {
            GeneratorVoltageControl copiedVc = new GeneratorVoltageControl(copiedBus, vc.getTargetPriority(), vc.getTargetValue());
            for (LfBus controllerBus : vc.getControllerElements()) {
                copiedVc.addControllerElement(copy.getBusById(controllerBus.getId()));
            }
            copiedBus.setGeneratorVoltageControl(copiedVc);
            generatorVoltageControlMap.put(vc, copiedVc);
        });

        bus.getTransformerVoltageControl().filter(vc -> vc.getControlledBus() == bus).ifPresent(vc -> {
            TransformerVoltageControl copiedVc = new TransformerVoltageControl(copiedBus, vc.getTargetPriority(),
                    vc.getTargetValue(), vc.getTargetDeadband().orElse(null));
            for (LfBranch controllerBranch : vc.getControllerElements()) {
                LfBranch copiedController = copy.getBranchById(controllerBranch.getId());
                copiedVc.addControllerElement(copiedController);
                copiedController.setVoltageControl(copiedVc);
            }
            copiedBus.setTransformerVoltageControl(copiedVc);
        });

        bus.getVoltageSourceConverterVoltageControl().filter(vc -> vc.getControlledBus() == bus).ifPresent(vc -> {
            VoltageSourceConverterVoltageControl copiedVc = new VoltageSourceConverterVoltageControl(copiedBus,
                    vc.getTargetPriority(), vc.getTargetValue());
            for (LfBus controllerBus : vc.getControllerElements()) {
                copiedVc.addControllerElement(copy.getBusById(controllerBus.getId()));
            }
            copiedBus.setVoltageSourceConverterVoltageControl(copiedVc);
        });

        bus.getShuntVoltageControl().filter(vc -> vc.getControlledBus() == bus).ifPresent(vc -> {
            ShuntVoltageControl copiedVc = new ShuntVoltageControl(copiedBus, vc.getTargetPriority(),
                    vc.getTargetValue(), vc.getTargetDeadband().orElse(null));
            for (LfShunt controllerShunt : vc.getControllerElements()) {
                LfShunt copiedController = copy.getShuntById(controllerShunt.getOriginalIds().get(0));
                copiedVc.addControllerElement(copiedController);
                copiedController.setVoltageControl(copiedVc);
            }
            copiedBus.setShuntVoltageControl(copiedVc);
        });
    }

    private static void copyBranchControls(LfBranch branch, LfNetwork copy) {
        branch.getPhaseControl().filter(pc -> pc.getControllerBranch() == branch).ifPresent(pc -> {
            LfBranch copiedController = copy.getBranchById(pc.getControllerBranch().getId());
            LfBranch copiedControlled = copy.getBranchById(pc.getControlledBranch().getId());
            TransformerPhaseControl copiedPc = new TransformerPhaseControl(copiedController, copiedControlled,
                    pc.getControlledSide(), pc.getMode(), pc.getTargetValue(), pc.getTargetDeadband(), pc.getUnit());
            copiedController.setPhaseControl(copiedPc);
            copiedControlled.setPhaseControl(copiedPc);
        });

        branch.getGeneratorReactivePowerControl().filter(rc -> rc.getControlledBranch() == branch).ifPresent(rc -> {
            LfBranch copiedControlled = copy.getBranchById(branch.getId());
            GeneratorReactivePowerControl copiedRc = new GeneratorReactivePowerControl(copiedControlled,
                    rc.getControlledSide(), rc.getTargetValue());
            for (LfBus controllerBus : rc.getControllerBuses()) {
                copiedRc.addControllerBus(copy.getBusById(controllerBus.getId()));
            }
            copiedControlled.setGeneratorReactivePowerControl(copiedRc);
        });

        branch.getTransformerReactivePowerControl().filter(rc -> rc.getControllerBranch() == branch).ifPresent(rc -> {
            LfBranch copiedController = copy.getBranchById(rc.getControllerBranch().getId());
            LfBranch copiedControlled = copy.getBranchById(rc.getControlledBranch().getId());
            TransformerReactivePowerControl copiedRc = new TransformerReactivePowerControl(copiedControlled,
                    rc.getControlledSide(), copiedController, rc.getTargetValue(),
                    rc.getTargetDeadband().orElseThrow());
            copiedController.setTransformerReactivePowerControl(copiedRc);
            copiedControlled.setTransformerReactivePowerControl(copiedRc);
        });
    }
}
