/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.util.Evaluable;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Reflective guard of {@link LfNetworkCopier}: walks the original and copied LfNetwork to check recursively
 * if their fields are the same
 *
 * @author Gautier Bureau {@literal <gautier.bureau at rte-france.com>}
 */
final class LfNetworkCopyDeepFieldComparator {

    private final Set<Class<?>> alreadyCheckedClasses;

    LfNetworkCopyDeepFieldComparator() {
        alreadyCheckedClasses = new HashSet<>();
    }

    private static final String OUR_PACKAGE = "com.powsybl.openloadflow";

    /**
     * Fields intentionally excluded from the comparison.
     */
    private static final Set<String> SKIPPED_FIELDS = Set.of(
            // selection flags and results, lazily re-run on the copy
            "AbstractLfBus#slack", "AbstractLfBus#reference",
            "LfSynchronousNetworkImpl#slackBuses", "LfSynchronousNetworkImpl#referenceBus",
            "LfSynchronousNetworkImpl#referenceGenerator", "LfSynchronousNetworkImpl#excludedSlackBuses",
            // lazily recomputed structures
            "LfNetwork#connectivity", "LfNetwork#zeroImpedanceNetworksByModel",
            "AbstractLfBus#zeroImpedanceNetwork", "ZeroImpedanceContext#spanningTreeEdge",
            "LfBusImpl#violationLocation",
            "AbstractLfBranch#currentLimits1", "AbstractLfBranch#activePowerLimits1", "AbstractLfBranch#apparentPowerLimits1",
            "AbstractLfBranch#currentLimits2", "AbstractLfBranch#activePowerLimits2", "AbstractLfBranch#apparentPowerLimits2",
            // voltage control merge state, recomputed with the zero impedance networks
            "VoltageControl#mergeStatus", "VoltageControl#mainMergedVoltageControl",
            "VoltageControl#mergedDependentVoltageControls", "VoltageControl#disabled",
            // solver-attached lists (populated by equation system creation, not part of the copy)
            "AbstractImpedantLfBranch#additionalOpenP1", "AbstractImpedantLfBranch#additionalClosedP1",
            "AbstractImpedantLfBranch#additionalOpenQ1", "AbstractImpedantLfBranch#additionalClosedQ1",
            "AbstractImpedantLfBranch#additionalOpenP2", "AbstractImpedantLfBranch#additionalClosedP2",
            "AbstractImpedantLfBranch#additionalOpenQ2", "AbstractImpedantLfBranch#additionalClosedQ2",
            // reporting and listeners are per-instance by design
            "LfNetwork#reportNode", "LfNetwork#listeners",
            // equivalent shunts are accumulated by the asymmetrical equation system creator, redone
            // by the copy's own equation system (not build state)
            "LfAsymBus#bzEquiv", "LfAsymBus#gzEquiv", "LfAsymBus#bnEquiv", "LfAsymBus#gnEquiv",
            // application-level property bag, not used by the security analysis simulation state
            "AbstractPropertyBag#properties");

    /**
     * Fields of this library intentionally shared between the original and the copy because they
     * are immutable (or never mutated after the load).
     */
    private static final Set<String> SHARED_FIELDS = Set.of(
            "LfBusImpl#bbsIds",
            "Controller#sectionsB",
            "Controller#sectionsG",
            "LfLoadImpl#loadModel",
            "LfStaticVarCompensatorImpl#standByAutomaton",
            "AbstractLfAcDcConverter#lossFactors",
            // immutable after the load by design
            "AbstractLfGenerator#asym",
            "AbstractLfBranch#asymLine",
            "LfNetwork#slackBusSelector",
            "LfNetwork#referenceBusSelector",
            "LfNetwork#connectivityFactory",
            // each synchronous network reuses the selectors of its parent network (shared after the load)
            "LfSynchronousNetworkImpl#slackBusSelector",
            "LfSynchronousNetworkImpl#referenceBusSelector");

    public void compareLfNetworks(LfNetwork original, LfNetwork copy, String caseName) {
        new Walker(alreadyCheckedClasses).compare(original, copy, caseName);
    }

    public Set<Class<?>> getAllCheckedClasses() {
        return alreadyCheckedClasses;
    }

    private static final class Walker {

        Walker(Set<Class<?>> alreadyCheckedClasses) {
            this.alreadyCheckedClasses = alreadyCheckedClasses;
        }

        private final IdentityHashMap<Object, Object> alreadyComparedElements = new IdentityHashMap<>();

        private final Set<Class<?>> alreadyCheckedClasses;

        private void addClassToAlreadyCheckedClasses(Class<?> clazz) {
            if (!alreadyCheckedClasses.contains(clazz)) {
                alreadyCheckedClasses.add(clazz);
                Class<?> parentClass = clazz.getSuperclass();
                while (parentClass != null) {
                    alreadyCheckedClasses.add(parentClass);
                    parentClass = parentClass.getSuperclass();
                }
            }
        }

