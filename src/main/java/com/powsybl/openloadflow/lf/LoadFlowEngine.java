/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.lf;

import com.powsybl.openloadflow.equations.Quantity;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public interface LoadFlowEngine<V extends Enum<V> & Quantity,
                                E extends Enum<E> & Quantity,
                                P extends AbstractLoadFlowParameters,
                                R extends LoadFlowResult> {

    LoadFlowContext<V, E, P> getContext();

    R run();
}
