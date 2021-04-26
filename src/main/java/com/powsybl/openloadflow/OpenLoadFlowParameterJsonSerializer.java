/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.google.auto.service.AutoService;
import com.powsybl.commons.json.JsonUtil;
import com.powsybl.loadflow.json.JsonLoadFlowParameters;

import java.io.IOException;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
@AutoService(JsonLoadFlowParameters.ExtensionSerializer.class)
public class OpenLoadFlowParameterJsonSerializer implements JsonLoadFlowParameters.ExtensionSerializer<OpenLoadFlowParameters> {

    @Override
    public String getExtensionName() {
        return "open-loadFlow-parameters";
    }

    @Override
    public String getCategoryName() {
        return "loadflow-parameters";
    }

    @Override
    public Class<? super OpenLoadFlowParameters> getExtensionClass() {
        return OpenLoadFlowParameters.class;
    }

    /**
     * Specifies serialization for our extension: ignore name et extendable
     */
    private interface SerializationSpec {

        @JsonIgnore
        String getName();

        @JsonIgnore
        OpenLoadFlowParameters getExtendable();
    }

    private static ObjectMapper createMapper() {
        return JsonUtil.createObjectMapper()
                .addMixIn(OpenLoadFlowParameters.class, SerializationSpec.class);
    }

    @Override
    public void serialize(OpenLoadFlowParameters extension, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
        createMapper().writeValue(jsonGenerator, extension);
    }

    @Override
    public OpenLoadFlowParameters deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
        return createMapper().readValue(jsonParser, OpenLoadFlowParameters.class);
    }
}
