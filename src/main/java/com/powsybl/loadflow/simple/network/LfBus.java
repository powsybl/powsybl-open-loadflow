/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.loadflow.simple.network;

import java.util.List;
import java.util.Optional;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public interface LfBus {

    String getId();

    int getNum();

    boolean isSlack();

    void setSlack(boolean slack);

    boolean hasVoltageControl();

    void setVoltageControl(boolean voltageControl);

    double getTargetP();

    double getTargetQ();

    double getLoadTargetP();

    double getLoadTargetQ();

    double getGenerationTargetP();

    void setGenerationTargetP(double generationTargetP);

    double getGenerationTargetQ();

    void setGenerationTargetQ(double generationTargetQ);

    double getTargetV();

    double getMinP();

    double getMaxP();

    double getParticipationFactor();

    double getV();

    void setV(double v);

    double getAngle();

    void setAngle(double angle);

    double getQ();

    void setQ(double q);

    /**
     * Get nominal voltage in Kv.
     * @return nominal voltage in Kv
     */
    double getNominalV();

    List<LfShunt> getShunts();

    Optional<LfReactiveDiagram> getReactiveDiagram();

    int getNeighbors();

    void updateState();
}
