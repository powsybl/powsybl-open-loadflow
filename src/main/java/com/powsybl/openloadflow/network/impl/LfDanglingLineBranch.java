/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.iidm.network.DanglingLine;
import com.powsybl.iidm.network.LimitType;
import com.powsybl.iidm.network.LoadingLimits;
import com.powsybl.iidm.network.TwoSides;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.sa.LimitReductionManager;
import com.powsybl.openloadflow.util.PerUnit;
import com.powsybl.security.results.BranchResult;

import java.util.List;
import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class LfDanglingLineBranch extends AbstractImpedantLfBranch {

    private final Ref<DanglingLine> danglingLineRef;

    protected LfDanglingLineBranch(LfNetwork network, LfBus bus1, LfBus bus2, PiModel piModel, DanglingLine danglingLine,
                                   LfNetworkParameters parameters) {
        super(network, bus1, bus2, piModel, parameters);
        this.danglingLineRef = Ref.create(danglingLine, parameters.isCacheEnabled());
    }

    public static LfDanglingLineBranch create(DanglingLine danglingLine, LfNetwork network, LfBus bus1, LfBus bus2,
                                              LfNetworkParameters parameters) {
        Objects.requireNonNull(danglingLine);
        Objects.requireNonNull(bus1);
        Objects.requireNonNull(bus2);
        Objects.requireNonNull(parameters);
        double zb = PerUnit.zb(danglingLine.getTerminal().getVoltageLevel().getNominalV());
        PiModel piModel = new SimplePiModel()
                .setR(danglingLine.getR() / zb)
                .setX(danglingLine.getX() / zb)
                .setG1(danglingLine.getG() / 2 * zb)
                .setG2(danglingLine.getG() / 2 * zb)
                .setB1(danglingLine.getB() / 2 * zb)
                .setB2(danglingLine.getB() / 2 * zb);
        return new LfDanglingLineBranch(network, bus1, bus2, piModel, danglingLine, parameters);
    }

    private DanglingLine getDanglingLine() {
        return danglingLineRef.get();
    }

    @Override
    public String getId() {
        return getDanglingLine().getId();
    }

    @Override
    public BranchType getBranchType() {
        return BranchType.DANGLING_LINE;
    }

    @Override
    public boolean hasPhaseControllerCapability() {
        return false;
    }

    @Override
    public List<BranchResult> createBranchResult(double preContingencyBranchP1, double preContingencyBranchOfContingencyP1, boolean createExtension) {
        // in a security analysis, we don't have any way to monitor the flows at boundary side. So in the branch result,
        // we follow the convention side 1 for network side and side 2 for boundary side.
        double currentScale = PerUnit.ib(getDanglingLine().getTerminal().getVoltageLevel().getNominalV());
        return List.of(new BranchResult(getId(), p1.eval() * PerUnit.SB, q1.eval() * PerUnit.SB, currentScale * i1.eval(),
                p2.eval() * PerUnit.SB, q2.eval() * PerUnit.SB, currentScale * i2.eval(), Double.NaN));
    }

    @Override
    public List<LfLimit> getLimits1(final LimitType type) {
        var danglingLine = getDanglingLine();
        switch (type) {
            case ACTIVE_POWER:
                return getLimits1(type, danglingLine.getActivePowerLimits().orElse(null));
            case APPARENT_POWER:
                return getLimits1(type, danglingLine.getApparentPowerLimits().orElse(null));
            case CURRENT:
                return getLimits1(type, danglingLine.getCurrentLimits().orElse(null));
            case VOLTAGE:
            default:
                throw new UnsupportedOperationException(String.format("Getting %s limits is not supported.", type.name()));
        }
    }

    @Override
    public List<Double> getLimitReductions(TwoSides side, LimitReductionManager limitReductionManager, LoadingLimits limits) {
        return null;
    }

    @Override
    public void updateState(LfNetworkStateUpdateParameters parameters) {
        updateFlows(p1.eval(), q1.eval(), Double.NaN, Double.NaN);
    }

    @Override
    public void updateFlows(double p1, double q1, double p2, double q2) {
        // Network side is always on side 1.
        getDanglingLine().getTerminal().setP(p1 * PerUnit.SB)
                .setQ(q1 * PerUnit.SB);
    }
}
