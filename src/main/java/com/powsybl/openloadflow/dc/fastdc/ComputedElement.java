/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.dc.fastdc;

import com.powsybl.commons.PowsyblException;
import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.math.matrix.Matrix;
import com.powsybl.openloadflow.dc.DcLoadFlowContext;
import com.powsybl.openloadflow.dc.equations.ClosedBranchSide1DcFlowEquationTerm;
import com.powsybl.openloadflow.dc.equations.DcEquationType;
import com.powsybl.openloadflow.dc.equations.DcVariableType;
import com.powsybl.openloadflow.equations.Equation;
import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.graph.GraphConnectivity;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.action.AbstractLfBranchAction;
import com.powsybl.openloadflow.network.action.AbstractLfTapChangerAction;
import com.powsybl.openloadflow.network.action.LfAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 * @author Gaël Macherel {@literal <gael.macherel@artelys.com>}
 */
public interface ComputedElement {

    Logger LOGGER = LoggerFactory.getLogger(ComputedElement.class);

    int getComputedElementIndex();

    void setComputedElementIndex(final int index);

    int getLocalIndex();

    void setLocalIndex(final int index);

    double getAlphaForWoodburyComputation();

    void setAlphaForWoodburyComputation(double alphaForPostContingencyStates);

    LfBranch getLfBranch();

    ClosedBranchSide1DcFlowEquationTerm getLfBranchEquation();

    void applyToConnectivity(GraphConnectivity<LfBus, LfBranch> connectivity);

    /**
     * Set the indexes of the computed elements in the +1-1 rhs, used in Woodbury calculations.
     * The indexes depend on the number of distinct branches affected by the elements.
     * Those affecting the same branch share the same +1-1 rhs column.
     */
    static void setComputedElementIndexes(Collection<? extends ComputedElement> elements) {
        AtomicInteger index = new AtomicInteger(0);
        Map<LfBranch, Integer> branchesToRhsIndex = new HashMap<>();
        for (ComputedElement element : elements) {
            LfBranch elementLfBranch = element.getLfBranch();
            Integer elementIndex = branchesToRhsIndex.computeIfAbsent(elementLfBranch, lfBranch -> index.getAndIncrement());
            element.setComputedElementIndex(elementIndex);
        }
    }

    static void setLocalIndexes(Collection<? extends ComputedElement> elements) {
        int index = 0;
        for (ComputedElement element : elements) {
            element.setLocalIndex(index++);
        }
    }

    /**
     * Fills the right hand side with +1/-1 to model a branch contingency or action.
     */
    private static void fillRhs(EquationSystem<DcVariableType, DcEquationType> equationSystem, Collection<? extends ComputedElement> computedElements, Matrix rhs) {
        for (ComputedElement element : computedElements) {
            LfBranch lfBranch = element.getLfBranch();
            if (lfBranch.getBus1() == null || lfBranch.getBus2() == null) {
                continue;
            }
            LfBus bus1 = lfBranch.getBus1();
            LfBus bus2 = lfBranch.getBus2();
            if (bus1.isSlack()) {
                Equation<DcVariableType, DcEquationType> p = equationSystem.getEquation(bus2.getNum(), DcEquationType.BUS_TARGET_P).orElseThrow(IllegalStateException::new);
                rhs.set(p.getColumn(), element.getComputedElementIndex(), -1);
            } else if (bus2.isSlack()) {
                Equation<DcVariableType, DcEquationType> p = equationSystem.getEquation(bus1.getNum(), DcEquationType.BUS_TARGET_P).orElseThrow(IllegalStateException::new);
                rhs.set(p.getColumn(), element.getComputedElementIndex(), 1);
            } else {
                Equation<DcVariableType, DcEquationType> p1 = equationSystem.getEquation(bus1.getNum(), DcEquationType.BUS_TARGET_P).orElseThrow(IllegalStateException::new);
                Equation<DcVariableType, DcEquationType> p2 = equationSystem.getEquation(bus2.getNum(), DcEquationType.BUS_TARGET_P).orElseThrow(IllegalStateException::new);
                rhs.set(p1.getColumn(), element.getComputedElementIndex(), 1);
                rhs.set(p2.getColumn(), element.getComputedElementIndex(), -1);
            }
        }
    }

    static DenseMatrix initRhs(EquationSystem<DcVariableType, DcEquationType> equationSystem, Collection<? extends ComputedElement> elements) {
        // the number of columns of the rhs equals the number of distinct branches affected by the computed elements
        // those affecting the same branch share the same column
        int columnCount = (int) elements.stream().map(ComputedElement::getLfBranch).filter(Objects::nonNull).distinct().count();
        // otherwise, defining the rhs matrix will result in integer overflow
        int equationCount = equationSystem.getIndex().getColumnCount();
        int maxElements = Integer.MAX_VALUE / (equationCount * Double.BYTES);
        if (columnCount > maxElements) {
            throw new PowsyblException("Too many elements " + columnCount
                    + ", maximum is " + maxElements + " for a system with " + equationCount + " equations");
        }
        DenseMatrix rhs = new DenseMatrix(equationCount, columnCount);
        fillRhs(equationSystem, elements, rhs);
        return rhs;
    }

    static DenseMatrix calculateElementsStates(DcLoadFlowContext loadFlowContext, Collection<? extends ComputedElement> computedElements) {
        DenseMatrix elementsStates = initRhs(loadFlowContext.getEquationSystem(), computedElements); // rhs with +1 -1 on computed elements
        loadFlowContext.getJacobianMatrix().solveTransposed(elementsStates);
        return elementsStates;
    }

    static Map<LfAction, ComputedElement> createActionElementsIndexByLfAction(Map<String, LfAction> lfActionById, EquationSystem<DcVariableType, DcEquationType> equationSystem) {
        Map<LfAction, ComputedElement> computedElements = lfActionById.values().stream()
                .flatMap(lfAction -> {
                    if (!lfAction.isValid()) {
                        LOGGER.warn("Action '{}' is not valid, it will be ignored", lfAction.getId());
                        return Stream.empty();
                    }
                    ComputedElement element = switch (lfAction) {
                        case AbstractLfTapChangerAction<?> abstractLfTapChangerAction ->
                            new ComputedTapPositionChangeElement(abstractLfTapChangerAction.getChange(), equationSystem);
                        case AbstractLfBranchAction<?> abstractLfBranchAction when abstractLfBranchAction.getEnabledBranch() != null ->
                            new ComputedSwitchBranchElement(abstractLfBranchAction.getEnabledBranch(), true, equationSystem);
                        case AbstractLfBranchAction<?> abstractLfBranchAction when abstractLfBranchAction.getDisabledBranch() != null ->
                            new ComputedSwitchBranchElement(abstractLfBranchAction.getDisabledBranch(), false, equationSystem);
                        default -> throw new IllegalStateException("Only tap position change and branch enabling/disabling are supported in WoodburyDcSecurityAnalysis");
                    };
                    return Stream.of(Map.entry(lfAction, element));
                })
                .filter(e -> e.getValue().getLfBranchEquation() != null)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (existing, replacement) -> existing,
                        LinkedHashMap::new
                ));
        ComputedElement.setComputedElementIndexes(computedElements.values());
        return computedElements;
    }

}
