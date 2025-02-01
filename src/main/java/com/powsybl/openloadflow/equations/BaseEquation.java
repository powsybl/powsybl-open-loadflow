/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.equations;

import com.powsybl.openloadflow.util.Evaluable;

import java.util.Collection;
import java.util.List;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public interface BaseEquation<V extends Enum<V> & Quantity, E extends Enum<E> & Quantity> extends Evaluable {

    void setActive(boolean active);

    BaseEquation<V, E> addTerm(BaseEquationTerm<V, E> term);

    <T extends BaseEquationTerm<V, E>> BaseEquation<V, E> addTerms(List<T> terms);

    boolean isActive();

    int getColumn();

    <T extends BaseEquationTerm<V, E>> List<T> getTerms();
}
