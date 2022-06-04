/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.sa;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import com.powsybl.commons.AbstractConverterTest;
import com.powsybl.commons.config.InMemoryPlatformConfig;
import com.powsybl.openloadflow.util.PowsyblOpenLoadFlowVersion;
import com.powsybl.security.SecurityAnalysisParameters;
import com.powsybl.security.json.SecurityAnalysisParametersJsonModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
class OpenSecurityAnalysisProviderTest extends AbstractConverterTest {

    private OpenSecurityAnalysisProvider provider;

    private FileSystem fileSystem;

    @BeforeEach
    public void setUp() {
        provider = new OpenSecurityAnalysisProvider();
        fileSystem = Jimfs.newFileSystem(Configuration.unix());
    }

    @Test
    void basicTest() {
        assertEquals("OpenSecurityAnalysis", provider.getName());
        assertEquals(new PowsyblOpenLoadFlowVersion().toString(), provider.getVersion());
        assertEquals("OpenLoadFlow", provider.getLoadFlowProviderName().orElseThrow());
    }

    @Test
    void specificParametersNamesTest() {
        assertEquals(List.of("createResultExtension"), provider.getSpecificParametersNames());
    }

    @Test
    void specificParametersFromDefaultPlatformConfigTest() {
        InMemoryPlatformConfig platformConfig = new InMemoryPlatformConfig(fileSystem);
        OpenSecurityAnalysisParameters parametersExt = (OpenSecurityAnalysisParameters) provider.loadSpecificParameters(platformConfig).orElseThrow();
        assertEquals("open-security-analysis-parameters", parametersExt.getName());
        assertFalse(parametersExt.isCreateResultExtension());
        parametersExt.setCreateResultExtension(true);
        assertTrue(parametersExt.isCreateResultExtension());
    }

    @Test
    void specificParametersFromPlatformConfigTest() {
        InMemoryPlatformConfig platformConfig = new InMemoryPlatformConfig(fileSystem);
        platformConfig.createModuleConfig("open-security-analysis-default-parameters")
                .setStringProperty("createResultExtension", "true");
        OpenSecurityAnalysisParameters parametersExt = (OpenSecurityAnalysisParameters) provider.loadSpecificParameters(platformConfig).orElseThrow();
        assertTrue(parametersExt.isCreateResultExtension());
    }

    @Test
    void specificParametersFromEmptyPropertiesTest() {
        OpenSecurityAnalysisParameters parametersExt = (OpenSecurityAnalysisParameters) provider.loadSpecificParameters(Collections.emptyMap()).orElseThrow();
        assertFalse(parametersExt.isCreateResultExtension());
    }

    @Test
    void specificParametersFromPropertiesTest() {
        Map<String, String> properties = Map.of("createResultExtension", "true");
        OpenSecurityAnalysisParameters parametersExt = (OpenSecurityAnalysisParameters) provider.loadSpecificParameters(properties).orElseThrow();
        assertTrue(parametersExt.isCreateResultExtension());
    }

    @Test
    void jsonTest() throws IOException {
        SecurityAnalysisParameters parameters = new SecurityAnalysisParameters();
        OpenSecurityAnalysisParameters parametersExt = new OpenSecurityAnalysisParameters()
                .setCreateResultExtension(true);
        parameters.addExtension(OpenSecurityAnalysisParameters.class, parametersExt);
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new SecurityAnalysisParametersJsonModule());
        String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(parameters);
        compareTxt(getClass().getResourceAsStream("/sa-params.json"), json);
    }
}
