/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.lf.outerloop;

import com.powsybl.commons.reporter.Reporter;
import com.powsybl.openloadflow.equations.Quantity;
import com.powsybl.openloadflow.lf.AbstractLoadFlowParameters;
import com.powsybl.openloadflow.lf.LoadFlowContext;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public interface OuterLoop<V extends Enum<V> & Quantity,
                           E extends Enum<E> & Quantity,
                           P extends AbstractLoadFlowParameters,
                           C extends LoadFlowContext<V, E, P>,
                           O extends OuterLoopContext<V, E, P, C>> {

    String getName();

    default String getType() {
        return getName();
    }

    default void initialize(O context) {
    }

    OuterLoopStatus check(O context, Reporter reporter);

    default void cleanup(O context) {
    }
}
