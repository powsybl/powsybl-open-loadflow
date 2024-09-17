/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network;

import com.powsybl.openloadflow.util.Evaluable;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * @author Anne Tilloy {@literal <anne.tilloy at rte-france.com>}
 */
public interface LfLoad extends PropertyBag {

    String getId();

    LfBus getBus();

    boolean isIidmLoadPassive(String originalId);

    Optional<LfLoadModel> getLoadModel();

    double getInitialTargetP();

    double getTargetP();

    void setTargetP(double targetP);

    double getTargetQ();

    void setTargetQ(double targetQ);

    boolean ensurePowerFactorConstantByLoad();

    double getAbsVariableTargetP();

    void setAbsVariableTargetP(double absVariableTargetP);

    double calculateNewTargetQ(double diffTargetP);

    List<String> getOriginalIds();

    int getOriginalLoadCount();

    boolean isOriginalLoadDisabled(String originalId);

    void setOriginalLoadDisabled(String originalId, boolean disabled);

    Map<String, Boolean> getOriginalLoadsDisablingStatus();

    void setOriginalLoadsDisablingStatus(Map<String, Boolean> originalLoadsDisablingStatus);

    void updateState(boolean loadPowerFactorConstant, boolean breakers);

    Evaluable getP();

    void setP(Evaluable p);

    Evaluable getQ();

    void setQ(Evaluable q);
}
