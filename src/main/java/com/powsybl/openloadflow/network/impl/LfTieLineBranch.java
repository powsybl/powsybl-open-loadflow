/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.iidm.network.*;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.sa.LimitReductionManager;
import com.powsybl.openloadflow.util.PerUnit;
import com.powsybl.security.results.BranchResult;

import java.util.List;
import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class LfTieLineBranch extends AbstractImpedantLfBranch {

    private final Ref<DanglingLine> danglingLine1Ref;

    private final Ref<DanglingLine> danglingLine2Ref;

    private final String id;

    protected LfTieLineBranch(LfNetwork network, LfBus bus1, LfBus bus2, PiModel piModel, TieLine tieLine, LfNetworkParameters parameters) {
        super(network, bus1, bus2, piModel, parameters);
        this.danglingLine1Ref = Ref.create(tieLine.getDanglingLine1(), parameters.isCacheEnabled());
        this.danglingLine2Ref = Ref.create(tieLine.getDanglingLine2(), parameters.isCacheEnabled());
        this.id = tieLine.getId();
    }

    public static LfTieLineBranch create(TieLine line, LfNetwork network, LfBus bus1, LfBus bus2, LfNetworkParameters parameters) {
        Objects.requireNonNull(line);
        Objects.requireNonNull(network);
        Objects.requireNonNull(parameters);
        double nominalV2 = line.getDanglingLine2().getTerminal().getVoltageLevel().getNominalV();
        double zb = PerUnit.zb(nominalV2);
        PiModel piModel = new SimplePiModel()
                .setR1(1 / Transformers.getRatioPerUnitBase(line))
                .setR(line.getR() / zb)
                .setX(line.getX() / zb)
                .setG1(line.getG1() * zb)
                .setG2(line.getG2() * zb)
                .setB1(line.getB1() * zb)
                .setB2(line.getB2() * zb);
        return new LfTieLineBranch(network, bus1, bus2, piModel, line, parameters);
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public List<String> getOriginalIds() {
        return List.of(id, danglingLine1Ref.get().getId(), danglingLine2Ref.get().getId());
    }

    @Override
    public BranchType getBranchType() {
        return BranchType.TIE_LINE;
    }

    public DanglingLine getHalf1() {
        return danglingLine1Ref.get();
    }

    public DanglingLine getHalf2() {
        return danglingLine2Ref.get();
    }

    @Override
    public List<BranchResult> createBranchResult(double preContingencyBranchP1, double preContingencyBranchOfContingencyP1, boolean createExtension) {
        double flowTransfer = Double.NaN;
        if (!Double.isNaN(preContingencyBranchP1) && !Double.isNaN(preContingencyBranchOfContingencyP1)) {
            flowTransfer = (p1.eval() * PerUnit.SB - preContingencyBranchP1) / preContingencyBranchOfContingencyP1;
        }
        double nominalV1 = getHalf1().getTerminal().getVoltageLevel().getNominalV();
        double nominalV2 = getHalf2().getTerminal().getVoltageLevel().getNominalV();
        double currentScale1 = PerUnit.ib(nominalV1);
        double currentScale2 = PerUnit.ib(nominalV2);
        var branchResult = new BranchResult(getId(), p1.eval() * PerUnit.SB, q1.eval() * PerUnit.SB, currentScale1 * i1.eval(),
                p2.eval() * PerUnit.SB, q2.eval() * PerUnit.SB, currentScale2 * i2.eval(), flowTransfer);
        var half1Result = new BranchResult(getHalf1().getId(), p1.eval() * PerUnit.SB, q1.eval() * PerUnit.SB, currentScale1 * i1.eval(),
                Double.NaN, Double.NaN, Double.NaN, flowTransfer);
        var half2Result = new BranchResult(getHalf2().getId(), p2.eval() * PerUnit.SB, q2.eval() * PerUnit.SB, currentScale2 * i2.eval(),
                Double.NaN, Double.NaN, Double.NaN, flowTransfer);
        if (createExtension) {
            branchResult.addExtension(OlfBranchResult.class, new OlfBranchResult(piModel.getR1(), piModel.getContinuousR1(),
                    getV1() * nominalV1, getV2() * nominalV2, Math.toDegrees(getAngle1()), Math.toDegrees(getAngle2())));
            half1Result.addExtension(OlfBranchResult.class, new OlfBranchResult(piModel.getR1(), piModel.getContinuousR1(),
                    getV1() * nominalV1, Double.NaN, Math.toDegrees(getAngle1()), Double.NaN));
            half2Result.addExtension(OlfBranchResult.class, new OlfBranchResult(piModel.getR1(), piModel.getContinuousR1(),
                    Double.NaN, getV2() * nominalV2, Double.NaN, Math.toDegrees(getAngle2())));
        }
        return List.of(branchResult, half1Result, half2Result);
    }

    @Override
    public List<LfLimit> getLimits1(final LimitType type, LimitReductionManager limitReductionManager) {
        switch (type) {
            case ACTIVE_POWER:
                return getLimits1(type, getHalf1().getActivePowerLimits().orElse(null), limitReductionManager);
            case APPARENT_POWER:
                return getLimits1(type, getHalf1().getApparentPowerLimits().orElse(null), limitReductionManager);
            case CURRENT:
                return getLimits1(type, getHalf1().getCurrentLimits().orElse(null), limitReductionManager);
            case VOLTAGE:
            default:
                throw new UnsupportedOperationException(String.format("Getting %s limits is not supported.", type.name()));
        }
    }

    @Override
    public List<LfLimit> getLimits2(final LimitType type, LimitReductionManager limitReductionManager) {
        switch (type) {
            case ACTIVE_POWER:
                return getLimits2(type, getHalf2().getActivePowerLimits().orElse(null), limitReductionManager);
            case APPARENT_POWER:
                return getLimits2(type, getHalf2().getApparentPowerLimits().orElse(null), limitReductionManager);
            case CURRENT:
                return getLimits2(type, getHalf2().getCurrentLimits().orElse(null), limitReductionManager);
            case VOLTAGE:
            default:
                throw new UnsupportedOperationException(String.format("Getting %s limits is not supported.", type.name()));
        }
    }

    @Override
    public double[] getLimitReductions(TwoSides side, LimitReductionManager limitReductionManager, LoadingLimits limits) {
        return new double[] {};
    }

    @Override
    public void updateState(LfNetworkStateUpdateParameters parameters, LfNetworkUpdateReport updateReport) {
        updateFlows(p1.eval(), q1.eval(), p2.eval(), q2.eval());
    }

    @Override
    public void updateFlows(double p1, double q1, double p2, double q2) {
        getHalf1().getTerminal().setP(p1 * PerUnit.SB)
                .setQ(q1 * PerUnit.SB);
        getHalf2().getTerminal().setP(p2 * PerUnit.SB)
                .setQ(q2 * PerUnit.SB);
    }

    @Override
    public boolean hasPhaseControllerCapability() {
        return false;
    }
}
