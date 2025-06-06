/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network;

import java.util.Objects;

/**
 * @author Anne Tilloy {@literal <anne.tilloy at rte-france.com>}
 * @author Jean-Luc Bouchot (Artelys) {@literal <jlbouchot at gmail.com>}
 */
public class TapPositionChange {
    private final LfBranch branch;
    private final int value;
    private final boolean isRelative;

    public TapPositionChange(LfBranch branch, int value, boolean isRelative) {
        if (!(branch.getPiModel() instanceof PiModelArray)) {
            throw new IllegalStateException("A TapPositionChange can not be applied on a branch without PiModelArray");
        }
        this.branch = Objects.requireNonNull(branch);
        this.value = value;
        this.isRelative = isRelative;
    }

    public int getNewTapPosition() {
        int tapPosition = branch.getPiModel().getTapPosition();
        return isRelative ? tapPosition + value : value;
    }

    public PiModel getNewPiModel() {
        int newTapPosition = getNewTapPosition();
        return ((PiModelArray) branch.getPiModel()).getModel(newTapPosition);
    }

    public LfBranch getBranch() {
        return branch;
    }
}
