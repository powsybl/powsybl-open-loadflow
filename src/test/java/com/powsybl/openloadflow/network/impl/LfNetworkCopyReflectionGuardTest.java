/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.ieeecdf.converter.IeeeCdfNetworkFactory;
import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.ac.AcLoadFlowContext;
import com.powsybl.openloadflow.ac.AcLoadFlowParameters;
import com.powsybl.openloadflow.ac.AcloadFlowEngine;
import com.powsybl.openloadflow.ac.AsymmetricalLoadFlowTest;
import com.powsybl.openloadflow.graph.EvenShiloachGraphDecrementalConnectivityFactory;
import com.powsybl.openloadflow.network.AcDcNetworkFactory;
import com.powsybl.openloadflow.network.HvdcNetworkFactory;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.LfTopoConfig;
import com.powsybl.openloadflow.network.LoadFlowModel;
import com.powsybl.openloadflow.network.MultiAreaNetworkFactory;
import com.powsybl.openloadflow.network.NodeBreakerNetworkFactory;
import com.powsybl.openloadflow.network.PhaseControlFactory;
import com.powsybl.openloadflow.network.SlackBusSelectionMode;
import com.powsybl.openloadflow.util.Evaluable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Reflective guard of {@link LfNetworkCopier}: walks the original and copied object graphs in
 * lockstep and checks every instance field against copy semantics derived from its type:
 * <ul>
 *   <li>IIDM {@link Ref} handles must be <b>shared</b> (same reference);</li>
 *   <li>values (primitives, strings, enums) must be <b>equal</b>;</li>
 *   <li>objects of this library must be <b>distinct but corresponding</b> instances — an identity
 *       map also checks the sharing topology (an object referenced twice in the original must map
 *       to a single copied object referenced the same way);</li>
 *   <li>collections and maps must be <b>distinct containers</b> (no aliasing of mutable state
 *       between the copy and the original, which two threads would then mutate concurrently)
 *       with corresponding content;</li>
 *   <li>solver-injected ({@link Evaluable}) and lazily recomputed state is skipped (covered by the
 *       behavioral equivalence tests of {@link LfNetworkCopierTest}).</li>
 * </ul>
 * A field added later is checked by the default rules automatically, so unlike the static
 * inventory of {@link LfNetworkCopyFieldGuardTest} (which forces the author to classify new
 * fields), this test verifies that the classification actually holds, including on an already
 * solved network whose mutable state differs from the freshly built defaults.
 *
 * @author Gautier Bureau {@literal <gautier.bureau at rte-france.com>}
 */
class LfNetworkCopyReflectionGuardTest {

    private static final String OUR_PACKAGE = "com.powsybl.openloadflow";

    /**
     * Fields excluded from the walk: either reset by design (lazily recomputed by the copy) or
     * managed at network level (slack selection). Keyed by declaring class simple name + field name.
     */
    private static final Set<String> SKIPPED_FIELDS = Set.of(
            // selection flags and results, lazily re-run on the copy (slack/reference selection and its
            // excluded slack buses input now live on the synchronous networks)
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
            "Controller#sectionsB", "Controller#sectionsG",
            "LfLoadImpl#loadModel",
            "LfStaticVarCompensatorImpl#standByAutomaton",
            "AbstractLfAcDcConverter#lossFactors",
            // immutable after the load by design
            "AbstractLfGenerator#asym", "AbstractLfBranch#asymLine",
            "LfNetwork#slackBusSelector", "LfNetwork#referenceBusSelector", "LfNetwork#connectivityFactory",
            // each synchronous network reuses the selectors of its parent network (shared after the load)
            "LfSynchronousNetworkImpl#slackBusSelector", "LfSynchronousNetworkImpl#referenceBusSelector");

    record Case(String name, Network network, Consumer<LoadFlowParameters> parametersCustomizer, LfTopoConfig topoConfig, boolean solve) {
        @Override
        public String toString() {
            return name;
        }
    }

