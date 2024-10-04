/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network;

public class TapPositionChange {
    private final LfBranch lfBranch;
    private final int value;
    private final boolean isRelative;

    public TapPositionChange(LfBranch branch, int value, boolean isRelative) {
        this.lfBranch = branch;
        this.value = value;
        this.isRelative = isRelative;
    }

    public int getNewTapPosition() {
        int tapPosition = lfBranch.getPiModel().getTapPosition();
        return isRelative ? tapPosition + value : value;
    }

    public LfBranch getLfBranch() {
        return lfBranch;
    }
}
