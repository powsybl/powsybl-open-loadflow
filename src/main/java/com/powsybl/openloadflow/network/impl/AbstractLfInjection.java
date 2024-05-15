/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.openloadflow.network.AbstractPropertyBag;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public abstract class AbstractLfInjection extends AbstractPropertyBag {

    protected double initialTargetP;

    protected double targetP;

    protected AbstractLfInjection(double initialTargetP, double targetP) {
        this.initialTargetP = initialTargetP;
        this.targetP = targetP;
    }

    public double getInitialTargetP() {
        return initialTargetP;
    }

    public double getTargetP() {
        return targetP;
    }
}
