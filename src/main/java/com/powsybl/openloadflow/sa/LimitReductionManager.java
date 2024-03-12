/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.sa;

import com.powsybl.openloadflow.network.LfNetwork;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author Anne Tilloy {@literal <anne.tilloy at rte-france.com>}
 */
public class LimitReductionManager {

    public static class TerminalLimitReduction {
        double minNominalV;
        double maxNominalV;
        boolean isPermanent;
        Integer maxAcceptableDuration; // could be null
        double reduction;

        public TerminalLimitReduction(double minNominalV, double maxNominalV, boolean isPermanent, Integer maxAcceptableDuration, double reduction) {
            this.minNominalV = minNominalV;
            this.maxNominalV = maxNominalV;
            this.isPermanent = isPermanent;
            this.maxAcceptableDuration = maxAcceptableDuration;
            this.reduction = reduction;
        }

        public boolean isPermanent() {
            return isPermanent;
        }

        public double getMinNominalV() {
            return minNominalV;
        }

        public double getMaxNominalV() {
            return maxNominalV;
        }

        public Integer getMaxAcceptableDuration() {
            return maxAcceptableDuration;
        }

        public double getReduction() {
            return reduction;
        }
    }

    List<TerminalLimitReduction> terminalLimitReductions = new ArrayList<>();

    LfNetwork network;

    public LimitReductionManager(LfNetwork network) {
        this.network = network;
    }

    public List<TerminalLimitReduction> getTerminalLimitReductions() {
        // to be sorted by acceptable duration
        // return terminalLimitReductions.stream().sorted().toList();
        return terminalLimitReductions;
    }

    public void addTerminalLimitReduction(TerminalLimitReduction terminalLimitReduction) {
        this.terminalLimitReductions.add(terminalLimitReduction);
    }
}
