/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.lf.outerloop;

import com.powsybl.openloadflow.ac.AcLoadFlowContext;
import com.powsybl.openloadflow.ac.nr.NewtonRaphsonResult;
import com.powsybl.openloadflow.network.LfNetwork;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public interface OuterLoopContext {

    LfNetwork getNetwork();

    int getIteration();

    NewtonRaphsonResult getLastNewtonRaphsonResult();

    Object getData();

    void setData(Object data);

    AcLoadFlowContext getAcLoadFlowContext();

    void setAcLoadFlowContext(AcLoadFlowContext acLoadFlowContext);
}
