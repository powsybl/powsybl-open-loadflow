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
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class LfBoundaryLineBranch extends AbstractImpedantLfBranch {

    private final Ref<BoundaryLine> boundaryLineRef;

    protected LfBoundaryLineBranch(LfNetwork network, LfBus bus1, LfBus bus2, PiModel piModel, BoundaryLine boundaryLine,
                                   LfNetworkParameters parameters) {
        super(network, bus1, bus2, piModel, parameters);
        this.boundaryLineRef = Ref.create(boundaryLine, parameters.isCacheEnabled());
    }

    public static LfBoundaryLineBranch create(BoundaryLine boundaryLine, LfNetwork network, LfBus bus1, LfBus bus2,
                                              LfNetworkParameters parameters) {
        Objects.requireNonNull(boundaryLine);
        Objects.requireNonNull(bus1);
        Objects.requireNonNull(bus2);
        Objects.requireNonNull(parameters);
        double zb = PerUnit.zb(boundaryLine.getTerminal().getVoltageLevel().getNominalV());
        // iIDM BoundaryLine shunt admittance is network side only which is always side 1 (boundary is side 2).
        PiModel piModel = new SimplePiModel()
                .setR(boundaryLine.getR() / zb)
                .setX(boundaryLine.getX() / zb)
                .setG1(boundaryLine.getG() * zb)
                .setG2(0)
                .setB1(boundaryLine.getB() * zb)
                .setB2(0);
        return new LfBoundaryLineBranch(network, bus1, bus2, piModel, boundaryLine, parameters);
    }

    private BoundaryLine getBoundaryLine() {
        return boundaryLineRef.get();
    }

    @Override
    public String getId() {
        return getBoundaryLine().getId();
    }

    @Override
    public BranchType getBranchType() {
        return BranchType.BOUNDARY_LINE;
    }

    @Override
    public boolean hasPhaseControllerCapability() {
        return false;
    }

    @Override
    public List<BranchResult> createBranchResult(double preContingencyBranchP1, double preContingencyBranchOfContingencyP1,
                                                 boolean createExtension, Map<String, LfBranchResults> zeroImpedanceFlows,
                                                 LoadFlowModel loadFlowModel) {
        // in a security analysis, we don't have any way to monitor the flows at boundary side. So in the branch result,
        // we follow the convention side 1 for network side and side 2 for boundary side.
        double currentScale = PerUnit.ib(getBoundaryLine().getTerminal().getVoltageLevel().getNominalV());
        return List.of(buildBranchResult(loadFlowModel, zeroImpedanceFlows, currentScale, currentScale, Double.NaN, Double.NaN));
    }

    private <T extends LoadingLimits> Supplier<Map<String, T>> toMapIndexedByOperationalLimitsGroupId(Function<OperationalLimitsGroup, Optional<T>> limitsGetter) {
        return () -> getBoundaryLine()
                .getAllSelectedOperationalLimitsGroups()
                .stream()
                .filter(o -> limitsGetter.apply(o).isPresent())
                .collect(Collectors.toMap(OperationalLimitsGroup::getId, o -> limitsGetter.apply(o).orElseThrow()));
    }

    @Override
    public List<LfLimitsGroup> getLimits1(final LimitType type, LimitReductionManager limitReductionManager) {
        switch (type) {
            case ACTIVE_POWER:
                return getLimits1(type, toMapIndexedByOperationalLimitsGroupId(OperationalLimitsGroup::getActivePowerLimits), limitReductionManager);
            case APPARENT_POWER:
                return getLimits1(type, toMapIndexedByOperationalLimitsGroupId(OperationalLimitsGroup::getApparentPowerLimits), limitReductionManager);
            case CURRENT:
                return getLimits1(type, toMapIndexedByOperationalLimitsGroupId(OperationalLimitsGroup::getCurrentLimits), limitReductionManager);
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
        updateFlows(p1.eval(), q1.eval(), Double.NaN, Double.NaN);
    }

    @Override
    public void updateFlows(double p1, double q1, double p2, double q2) {
        // Network side is always on side 1.
        getBoundaryLine().getTerminal().setP(p1 * PerUnit.SB)
                .setQ(q1 * PerUnit.SB);
    }
}
