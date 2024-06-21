/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.outerloop.tap;

import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.PiModel;
import com.powsybl.openloadflow.network.VoltageControl;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Didier Vidal {@literal <didier.vidal-ext at rte-france.com>}
 */
public class TransformerRatioManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(TransformerRatioManager.class);

    private final boolean useInitialTapPosition;

    public record SharedControl(double maxR1, double minR1, double initialR1) {

        public SharedControl(PiModel piModel) {
            this(piModel.getMaxR1(), piModel.getMinR1(), piModel.getR1());
        }

        public SharedControl(double maxR1, double minR1, double a, double b, int count) {
            this(maxR1, minR1, (minR1 * a + maxR1 * b) / count);
        }
    }

    private final Map<String, Pair<Double, SharedControl>> sharedControlByBranchId = new HashMap<>();

    /**
     * Initializes the ratios. In particular maxR1, minR1 and initialR1.
     * If 'useInitialTapPosition' is true then maxR1, minR1 and initialR1 are the average value of the transformers of the same
     * control otherwise the transformer individual values.
     */
    public TransformerRatioManager(LfNetwork network, boolean useInitialTapPosition) {
        this.useInitialTapPosition = useInitialTapPosition;
        for (LfBus bus : network.getControlledBuses(VoltageControl.Type.TRANSFORMER)) {
            bus.getTransformerVoltageControl()
                    .filter(voltageControl -> voltageControl.getMergeStatus() == VoltageControl.MergeStatus.MAIN)
                    .ifPresent(voltageControl -> {
                        var controllerBranches = voltageControl.getMergedControllerElements().stream()
                                .filter(b -> !b.isDisabled())
                                .filter(LfBranch::isVoltageControlEnabled)
                                .toList();
                        if (!controllerBranches.isEmpty()) {
                            if (useInitialTapPosition) {
                                // shared behavior
                                double a = 0;
                                double b = 0;
                                for (LfBranch branch : controllerBranches) {
                                    PiModel piModel = branch.getPiModel();
                                    double r1 = piModel.getR1();
                                    double maxR1 = piModel.getMaxR1();
                                    double minR1 = piModel.getMinR1();
                                    if (maxR1 != minR1) {
                                        a += (maxR1 - r1) / (maxR1 - minR1);
                                        b += (r1 - minR1) / (maxR1 - minR1);
                                    } else {
                                        a += 1;
                                        b += 1;
                                    }
                                }
                                SharedControl sharedControl = new SharedControl(
                                        controllerBranches.stream().mapToDouble(branch -> branch.getPiModel().getMaxR1()).average().orElseThrow(),
                                        controllerBranches.stream().mapToDouble(branch -> branch.getPiModel().getMinR1()).average().orElseThrow(),
                                        a,
                                        b,
                                        controllerBranches.size());
                                controllerBranches.forEach(branch -> sharedControlByBranchId.put(branch.getId(), Pair.of(branch.getPiModel().getR1(), sharedControl)));
                            } else {
                                // individual behavior
                                controllerBranches.forEach(branch -> sharedControlByBranchId.put(branch.getId(), Pair.of(branch.getPiModel().getR1(), new SharedControl(branch.getPiModel()))));
                            }
                        }
                    });
        }
    }

    /**
     * If 'useInitialTapPosition' is true, for transformers of the same control, we try to keep the initial difference
     * between individual ratios. This algorithm maintains the individual tap positions if the voltage is correct with initial
     * state. It can also be seen as an approximate simulation of transformers acting with independent automation systems.
     * Assumes that all transformers of the same control have the same ratio (should be maintained by
     * ACEquationSystemCreator.createR1DistributionEquations).
     * If 'useInitialTapPosition' is false, the transformer is not modified and keeps its computed R1.
     */
    public void updateContinuousRatio(LfBranch branch) {
        if (useInitialTapPosition) {
            double individualInitialR1 = sharedControlByBranchId.get(branch.getId()).getLeft();
            SharedControl sharedControl = sharedControlByBranchId.get(branch.getId()).getRight();
            double maxR1 = sharedControl.maxR1();
            double minR1 = sharedControl.minR1();
            double initialR1 = sharedControl.initialR1();
            double computedR1 = branch.getPiModel().getR1(); // equations provide the same R1 for all branches
            double updatedR1 = computedR1 >= initialR1 ?
                    individualInitialR1 + (computedR1 - initialR1) * (branch.getPiModel().getMaxR1() - individualInitialR1) / (maxR1 - initialR1)
                    :
                    individualInitialR1 - (initialR1 - computedR1) * (individualInitialR1 - branch.getPiModel().getMinR1()) / (initialR1 - minR1);
            branch.getPiModel().setR1(updatedR1);
        }
    }

    /**
     * Freezes the transformer to its limit if the common ratio of transformers of the same shared control is above the max
     * (or below the min) of the group.
     * @return true if the transformer has been frozen and false it voltage control remains disabled
     */
    public boolean freezeAtExtremeTapPosition(LfBranch branch) {
        if (branch.isVoltageControlEnabled() && !branch.isDisabled()) {
            // round the rho shift to the closest tap
            PiModel piModel = branch.getPiModel();
            double r1 = piModel.getR1();
            SharedControl sharedControl = sharedControlByBranchId.get(branch.getId()).getRight();
            double minR1 = sharedControl.minR1();
            double maxR1 = sharedControl.maxR1();
            if (r1 < minR1 || r1 > maxR1) {
                LOGGER.info("Transformer {} with voltage control frozen: rounded at extreme tap position", branch.getId());
                piModel.setR1(r1 > maxR1 ? maxR1 : minR1);
                piModel.roundR1ToClosestTap();
                branch.setVoltageControlEnabled(false);
                return true;
            }
        }
        return false;
    }
}
