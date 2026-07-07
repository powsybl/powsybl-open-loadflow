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
import com.powsybl.openloadflow.network.LfHvdc;

/**
 * A computed element representing the removal of an HVDC AC-emulation droop coupling in a contingency.
 * The droop coupling k*(φ1 − φ2) is structurally equivalent to a virtual branch with susceptance k,
 * so the Woodbury formula applies verbatim.
 *
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public final class ComputedHvdcAcEmulationElement implements ComputedElement {

    private int computedElementIndex = -1;
    private int localIndex = -1;
    private double alphaForWoodburyComputation = Double.NaN;

    private final LfHvdc hvdc;
    private final HvdcAcEmulationSide1DCFlowEquationTerm p1;

    public ComputedHvdcAcEmulationElement(LfHvdc hvdc, EquationSystem<DcVariableType, DcEquationType> equationSystem) {
        this.hvdc = hvdc;
        this.p1 = equationSystem.getEquationTerm(ElementType.HVDC, hvdc.getNum(), HvdcAcEmulationSide1DCFlowEquationTerm.class);
    }

    public LfHvdc getLfElement() {
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

    public double getSusceptance() {
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
    public void setAlphaForWoodburyComputation(double alpha) {
        this.alphaForWoodburyComputation = alpha;
    }

    @Override
    public void applyToConnectivity(GraphConnectivity<LfBus, LfBranch> connectivity) {
        // HVDC is not a graph branch — tripping it does not change AC connectivity
    }
}
