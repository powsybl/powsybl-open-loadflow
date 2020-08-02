/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow;

import com.powsybl.computation.ComputationManager;
import com.powsybl.iidm.network.Network;
import com.powsybl.security.LimitViolationDetector;
import com.powsybl.security.LimitViolationFilter;
import com.powsybl.security.SecurityAnalysis;
import com.powsybl.security.SecurityAnalysisFactory;
import com.powsybl.security.detectors.DefaultLimitViolationDetector;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class OpenSecurityAnalysisFactory implements SecurityAnalysisFactory {

    @Override
    public SecurityAnalysis create(Network network, ComputationManager computationManager, int priority) {
        return new OpenSecurityAnalysis(network, new DefaultLimitViolationDetector(), new LimitViolationFilter());
    }

    @Override
    public SecurityAnalysis create(Network network, LimitViolationFilter filter, ComputationManager computationManager, int priority) {
        return new OpenSecurityAnalysis(network, new DefaultLimitViolationDetector(), filter);
    }

    @Override
    public SecurityAnalysis create(Network network, LimitViolationDetector detector, LimitViolationFilter filter, ComputationManager computationManager, int priority) {
        return new OpenSecurityAnalysis(network, detector, filter);
    }
}
