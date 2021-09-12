/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.equations;

import com.powsybl.openloadflow.network.ElementType;
import com.powsybl.openloadflow.network.LfNetwork;
import org.apache.commons.lang3.tuple.Pair;

import java.io.Writer;
import java.util.*;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public interface EquationSystem<V extends Enum<V> & Quantity, E extends Enum<E> & Quantity> {

    enum EquationUpdateType {
        DEFAULT,
        AFTER_NR,
        NEVER
    }

    Equation<V, E> createEquation(int num, E type);

    Collection<Equation<V, E>> getEquations();

    List<Equation<V, E>> getEquations(ElementType elementType, int elementNum);

    Optional<Equation<V, E>> getEquation(int num, E type);

    boolean hasEquation(int num, E type);

    Equation<V, E> removeEquation(int num, E type);

    void addEquationTerm(EquationTerm<V, E> equationTerm);

    List<EquationTerm<V, E>> getEquationTerms(ElementType elementType, int elementNum);

    <T extends EquationTerm<V, E>> T getEquationTerm(ElementType elementType, int elementNum, Class<T> clazz);

    SortedSet<Variable<V>> getSortedVariablesToFind();

    NavigableMap<Equation<V, E>, NavigableMap<Variable<V>, List<EquationTerm<V, E>>>> getSortedEquationsToSolve();

    List<String> getRowNames(LfNetwork network);

    List<String> getColumnNames(LfNetwork network);

    double[] createEquationVector();

    void updateEquationVector(double[] fx);

    void updateEquations(double[] x);

    void updateEquations(double[] x, EquationUpdateType updateType);

    void addListener(EquationSystemListener<V, E> listener);

    void removeListener(EquationSystemListener<V, E> listener);

    void notifyEquationChange(Equation<V, E> equation, EquationEventType eventType);

    void notifyEquationTermChange(EquationTerm<V, E> term, EquationTermEventType eventType);

    void write(Writer writer);

    List<Pair<Equation<V, E>, Double>> findLargestMismatches(double[] mismatch, int count);
}
