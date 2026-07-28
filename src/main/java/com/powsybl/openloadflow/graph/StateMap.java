/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Associates to each element whether it was added to the main
 * component or removed from a main component between the last call
 * to {@link GraphConnectivity#startTemporaryChanges(boolean)} and the
 * current instant. As an element cannot be added and removed at the same
 * time, we can use one {@link Map}, mapping an element to its {@link State}
 * (removed or added), instead of two {@link Set} (one for added element
 * and one for removed element).
 * <p>
 * If an element is not found, there are two possibilities:
 * <ol>
 *     <li>it was in the main component before and is still in the
 *     main component,</li>
 *     <li>it was <strong>not</strong> in the main component before and
 *     is still <strong>not</strong> in the main component.</li>
 * </ol>
 * </p>
 *
 * @param <T> the type of the stored element (edges or vertices)
 */
public class StateMap<T> extends HashMap<T, StateMap.State> {

    private Set<T> removed;
    private Set<T> added;

    /**
     * Mark the specified element as added. That is, mark the
     * element as being added to the main component by the
     * last topological changes.
     *
     * @param element the element to mark
     */
    public void markAdded(T element) {
        mark(element, State.ADDED);
    }

    /**
     * Mark the specified element as removed. That is, mark the
     * element as being removed from the main component by the
     * last topological changes.
     *
     * @param element the element to mark
     */
    public void markRemoved(T element) {
        mark(element, State.REMOVED);
    }

    /**
     * Update the state of the specified element, according
     * to the following rules:
     * <ul>
     *     <li>An element that is in the same state as it was before the call to
     *     {@link GraphConnectivity#startTemporaryChanges(boolean)} is inserted
     *     with the specified value.</li>
     *     <li>An element marked as added and removed by the last changes is removed.
     *     Indeed, it was outside the main component before the last call to
     *     {@link GraphConnectivity#startTemporaryChanges(boolean)}, then it was added
     *     to it, and now it is removed from it.</li>
     *     <li>An element marked as removed and added by the last changes is removed.</li>
     * </ul>
     *
     * @param element  the element to update
     * @param newState whether the element was added to or removed from
     *                 the main component
     */
    public void mark(T element, State newState) {
        compute(element, (k, state) -> {
            if (state == null || state == newState) {
                return newState;
            } else {
                return null;
            }
        });

        removed = null;
        added = null;
    }

    public Set<T> getRemoved() {
        if (removed == null) {
            removed = entrySet().stream()
                    .filter(e -> e.getValue() == State.REMOVED)
                    .map(Entry::getKey)
                    .collect(Collectors.toSet());
        }

        return removed;
    }

    public Set<T> getAdded() {
        if (added == null) {
            added = entrySet().stream()
                    .filter(e -> e.getValue() == State.ADDED)
                    .map(Entry::getKey)
                    .collect(Collectors.toSet());
        }
        return added;
    }

    /**
     * Describes the state in which an element (vertex or edge)
     * is relative to the main component and the last call to
     * {@link GraphConnectivity#startTemporaryChanges(boolean)}.
     * An element can either be added or removed.
     */
    public enum State {
        ADDED,
        REMOVED,
    }
}
