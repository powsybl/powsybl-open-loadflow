/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network;

import com.powsybl.iidm.network.TwoSides;
import com.powsybl.openloadflow.util.Evaluable;
import com.powsybl.openloadflow.util.PerUnit;

/**
 * @author Anne Tilloy {@literal <anne.tilloy at rte-france.com>}
 */
public interface LfHvdc extends LfElement {

    class AcEmulationControl {
        public enum AcEmulationStatus {
            FREE,
            BOUNDED,
            NULL;
        }
        private final double droop;
        private final double p0;
        private final double pMaxFromCS1toCS2;
        private final double pMaxFromCS2toCS1;
        private AcEmulationStatus acEmulationStatus = AcEmulationStatus.FREE;
        private TwoSides feedingSide;

        public AcEmulationControl(double droop, double p0, double pMaxFromCS1toCS2, double pMaxFromCS2toCS1) {
            this.droop = droop;
            this.p0 = p0;
            this.pMaxFromCS1toCS2 = pMaxFromCS1toCS2;
            this.pMaxFromCS2toCS1 = pMaxFromCS2toCS1;
            this.feedingSide = (p0 >= 0) ? TwoSides.ONE : TwoSides.TWO;
        }

        public double getDroop() {
            return droop / PerUnit.SB;
        }

        public double getP0() {
            return p0 / PerUnit.SB;
        }

        public double getPMaxFromCS1toCS2() {
            return pMaxFromCS1toCS2 / PerUnit.SB;
        }

        public double getPMaxFromCS2toCS1() {
            return pMaxFromCS2toCS1 / PerUnit.SB;
        }

        public AcEmulationStatus getAcEmulationStatus() {
            return acEmulationStatus;
        }

        public TwoSides getFeedingSide() {
            return feedingSide;
        }

        public void setAcEmulationStatus(AcEmulationStatus status) {
            acEmulationStatus = status;
        }

        public void setFeedingSide(TwoSides side) {
            feedingSide = side;
        }
    }

    LfBus getBus1();

    LfBus getBus2();

    LfBus getOtherBus(LfBus bus);

    void setP1(Evaluable p1);

    Evaluable getP1();

    void setP2(Evaluable p2);

    Evaluable getP2();

    boolean isAcEmulation();

    void setAcEmulation(boolean acEmulation);

    LfVscConverterStation getConverterStation1();

    LfVscConverterStation getConverterStation2();

    void setConverterStation1(LfVscConverterStation converterStation1);

    void setConverterStation2(LfVscConverterStation converterStation2);

    AcEmulationControl getAcEmulationControl();

    void updateAcEmulationStatus(AcEmulationControl.AcEmulationStatus acEmulationStatus);

    void updateFeedingSide(TwoSides side);

    void updateState();
}
