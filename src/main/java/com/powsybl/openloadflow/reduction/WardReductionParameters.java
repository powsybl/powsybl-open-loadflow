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

import java.util.Set;

/**
 * @author Romain Courtier {@literal <romain.courtier at rte-france.com>}
 */
public class WardReductionParameters {

    private LoadFlowParameters parameters = new LoadFlowParameters();
    private MatrixFactory matrixFactory = new SparseMatrixFactory();
    private WardReductionType reductionType;
    private Set<String> internalVoltageLevelIds;

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

    public WardReductionType getReductionType() {
        return reductionType;
    }

    public WardReductionParameters setReductionType(WardReductionType reductionType) {
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
