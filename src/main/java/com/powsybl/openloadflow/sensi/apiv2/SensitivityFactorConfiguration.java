/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.sensi.apiv2;

import com.fasterxml.jackson.core.JsonGenerator;

import java.io.IOException;
import java.util.*;

/**
 * A set of simple factors or factor matrices associated to a contingency context.
 *
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public final class SensitivityFactorConfiguration {

    private static final String CURRENT_VERSION = "1";

    private final ContingencyContext contingencyContext;

    private final String contingencyId;

    private final List<SimpleSensitivityFactor> simpleFactors;

    private final List<MatrixSensitivityFactor> matrixFactors;

    private final List<MultiVariablesSensitivityFactor> multiVarsFactors;

    private SensitivityFactorConfiguration(ContingencyContext contingencyContext, String contingencyId, List<SimpleSensitivityFactor> simpleFactors,
                                           List<MatrixSensitivityFactor> matrixFactors, List<MultiVariablesSensitivityFactor> multiVarsFactors) {
        this.contingencyContext = contingencyContext;
        this.contingencyId = contingencyId;
        this.simpleFactors = new ArrayList<>(Objects.requireNonNull(simpleFactors));
        this.matrixFactors = new ArrayList<>(Objects.requireNonNull(matrixFactors));
        this.multiVarsFactors = new ArrayList<>(Objects.requireNonNull(multiVarsFactors));
    }

    public static SensitivityFactorConfiguration create(List<SimpleSensitivityFactor> simpleFactors,
                                                        List<MatrixSensitivityFactor> matrixFactors,
                                                        List<MultiVariablesSensitivityFactor> multiVarsFactors) {
        return new SensitivityFactorConfiguration(ContingencyContext.NONE, null, simpleFactors, matrixFactors, multiVarsFactors);
    }

    public static SensitivityFactorConfiguration create() {
        return create(Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
    }

    public static SensitivityFactorConfiguration createForOneContingency(String contingencyId,
                                                                         List<SimpleSensitivityFactor> simpleFactors,
                                                                         List<MatrixSensitivityFactor> matrixFactors,
                                                                         List<MultiVariablesSensitivityFactor> multiVarsFactors) {
        Objects.requireNonNull(contingencyId);
        return new SensitivityFactorConfiguration(ContingencyContext.ONE_CONTINGENCY, contingencyId, simpleFactors, matrixFactors, multiVarsFactors);
    }

    public static SensitivityFactorConfiguration createForOneContingency(String contingencyId) {
        return createForOneContingency(contingencyId, Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
    }

    public static SensitivityFactorConfiguration createForAllContingency(List<SimpleSensitivityFactor> simpleFactors,
                                                                         List<MatrixSensitivityFactor> matrixFactors,
                                                                         List<MultiVariablesSensitivityFactor> multiVarsFactors) {
        return new SensitivityFactorConfiguration(ContingencyContext.ALL_CONTINGENCIES, null, simpleFactors, matrixFactors, multiVarsFactors);
    }

    public static SensitivityFactorConfiguration createForAllContingency() {
        return createForAllContingency(Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
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

    public List<MultiVariablesSensitivityFactor> getMultiVarsFactors() {
        return multiVarsFactors;
    }

    public void writeJson(JsonGenerator jsonGenerator) throws IOException {
        jsonGenerator.writeStartObject();

        jsonGenerator.writeStringField("version", CURRENT_VERSION);
        jsonGenerator.writeStringField("contingencyContext", contingencyContext.name());
        if (contingencyContext == ContingencyContext.ONE_CONTINGENCY) {
            jsonGenerator.writeStringField("contingencyId", contingencyId);
        }
        jsonGenerator.writeObjectField("simpleFactors", simpleFactors);
        jsonGenerator.writeObjectField("matrixFactors", matrixFactors);
        jsonGenerator.writeObjectField("multiVarsFactors", multiVarsFactors);

        jsonGenerator.writeEndObject();
    }
}