    static Stream<Arguments> cases() {
        Network nodeBreakerNetwork = NodeBreakerNetworkFactory.create();
        LfTopoConfig nodeBreakerTopoConfig = new LfTopoConfig();
        nodeBreakerTopoConfig.getSwitchesToOpen().add(nodeBreakerNetwork.getSwitch("C"));

        return Stream.of(
                // solved network: mutable state (solved voltages, distributed targets, PV->PQ switches
                // with frozen reactive targets) differs from the freshly built defaults, so the
                // equality checks actually discriminate
                new Case("ieee300Solved", IeeeCdfNetworkFactory.create300(), p -> { }, new LfTopoConfig(), true),
                new Case("nodeBreakerRetainedSwitch", nodeBreakerNetwork, p -> { }, nodeBreakerTopoConfig, false),
                new Case("phaseControlT2wt", PhaseControlFactory.createNetworkWithT2wt(), p -> p.setPhaseShifterRegulationOn(true), new LfTopoConfig(), false),
                new Case("hvdcAcEmulationSolved", HvdcNetworkFactory.createNetworkWithGenerators(), p -> p.setHvdcAcEmulation(true), new LfTopoConfig(), true),
                new Case("areas", MultiAreaNetworkFactory.createTwoAreasWithTieLine(), p -> { }, new LfTopoConfig(), false),
                new Case("acDcThreeConvertersSolved", AcDcNetworkFactory.createAcDcNetworkWithThreeConverters(),
                        p -> OpenLoadFlowParameters.create(p).setAcDcNetwork(true), new LfTopoConfig(), true),
                new Case("asymmetricalSolved", AsymmetricalLoadFlowTest.fourNodescreate(),
                        p -> {
                            p.setUseReactiveLimits(false).setDistributedSlack(false);
                            OpenLoadFlowParameters.create(p).setAsymmetrical(true)
                                    .setSlackBusSelectionMode(SlackBusSelectionMode.FIRST);
                        }, new LfTopoConfig(), true)
        ).map(Arguments::of);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void testReflectiveCopyEquivalence(Case c) {
        LoadFlowParameters parameters = new LoadFlowParameters();
        OpenLoadFlowParameters.create(parameters);
        c.parametersCustomizer().accept(parameters);
        OpenLoadFlowParameters parametersExt = parameters.getExtension(OpenLoadFlowParameters.class);

        AcLoadFlowParameters acParameters = OpenLoadFlowParameters.createAcParameters(c.network(), parameters, parametersExt,
                new DenseMatrixFactory(), new EvenShiloachGraphDecrementalConnectivityFactory<>());
        if (!c.topoConfig().getSwitchesToOpen().isEmpty()) {
            acParameters.getNetworkParameters().setBreakers(true);
        }
        List<LfNetwork> originals = Networks.load(c.network(), c.topoConfig(), acParameters.getNetworkParameters(), ReportNode.NO_OP);

        for (LfNetwork original : originals) {
            if (original.getValidity() != LfNetwork.Validity.VALID) {
                continue;
            }
            if (c.solve()) {
                try (var context = new AcLoadFlowContext(original, new AcLoadFlowParameters(acParameters))) {
                    new AcloadFlowEngine(context).run();
                }
            }
            LfNetwork copy = LfNetworkCopier.copy(original, LoadFlowModel.AC, ReportNode.NO_OP);
            new Walker().compare(original, copy, "network");
        }
    }

    @org.junit.jupiter.api.Test
    void testReflectiveCopyEquivalenceOnRestoredTopologyNetwork() {
        // switches built closed (for a closing remedial action) then reopened by the initial topology
        // restoration: disabled elements and removed connectivity edges must be reproduced
        Network network = NodeBreakerNetworkFactory.create3Bars();
        network.getSwitch("C1").setOpen(true);
        network.getSwitch("C2").setOpen(true);
        LfTopoConfig topoConfig = new LfTopoConfig();
        topoConfig.getSwitchesToClose().add(network.getSwitch("C1"));
        topoConfig.getSwitchesToClose().add(network.getSwitch("C2"));

        LoadFlowParameters parameters = new LoadFlowParameters();
        OpenLoadFlowParameters parametersExt = OpenLoadFlowParameters.create(parameters);
        AcLoadFlowParameters acParameters = OpenLoadFlowParameters.createAcParameters(network, parameters, parametersExt,
                new DenseMatrixFactory(), new EvenShiloachGraphDecrementalConnectivityFactory<>());
        acParameters.getNetworkParameters().setBreakers(true);

        try (var lfNetworks = Networks.loadWithReconnectableElements(network, topoConfig,
                acParameters.getNetworkParameters(), ReportNode.NO_OP)) {
            LfNetwork original = lfNetworks.getLargest().orElseThrow();
            LfNetwork copy = LfNetworkCopier.copy(original, LoadFlowModel.AC, ReportNode.NO_OP);
            new Walker().compare(original, copy, "network");
        }
    }

    private static final class Walker {

        private final IdentityHashMap<Object, Object> correspondence = new IdentityHashMap<>();

        void compare(Object original, Object copy, String path) {
            if (original == null || copy == null) {
                if (original != copy) {
                    fail(path + ": one side is null (original=" + original + ", copy=" + copy + ")");
                }
                return;
            }
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
                // foreign objects (IIDM, commons) are shared by design
                assertSame(original, copy, path + ": foreign object must be shared");
                return;
            }

            // an object of this library must be a distinct corresponding instance, and an object
            // referenced twice in the original must map to a single copy referenced the same way
            Object knownCopy = correspondence.get(original);
            if (knownCopy != null) {
                assertSame(knownCopy, copy, path + ": sharing topology broken, two different copies for one original object");
                return;
            }
            assertEquals(original.getClass(), copy.getClass(), path + ": type mismatch");
            assertNotSame(original, copy, path + ": object of the copy graph must not be shared with the original");
            correspondence.put(original, copy);

            for (Class<?> type = original.getClass(); type != null && type.getName().startsWith(OUR_PACKAGE); type = type.getSuperclass()) {
                for (Field field : type.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                        continue;
                    }
                    String key = type.getSimpleName() + "#" + field.getName();
                    if (SKIPPED_FIELDS.contains(key)) {
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
