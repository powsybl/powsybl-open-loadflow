/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.openloadflow.ac.nr.DefaultAcLoadFlowObserver;
import com.powsybl.openloadflow.network.LfNetwork;

import java.nio.file.Path;
import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class AcLoadFlowNetworkJsonWriter extends DefaultAcLoadFlowObserver {

    private final int num;

    private final Path jsonFile;

    public AcLoadFlowNetworkJsonWriter(int num, Path jsonFile) {
        this.num = num;
        this.jsonFile = Objects.requireNonNull(jsonFile);
    }

    @Override
    public void afterNetworkUpdate(LfNetwork network) {
        if (network.getNum() == num) {
            network.writeJson(jsonFile);
        }
    }
}
