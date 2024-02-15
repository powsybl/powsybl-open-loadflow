/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.solver;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class NewtonKrylovParameters extends AbstractNewtonParameters<NewtonKrylovParameters> {

    public static final int DEFAULT_MAX_ITERATIONS = 100;

    public static final boolean LINE_SEARCH_DEFAULT_VALUE = false;

    private boolean lineSearch = LINE_SEARCH_DEFAULT_VALUE;

    public NewtonKrylovParameters() {
        super(DEFAULT_MAX_ITERATIONS);
    }

    public boolean isLineSearch() {
        return lineSearch;
    }

    public NewtonKrylovParameters setLineSearch(boolean lineSearch) {
        this.lineSearch = lineSearch;
        return this;
    }

    @Override
    public String toString() {
        return "NewtonKrylovParameters(" +
                "maxIterations=" + maxIterations +
                ", lineSearch=" + lineSearch +
                ')';
    }
}
