/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.lf;

import com.powsybl.commons.PowsyblException;
import com.powsybl.openloadflow.equations.Equation;
import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.equations.Quantity;
import com.powsybl.openloadflow.network.*;

import java.util.Objects;

import static com.powsybl.openloadflow.equations.EquationTerm.setActive;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
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
        if (element instanceof LfBranch branch && branch.isZeroImpedance(loadFlowModel) && branch.getBus1() != null && branch.getBus2() != null) {
            updateNonImpedantBranchEquations(branch, enable && branch.isSpanningTreeEdge(loadFlowModel));
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

            // update also in equation arrays
            for (var equationArray : equationSystem.getEquationArrays()) {
                equationArray.updateElementEquation(element, enable);
            }
        }
    }

    protected abstract E getTypeBusTargetP();

    protected abstract E getTypeBusTargetPhi();

    protected abstract V getTypeBusPhi();

    @Override
    public void onSlackBusChange(LfBus bus, boolean slack) {
        equationSystem.getEquation(bus.getNum(), getTypeBusTargetP())
                .orElseThrow()
                .setActive(!slack);
    }

    @Override
    public void onReferenceBusChange(LfBus bus, boolean reference) {
        if (reference) {
            Equation<V, E> phiEq = equationSystem.getEquation(bus.getNum(), getTypeBusTargetPhi()).orElse(null);
            if (phiEq == null) {
                phiEq = equationSystem.createEquation(bus, getTypeBusTargetPhi())
                        .addTerm(equationSystem.getVariable(bus.getNum(), getTypeBusPhi())
                                .createTerm());
            }
            phiEq.setActive(true);
        } else {
            equationSystem.getEquation(bus.getNum(), getTypeBusTargetPhi())
                    .orElseThrow()
                    .setActive(false);
        }
    }

    @Override
    public void onHvdcAcEmulationStatusChange(LfHvdc hvdc, LfHvdc.AcEmulationControl.AcEmulationStatus acEmulationStatus) {
        updateHvdcAcEmulationEquations(hvdc);
    }

    public static void updateHvdcAcEmulationEquations(LfHvdc hvdc) {
        if (hvdc.getBus1() != null && !hvdc.getBus1().isDisabled()
                && hvdc.getBus2() != null && !hvdc.getBus2().isDisabled()
                && !hvdc.isDisabled() && hvdc.isAcEmulation()) {
            switch (hvdc.getAcEmulationControl().getAcEmulationStatus()) {
                case LINEAR_MODE -> {
                    setActive(hvdc.getP1(), true);
                    setActive(hvdc.getP2(), true);
                }
                case SATURATION_MODE_FROM_CS1_TO_CS2,
                     SATURATION_MODE_FROM_CS2_TO_CS1,
                     FROZEN -> {
                    setActive(hvdc.getP1(), false);
                    setActive(hvdc.getP2(), false);
                }
            }
        } else {
            setActive(hvdc.getP1(), false);
            setActive(hvdc.getP2(), false);
        }
    }
}
