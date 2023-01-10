/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import java.util.List;
import java.util.Map;

/**
 * @author Anne Tilloy <anne.tilloy at rte-france.com>
 */
public interface LfAggregatedLoads extends PropertyBag {

    List<String> getOriginalIds();

    double getAbsVariableLoadTargetP();

    void setAbsVariableLoadTargetP(double absVariableLoadTargetP);

    double getLoadCount();

    double getLoadTargetQ(double diffLoadTargetP);

    boolean isDisabled(String originalId);

    void setDisabled(String originalId, boolean disabled);

    Map<String, Boolean> getLoadsDisablingStatus();

    void setLoadsDisablingStatus(Map<String, Boolean> loadsDisablingStatus);
}
