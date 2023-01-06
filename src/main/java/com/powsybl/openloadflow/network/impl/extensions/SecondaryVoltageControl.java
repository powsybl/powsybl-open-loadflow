/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.impl.extensions;

import com.powsybl.commons.extensions.Extension;
import com.powsybl.iidm.network.Network;

import java.util.List;
import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public interface SecondaryVoltageControl extends Extension<Network> {

    String NAME = "secondaryVoltageControl";

    class PilotPoint {

        private final String busbarSectionOrBusId;

        private final double targetV;

        public PilotPoint(String busbarSectionOrBusId, double targetV) {
            this.busbarSectionOrBusId = Objects.requireNonNull(busbarSectionOrBusId);
            this.targetV = targetV;
        }

        public String getBusbarSectionOrBusId() {
            return busbarSectionOrBusId;
        }

        public double getTargetV() {
            return targetV;
        }
    }

    class Zone {

        private final PilotPoint pilotPoint;

        private final List<String> generatorsIds;

        public Zone(PilotPoint pilotPoint, List<String> generatorsIds) {
            this.pilotPoint = Objects.requireNonNull(pilotPoint);
            this.generatorsIds = Objects.requireNonNull(generatorsIds);
        }

        public PilotPoint getPilotPoint() {
            return pilotPoint;
        }

        public List<String> getGeneratorsIds() {
            return generatorsIds;
        }
    }

    List<Zone> getZones();

    @Override
    default String getName() {
        return NAME;
    }
}
