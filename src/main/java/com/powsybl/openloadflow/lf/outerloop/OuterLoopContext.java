/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.lf.outerloop;

import com.powsybl.openloadflow.equations.Quantity;
import com.powsybl.openloadflow.lf.AbstractLoadFlowParameters;
import com.powsybl.openloadflow.lf.LoadFlowContext;
import com.powsybl.openloadflow.network.LfNetwork;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public interface OuterLoopContext<V extends Enum<V> & Quantity,
                                  E extends Enum<E> & Quantity,
                                  P extends AbstractLoadFlowParameters,
                                  C extends LoadFlowContext<V, E, P>> {

    LfNetwork getNetwork();

    int getIteration();

    Object getData();

    void setData(Object data);

    C getLoadFlowContext();

    void setLoadFlowContext(C loadFlowContext);
}
