/**
 * Copyright (c) 2023, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.outerloop;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.iidm.network.TwoSides;
import com.powsybl.openloadflow.ac.AcOuterLoopContext;
import com.powsybl.openloadflow.lf.outerloop.OuterLoopResult;
import com.powsybl.openloadflow.lf.outerloop.OuterLoopStatus;
import com.powsybl.openloadflow.network.LfHvdc;
import org.apache.commons.lang3.mutable.MutableInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Hadrien Godard {@literal <hadrien.godard at artelys.com>}
 */
public class AcEmulationOuterLoop implements AcOuterLoop {

    private static final Logger LOGGER = LoggerFactory.getLogger(AcEmulationOuterLoop.class);
    public static final String NAME = "AcEmulation";

    private static final int MAX_MODE_SWITCH = 2;
    private static final int MAX_FEEDING_SIDE_SWITCH = 2;

    private static final class ContextData {
        private final Map<String, MutableInt> modeSwitchCount = new HashMap<>();
        private final Map<String, MutableInt> feedingSideSwitchCount = new HashMap<>();

        void incrementModeSwitchCount(String hvdcId) {
            modeSwitchCount.computeIfAbsent(hvdcId, k -> new MutableInt(0))
                    .increment();
        }

        void incrementFeedingSideSwitchCount(String hvdcId) {
            feedingSideSwitchCount.computeIfAbsent(hvdcId, k -> new MutableInt(0))
                    .increment();
        }

        int getModeSwitchCount(String hvdcId) {
            MutableInt counter = modeSwitchCount.get(hvdcId);
            if (counter == null) {
                return 0;
            }
            return counter.getValue();
        }

        int getFeedingSideSwitchCount(String hvdcId) {
            MutableInt counter = feedingSideSwitchCount.get(hvdcId);
            if (counter == null) {
                return 0;
            }
            return counter.getValue();
        }
    }

    @Override
    public void initialize(AcOuterLoopContext context) {
        context.setData(new AcEmulationOuterLoop.ContextData());
    }

    @Override
    public String getName() {
        return NAME;
    }

    private boolean checkFeedingSide(LfHvdc hvdc, ContextData contextData) {
        String hvdcId = hvdc.getId();
        LfHvdc.AcEmulationControl acEmulationControl = hvdc.getAcEmulationControl();

        if (acEmulationControl.getFeedingSide() == TwoSides.ONE) {
            if (hvdc.getP1().eval() < 0) {
                // Switch feeding side
                LOGGER.trace("Switching feeding side from One to Two for Hvdc: " + hvdcId);
                contextData.incrementFeedingSideSwitchCount(hvdcId);
                acEmulationControl.setFeedingSide(TwoSides.TWO);
                if (contextData.getFeedingSideSwitchCount(hvdcId) == MAX_FEEDING_SIDE_SWITCH) {
                    LOGGER.debug("Two many feeding side switches (flow blocked to 0 MW) for Hvdc: " + hvdcId);
                    acEmulationControl.setAcEmulationStatus(LfHvdc.AcEmulationControl.AcEmulationStatus.NULL);
                }
                return true;
            }
        } else {
            if (hvdc.getP2().eval() < 0) {
                // Switch feeding side
                LOGGER.trace("Switching feeding side from Two to One for Hvdc: " + hvdcId);
                contextData.incrementFeedingSideSwitchCount(hvdcId);
                acEmulationControl.setFeedingSide(TwoSides.ONE);
                if (contextData.getFeedingSideSwitchCount(hvdcId) == MAX_FEEDING_SIDE_SWITCH) {
                    LOGGER.debug("Two many feeding side switches (flow blocked to 0 MW) for Hvdc: " + hvdcId);
                    acEmulationControl.setAcEmulationStatus(LfHvdc.AcEmulationControl.AcEmulationStatus.NULL);
                }
                return true;
            }
        }
        return false;
    }

