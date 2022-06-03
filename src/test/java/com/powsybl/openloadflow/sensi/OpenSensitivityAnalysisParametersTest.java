/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.sensi;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import com.powsybl.commons.config.InMemoryPlatformConfig;
import com.powsybl.commons.config.MapModuleConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.FileSystem;

/**
 * @author Etienne Lesot <etienne.lesot at rte-france.com>
 */
class OpenSensitivityAnalysisParametersTest {

    private InMemoryPlatformConfig platformConfig;

    private FileSystem fileSystem;

    @BeforeEach
    public void setUp() {
        fileSystem = Jimfs.newFileSystem(Configuration.unix());
        platformConfig = new InMemoryPlatformConfig(fileSystem);

        MapModuleConfig lfModuleConfig = platformConfig.createModuleConfig("open-sensitivityanalysis-default-parameters");
        lfModuleConfig.setStringProperty("debugDir", "/debugDir");
    }

    @AfterEach
    void tearDown() throws IOException {
        fileSystem.close();
    }

    @Test
    void test() {
        OpenSensitivityAnalysisParameters parameters = OpenSensitivityAnalysisParameters.load(platformConfig);
        Assertions.assertEquals("/debugDir", parameters.getDebugDir());
    }
}
