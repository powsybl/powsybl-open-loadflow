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
import java.util.Objects;
import java.util.Optional;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public interface LfBranch extends LfElement {

    enum BranchType {
        LINE,
        TRANSFO_2,
        TRANSFO_3_LEG_1,
        TRANSFO_3_LEG_2,
        TRANSFO_3_LEG_3,
        DANGLING_LINE,
        SWITCH
    }

    class LfLimit {

        private int acceptableDuration;

        private final double value;

        public LfLimit(int acceptableDuration, double value) {
            this.acceptableDuration = acceptableDuration;
            this.value = value;
        }

        public static LfLimit createTemporaryLimit(int acceptableDuration, double valuePerUnit) {
            return new LfLimit(acceptableDuration, valuePerUnit);
        }

        public static LfLimit createPermanentLimit(double valuePerUnit) {
            return new LfLimit(Integer.MAX_VALUE, valuePerUnit);
        }

        public int getAcceptableDuration() {
            return acceptableDuration;
        }

        public double getValue() {
            return value;
        }

        public void setAcceptableDuration(int acceptableDuration) {
            this.acceptableDuration = acceptableDuration;
        }
    }

    BranchType getBranchType();

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

    List<LfLimit> getLimits1(LimitType type);

    default List<LfLimit> getLimits2(LimitType type) {
        return Collections.emptyList();
    }

    boolean hasPhaseControlCapability();

    Optional<DiscretePhaseControl> getDiscretePhaseControl();

    void updateState(LfNetworkStateUpdateParameters parameters);

    void updateFlows(double p1, double q1, double p2, double q2);

    boolean isPhaseController();

    boolean isPhaseControlled();

    void setDiscretePhaseControl(DiscretePhaseControl discretePhaseControl);

    boolean isPhaseControlEnabled();

    void setPhaseControlEnabled(boolean phaseControlEnabled);

    Optional<TransformerVoltageControl> getVoltageControl();

    boolean isVoltageControlEnabled();

    void setVoltageControlEnabled(boolean voltageControlEnabled);

    boolean isVoltageController();

    void setVoltageControl(TransformerVoltageControl transformerVoltageControl);

    BranchResult createBranchResult(double preContingencyBranchP1, double preContingencyBranchOfContingencyP1, boolean createExtension);

    double computeApparentPower1();

    double computeApparentPower2();

    boolean isZeroImpedance(boolean dc);

    void setZeroImpedanceNetwork(boolean dc, LfZeroImpedanceNetwork zeroImpedanceNetwork);

    void setSpanningTreeEdge(boolean dc, boolean spanningTreeEdge);

    boolean isSpanningTreeEdge(boolean dc);

    Evaluable getA1();

    void setA1(Evaluable a1);

    static double getA(LfBranch branch) {
        Objects.requireNonNull(branch);
        PiModel piModel = branch.getPiModel();
        return PiModel.A2 - piModel.getA1();
    }

    static double getDiscretePhaseControlTarget(LfBranch branch, DiscretePhaseControl.Unit unit) {
        Objects.requireNonNull(branch);
        Objects.requireNonNull(unit);
        Optional<DiscretePhaseControl> phaseControl = branch.getDiscretePhaseControl().filter(dpc -> branch.isPhaseControlled());
        if (phaseControl.isEmpty()) {
            throw new PowsyblException("Branch '" + branch.getId() + "' is not phase-controlled");
        }
        if (phaseControl.get().getUnit() != unit) {
            throw new PowsyblException("Branch '" + branch.getId() + "' has not a target in " + unit);
        }
        return phaseControl.get().getTargetValue();
    }

    Optional<ReactivePowerControl> getReactivePowerControl();

    void setReactivePowerControl(ReactivePowerControl reactivePowerControl);

    boolean isConnectedAtBothSides();

    void setMinZ(double lowImpedanceThreshold);
}
