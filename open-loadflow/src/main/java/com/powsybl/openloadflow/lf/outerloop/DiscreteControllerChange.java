/**
 * Copyright (c) 2026, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */

package com.powsybl.openloadflow.lf.outerloop;

import com.powsybl.iidm.network.ThreeSides;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfShunt;

import java.util.Optional;

/**
 * Used to track tap changers and shunts section changes
 *
 * @author Valentin Mouradian {@literal <valentin.mouradian at artelys.com>}
 */
public final class DiscreteControllerChange {

    private final String elementId;

    /**
     * Only set for three windings transformers legs, null otherwise
     */
    private final ThreeSides side;

    private final int oldPosition;

    private final int newPosition;

    private DiscreteControllerChange(String elementId, ThreeSides side, int oldPosition, int newPosition) {
        this.elementId = elementId;
        this.side = side;
        this.oldPosition = oldPosition;
        this.newPosition = newPosition;
    }

    public static DiscreteControllerChange ofTransformer(LfBranch controllerBranch, int oldPosition, int newPosition) {
        return new DiscreteControllerChange(controllerBranch.getMainOriginalId(), controllerBranch.getOriginalSide().orElse(null), oldPosition, newPosition);
    }

    public static DiscreteControllerChange ofShunt(LfShunt.Controller controller, int oldPosition, int newPosition) {
        return new DiscreteControllerChange(controller.getId(), null, oldPosition, newPosition);
    }

    public String getElementId() {
        return elementId;
    }

    public Optional<ThreeSides> getSide() {
        return Optional.ofNullable(side);
    }

    public int getOldPosition() {
        return oldPosition;
    }

    public int getNewPosition() {
        return newPosition;
    }
}
