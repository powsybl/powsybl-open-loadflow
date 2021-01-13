/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.sensi.apiv2;

import com.powsybl.math.matrix.DenseMatrix;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public interface SensitivityAnalysisResult2 {

    SensitivityAnalysisStatus getStatus();

    double getValue(String functionId, String variableId);

    double getValue(String contingencyId, String functionId, String variableId);

    DenseMatrix getMatrix(String factorMatrixId);

    DenseMatrix getMatrix(String contingencyId, String factorMatrixId);
}
