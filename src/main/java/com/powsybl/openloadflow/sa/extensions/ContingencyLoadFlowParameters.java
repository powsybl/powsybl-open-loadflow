/**
 * Copyright (c) 2024, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.sa.extensions;

import com.powsybl.commons.extensions.AbstractExtension;
import com.powsybl.contingency.Contingency;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.LoadFlowParametersOverride;

import java.util.List;
import java.util.Optional;

/**
 * @author Valentin Mouradian {@literal <valentin.mouradian at artelys.com>}
 */
public class ContingencyLoadFlowParameters extends AbstractExtension<Contingency> implements LoadFlowParametersOverride {

    private Boolean distributedSlack;

    private Boolean areaInterchangeControl;

    private LoadFlowParameters.BalanceType balanceType;

    private List<String> outerLoopNames;

    @Override
    public String getName() {
        return "contingency-load-flow-parameters";
    }

    @Override
    public boolean isDistributedSlack(LoadFlowParameters loadFlowParameters) {
        return distributedSlack != null ? distributedSlack : loadFlowParameters.isDistributedSlack();
    }

    @Override
    public boolean isAreaInterchangeControl(OpenLoadFlowParameters loadFlowParametersExt) {
        return areaInterchangeControl != null ? areaInterchangeControl : loadFlowParametersExt.isAreaInterchangeControl();
    }

    @Override
    public LoadFlowParameters.BalanceType getBalanceType(LoadFlowParameters loadFlowParameters) {
        return balanceType != null ? balanceType : loadFlowParameters.getBalanceType();
    }

    @Override
    public List<String> getOuterLoopNames(OpenLoadFlowParameters loadFlowParametersExt) {
        return outerLoopNames != null ? outerLoopNames : loadFlowParametersExt.getOuterLoopNames();
    }

    public Optional<Boolean> isDistributedSlack() {
        return Optional.ofNullable(distributedSlack);
    }

    public Optional<Boolean> isAreaInterchangeControl() {
        return Optional.ofNullable(areaInterchangeControl);
    }

    public Optional<LoadFlowParameters.BalanceType> getBalanceType() {
        return Optional.ofNullable(balanceType);
    }

    public Optional<List<String>> getOuterLoopNames() {
        return Optional.ofNullable(outerLoopNames);
    }

    public ContingencyLoadFlowParameters setDistributedSlack(Boolean distributedSlack) {
        this.distributedSlack = distributedSlack;
        return this;
    }

    public ContingencyLoadFlowParameters setAreaInterchangeControl(Boolean areaInterchangeControl) {
        this.areaInterchangeControl = areaInterchangeControl;
        return this;
    }

    public ContingencyLoadFlowParameters setBalanceType(LoadFlowParameters.BalanceType balanceType) {
        this.balanceType = balanceType;
        return this;
    }

    public ContingencyLoadFlowParameters setOuterLoopNames(List<String> outerLoopNames) {
        this.outerLoopNames = outerLoopNames;
        return this;
    }
}
