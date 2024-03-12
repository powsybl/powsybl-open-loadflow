/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.commons.PowsyblException;
import com.powsybl.iidm.network.LimitType;
import com.powsybl.iidm.network.LoadingLimits;
import com.powsybl.iidm.network.Switch;
import com.powsybl.iidm.network.TwoSides;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.sa.LimitReductionManager;
import com.powsybl.openloadflow.util.Evaluable;
import com.powsybl.security.results.BranchResult;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static com.powsybl.openloadflow.util.EvaluableConstants.NAN;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class LfSwitch extends AbstractLfBranch {

    private final Ref<Switch> switchRef;

    public LfSwitch(LfNetwork network, LfBus bus1, LfBus bus2, Switch aSwitch, LfNetworkParameters parameters) {
        super(network, Objects.requireNonNull(bus1), Objects.requireNonNull(bus2), new SimplePiModel(), parameters);
        this.switchRef = Ref.create(aSwitch, parameters.isCacheEnabled());
    }

    private Switch getSwitch() {
        return switchRef.get();
    }

    @Override
    public String getId() {
        return getSwitch().getId();
    }

    @Override
    public BranchType getBranchType() {
        return BranchType.SWITCH;
    }

    @Override
    public boolean hasPhaseControllerCapability() {
        return false;
    }

    @Override
    public boolean isConnectedSide1() {
        return true;
    }

    @Override
    public void setConnectedSide1(boolean connectedSide1) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isConnectedSide2() {
        return true;
    }

    @Override
    public void setConnectedSide2(boolean connectedSide2) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isDisconnectionAllowedSide1() {
        return false;
    }

    @Override
    public void setDisconnectionAllowedSide1(boolean disconnectionAllowedSide1) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isDisconnectionAllowedSide2() {
        return false;
    }

    @Override
    public void setDisconnectionAllowedSide2(boolean disconnectionAllowedSide2) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setP1(Evaluable p1) {
        // nothing to do
    }

    @Override
    public Evaluable getP1() {
        return NAN;
    }

    @Override
    public void setP2(Evaluable p2) {
        // nothing to do
    }

    @Override
    public Evaluable getP2() {
        return NAN;
    }

    @Override
    public void setQ1(Evaluable q1) {
        // nothing to do
    }

    @Override
    public Evaluable getQ1() {
        return NAN;
    }

    @Override
    public void setQ2(Evaluable q2) {
        // nothing to do
    }

    @Override
    public Evaluable getQ2() {
        return NAN;
    }

    @Override
    public void setI1(Evaluable i1) {
        // nothing to do
    }

    @Override
    public Evaluable getI1() {
        return NAN;
    }

    @Override
    public void setI2(Evaluable i2) {
        // nothing to do
    }

    @Override
    public Evaluable getI2() {
        return NAN;
    }

    @Override
    public Evaluable getOpenP1() {
        return NAN;
    }

    @Override
    public void setOpenP1(Evaluable openP1) {
        // nothing to do
    }

    @Override
    public Evaluable getOpenQ1() {
        return NAN;
    }

    @Override
    public void setOpenQ1(Evaluable openQ1) {
        // nothing to do
    }

    @Override
    public Evaluable getOpenI1() {
        return NAN;
    }

    @Override
    public void setOpenI1(Evaluable openI1) {
        // nothing to do
    }

    @Override
    public Evaluable getOpenP2() {
        return NAN;
    }

    @Override
    public void setOpenP2(Evaluable openP2) {
        // nothing to do
    }

    @Override
    public Evaluable getOpenQ2() {
        return NAN;
    }

    @Override
    public void setOpenQ2(Evaluable openQ2) {
        // nothing to do
    }

    @Override
    public Evaluable getOpenI2() {
        return NAN;
    }

    @Override
    public void setOpenI2(Evaluable openI2) {
        // nothing to do
    }

    @Override
    public Evaluable getClosedP1() {
        return NAN;
    }

    @Override
    public void setClosedP1(Evaluable closedP1) {
        // nothing to do
    }

    @Override
    public Evaluable getClosedQ1() {
        return NAN;
    }

    @Override
    public void setClosedQ1(Evaluable closedQ1) {
        // nothing to do
    }

    @Override
    public Evaluable getClosedI1() {
        return NAN;
    }

    @Override
    public void setClosedI1(Evaluable closedI1) {
        // nothing to do
    }

    @Override
    public Evaluable getClosedP2() {
        return NAN;
    }

    @Override
    public void setClosedP2(Evaluable closedP2) {
        // nothing to do
    }

    @Override
    public Evaluable getClosedQ2() {
        return NAN;
    }

    @Override
    public void setClosedQ2(Evaluable closedQ2) {
        // nothing to do
    }

    @Override
    public Evaluable getClosedI2() {
        return NAN;
    }

    @Override
    public void setClosedI2(Evaluable closedI2) {
        // nothing to do
    }

    @Override
    public void addAdditionalOpenP1(Evaluable openP1) {
        // nothing to do
    }

    @Override
    public List<Evaluable> getAdditionalOpenP1() {
        return Collections.emptyList();
    }

    @Override
    public void addAdditionalClosedP1(Evaluable closedP1) {
        // nothing to do
    }

    @Override
    public List<Evaluable> getAdditionalClosedP1() {
        return Collections.emptyList();
    }

    @Override
    public void addAdditionalOpenQ1(Evaluable openQ1) {
        // nothing to do
    }

    @Override
    public List<Evaluable> getAdditionalOpenQ1() {
        return Collections.emptyList();
    }

    @Override
    public void addAdditionalClosedQ1(Evaluable closedQ1) {
        // nothing to do
    }

    @Override
    public List<Evaluable> getAdditionalClosedQ1() {
        return Collections.emptyList();
    }

    @Override
    public void addAdditionalOpenP2(Evaluable openP2) {
        // nothing to do
    }

    @Override
    public List<Evaluable> getAdditionalOpenP2() {
        return Collections.emptyList();
    }

    @Override
    public void addAdditionalClosedP2(Evaluable closedP2) {
        // nothing to do
    }

    @Override
    public List<Evaluable> getAdditionalClosedP2() {
        return Collections.emptyList();
    }

    @Override
    public void addAdditionalOpenQ2(Evaluable openQ2) {
        // nothing to do
    }

    @Override
    public List<Evaluable> getAdditionalOpenQ2() {
        return Collections.emptyList();
    }

    @Override
    public void addAdditionalClosedQ2(Evaluable closedQ2) {
        // nothing to do
    }

    @Override
    public List<Evaluable> getAdditionalClosedQ2() {
        return Collections.emptyList();
    }

    @Override
    public List<BranchResult> createBranchResult(double preContingencyBranchP1, double preContingencyBranchOfContingencyP1, boolean createExtension) {
        throw new PowsyblException("Unsupported type of branch for branch result: " + getSwitch().getId());
    }

    public List<LfLimit> getLimits1(final LimitType type) {
        return Collections.emptyList();
    }

    @Override
    public List<LfLimit> getLimits2(final LimitType type) {
        return Collections.emptyList();
    }

    @Override
    public List<Double> getLimitReductions(TwoSides side, LimitReductionManager limitReductionManager, LoadingLimits limits) {
        return null;
    }

    @Override
    public void updateState(LfNetworkStateUpdateParameters parameters) {
        // nothing to do
    }

    @Override
    public void updateFlows(double p1, double q1, double p2, double q2) {
        // nothing to do
    }
}
