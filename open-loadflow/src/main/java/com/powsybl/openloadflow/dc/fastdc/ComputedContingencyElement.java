/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.dc.fastdc;

import com.powsybl.contingency.ContingencyElement;
import com.powsybl.na.equations.EquationSystem;
import com.powsybl.na.equations.dc.ClosedBranchSide1DcFlowEquationTerm;
import com.powsybl.na.equations.dc.DcEquationType;
import com.powsybl.na.equations.dc.DcVariableType;
import com.powsybl.na.graph.GraphConnectivity;
import com.powsybl.na.network.ElementType;
import com.powsybl.na.network.LfBranch;
import com.powsybl.na.network.LfBus;
import com.powsybl.na.network.LfNetwork;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 * @author Gaël Macherel {@literal <gael.macherel@artelys.com>}
 */
public final class ComputedContingencyElement extends AbstractComputedElement implements ComputedElement {

    private final ContingencyElement element;

    public ComputedContingencyElement(final ContingencyElement element, LfNetwork lfNetwork, EquationSystem<DcVariableType, DcEquationType> equationSystem) {
        super(lfNetwork.getBranchById(element.getId()),
                equationSystem.getEquationTerm(ElementType.BRANCH, lfNetwork.getBranchById(element.getId()).getNum(), ClosedBranchSide1DcFlowEquationTerm.class));
        this.element = element;
    }

    public ContingencyElement getElement() {
        return element;
    }

    @Override
    public void applyToConnectivity(GraphConnectivity<LfBus, LfBranch> connectivity) {
        LfBranch lfBranch = getLfBranch();
        if (lfBranch.getBus1() != null && lfBranch.getBus2() != null) {
            connectivity.removeEdge(lfBranch);
        }
    }
}
