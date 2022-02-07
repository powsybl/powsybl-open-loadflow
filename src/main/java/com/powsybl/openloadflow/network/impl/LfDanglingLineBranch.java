/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.commons.PowsyblException;
import com.powsybl.iidm.network.DanglingLine;
import com.powsybl.iidm.network.LimitType;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.util.PerUnit;
import com.powsybl.security.results.BranchResult;

import java.util.List;
import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class LfDanglingLineBranch extends AbstractFictitiousLfBranch {

    private final DanglingLine danglingLine;

    protected LfDanglingLineBranch(LfNetwork network, LfBus bus1, LfBus bus2, PiModel piModel, DanglingLine danglingLine) {
        super(network, bus1, bus2, piModel);
        this.danglingLine = danglingLine;
    }

    public static LfDanglingLineBranch create(DanglingLine danglingLine, LfNetwork network, LfBus bus1, LfBus bus2) {
        Objects.requireNonNull(danglingLine);
        Objects.requireNonNull(bus1);
        Objects.requireNonNull(bus2);
        double nominalV = danglingLine.getTerminal().getVoltageLevel().getNominalV();
        double zb = nominalV * nominalV / PerUnit.SB;
        PiModel piModel = new SimplePiModel()
                .setR(danglingLine.getR() / zb)
                .setX(danglingLine.getX() / zb)
                .setG1(danglingLine.getG() / 2 * zb)
                .setG2(danglingLine.getG() / 2 * zb)
                .setB1(danglingLine.getB() / 2 * zb)
                .setB2(danglingLine.getB() / 2 * zb);
        return new LfDanglingLineBranch(network, bus1, bus2, piModel, danglingLine);
    }

    @Override
    public String getId() {
        return danglingLine.getId();
    }

    @Override
    public boolean hasPhaseControlCapability() {
        return false;
    }

    @Override
    public BranchResult createBranchResult(double preContingencyP1, double branchInContingencyP1) {
        throw new PowsyblException("Unsupported type of branch for branch result: " + getId());
    }

    @Override
    public List<LfLimit> getLimits1(final LimitType type) {
        switch (type) {
            case ACTIVE_POWER:
                return getLimits1(type, danglingLine.getActivePowerLimits());
            case APPARENT_POWER:
                return getLimits1(type, danglingLine.getApparentPowerLimits());
            case CURRENT:
                return getLimits1(type, danglingLine.getCurrentLimits());
            case VOLTAGE:
            default:
                throw new UnsupportedOperationException(String.format("Getting %s limits is not supported.", type.name()));
        }
    }

    @Override
    public void updateState(boolean phaseShifterRegulationOn, boolean isTransformerVoltageControlOn) {
        danglingLine.getTerminal().setP(p.eval() * PerUnit.SB);
        danglingLine.getTerminal().setQ(q.eval() * PerUnit.SB);
    }
}