        /**
         * Recursive method that checks every fields inside each element of the compared LfNetwork
         */
        void compare(Object original, Object copy, String path) {
            if (original == null || copy == null) {
                if (original != copy) {
                    fail(path + ": one side is null (original=" + original + ", copy=" + copy + ")");
                }
                return;
            }
            // Adding the checked class (and all its parents) to the global alreadyCheckedClasses set (to check coverage at the end)
            addClassToAlreadyCheckedClasses(original.getClass());
            if (original instanceof Ref<?>) {
                assertSame(original, copy, path + ": IIDM Ref must be shared");
                return;
            }
            if (original instanceof Evaluable || copy instanceof Evaluable) {
                return; // solver injected, reset by design
            }
            if (isValue(original)) {
                assertEquals(original.getClass(), copy.getClass(), path + ": type mismatch");
                assertEquals(original, copy, path + ": value mismatch");
                return;
            }
            if (original instanceof Collection<?> originalCollection) {
                compareCollections(originalCollection, (Collection<?>) copy, path);
                return;
            }
            if (original instanceof Map<?, ?> originalMap) {
                compareMaps(originalMap, (Map<?, ?>) copy, path);
                return;
            }
            if (!original.getClass().getName().startsWith(OUR_PACKAGE)) {
                assertSame(original, copy, path + ": foreign object must be shared");
                return;
            }

            // an object of this library must be a distinct corresponding instance, and an object
            // referenced twice in the original must map to a single copy referenced the same way
            Object knownCopy = alreadyComparedElements.get(original);
            if (knownCopy != null) {
                assertSame(knownCopy, copy, path + ": sharing topology broken, two different copies for one original object");
                return;
            }
            assertEquals(original.getClass(), copy.getClass(), path + ": type mismatch");
            assertNotSame(original, copy, path + ": object of the copy graph must not be shared with the original");
            alreadyComparedElements.put(original, copy);
            Map<Class<?>, Set<String>> allExpectedFields = LfNetworkCopyFieldGuardTest.getLfNetworkExpectedFields();
            for (Class<?> type = original.getClass(); type != null && type.getName().startsWith(OUR_PACKAGE); type = type.getSuperclass()) {
                if (!allExpectedFields.containsKey(original.getClass())) {
                    fail(path + ": " + original.getClass().getSimpleName() + " is not referenced in LfNetworkCopyFieldGuardTest");
                }
                for (Field field : type.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                        continue;
                    }
                    String key = type.getSimpleName() + "#" + field.getName();
                    if (SKIPPED_FIELDS.contains(key)) {
                        addClassToAlreadyCheckedClasses(field.getClass());
                        continue;
                    }
                    if (Evaluable.class.isAssignableFrom(field.getType())) {
                        continue; // solver injected, reset by design (may be null on one side)
                    }
                    field.setAccessible(true);
                    Object originalValue;
                    Object copyValue;
                    try {
                        originalValue = field.get(original);
                        copyValue = field.get(copy);
                    } catch (IllegalAccessException e) {
                        throw new IllegalStateException(e);
                    }
                    String fieldPath = path + "." + field.getName();
                    if (SHARED_FIELDS.contains(key)) {
                        assertSame(originalValue, copyValue, fieldPath + ": field must be shared");
                    } else {
                        compare(originalValue, copyValue, fieldPath);
                    }
                }
            }
        }

        private void compareCollections(Collection<?> original, Collection<?> copy, String path) {
            assertEquals(original.size(), copy.size(), path + ": collection size mismatch");
            if (original == copy && !original.isEmpty()) {
                fail(path + ": mutable collection aliased between original and copy");
            }
            // copied collections are built in the original iteration order
            List<?> originalElements = new ArrayList<>(original);
            List<?> copyElements = new ArrayList<>(copy);
            for (int i = 0; i < originalElements.size(); i++) {
                compare(originalElements.get(i), copyElements.get(i), path + "[" + i + "]");
            }
        }

        private void compareMaps(Map<?, ?> original, Map<?, ?> copy, String path) {
            assertEquals(original.size(), copy.size(), path + ": map size mismatch");
            if (original == copy && !original.isEmpty()) {
                fail(path + ": mutable map aliased between original and copy");
            }
            for (Map.Entry<?, ?> entry : original.entrySet()) {
                if (!copy.containsKey(entry.getKey())) {
                    fail(path + ": missing key " + entry.getKey() + " in copy");
                }
                compare(entry.getValue(), copy.get(entry.getKey()), path + "[" + entry.getKey() + "]");
            }
        }

        private static boolean isValue(Object o) {
            return o instanceof Number || o instanceof Boolean || o instanceof Character
                    || o instanceof String || o instanceof Enum<?>;
        }
    }
}
