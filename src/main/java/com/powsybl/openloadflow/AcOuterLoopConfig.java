/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow;

import com.powsybl.commons.PowsyblException;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.openloadflow.ac.outerloop.AcOuterLoop;
import org.apache.commons.compress.utils.Lists;

import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public interface AcOuterLoopConfig {

    List<AcOuterLoop> configure(LoadFlowParameters parameters, OpenLoadFlowParameters parametersExt);

    static Optional<AcOuterLoopConfig> findOuterLoopConfig() {
        List<AcOuterLoopConfig> outerLoopConfigs = Lists.newArrayList(ServiceLoader.load(AcOuterLoopConfig.class, AcOuterLoopConfig.class.getClassLoader()).iterator());
        if (outerLoopConfigs.isEmpty()) {
            return Optional.empty();
        } else {
            if (outerLoopConfigs.size() > 1) {
                throw new PowsyblException("Only one outer loop config is expected on class path");
            }
            return Optional.of(outerLoopConfigs.get(0));
        }
    }
}
