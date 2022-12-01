/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.dc.equations;

import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.lf.AbstractEquationSystemUpdater;
import com.powsybl.openloadflow.network.*;

import java.util.Objects;

/**
 * @author Anne Tilloy <anne.tilloy at rte-france.com>
 */
public class DcEquationSystemUpdater extends AbstractEquationSystemUpdater {

    private final EquationSystem<DcVariableType, DcEquationType> equationSystem;

    private final double lowImpedanceThreshold;

    public DcEquationSystemUpdater(EquationSystem<DcVariableType, DcEquationType> equationSystem, double lowImpedanceThreshold) {
        this.equationSystem = Objects.requireNonNull(equationSystem);
        this.lowImpedanceThreshold = lowImpedanceThreshold;
    }

    private void updateElementEquations(LfElement element, boolean enable) {
        if (element instanceof LfBranch && ((LfBranch) element).isZeroImpedanceBranch(true, lowImpedanceThreshold)) {
            LfBranch branch = (LfBranch) element;
            if (branch.isSpanningTreeEdge()) {
                // depending on the switch status, we activate either v1 = v2, ph1 = ph2 equations
                // or equations that set dummy p and q variable to zero
                equationSystem.getEquation(element.getNum(), DcEquationType.ZERO_PHI)
                        .orElseThrow()
                        .setActive(enable);
                equationSystem.getEquation(element.getNum(), DcEquationType.DUMMY_TARGET_P)
                        .orElseThrow()
                        .setActive(!enable);
            }
        } else {
            // update all equations related to the element
            for (var equation : equationSystem.getEquations(element.getType(), element.getNum())) {
                if (equation.isActive() != enable) {
                    equation.setActive(enable);
                }
            }

            // update all equation terms related to the element
            for (var equationTerm : equationSystem.getEquationTerms(element.getType(), element.getNum())) {
                if (equationTerm.isActive() != enable) {
                    equationTerm.setActive(enable);
                }
            }
        }
    }

    @Override
    public void onDisableChange(LfElement element, boolean disabled) {
        updateElementEquations(element, !disabled);
        switch (element.getType()) {
            case BUS:
                LfBus bus = (LfBus) element;
                checkSlackBus(bus, disabled);
                equationSystem.getEquation(bus.getNum(), DcEquationType.BUS_TARGET_PHI)
                        .ifPresent(eq -> eq.setActive(!bus.isDisabled()));
                equationSystem.getEquation(bus.getNum(), DcEquationType.BUS_TARGET_P)
                        .ifPresent(eq -> eq.setActive(!bus.isDisabled() && !bus.isSlack()));
                break;
            case BRANCH:
                // nothing to do.
                break;
            default:
                throw new IllegalStateException("Unknown element type: " + element.getType());
        }
    }
}
