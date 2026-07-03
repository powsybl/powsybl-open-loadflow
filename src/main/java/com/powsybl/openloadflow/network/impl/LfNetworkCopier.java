/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.openloadflow.network.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

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

    private static LfNetwork copyFlat(LfNetwork originalNetwork, ReportNode reportNode) {
        LfNetwork copyNetwork = new LfNetwork(originalNetwork, reportNode);

        // buses (with their owned generators, loads and shunts); nums are reassigned in the same
        // order by addBus, so they match the original ones. addBus also lazily recreates one
        // synchronous network per synchronous component, so the copy ends up with the same set.
        for (LfBus bus : originalNetwork.getBuses()) {
            copyNetwork.addBus(bus.copy(copyNetwork));
        }
        // reproduce the per synchronous component state (excluded slack buses); slack and reference
        // selection is left to be lazily redone on the copy
        for (LfSynchronousNetwork originalSc : originalNetwork.getSynchronousNetworks()) {
            copyNetwork.getSynchronousNetwork(originalSc.getNumSC()).copyStateFrom(originalSc);
        }

        // branches: addBranch rebuilds the bus to branches links in the same order
        for (LfBranch branch : originalNetwork.getBranches()) {
            copyNetwork.addBranch(branch.copy(copyNetwork));
        }
        // branches whose connectivity edge was removed by the initial topology restoration (networks
        // with elements disabled at build, e.g. switches built closed for a closing remedial action):
        // the copy's lazily rebuilt connectivity excludes them, making it equivalent to the original's
        for (LfBranch branch : originalNetwork.getConnectivityRemovedBranches()) {
            copyNetwork.addConnectivityRemovedBranch(copyNetwork.getBranchById(branch.getId()));
        }

        // DC part of AC/DC networks: DC buses first, then the DC lines connecting them, then the
        // voltage source converters linking a DC bus to an AC bus (all owned by the same network now)
        for (LfDcBus dcBus : originalNetwork.getDcBuses()) {
            copyNetwork.addDcBus(dcBus.copy(copyNetwork));
        }
        for (LfDcLine dcLine : originalNetwork.getDcLines()) {
            copyNetwork.addDcLine(dcLine.copy(copyNetwork));
        }
        for (LfVoltageSourceConverter converter : originalNetwork.getVoltageSourceConverters()) {
            copyNetwork.addVoltageSourceConverter(converter.copy(copyNetwork));
        }

        for (LfHvdc hvdc : originalNetwork.getHvdcs()) {
            copyNetwork.addHvdc(hvdc.copy(copyNetwork));
        }

        for (LfArea area : originalNetwork.getAreas()) {
            copyNetwork.addArea(area.copy(copyNetwork));
        }

        Map<GeneratorVoltageControl, GeneratorVoltageControl> generatorVoltageControlMap = copyControls(originalNetwork, copyNetwork);

        for (LfSecondaryVoltageControl secondaryVoltageControl : originalNetwork.getSecondaryVoltageControls()) {
            copyNetwork.addSecondaryVoltageControl(secondaryVoltageControl.copy(copyNetwork));
        }

        for (LfNetwork.LfVoltageAngleLimit limit : originalNetwork.getVoltageAngleLimits()) {
            copyNetwork.addVoltageAngleLimit(limit.copy(copyNetwork));
        }

        for (LfOverloadManagementSystem system : originalNetwork.getOverloadManagementSystems()) {
            LfOverloadManagementSystem copiedSystem = new LfOverloadManagementSystem(
                    copyNetwork.getBranchById(system.getMonitoredBranch().getId()), system.getMonitoredSide());
            for (LfOverloadManagementSystem.LfBranchTripping tripping : system.getBranchTrippingList()) {
                copiedSystem.addLfBranchTripping(copyNetwork.getBranchById(tripping.branchToOperate().getId()),
                        tripping.branchOpen(), tripping.threshold());
            }
            copyNetwork.addOverloadManagementSystem(copiedSystem);
        }

        return copyNetwork;
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
