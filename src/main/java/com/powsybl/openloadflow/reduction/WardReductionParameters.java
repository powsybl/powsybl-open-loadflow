/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.reduction;

import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.math.matrix.SparseMatrixFactory;
import com.powsybl.openloadflow.graph.EvenShiloachGraphDecrementalConnectivityFactory;
import com.powsybl.openloadflow.graph.GraphConnectivityFactory;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;

import java.util.Set;

/**
 * @author Romain Courtier {@literal <romain.courtier at rte-france.com>}
 */
public class WardReductionParameters {

    private LoadFlowParameters parameters = new LoadFlowParameters();
    private MatrixFactory matrixFactory = new SparseMatrixFactory();
    private GraphConnectivityFactory<LfBus, LfBranch> connectivityFactory = new EvenShiloachGraphDecrementalConnectivityFactory<>();

    private ReductionType reductionType;
    private Set<String> internalVoltageLevelIds;

    public enum ReductionType {
        WARD_ADMITTANCE;
        // WARD_INJECTION
        // WARD_PV
        // EXTENDED_WARD
    }

    public LoadFlowParameters getLoadFlowParameters() {
        return parameters;
    }

    public WardReductionParameters setLoadFlowParameters(LoadFlowParameters parameters) {
        this.parameters = parameters;
        return this;
    }

    public MatrixFactory getMatrixFactory() {
        return matrixFactory;
    }

    public WardReductionParameters setMatrixFactory(MatrixFactory matrixFactory) {
        this.matrixFactory = matrixFactory;
        return this;
    }

    public GraphConnectivityFactory<LfBus, LfBranch> getConnectivityFactory() {
        return connectivityFactory;
    }

    public WardReductionParameters setConnectivityFactory(GraphConnectivityFactory<LfBus, LfBranch> connectivityFactory) {
        this.connectivityFactory = connectivityFactory;
        return this;
    }

    public ReductionType getReductionType() {
        return reductionType;
    }

    public WardReductionParameters setReductionType(ReductionType reductionType) {
        this.reductionType = reductionType;
        return this;
    }

    public Set<String> getInternalVoltageLevelIds() {
        return internalVoltageLevelIds;
    }

    public WardReductionParameters setInternalVoltageLevelIds(Set<String> internalVoltageLevelIds) {
        this.internalVoltageLevelIds = internalVoltageLevelIds;
        return this;
    }
}
