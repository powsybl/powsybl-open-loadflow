/*
 * Copyright (c) 2019-2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.commons.report.ReportNode;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class LfNetworkLoadingReport {

    public LfNetworkLoadingReport(ReportNode firstRootReportNode, boolean detailed) {
        this.firstRootReportNode = firstRootReportNode;
        this.detailed = detailed;
    }

    ReportNode firstRootReportNode;

    boolean detailed;

    int generatorsDiscardedFromVoltageControlBecauseNotStarted = 0;

    List<ReportNode> reportGeneratorsDiscardedFromVoltageControlBecauseNotStarted = new ArrayList<>();

    int generatorsDiscardedFromVoltageControlBecauseReactiveRangeIsTooSmall = 0;

    List<ReportNode> reportGeneratorsDiscardedFromVoltageControlBecauseReactiveRangeIsTooSmall = new ArrayList<>();

    int generatorsDiscardedFromVoltageControlBecauseTargetPIsOutsideActiveLimits = 0;

    List<ReportNode> reportGeneratorsDiscardedFromVoltageControlBecauseTargetPIsOutsideActiveLimits = new ArrayList<>();

    int generatorsDiscardedFromVoltageControlBecauseInconsistentTargetVoltages = 0;

    int generatorsDiscardedFromVoltageControlBecauseInconsistentControlledBus = 0;

    int generatorsDiscardedFromActivePowerControlBecauseTargetEqualsToZero = 0;

    List<ReportNode> reportGeneratorsDiscardedFromActivePowerControlBecauseTargetEqualsToZero = new ArrayList<>();

    int generatorsDiscardedFromActivePowerControlBecauseTargetPGreaterThanMaxP = 0;

    List<ReportNode> reportGeneratorsDiscardedFromActivePowerControlBecauseTargetPGreaterThanMaxP = new ArrayList<>();

    int generatorsDiscardedFromActivePowerControlBecauseTargetPLowerThanMinP = 0;

    List<ReportNode> reportGeneratorsDiscardedFromActivePowerControlBecauseTargetPLowerThanMinP = new ArrayList<>();

    int generatorsDiscardedFromActivePowerControlBecauseMaxPNotPlausible = 0;

    List<ReportNode> reportGeneratorsDiscardedFromActivePowerControlBecauseMaxPNotPlausible = new ArrayList<>();

    int generatorsDiscardedFromActivePowerControlBecauseMaxPEqualsMinP = 0;

    List<ReportNode> reportGeneratorsDiscardedFromActivePowerControlBecauseMaxPEqualsMinP = new ArrayList<>();

    int branchesDiscardedBecauseConnectedToSameBusAtBothEnds = 0;

    int nonImpedantBranches = 0;

    int generatorsDiscardedFromVoltageControlBecauseImplausibleTargetVoltage = 0;

    List<ReportNode> reportGeneratorsDiscardedFromVoltageControlBecauseImplausibleTargetVoltage = new ArrayList<>();

    int generatorsWithZeroRemoteVoltageControlReactivePowerKey = 0;

    int transformerVoltageControlDiscardedBecauseControllerBranchIsOpen = 0;

    int transformerReactivePowerControlDiscardedBecauseControllerBranchIsOpen = 0;

    int transformersWithInconsistentTargetVoltage = 0;

    int shuntsWithInconsistentTargetVoltage = 0;

    int rescaledRemoteVoltageControls = 0;
}
