/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class LfNetworkUpdateReport {
    public int openedSwitchCount = 0;
    public int closedSwitchCount = 0;
    public int connectedBranchSide1Count = 0;
    public int disconnectedBranchSide1Count = 0;
    public int connectedBranchSide2Count = 0;
    public int disconnectedBranchSide2Count = 0;
}
