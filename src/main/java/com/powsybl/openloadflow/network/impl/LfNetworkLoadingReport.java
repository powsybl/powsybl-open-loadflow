/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network.impl;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class LfNetworkLoadingReport {

    int generatorsDiscardedFromVoltageControlBecauseNotStarted = 0;

    int generatorsDiscardedFromVoltageControlBecauseReactiveRangeIsTooSmall = 0;

    int generatorsDiscardedFromVoltageControlBecauseTargetPIsOutsideActiveLimits = 0;

    int generatorsDiscardedFromActivePowerControlBecauseTargetEqualsToZero = 0;

    int generatorsDiscardedFromActivePowerControlBecauseTargetPGreaterThanMaxP = 0;

    int generatorsDiscardedFromActivePowerControlBecauseTargetPLowerThanMinP = 0;

    int generatorsDiscardedFromActivePowerControlBecauseMaxPNotPlausible = 0;

    int generatorsDiscardedFromActivePowerControlBecauseMaxPEqualsMinP = 0;

    int branchesDiscardedBecauseConnectedToSameBusAtBothEnds = 0;

    int nonImpedantBranches = 0;

    int generatorsWithInconsistentTargetVoltage = 0;

    int generatorsWithZeroRemoteVoltageControlReactivePowerKey = 0;

    int transformerVoltageControlDiscardedBecauseControllerBranchIsOpen = 0;

    int transformerReactivePowerControlDiscardedBecauseControllerBranchIsOpen = 0;

    int ratioTapChangersWithInconsistentTargetVoltage = 0;

    int shuntsWithInconsistentTargetVoltage = 0;
}
