/**
 * Copyright (c) 2023, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.lf.outerloop;

/**
 * @author Hadrien Godard {@literal <hadrien.godard at artelys.com>}
 */

import com.powsybl.iidm.network.TwoSides;
import com.powsybl.openloadflow.equations.Quantity;
import com.powsybl.openloadflow.lf.AbstractLoadFlowParameters;
import com.powsybl.openloadflow.lf.LoadFlowContext;
import com.powsybl.openloadflow.network.LfHvdc;
import org.apache.commons.lang3.mutable.MutableInt;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;

public abstract class AbstractAcEmulationOuterLoop <V extends Enum<V> & Quantity,
                                                    E extends Enum<E> & Quantity,
                                                    P extends AbstractLoadFlowParameters,
                                                    C extends LoadFlowContext<V, E, P>,
                                                    O extends OuterLoopContext<V, E, P, C>> implements OuterLoop<V, E, P, C, O> {

    protected static final int MAX_MODE_SWITCH = 2;
    protected static final int MAX_FEEDING_SIDE_SWITCH = 2;

    protected static final class ContextData {
        private final Map<String, MutableInt> modeSwitchCount = new HashMap<>();
        private final Map<String, MutableInt> feedingSideSwitchCount = new HashMap<>();

        public void incrementModeSwitchCount(String hvdcId) {
            modeSwitchCount.computeIfAbsent(hvdcId, k -> new MutableInt(0))
                    .increment();
        }

        public void incrementFeedingSideSwitchCount(String hvdcId) {
            feedingSideSwitchCount.computeIfAbsent(hvdcId, k -> new MutableInt(0))
                    .increment();
        }

        public int getModeSwitchCount(String hvdcId) {
            MutableInt counter = modeSwitchCount.get(hvdcId);
            if (counter == null) {
                return 0;
            }
            return counter.getValue();
        }

        public int getFeedingSideSwitchCount(String hvdcId) {
            MutableInt counter = feedingSideSwitchCount.get(hvdcId);
            if (counter == null) {
                return 0;
            }
            return counter.getValue();
        }
    }

    public void initialize(OuterLoopContext context) {
        context.setData(new ContextData());
    }

    protected boolean checkMode(LfHvdc hvdc, ContextData contextData, Logger logger) {
        String hvdcId = hvdc.getId();
        LfHvdc.AcEmulationControl acEmulationControl = hvdc.getAcEmulationControl();

        // Check for mode switch between FREE and BOUNDED
        if (acEmulationControl.getAcEmulationStatus() == LfHvdc.AcEmulationControl.AcEmulationStatus.FREE) {
            // Check Pmax
            if (acEmulationControl.getFeedingSide() == TwoSides.ONE) {
                if (hvdc.getP1().eval() > acEmulationControl.getPMaxFromCS1toCS2()) {
                    // Switch mode
                    logger.trace("Bound Hvdc flow to Pmax from CS1 to CS2 for Hvdc: " + hvdcId);
                    contextData.incrementModeSwitchCount(hvdcId);
                    acEmulationControl.setAcEmulationStatus(LfHvdc.AcEmulationControl.AcEmulationStatus.BOUNDED);
                    if (contextData.getModeSwitchCount(hvdcId) == MAX_MODE_SWITCH) {
                        logger.debug("Two many mode switches (flow blocked to Pmax from CS1 to CS2) for Hvdc: " + hvdcId);
                    }
                    return true;
                }
            } else {
                if (hvdc.getP2().eval() > acEmulationControl.getPMaxFromCS2toCS1()) {
                    // Switch mode
                    logger.trace("Bound Hvdc flow to Pmax from CS2 to CS1 for Hvdc: " + hvdcId);
                    contextData.incrementModeSwitchCount(hvdcId);
                    acEmulationControl.setAcEmulationStatus(LfHvdc.AcEmulationControl.AcEmulationStatus.BOUNDED);
                    if (contextData.getModeSwitchCount(hvdcId) == MAX_MODE_SWITCH) {
                        logger.debug("Two many mode switches (flow blocked to Pmax from CS2 to CS1) for Hvdc: " + hvdcId);
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
                    logger.trace("Set free the Ac Emulation mode for Hvdc: " + hvdcId);
                    acEmulationControl.setAcEmulationStatus(LfHvdc.AcEmulationControl.AcEmulationStatus.FREE);
                    return true;
                }
            } else {
                if (hvdc.getP2().eval() < acEmulationControl.getPMaxFromCS2toCS1()) {
                    // Switch mode
                    logger.trace("Set free the Ac Emulation mode for Hvdc: " + hvdcId);
                    acEmulationControl.setAcEmulationStatus(LfHvdc.AcEmulationControl.AcEmulationStatus.FREE);
                    return true;
                }
            }
        }
        return false;
    }

}
