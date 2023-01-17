/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.lf;

import com.powsybl.commons.PowsyblException;
import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.equations.Quantity;
import com.powsybl.openloadflow.network.AbstractLfNetworkListener;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfElement;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public abstract class AbstractEquationSystemUpdater<V extends Enum<V> & Quantity, E extends Enum<E> & Quantity> extends AbstractLfNetworkListener {

    protected final EquationSystem<V, E> equationSystem;

    protected final boolean dc;

    protected AbstractEquationSystemUpdater(EquationSystem<V, E> equationSystem, boolean dc) {
        this.equationSystem = equationSystem;
        this.dc = dc;
    }

    protected static void checkSlackBus(LfBus bus, boolean disabled) {
        if (disabled && bus.isSlack()) {
            throw new PowsyblException("Slack bus '" + bus.getId() + "' disabling is not supported");
        }
    }

    protected abstract void updateNonImpedantBranchEquations(LfBranch branch, boolean enable);

    protected void updateElementEquations(LfElement element, boolean enable) {
        if (element instanceof LfBranch && ((LfBranch) element).isZeroImpedance(dc) && ((LfBranch) element).isSpanningTreeEdge(dc)) {
            updateNonImpedantBranchEquations((LfBranch) element, enable);
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
}