    private boolean checkMode(LfHvdc hvdc, ContextData contextData) {
        String hvdcId = hvdc.getId();
        LfHvdc.AcEmulationControl acEmulationControl = hvdc.getAcEmulationControl();

        // Check for mode switch between FREE and BOUNDED
        if (acEmulationControl.getAcEmulationStatus() == LfHvdc.AcEmulationControl.AcEmulationStatus.FREE) {
            // Check Pmax
            if (acEmulationControl.getFeedingSide() == TwoSides.ONE) {
                if (hvdc.getP1().eval() > acEmulationControl.getPMaxFromCS1toCS2()) {
                    // Switch mode
                    LOGGER.trace("Bound Hvdc flow to Pmax from CS1 to CS2 for Hvdc: " + hvdcId);
                    contextData.incrementModeSwitchCount(hvdcId);
                    acEmulationControl.setAcEmulationStatus(LfHvdc.AcEmulationControl.AcEmulationStatus.BOUNDED);
                    if (contextData.getModeSwitchCount(hvdcId) == MAX_MODE_SWITCH) {
                        LOGGER.debug("Two many mode switches (flow blocked to Pmax from CS1 to CS2) for Hvdc: " + hvdcId);
                    }
                    return true;
                }
            } else {
                if (hvdc.getP2().eval() > acEmulationControl.getPMaxFromCS2toCS1()) {
                    // Switch mode
                    LOGGER.trace("Bound Hvdc flow to Pmax from CS2 to CS1 for Hvdc: " + hvdcId);
                    contextData.incrementModeSwitchCount(hvdcId);
                    acEmulationControl.setAcEmulationStatus(LfHvdc.AcEmulationControl.AcEmulationStatus.BOUNDED);
                    if (contextData.getModeSwitchCount(hvdcId) == MAX_MODE_SWITCH) {
                        LOGGER.debug("Two many mode switches (flow blocked to Pmax from CS2 to CS1) for Hvdc: " + hvdcId);
                    }
                    return true;
                }
            }
        }

        // Check for mode switch between BOUNDED and FREE
        if (acEmulationControl.getAcEmulationStatus() == LfHvdc.AcEmulationControl.AcEmulationStatus.BOUNDED) {
            if (acEmulationControl.getFeedingSide() == TwoSides.ONE) {
                if (hvdc.getP1().eval() < acEmulationControl.getPMaxFromCS1toCS2()) {
                    // Switch mode
                    LOGGER.trace("Set free the Ac Emulation mode for Hvdc: " + hvdcId);
                    acEmulationControl.setAcEmulationStatus(LfHvdc.AcEmulationControl.AcEmulationStatus.FREE);
                    return true;
                }
            } else {
                if (hvdc.getP2().eval() < acEmulationControl.getPMaxFromCS2toCS1()) {
                    // Switch mode
                    LOGGER.trace("Set free the Ac Emulation mode for Hvdc: " + hvdcId);
                    acEmulationControl.setAcEmulationStatus(LfHvdc.AcEmulationControl.AcEmulationStatus.FREE);
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public OuterLoopResult check(AcOuterLoopContext context, ReportNode reportNode) {
        OuterLoopStatus status = OuterLoopStatus.STABLE;
        ContextData contextData = (ContextData) context.getData();

        for (LfHvdc hvdc : context.getNetwork().getHvdcs()) {
            if (!hvdc.isAcEmulation() || hvdc.getBus1().isDisabled() || hvdc.getBus2().isDisabled() || hvdc.isDisabled()) {
                continue;
            }
            String hvdcId = hvdc.getId();
            if (contextData.getFeedingSideSwitchCount(hvdcId) < MAX_FEEDING_SIDE_SWITCH && contextData.getModeSwitchCount(hvdcId) < MAX_MODE_SWITCH) {
                // First check the feeding side
                if (checkFeedingSide(hvdc, contextData)) {
                    status = OuterLoopStatus.UNSTABLE;
                }

                // Second check for Pmax values
                if (checkMode(hvdc, contextData)) {
                    status = OuterLoopStatus.UNSTABLE;
                }
            }
        }

        return new OuterLoopResult(this, status);
    }
}
