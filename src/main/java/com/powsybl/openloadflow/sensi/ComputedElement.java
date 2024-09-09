/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.sensi;

public class ComputedElement {
    private int computedElementIndex = -1; // index of the element in the rhs for +1-1
    private int localIndex = -1; // local index of the element : index of the element in the matrix used in the setAlphas method
    private double alphaForWoodburyState = Double.NaN;

    public int getComputedElementIndex() {
        return computedElementIndex;
    }

    public void setComputedElementIndex(final int index) {
        this.computedElementIndex = index;
    }

    public int getLocalIndex() {
        return localIndex;
    }

    protected void setLocalIndex(final int index) {
        this.localIndex = index;
    }

    public double getAlphaForWoodburyState() {
        return alphaForWoodburyState;
    }

    public void setAlphaForWoodburyState(double alphaForPostContingencyStates) {
        this.alphaForWoodburyState = alphaForPostContingencyStates;
    }
}
