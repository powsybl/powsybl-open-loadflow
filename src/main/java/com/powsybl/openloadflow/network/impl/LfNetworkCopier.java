/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network.impl;

import com.google.common.base.Stopwatch;
import com.powsybl.commons.report.ReportNode;
import com.powsybl.openloadflow.network.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.powsybl.openloadflow.util.Markers.PERFORMANCE_MARKER;

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
 * @author Gautier Bureau {@literal <gautier.bureau at rte-france.com>}
 */
public final class LfNetworkCopier {

    private static final Logger LOGGER = LoggerFactory.getLogger(LfNetworkCopier.class);

    private LfNetworkCopier() {
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

        Stopwatch stopwatch = Stopwatch.createStarted();

        LfNetwork copy = copyFlat(original, reportNode);
        copy.validate(loadFlowModel, null);

        stopwatch.stop();
        LOGGER.debug(PERFORMANCE_MARKER, "LF networks copied in {} ms", stopwatch.elapsed(TimeUnit.MILLISECONDS));

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

        for (LfBus bus : originalNetwork.getBuses()) {
            copyBusControls(bus, copyNetwork);
        }

        for (LfBranch branch : originalNetwork.getBranches()) {
            copyBranchControls(branch, copyNetwork);
        }

        // control wiring (addControllerElement / addControllerBus) forces some enabled flags and may
        // invalidate the reactive target state: restore the raw copied state, which also preserves the
        // simulation state of an already solved network (PV to PQ switched buses with frozen targets)
        for (LfBus bus : originalNetwork.getBuses()) {
            ((AbstractLfBus) copyNetwork.getBusById(bus.getId())).copyReactiveStateFrom((AbstractLfBus) bus);
        }

        for (LfSecondaryVoltageControl secondaryVoltageControl : originalNetwork.getSecondaryVoltageControls()) {
            copyNetwork.addSecondaryVoltageControl(secondaryVoltageControl.copy(copyNetwork));
        }

        for (LfNetwork.LfVoltageAngleLimit limit : originalNetwork.getVoltageAngleLimits()) {
            copyNetwork.addVoltageAngleLimit(limit.copy(copyNetwork));
        }

        for (LfOverloadManagementSystem overloadSystem : originalNetwork.getOverloadManagementSystems()) {
            copyNetwork.addOverloadManagementSystem(overloadSystem.copy(copyNetwork));
        }

        // reproduce the per synchronous component state (excluded slack buses); slack and reference
        // selection is left to be lazily redone on the copy
        for (LfSynchronousNetwork originalSc : originalNetwork.getSynchronousNetworks()) {
            if (!originalSc.getExcludedSlackBuses().isEmpty()) {
                copyNetwork.getSynchronousNetwork(originalSc.getNumSC())
                        .setExcludedSlackBuses(originalSc.getExcludedSlackBuses()
                                .stream()
                                .map(bus -> copyNetwork.getBusById(bus.getId()))
                                .collect(Collectors.toCollection(LinkedHashSet::new)));
            }
        }

        return copyNetwork;
    }

    private static void copyBusControls(LfBus bus, LfNetwork copyNetwork) {
        LfBus copiedBus = copyNetwork.getBusById(bus.getId());
        bus.getGeneratorVoltageControl().filter(vc -> vc.getControlledBus() == bus).ifPresent(vc -> {
            copiedBus.setGeneratorVoltageControl(vc.copy(copyNetwork));
        });

        bus.getTransformerVoltageControl().filter(vc -> vc.getControlledBus() == bus).ifPresent(vc -> {
            copiedBus.setTransformerVoltageControl(vc.copy(copyNetwork));
        });

        bus.getVoltageSourceConverterVoltageControl().filter(vc -> vc.getControlledBus() == bus).ifPresent(vc -> {
            copiedBus.setVoltageSourceConverterVoltageControl(vc.copy(copyNetwork));
        });

        bus.getShuntVoltageControl().filter(vc -> vc.getControlledBus() == bus).ifPresent(vc -> {
            copiedBus.setShuntVoltageControl(vc.copy(copyNetwork));
        });
    }

    private static void copyBranchControls(LfBranch branch, LfNetwork copyNetwork) {
        branch.getPhaseControl().filter(pc -> pc.getControllerBranch() == branch).ifPresent(pc -> {
            LfBranch copiedController = copyNetwork.getBranchById(pc.getControllerBranch().getId());
            LfBranch copiedControlled = copyNetwork.getBranchById(pc.getControlledBranch().getId());
            TransformerPhaseControl copiedPc = pc.copy(copyNetwork);
            copiedController.setPhaseControl(copiedPc);
            copiedControlled.setPhaseControl(copiedPc);
        });

        branch.getGeneratorReactivePowerControl().filter(rc -> rc.getControlledBranch() == branch).ifPresent(rc -> {
            LfBranch copiedControlled = copyNetwork.getBranchById(branch.getId());
            copiedControlled.setGeneratorReactivePowerControl(rc.copy(copyNetwork));
        });

        branch.getTransformerReactivePowerControl().filter(rc -> rc.getControllerBranch() == branch).ifPresent(rc -> {
            LfBranch copiedController = copyNetwork.getBranchById(rc.getControllerBranch().getId());
            LfBranch copiedControlled = copyNetwork.getBranchById(rc.getControlledBranch().getId());
            TransformerReactivePowerControl copiedRc = rc.copy(copyNetwork);
            copiedController.setTransformerReactivePowerControl(copiedRc);
            copiedControlled.setTransformerReactivePowerControl(copiedRc);
        });
    }
}
