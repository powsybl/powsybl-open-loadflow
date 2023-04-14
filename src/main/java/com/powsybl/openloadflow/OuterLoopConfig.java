/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow;

import com.powsybl.commons.PowsyblException;
import com.powsybl.loadflow.LoadFlowParameters;
import org.apache.commons.compress.utils.Lists;

import java.util.List;
import java.util.Objects;
import java.util.ServiceLoader;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public interface OuterLoopConfig {

    List<OuterLoop> configure(LoadFlowParameters parameters, OpenLoadFlowParameters parametersExt);

    static OuterLoopConfig findOuterLoopConfig(OuterLoopConfig defaultOuterLoopConfig) {
        Objects.requireNonNull(defaultOuterLoopConfig);
        OuterLoopConfig outerLoopConfig;
        List<OuterLoopConfig> outerLoopConfigs = Lists.newArrayList(ServiceLoader.load(OuterLoopConfig.class, OuterLoopConfig.class.getClassLoader()).iterator());
        if (outerLoopConfigs.isEmpty()) {
            outerLoopConfig = defaultOuterLoopConfig;
        } else {
            if (outerLoopConfigs.size() > 1) {
                throw new PowsyblException("Only one outer loop config is expected on class path");
            }
            outerLoopConfig = outerLoopConfigs.get(0);
        }
        return outerLoopConfig;
    }
}
