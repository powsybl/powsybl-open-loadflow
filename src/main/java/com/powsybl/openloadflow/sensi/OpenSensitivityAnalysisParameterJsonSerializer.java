/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.sensi;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.google.auto.service.AutoService;
import com.powsybl.commons.json.JsonUtil;
import com.powsybl.sensitivity.json.SensitivityJson;

import java.io.IOException;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
@AutoService(SensitivityJson.ExtensionSerializer.class)
public class OpenSensitivityAnalysisParameterJsonSerializer implements SensitivityJson.ExtensionSerializer<OpenSensitivityAnalysisParameters> {

    @Override
    public String getExtensionName() {
        return "open-sensitivity-parameters";
    }

    @Override
    public String getCategoryName() {
        return "sensitivity-parameters";
    }

    @Override
    public Class<? super OpenSensitivityAnalysisParameters> getExtensionClass() {
        return OpenSensitivityAnalysisParameters.class;
    }

    /**
     * Specifies serialization for our extension: ignore name et extendable
     */
    private interface SerializationSpec {

        @JsonIgnore
        String getName();

        @JsonIgnore
        OpenSensitivityAnalysisParameters getExtendable();
    }

    private static ObjectMapper createMapper() {
        return JsonUtil.createObjectMapper()
                .addMixIn(OpenSensitivityAnalysisParameters.class, SerializationSpec.class);
    }

    @Override
    public void serialize(OpenSensitivityAnalysisParameters extension, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
        createMapper().writeValue(jsonGenerator, extension);
    }

    @Override
    public OpenSensitivityAnalysisParameters deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
        return createMapper().readValue(jsonParser, OpenSensitivityAnalysisParameters.class);
    }
}
