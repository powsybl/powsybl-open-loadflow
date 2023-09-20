/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import com.powsybl.commons.reporter.Reporter;
import com.powsybl.iidm.network.PhaseTapChangerHolder;
import com.powsybl.iidm.network.RatioTapChangerHolder;

import java.util.List;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public interface LfNetworkLoader<T> {

    /**
     * Load the given network object
     * @param network the network to load
     * @param parameters parameters used to load the network
     * @param reporter the reporter used for functional logs
     * @return the list of LfNetwork, sorted by ascending connected components number then by ascending synchronous
     * components number (hence sorted by descending connected components size then by descending synchronous components
     * size)
     */
    List<LfNetwork> load(T network, List<RatioTapChangerHolder> rtcToOperate, List<PhaseTapChangerHolder> pstToOperate,
                         LfNetworkParameters parameters, Reporter reporter);
}
