/**
 * Copyright (c) 2026, Artelys (http://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

/**
 * Enforces the package layering required to split the repository into the modules of the target architecture
 * (see docs/design/target_architecture.md): each layer below is a future module, and a dependency from a lower
 * module to a higher one would be a cycle between modules. Test classes are checked too, as tests of a module can
 * only see that module, the modules below it and the test utilities.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
@AnalyzeClasses(packages = "com.powsybl.openloadflow")
class LayeringTest {

    private static final String COMMONS = "powsybl-na-commons";
    private static final String GRAPH = "powsybl-na-graph";
    private static final String NETWORK = "powsybl-na-network";
    private static final String EQUATIONS = "powsybl-na-equations";
    private static final String TEST = "powsybl-na-test";
    private static final String OPEN_LOADFLOW = "powsybl-open-loadflow";

    private static final String[] COMMONS_PACKAGES = {"com.powsybl.openloadflow.util"};
    private static final String[] GRAPH_PACKAGES = {"com.powsybl.openloadflow.graph.."};
    private static final String[] NETWORK_PACKAGES = {"com.powsybl.openloadflow.network.."};
    private static final String[] EQUATIONS_PACKAGES = {
        "com.powsybl.openloadflow.equations..",
        "com.powsybl.openloadflow.ac.equations..",
        "com.powsybl.openloadflow.dc.equations..",
        "com.powsybl.openloadflow.adm.."
    };
    private static final String[] TEST_PACKAGES = {"com.powsybl.openloadflow.test"};

    private static final DescribedPredicate<JavaClass> IN_OPEN_LOADFLOW = resideInAPackage("com.powsybl.openloadflow..")
            .and(DescribedPredicate.not(resideInAnyPackage(COMMONS_PACKAGES)))
            .and(DescribedPredicate.not(resideInAnyPackage(GRAPH_PACKAGES)))
            .and(DescribedPredicate.not(resideInAnyPackage(NETWORK_PACKAGES)))
            .and(DescribedPredicate.not(resideInAnyPackage(EQUATIONS_PACKAGES)))
            .and(DescribedPredicate.not(resideInAnyPackage(TEST_PACKAGES)))
            .as("any other package of com.powsybl.openloadflow");

    private static final ArchRule MODULE_LAYERING = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer(COMMONS).definedBy(COMMONS_PACKAGES)
            .layer(GRAPH).definedBy(GRAPH_PACKAGES)
            .layer(NETWORK).definedBy(NETWORK_PACKAGES)
            .layer(EQUATIONS).definedBy(EQUATIONS_PACKAGES)
            .layer(TEST).definedBy(TEST_PACKAGES)
            .layer(OPEN_LOADFLOW).definedBy(IN_OPEN_LOADFLOW)
            .whereLayer(OPEN_LOADFLOW).mayNotBeAccessedByAnyLayer()
            .whereLayer(EQUATIONS).mayOnlyBeAccessedByLayers(OPEN_LOADFLOW)
            .whereLayer(NETWORK).mayOnlyBeAccessedByLayers(EQUATIONS, OPEN_LOADFLOW)
            .whereLayer(GRAPH).mayOnlyBeAccessedByLayers(NETWORK, EQUATIONS, OPEN_LOADFLOW)
            .whereLayer(COMMONS).mayOnlyBeAccessedByLayers(GRAPH, NETWORK, EQUATIONS, TEST, OPEN_LOADFLOW);

    @ArchTest
    void checkModuleLayering(JavaClasses classes) {
        MODULE_LAYERING.check(classes);
    }
}
