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
import java.util.Map;
import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class LfTieLineBranch extends AbstractImpedantLfBranch {

    private final Ref<BoundaryLine> boundaryLine1Ref;

    private final Ref<BoundaryLine> boundaryLine2Ref;

    private final String id;

    protected LfTieLineBranch(LfNetwork network, LfBus bus1, LfBus bus2, PiModel piModel, TieLine tieLine, LfNetworkParameters parameters) {
        super(network, bus1, bus2, piModel, parameters);
        this.boundaryLine1Ref = Ref.create(tieLine.getBoundaryLine1(), parameters.isCacheEnabled());
        this.boundaryLine2Ref = Ref.create(tieLine.getBoundaryLine2(), parameters.isCacheEnabled());
        this.id = tieLine.getId();
    }

    public static LfTieLineBranch create(TieLine line, LfNetwork network, LfBus bus1, LfBus bus2, LfNetworkParameters parameters) {
        Objects.requireNonNull(line);
        Objects.requireNonNull(network);
        Objects.requireNonNull(parameters);
        double nominalV2 = line.getBoundaryLine2().getTerminal().getVoltageLevel().getNominalV();
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
        return List.of(id, boundaryLine1Ref.get().getId(), boundaryLine2Ref.get().getId());
    }

    @Override
    public BranchType getBranchType() {
        return BranchType.TIE_LINE;
    }

    public BoundaryLine getHalf1() {
        return boundaryLine1Ref.get();
    }

    public BoundaryLine getHalf2() {
        return boundaryLine2Ref.get();
    }

    @Override
    public List<BranchResult> createBranchResult(double preContingencyBranchP1, double preContingencyBranchOfContingencyP1,
                                                 boolean createExtension, Map<String, LfBranchResults> zeroImpedanceFlows,
                                                 LoadFlowModel loadFlowModel) {
        double nominalV1 = getHalf1().getTerminal().getVoltageLevel().getNominalV();
        double nominalV2 = getHalf2().getTerminal().getVoltageLevel().getNominalV();
        double currentScale1 = PerUnit.ib(nominalV1);
        double currentScale2 = PerUnit.ib(nominalV2);

        var branchResult = buildBranchResult(loadFlowModel, zeroImpedanceFlows, currentScale1, currentScale2, preContingencyBranchP1, preContingencyBranchOfContingencyP1);

        var half1Result = new BranchResult(getHalf1().getId(), branchResult.getP1(), branchResult.getQ1(), branchResult.getI1(), Double.NaN, Double.NaN, Double.NaN, branchResult.getFlowTransfer());
        var half2Result = new BranchResult(getHalf2().getId(), branchResult.getP2(), branchResult.getQ2(), branchResult.getI2(), Double.NaN, Double.NaN, Double.NaN, branchResult.getFlowTransfer());
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
                return getLimits1(type, getHalf1()::getActivePowerLimits, limitReductionManager);
            case APPARENT_POWER:
                return getLimits1(type, getHalf1()::getApparentPowerLimits, limitReductionManager);
            case CURRENT:
                return getLimits1(type, getHalf1()::getCurrentLimits, limitReductionManager);
            case VOLTAGE:
            default:
                throw new UnsupportedOperationException(String.format("Getting %s limits is not supported.", type.name()));
        }
    }

    @Override
    public List<LfLimit> getLimits2(final LimitType type, LimitReductionManager limitReductionManager) {
        switch (type) {
            case ACTIVE_POWER:
                return getLimits2(type, getHalf2()::getActivePowerLimits, limitReductionManager);
            case APPARENT_POWER:
                return getLimits2(type, getHalf2()::getApparentPowerLimits, limitReductionManager);
            case CURRENT:
                return getLimits2(type, getHalf2()::getCurrentLimits, limitReductionManager);
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
