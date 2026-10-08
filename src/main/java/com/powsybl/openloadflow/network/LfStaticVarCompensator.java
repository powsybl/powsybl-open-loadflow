/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network;

import java.util.Optional;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public interface LfStaticVarCompensator extends LfGenerator {

    /**
     * if the static var compensator has an automaton in stand by, this object must be field.
     */
    class StandByAutomaton {

        private final double highVoltageThreshold;
        private final double lowVoltageThreshold;
        private final double highTargetV;
        private final double lowTargetV;

        public StandByAutomaton(double highVoltageThreshold, double lowVoltageThreshold, double highTargetV, double lowTargetV) {
            this.highVoltageThreshold = highVoltageThreshold;
            this.lowVoltageThreshold = lowVoltageThreshold;
            this.highTargetV = highTargetV;
            this.lowTargetV = lowTargetV;
        }

        public double getLowTargetV() {
            return lowTargetV;
        }

        public double getHighTargetV() {
            return highTargetV;
        }

        public double getLowVoltageThreshold() {
            return lowVoltageThreshold;
        }

        public double getHighVoltageThreshold() {
            return highVoltageThreshold;
        }
    }

    double getB0();

    double getBmin();

    double getBmax();

    Optional<StandByAutomaton> getStandByAutomaton();

    /**
     * The reactive limit (MIN_Q or MAX_Q) the static var compensator is blocked at, if any. At limit, it is modeled as
     * a fixed susceptance (Bmin or Bmax) through the bus SVC shunt so that its reactive power follows the voltage.
     */
    Optional<LfBus.QLimitType> getQLimitType();

    void setQLimitType(LfBus.QLimitType qLimitType);

    /**
     * The susceptance (in S) modeled through the bus SVC shunt: B0 and, when at a reactive limit, Bmin or Bmax.
     */
    double getB();
}
