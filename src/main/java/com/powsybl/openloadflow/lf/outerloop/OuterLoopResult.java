/**
 * Copyright (c) 2024, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.lf.outerloop;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public record OuterLoopResult(String outerLoopName, OuterLoopStatus status, String statusText) {

    public OuterLoopResult(OuterLoop<?, ?, ?, ?, ?> outerLoop, OuterLoopStatus status) {
        this(outerLoop, status, status.name());
    }

    public OuterLoopResult(OuterLoop<?, ?, ?, ?, ?> outerLoop, OuterLoopStatus status, String statusText) {
        this(outerLoop.getName(), status, statusText);
    }

    public OuterLoopResult(String outerLoopName, OuterLoopStatus status) {
        this(outerLoopName, status, status.name());
    }

    public static OuterLoopResult stable() {
        return new OuterLoopResult("", OuterLoopStatus.STABLE);
    }

}
