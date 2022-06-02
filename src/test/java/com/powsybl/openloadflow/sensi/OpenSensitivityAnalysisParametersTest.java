package com.powsybl.openloadflow.sensi;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import com.powsybl.commons.config.InMemoryPlatformConfig;
import com.powsybl.commons.config.MapModuleConfig;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.FileSystem;

public class OpenSensitivityAnalysisParametersTest {
    private InMemoryPlatformConfig platformConfig;

    private FileSystem fileSystem;

    @BeforeEach
    public void setUp() {
        fileSystem = Jimfs.newFileSystem(Configuration.unix());
        platformConfig = new InMemoryPlatformConfig(fileSystem);

        MapModuleConfig lfModuleConfig = platformConfig.createModuleConfig("open-sensitivityanalysis-default-parameters");
        lfModuleConfig.setStringProperty("debugDir", "/debugDir");
    }

    @Test
    public void test() {
        OpenSensitivityAnalysisParameters parameters = OpenSensitivityAnalysisParameters.load(platformConfig);
        Assertions.assertEquals("/debugDir", parameters.getDebugDir());
    }
}
