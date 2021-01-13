/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.sensi.apiv2;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A set of simple factors or factor matrices associated to a contingency context.
 *
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public final class SensitivityFactorConfiguration {

    private final ContingencyContext contingencyContext;

    private final String contingencyId;

    private final List<SimpleSensitivityFactor> simpleFactors;

    private final List<MatrixSensitivityFactor> matrixFactors;

    private SensitivityFactorConfiguration(ContingencyContext contingencyContext, String contingencyId, List<SimpleSensitivityFactor> factors,
                                           List<MatrixSensitivityFactor> matrixFactors) {
        this.contingencyContext = contingencyContext;
        this.contingencyId = contingencyId;
        this.simpleFactors = Objects.requireNonNull(factors);
        this.matrixFactors = matrixFactors;
    }

    public static SensitivityFactorConfiguration create(List<SimpleSensitivityFactor> simpleFactors, List<MatrixSensitivityFactor> matrixFactors) {
        return new SensitivityFactorConfiguration(ContingencyContext.NONE, null, simpleFactors, matrixFactors);
    }

    public static SensitivityFactorConfiguration createForOneContingency(String contingencyId, List<SimpleSensitivityFactor> simpleFactors,
                                                                         List<MatrixSensitivityFactor> matrixFactors) {
        return new SensitivityFactorConfiguration(ContingencyContext.ONE_CONTINGENCY, contingencyId, simpleFactors, matrixFactors);
    }

    public static SensitivityFactorConfiguration createForAllContingency(List<SimpleSensitivityFactor> simpleFactors, List<MatrixSensitivityFactor> matrixFactors) {
        return new SensitivityFactorConfiguration(ContingencyContext.ALL_CONTINGENCIES, null, simpleFactors, matrixFactors);
    }

    public ContingencyContext getContingencyContext() {
        return contingencyContext;
    }

    public Optional<String> getContingencyId() {
        return Optional.ofNullable(contingencyId);
    }

    public List<SimpleSensitivityFactor> getSimpleFactors() {
        return simpleFactors;
    }

    public List<MatrixSensitivityFactor> getMatrixFactors() {
        return matrixFactors;
    }
}
