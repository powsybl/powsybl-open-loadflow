/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.loadflow.open.network;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public interface LfGenerator {

    String getId();

    boolean hasVoltageControl();

    double getTargetQ();

    double getTargetP();

    void setTargetP(double targetP);

    double getMinP();

    double getMaxP();

    double getMinQ();

    double getMaxQ();

    double getMaxRangeQ();

    double getParticipationFactor();

    double getQ();

    void setQ(double q);

    void updateState();
}
