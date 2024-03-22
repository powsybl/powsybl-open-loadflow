/**
 * Copyright (c) 2024, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.sensi;

import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.openloadflow.network.DisabledNetwork;
import com.powsybl.openloadflow.network.impl.PropagatedContingency;

import java.util.HashMap;
import java.util.List;

// TODO : refactor to avoid matrices storage
public class WoodburyResult {
    private double[] preContingenciesFlowStates;
    private final DenseMatrix preContingenciesFactorStates;
    List<PropagatedContingency> contingencies;

    // TODO : modify to use list if not necessary to use HashMap
    private final HashMap<Integer, DenseMatrix> postContingenciesFlowStates;
    private final HashMap<Integer, DenseMatrix> postContingenciesStates; // indexed on the contingencies
    private final HashMap<Integer, DisabledNetwork> postContingenciesDisabledNetworks;
    private final HashMap<Integer, Boolean> isContingencySuccessful;

    public WoodburyResult(DenseMatrix preContingenciesFactorStates, List<PropagatedContingency> contingencies) {
        this.preContingenciesFactorStates = preContingenciesFactorStates;
        this.contingencies = contingencies;
        this.isContingencySuccessful = new HashMap<>();
        this.postContingenciesFlowStates = new HashMap<>();
        this.postContingenciesStates = new HashMap<>();
        this.postContingenciesDisabledNetworks = new HashMap<>();
    }

    public double[] getPreContingenciesFlowStates() {
        return preContingenciesFlowStates;
    }

    public void setPreContingenciesFlowStates(double[] preContingenciesFlowStates) {
        this.preContingenciesFlowStates = preContingenciesFlowStates;
    }

    public DenseMatrix getPreContingenciesFactorStates() {
        return preContingenciesFactorStates;
    }

    public List<PropagatedContingency> getContingencies() {
        return contingencies;
    }

    public HashMap<Integer, DenseMatrix> getPostContingenciesFlowStates() {
        return postContingenciesFlowStates;
    }

    public HashMap<Integer, DenseMatrix> getPostContingenciesStates() {
        return postContingenciesStates;
    }

    public HashMap<Integer, DisabledNetwork> getPostContingenciesDisabledNetworks() {
        return postContingenciesDisabledNetworks;
    }

    public HashMap<Integer, Boolean> getIsContingencySuccessful() {
        return isContingencySuccessful;
    }
}
