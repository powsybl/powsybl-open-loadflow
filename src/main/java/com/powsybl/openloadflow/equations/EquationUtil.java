/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.equations;

import com.powsybl.openloadflow.network.ElementType;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfContingency;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public final class EquationUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(EquationUtil.class);

    private EquationUtil() {
    }

    public static <V extends Enum<V> & Quantity, E extends Enum<E> & Quantity> void deactivateEquations(LfContingency lfContingency, EquationSystem<V, E> equationSystem, List<Equation<V, E>> deactivatedEquations, List<EquationTerm<V, E>> deactivatedEquationTerms) {
        for (LfBranch branch : lfContingency.getBranches()) {
            LOGGER.trace("Remove equations and equations terms related to branch '{}'", branch.getId());

            // deactivate all equations related to a branch
            for (Equation<V, E> equation : equationSystem.getEquations(ElementType.BRANCH, branch.getNum())) {
                if (equation.isActive()) {
                    equation.setActive(false);
                    deactivatedEquations.add(equation);
                }
            }

            // deactivate all equation terms related to a branch
            for (EquationTerm<V, E> equationTerm : equationSystem.getEquationTerms(ElementType.BRANCH, branch.getNum())) {
                if (equationTerm.isActive()) {
                    equationTerm.setActive(false);
                    deactivatedEquationTerms.add(equationTerm);
                }
            }
        }

        for (LfBus bus : lfContingency.getBuses()) {
            LOGGER.trace("Remove equations and equation terms related to bus '{}'", bus.getId());

            // deactivate all equations related to a bus
            for (Equation<V, E> equation : equationSystem.getEquations(ElementType.BUS, bus.getNum())) {
                if (equation.isActive()) {
                    equation.setActive(false);
                    deactivatedEquations.add(equation);
                }
            }

            // deactivate all equation terms related to a bus
            for (EquationTerm<V, E> equationTerm : equationSystem.getEquationTerms(ElementType.BUS, bus.getNum())) {
                if (equationTerm.isActive()) {
                    equationTerm.setActive(false);
                    deactivatedEquationTerms.add(equationTerm);
                }
            }
        }
    }

    public static <V extends Enum<V> & Quantity, E extends Enum<E> & Quantity> void reactivateEquations(List<Equation<V, E>> deactivatedEquations, List<EquationTerm<V, E>> deactivatedEquationTerms) {
        // restore deactivated equations and equations terms from previous contingency
        if (!deactivatedEquations.isEmpty()) {
            for (Equation<V, E> equation : deactivatedEquations) {
                equation.setActive(true);
            }
            deactivatedEquations.clear();
        }
        if (!deactivatedEquationTerms.isEmpty()) {
            for (EquationTerm<V, E> equationTerm : deactivatedEquationTerms) {
                equationTerm.setActive(true);
            }
            deactivatedEquationTerms.clear();
        }
    }
}
