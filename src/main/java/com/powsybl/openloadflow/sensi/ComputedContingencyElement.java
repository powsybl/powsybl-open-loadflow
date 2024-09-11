/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.sensi;

import com.powsybl.contingency.ContingencyElement;
import com.powsybl.openloadflow.dc.equations.ClosedBranchSide1DcFlowEquationTerm;
import com.powsybl.openloadflow.dc.equations.DcEquationType;
import com.powsybl.openloadflow.dc.equations.DcVariableType;
import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.graph.GraphConnectivity;
import com.powsybl.openloadflow.network.ElementType;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;

import java.util.Collection;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 * @author Gaël Macherel {@literal <gael.macherel@artelys.com>}
 */
public final class ComputedContingencyElement extends ComputedElement {

    private final ContingencyElement element;

    // TODO : refactor this
    public ComputedContingencyElement(final ContingencyElement element, LfNetwork lfNetwork, EquationSystem<DcVariableType, DcEquationType> equationSystem) {
        super(lfNetwork.getBranchById(element.getId()),
                equationSystem.getEquationTerm(ElementType.BRANCH, lfNetwork.getBranchById(element.getId()).getNum(), ClosedBranchSide1DcFlowEquationTerm.class));
        this.element = element;
    }

    public ContingencyElement getElement() {
        return element;
    }

    public static void applyToConnectivity(LfNetwork lfNetwork, GraphConnectivity<LfBus, LfBranch> connectivity, Collection<ComputedContingencyElement> breakingConnectivityElements) {
        breakingConnectivityElements.stream()
                .map(ComputedContingencyElement::getElement)
                .map(ContingencyElement::getId)
                .distinct()
                .map(lfNetwork::getBranchById)
                .filter(b -> b.getBus1() != null && b.getBus2() != null)
                .forEach(connectivity::removeEdge);
    }
}
