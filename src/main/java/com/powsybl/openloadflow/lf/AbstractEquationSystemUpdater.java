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
import com.powsybl.openloadflow.network.*;

import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public abstract class AbstractEquationSystemUpdater<V extends Enum<V> & Quantity, E extends Enum<E> & Quantity> extends AbstractLfNetworkListener {

    protected final EquationSystem<V, E> equationSystem;

    protected final LoadFlowModel loadFlowModel;

    protected AbstractEquationSystemUpdater(EquationSystem<V, E> equationSystem, LoadFlowModel loadFlowModel) {
        this.equationSystem = Objects.requireNonNull(equationSystem);
        this.loadFlowModel = Objects.requireNonNull(loadFlowModel);
    }

    protected static void checkSlackBus(LfBus bus, boolean disabled) {
        if (disabled && bus.isSlack()) {
            throw new PowsyblException("Slack bus '" + bus.getId() + "' disabling is not supported");
        }
    }

    protected abstract void updateNonImpedantBranchEquations(LfBranch branch, boolean enable);

    @Override
    public void onZeroImpedanceNetworkSpanningTreeChange(LfBranch branch, LoadFlowModel loadFlowModel, boolean spanningTree) {
        if (loadFlowModel == this.loadFlowModel) {
            updateNonImpedantBranchEquations(branch, !branch.isDisabled() && spanningTree);
        }
    }

    protected void updateElementEquations(LfElement element, boolean enable) {
        if (element instanceof LfBranch && ((LfBranch) element).isZeroImpedance(loadFlowModel)) {
            updateNonImpedantBranchEquations((LfBranch) element, enable && ((LfBranch) element).isSpanningTreeEdge(loadFlowModel));
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
