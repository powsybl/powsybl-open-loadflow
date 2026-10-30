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
 * The parameters for performing a network reduction by Ward equivalent.
 *
 * @author Romain Courtier {@literal <romain.courtier at rte-france.com>}
 */
public class WardReductionParameters {

    private LoadFlowParameters parameters = new LoadFlowParameters();
    private MatrixFactory matrixFactory = new SparseMatrixFactory();
    private WardEquivalentType equivalentType = WardEquivalentType.STANDARD;
    private Set<String> internalVoltageLevelIds = Set.of();

    /**
     * Get the load flow parameters used for the {@code LfNetwork} creation.
     *
     * @return the {@link LoadFlowParameters} used for the {@code LfNetwork} creation.
     */
    public LoadFlowParameters getLoadFlowParameters() {
        return parameters;
    }

    /**
     * Set the load flow parameters to be used for the {@code LfNetwork} creation.
     *
     * @param parameters the {@link LoadFlowParameters} instance to be used for the {@code LfNetwork} creation.
     * @return this instance for method chaining.
     */
    public WardReductionParameters setLoadFlowParameters(LoadFlowParameters parameters) {
        this.parameters = parameters;
        return this;
    }

    /**
     * Get the matrix factory used for matrix operations.
     *
     * @return the {@link MatrixFactory} used for matrix operations.
     */
    public MatrixFactory getMatrixFactory() {
        return matrixFactory;
    }

    /**
     * Set the matrix factory to be used for matrix operations.
     *
     * @param matrixFactory the {@link MatrixFactory} instance to be used for matrix operations.
     * @return this instance for method chaining.
     */
    public WardReductionParameters setMatrixFactory(MatrixFactory matrixFactory) {
        this.matrixFactory = matrixFactory;
        return this;
    }

    /**
     * Get the Ward equivalent type used for the network reduction.
     *
     * @return the {@link WardEquivalentType} used for the network reduction.
     */
    public WardEquivalentType getEquivalentType() {
        return equivalentType;
    }

    /**
     * Set the Ward equivalent type to be used for the network reduction.
     *
     * @param equivalentType the {@link WardEquivalentType} to be used for the network reduction.
     * @return this instance for method chaining.
     */
    public WardReductionParameters setEquivalentType(WardEquivalentType equivalentType) {
        this.equivalentType = equivalentType;
        return this;
    }

    /**
     * Get the set of {@code VoltageLevel} IDs that won't be reduced.
     *
     * @return a set of strings representing the internal voltage level IDs.
     */
    public Set<String> getInternalVoltageLevelIds() {
        return internalVoltageLevelIds;
    }

    /**
     * Set the set of {@code VoltageLevel} IDs that won't be reduced.
     *
     * @param internalVoltageLevelIds the set of internal {@code VoltageLevel} IDs.
     * @return this instance for method chaining.
     */
    public WardReductionParameters setInternalVoltageLevelIds(Set<String> internalVoltageLevelIds) {
        this.internalVoltageLevelIds = internalVoltageLevelIds;
        return this;
    }
}
