/**
 * Copyright (c) 2026, SuperGrid Institute (http://www.supergrid-institute.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.outerloop;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.iidm.network.AcDcConverter;
import com.powsybl.openloadflow.ac.AcLoadFlowContext;
import com.powsybl.openloadflow.ac.AcOuterLoopContext;
import com.powsybl.openloadflow.lf.outerloop.OuterLoopResult;
import com.powsybl.openloadflow.lf.outerloop.OuterLoopStatus;
import com.powsybl.openloadflow.network.LfAcDcConverter;
import com.powsybl.openloadflow.network.LfDcBus;
import com.powsybl.openloadflow.network.LfDcComponentValidator;
import com.powsybl.openloadflow.network.LfNetwork;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Outer loop that ensures AC-DC converters operate within their power limits, changing their control mode if needed.
 * No switch-back is implemented, which might become a blocking point for contingency analysis.
 *
 * @author Baptiste Perreyon {@literal <baptiste.perreyon at supergrid-institute.com>}
 */
public class AcDcConverterPowerLimitsOuterLoop implements AcOuterLoop {

    public static final String NAME = "AcDcConverterPowerLimits";
    private final double maxActivePowerMismatch;  // Maximum active power mismatch after Newton-Raphson

    public AcDcConverterPowerLimitsOuterLoop(double maxActivePowerMismatch) {
        this.maxActivePowerMismatch = maxActivePowerMismatch;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public void initialize(AcOuterLoopContext context) {
        context.setData(new ContextData(context.getNetwork()));
    }

    @Override
    public OuterLoopResult check(AcOuterLoopContext context, ReportNode reportNode) {
        // In each DC network, up to one converter control mode can be modified
        OuterLoopStatus globalStatus = OuterLoopStatus.STABLE;
        ContextData contextData = (ContextData) context.getData();
        for (int numDcc : contextData.numDccs) {

            List<LfAcDcConverter> converters = LfDcComponentValidator.getAcDcConverters(context.getNetwork(), numDcc);

            // List all converters violating their power limits and the distance to this limit
            Map<LfAcDcConverter, Double> convertersDistanceToLimit = getConvertersViolatingLimits(converters);

            if (convertersDistanceToLimit.isEmpty()) {
                continue;
            }

            // Get the converter with the largest distance to its power limit : this is the one we want to change in P_PCC mode
            LfAcDcConverter selectedConverter = convertersDistanceToLimit.entrySet().stream()
                    .max(Comparator.comparingDouble(Map.Entry::getValue))
                    .map(Map.Entry::getKey).orElseThrow();

            // However, in a first place, we must ensure that this control mode change does not prevent the control of DC buses voltage.
            if (!LfDcComponentValidator.isValidAfterConverterChangesToPpccMode(context.getNetwork(), numDcc, selectedConverter)) {
                return new OuterLoopResult(this, OuterLoopStatus.FAILED,
                        "Could not enforce AC-DC converter active power limit and ensure DC voltage control in DC component " + numDcc);
            }

            // Set the converter's targetP to the exceeded limit.
            double newTargetP;
            if (selectedConverter.getPac() < selectedConverter.getMinP()) {
                newTargetP = selectedConverter.getMinP();
            } else {
                newTargetP = selectedConverter.getMaxP();
            }
            selectedConverter.setTargetP(newTargetP);
            selectedConverter.setControlMode(AcDcConverter.ControlMode.P_PCC);
            globalStatus = OuterLoopStatus.UNSTABLE;
        }
        return new OuterLoopResult(this, globalStatus);
    }

    /**
     * List the converters in V_DC and DC_DROOP mode that exceeded their power limit.
     * @param converters The converters in a given DC component.
     * @return A map whose key are AC-DC converters violating their active power limit and that maps them to the distance to this limit in absolute value.
     */
    private Map<LfAcDcConverter, Double> getConvertersViolatingLimits(List<LfAcDcConverter> converters) {
        List<AcDcConverter.ControlMode> controlModesToCheck = List.of(AcDcConverter.ControlMode.V_DC, AcDcConverter.ControlMode.DC_DROOP);
        Map<LfAcDcConverter, Double> convertersDistanceToLimit = new HashMap<>();
        for (LfAcDcConverter converter : converters) {
            if (controlModesToCheck.contains(converter.getControlMode())) {
                if (converter.getPac() < converter.getMinP() - maxActivePowerMismatch) {
                    // Converter is below its lower limit
                    convertersDistanceToLimit.put(converter, converter.getMinP() - converter.getPac());
                }
                if (converter.getPac() > converter.getMaxP() + maxActivePowerMismatch) {
                    // Converter is above its higher limit
                    convertersDistanceToLimit.put(converter, converter.getPac() - converter.getMaxP());
                }
            }
        }
        return convertersDistanceToLimit;
    }

    @Override
    public boolean isNeeded(AcLoadFlowContext context) {
        // Needed if the network contains an AC-DC converter.
        return !context.getNetwork().getVoltageSourceConverters().isEmpty();
    }

    private static final class ContextData {
        private final List<Integer> numDccs;

        private ContextData(LfNetwork network) {
            numDccs = network.getDcBuses().stream().map(LfDcBus::getNumDCC).distinct().sorted().toList();
        }
    }
}
