/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.sensi.apiv2;

import com.powsybl.math.matrix.DenseMatrix;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class SensitivityAnalysisResultImpl2 implements SensitivityAnalysisResult2 {

    private final SensitivityAnalysisStatus status;

    private final Map<Pair<String, String>, Double> values = new HashMap<>();

    private final Map<Triple<String, String, String>, Double> contingencyValues = new HashMap<>();

    private final Map<String, DenseMatrix> matrices = new HashMap<>();

    private final Map<Pair<String, String>, DenseMatrix> contingencyMatrices = new HashMap<>();

    public SensitivityAnalysisResultImpl2(SensitivityAnalysisStatus status) {
        this.status = Objects.requireNonNull(status);
    }

    public SensitivityAnalysisStatus getStatus() {
        return status;
    }

    public double getValue(String functionId, String variableId) {
        return values.computeIfAbsent(Pair.of(functionId, variableId), p -> {
            throw new IllegalArgumentException("Factor '" + functionId + "' wrt '" + variableId + "' not found");
        });
    }

    public double getValue(String contingencyId, String functionId, String variableId) {
        return contingencyValues.computeIfAbsent(Triple.of(contingencyId, functionId, variableId), p -> {
            throw new IllegalArgumentException("Factor '" + functionId + "' wrt '" + variableId + "' for contingency '"
                    + contingencyId + "' not found");
        });
    }

    public DenseMatrix getMatrix(String factorMatrixId) {
        return matrices.computeIfAbsent(factorMatrixId, p -> {
            throw new IllegalArgumentException("Factor matrix '" + factorMatrixId + "' not found");
        });
    }

    public DenseMatrix getMatrix(String contingencyId, String factorMatrixId) {
        return contingencyMatrices.computeIfAbsent(Pair.of(contingencyId, factorMatrixId), p -> {
            throw new IllegalArgumentException("Factor matrix '" + factorMatrixId + "' for contingency '" + contingencyId + "' not found");
        });
    }
}
