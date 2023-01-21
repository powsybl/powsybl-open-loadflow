/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.commons.config.PlatformConfig;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
class DebugDirTest {

    @Test
    void test() throws IOException {
        var network = EurostagTutorialExample1Factory.create();
        var loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        LoadFlowParameters parameters = new LoadFlowParameters();
        FileSystem fileSystem = PlatformConfig.defaultConfig().getConfigDir().orElseThrow().getFileSystem();
        Path work = fileSystem.getPath("/work");
        OpenLoadFlowParameters.create(parameters)
                .setDebugDir(work.toString());
        loadFlowRunner.run(network, parameters);
        List<Path> debugFiles = new ArrayList<>();
        try (var stream = Files.list(work)) {
            stream.filter(Files::isRegularFile).forEach(debugFiles::add);
        }
        assertEquals(2, debugFiles.size());
        assertTrue(debugFiles.stream().anyMatch(path -> path.getFileName().toString().endsWith(".json")));
        assertTrue(debugFiles.stream().anyMatch(path -> path.getFileName().toString().endsWith(".dot")));
    }
}
