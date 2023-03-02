/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.commons.PowsyblException;
import com.powsybl.iidm.network.LimitType;
import com.powsybl.iidm.network.Switch;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.util.Evaluable;
import com.powsybl.security.results.BranchResult;

import java.util.Collections;
import java.util.List;

import static com.powsybl.openloadflow.util.EvaluableConstants.NAN;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class LfSwitch extends AbstractLfBranch {

    private final Ref<Switch> switchRef;

    public LfSwitch(LfNetwork network, LfBus bus1, LfBus bus2, Switch aSwitch,
                    LfNetworkParameters parameters, NominalVoltageMapping nominalVoltageMapping) {
        super(network, bus1, bus2, new SimplePiModel(), parameters, nominalVoltageMapping);
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
    public BranchResult createBranchResult(double preContingencyBranchP1, double preContingencyBranchOfContingencyP1, boolean createExtension) {
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
    public void updateState(LfNetworkStateUpdateParameters parameters) {
        // nothing to do
    }

    @Override
    public void updateFlows(double p1, double q1, double p2, double q2) {
        // nothing to do
    }
}
