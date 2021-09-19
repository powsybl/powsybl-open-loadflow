/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.commons.PowsyblException;
import com.powsybl.iidm.network.Branch;
import com.powsybl.iidm.network.LimitType;
import com.powsybl.iidm.network.TieLine;
import com.powsybl.openloadflow.network.*;
import com.powsybl.security.results.BranchResult;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class LfTieLineBranch extends AbstractFictitiousLfBranch {

    private final TieLine tieLine;

    private final Branch.Side side;

    protected LfTieLineBranch(LfNetwork network, LfBus bus1, LfBus bus2, PiModel piModel, TieLine tieLine, Branch.Side side) {
        super(network, bus1, bus2, piModel);
        this.tieLine = tieLine;
        this.side = side;
    }

    public static LfTieLineBranch create(TieLine tieLine, Branch.Side side, LfNetwork network, LfBus bus1, LfBus bus2) {
        Objects.requireNonNull(tieLine);
        Objects.requireNonNull(bus1);
        Objects.requireNonNull(bus2);
        double nominalV = tieLine.getTerminal(side).getVoltageLevel().getNominalV();
        double zb = nominalV * nominalV / PerUnit.SB;
        TieLine.HalfLine halfLine = tieLine.getHalf(side);
        PiModel piModel = new SimplePiModel()
                .setR(halfLine.getR() / zb)
                .setX(halfLine.getX() / zb)
                .setG1(halfLine.getG1() * zb)
                .setG2(halfLine.getG2() * zb)
                .setB1(halfLine.getB1() * zb)
                .setB2(halfLine.getB2() * zb);
        return new LfTieLineBranch(network, bus1, bus2, piModel, tieLine, side);
    }

    @Override
    public String getId() {
        return tieLine.getHalf(side).getId();
    }

    @Override
    public boolean hasPhaseControlCapability() {
        return false;
    }

    @Override
    public List<LfLimit> getLimits1(LimitType type) {
        if (side == Branch.Side.ONE) {
            // half line at side 1 has limits at its terminal 1 only.
            switch (type) {
                case ACTIVE_POWER:
                    return getLimits1(type, tieLine.getActivePowerLimits1());
                case APPARENT_POWER:
                    return getLimits1(type, tieLine.getApparentPowerLimits1());
                case CURRENT:
                    return getLimits1(type, tieLine.getCurrentLimits1());
                case VOLTAGE:
                default:
                    throw new UnsupportedOperationException(String.format("Getting %s limits is not supported.", type.name()));
            }
        } else if (side == Branch.Side.TWO) {
            return Collections.emptyList();
        } else {
            throw new UnsupportedOperationException(String.format("Getting %s limits is not supported.", type.name()));
        }
    }

    @Override
    public List<LfLimit> getLimits2(LimitType type) {
        if (side == Branch.Side.TWO) {
            // half line at side 2 has limits at its terminal 2 only.
            switch (type) {
                case ACTIVE_POWER:
                    return getLimits2(type, tieLine.getActivePowerLimits2());
                case APPARENT_POWER:
                    return getLimits2(type, tieLine.getApparentPowerLimits2());
                case CURRENT:
                    return getLimits2(type, tieLine.getCurrentLimits2());
                case VOLTAGE:
                default:
                    throw new UnsupportedOperationException(String.format("Getting %s limits is not supported.", type.name()));
            }
        } else if (side == Branch.Side.ONE) {
            return Collections.emptyList();
        } else {
            throw new UnsupportedOperationException(String.format("Getting %s limits is not supported.", type.name()));
        }
    }

    @Override
    public BranchResult createBranchResult(double preContingencyP1, double branchInContingencyP1) {
        throw new PowsyblException("Unsupported type of branch for branch result: " + getId());
    }

    @Override
    public void updateState(boolean phaseShifterRegulationOn, boolean isTransformerVoltageControlOn) {
        tieLine.getTerminal(side).setP(p.eval() * PerUnit.SB);
        tieLine.getTerminal(side).setQ(q.eval() * PerUnit.SB);
    }
}
