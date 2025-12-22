/*
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */

package com.powsybl.openloadflow.ac.outerloop;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.openloadflow.ac.AcLoadFlowContext;
import com.powsybl.openloadflow.ac.AcOuterLoopContext;
import com.powsybl.openloadflow.ac.solver.AcSolverUtil;
import com.powsybl.openloadflow.lf.outerloop.OuterLoopResult;
import com.powsybl.openloadflow.lf.outerloop.OuterLoopStatus;
import com.powsybl.openloadflow.network.LfHvdc;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.util.PreviousValueVoltageInitializer;
import com.powsybl.openloadflow.util.PerUnit;
import com.powsybl.openloadflow.util.Reports;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;

/**
 * @author Didier Vidal {@literal <didier.vidal_externe at rte-france.com>}
 */
public class FreezingHvdcACEmulationOuterloop implements AcOuterLoop {

    private static final Logger LOGGER = LoggerFactory.getLogger(FreezingHvdcACEmulationOuterloop.class);

    public static final String NAME = "FreezingHvdcACEmulation";

    private enum Step {
        UNFREEZE,
        COMPLETE
    }

    private static final class ContextData {

        private final Step step = Step.UNFREEZE;
        private final HashMap<String, Double> angles = new HashMap<>();
        private final HashMap<String, Double> voltage = new HashMap<>();

        private ContextData(LfNetwork network) {
            network.getBuses()
                    .stream().filter(b -> !b.isDisabled())
                    .forEach(b -> {
                        angles.put(b.getId(), Double.isNaN(b.getAngle()) ? 0 : b.getAngle());
                        voltage.put(b.getId(), Double.isNaN(b.getV()) ? 1 : b.getV());
                    });
        }

    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public void initialize(AcOuterLoopContext context) {
        ContextData contextData = new ContextData(context.getNetwork());
        context.setData(contextData);
        LfNetwork network = context.getNetwork();
        if (context.getLoadFlowContext().getEquationSystem().getStateVector().get() == null) { // If State Vector was not initialized, initializing (angles are used to compute frozen state of AC emulation)
            AcSolverUtil.initStateVector(context.getNetwork(), context.getLoadFlowContext().getEquationSystem(), new PreviousValueVoltageInitializer());
        }
        network.getHvdcs().stream()
                .filter(LfHvdc::isAcEmulation)
                .filter(lfHvdc -> !lfHvdc.isDisabled() && !lfHvdc.getBus1().isDisabled() && !lfHvdc.getBus2().isDisabled() && lfHvdc.getAcEmulationControl().isFreezable())
                .forEach(lfHvdc -> {
                    double setPointBus1 = lfHvdc.getAcEmulationControl().switchToFrozenState(true);
                    Reports.reportFreezeHvdc(context.getNetwork().getReportNode(), lfHvdc.getId(), lfHvdc.getConverterStation1().getId(), setPointBus1 * PerUnit.SB, LOGGER);
                });
    }

    @Override
    public boolean isNeeded(AcLoadFlowContext context) {
        // Needed if the network contains an lfHVDC in AC Emulation mode
        return context.getNetwork().getHvdcs().stream().anyMatch(LfHvdc::isAcEmulation);
    }

    @Override
    public OuterLoopResult check(AcOuterLoopContext context, ReportNode reportNode) {
        ContextData contextData = (ContextData) context.getData();
        return switch (contextData.step) {
            case UNFREEZE -> unfreezeHvdcs(context, contextData, reportNode);
            case COMPLETE -> new OuterLoopResult(this, OuterLoopStatus.STABLE);
        };
    }

    private OuterLoopResult unfreezeHvdcs(AcOuterLoopContext context, ContextData contextData, ReportNode reportNode) {

        List<LfHvdc> frozenHvdc = context.getNetwork().getHvdcs().stream()
                .filter(LfHvdc::isAcEmulation)
                .filter(hvdc -> hvdc.getAcEmulationControl().getAcEmulationStatus() == LfHvdc.AcEmulationControl.AcEmulationStatus.FROZEN)
                .toList();

        if (!frozenHvdc.isEmpty()) {
            for (LfHvdc lfHvdc : frozenHvdc) {
                Reports.reportUnfreezeHvdc(reportNode, lfHvdc.getId(), LOGGER);
                lfHvdc.getAcEmulationControl().switchToLinearMode();
            }

            // Return to initial state (we are in a possibly non-physical state after first partial resolution)
            context.getNetwork().getBuses()
                    .stream()
                    .filter(b -> !b.isDisabled())
                    .forEach(b -> {
                        b.setAngle(contextData.angles.get(b.getId()));
                        b.setV(contextData.voltage.get(b.getId()));
                    });
        }

        return new OuterLoopResult(this, frozenHvdc.isEmpty() ? OuterLoopStatus.STABLE : OuterLoopStatus.UNSTABLE);
    }

}
