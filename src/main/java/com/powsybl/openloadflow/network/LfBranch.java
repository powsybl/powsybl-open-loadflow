/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import com.powsybl.commons.PowsyblException;
import com.powsybl.iidm.network.LimitType;
import com.powsybl.openloadflow.util.Evaluable;
import com.powsybl.security.results.BranchResult;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public interface LfBranch extends LfElement {

    LfBus getBus1();

    LfBus getBus2();

    void setP1(Evaluable p1);

    Evaluable getP1();

    void setP2(Evaluable p2);

    Evaluable getP2();

    Evaluable getQ1();

    void setQ1(Evaluable q1);

    Evaluable getQ2();

    void setQ2(Evaluable q2);

    PiModel getPiModel();

    void setI1(Evaluable i1);

    void setI2(Evaluable i2);

    Evaluable getI1();

    Evaluable getI2();

    List<AbstractLfBranch.LfLimit> getLimits1(LimitType type);

    default List<AbstractLfBranch.LfLimit> getLimits2(LimitType type) {
        return Collections.emptyList();
    }

    boolean hasPhaseControlCapability();

    Optional<DiscretePhaseControl> getDiscretePhaseControl();

    void updateState(boolean phaseShifterRegulationOn, boolean isTransformerVoltageControlOn);

    boolean isPhaseController();

    boolean isPhaseControlled(DiscretePhaseControl.ControlledSide controlledSide);

    boolean isPhaseControlled();

    void setDiscretePhaseControl(DiscretePhaseControl discretePhaseControl);

    Optional<DiscreteVoltageControl> getDiscreteVoltageControl();

    boolean isVoltageController();

    void setDiscreteVoltageControl(DiscreteVoltageControl discreteVoltageControl);

    default BranchResult createBranchResult() {
        throw new PowsyblException("Unsupported type of branch for branch result: " + getId());
    }

    boolean isDisabled();

    void setDisabled(boolean disabled);

    double computeApparentPower1();

    double computeApparentPower2();

    void setSpanningTreeEdge(boolean spanningTreeEdge);

    boolean isSpanningTreeEdge();
}
