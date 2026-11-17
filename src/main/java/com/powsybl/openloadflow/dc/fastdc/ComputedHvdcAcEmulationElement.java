/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.dc.fastdc;

import com.powsybl.openloadflow.dc.equations.DcEquationType;
import com.powsybl.openloadflow.dc.equations.DcVariableType;
import com.powsybl.openloadflow.dc.equations.HvdcAcEmulationSide1DCFlowEquationTerm;
import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.equations.EquationTerm;
import com.powsybl.openloadflow.graph.GraphConnectivity;
import com.powsybl.openloadflow.network.ElementType;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfElement;
import com.powsybl.openloadflow.network.LfHvdc;

import java.util.Objects;

/**
 * Computed element modelling the loss of the droop coupling of an HVDC line in AC emulation (linear mode).
 * The droop law {@code p1 = k (phi1 - phi2) + p0} couples the two buses of the HVDC line exactly like a DC branch of
 * power {@code k}: removing the coupling (HVDC line lost, or one of its buses lost) is a rank one modification of the
 * Jacobian matrix handled by the Woodbury formula like a branch contingency. The constant term {@code p0} is in the
 * target vector and is handled by {@link WoodburyEngine#runDcLoadFlowWithModifiedTargetVector}.
 *
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public final class ComputedHvdcAcEmulationElement implements ComputedElement {

    private int computedElementIndex = -1; // index of the element in the rhs for +1-1
    private int localIndex = -1; // local index of the element in the matrix used in the setAlphas method
    private double alphaForWoodburyComputation = Double.NaN;

    private final LfHvdc hvdc;
    private final HvdcAcEmulationSide1DCFlowEquationTerm p1;

    public ComputedHvdcAcEmulationElement(LfHvdc hvdc, EquationSystem<DcVariableType, DcEquationType> equationSystem) {
        this.hvdc = Objects.requireNonNull(hvdc);
        this.p1 = equationSystem.getEquationTerm(ElementType.HVDC, hvdc.getNum(), HvdcAcEmulationSide1DCFlowEquationTerm.class);
    }

    public LfHvdc getHvdc() {
        return hvdc;
    }

    @Override
    public LfElement getLfElement() {
        return hvdc;
    }

    @Override
    public LfBus getBus1() {
        return hvdc.getBus1();
    }

    @Override
    public LfBus getBus2() {
        return hvdc.getBus2();
    }

    @Override
    public int getPh1VarRow() {
        return p1.getPh1Var().getRow();
    }

    @Override
    public int getPh2VarRow() {
        return p1.getPh2Var().getRow();
    }

    @Override
    public EquationTerm<DcVariableType, DcEquationType> getEquation() {
        return p1;
    }

    /**
     * Power of the virtual branch equivalent to the droop coupling.
     */
    public double getPower() {
        return p1.getK();
    }

    @Override
    public int getComputedElementIndex() {
        return computedElementIndex;
    }

    @Override
    public void setComputedElementIndex(int index) {
        this.computedElementIndex = index;
    }

    @Override
    public int getLocalIndex() {
        return localIndex;
    }

    @Override
    public void setLocalIndex(int index) {
        this.localIndex = index;
    }

    @Override
    public double getAlphaForWoodburyComputation() {
        return alphaForWoodburyComputation;
    }

    @Override
    public void setAlphaForWoodburyComputation(double alphaForPostContingencyStates) {
        this.alphaForWoodburyComputation = alphaForPostContingencyStates;
    }

    @Override
    public void applyToConnectivity(GraphConnectivity<LfBus, LfBranch> connectivity) {
        // an HVDC line is not an edge of the AC connectivity graph: losing it does not change the AC connectivity
    }
}
