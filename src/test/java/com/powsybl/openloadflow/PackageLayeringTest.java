/*
 * Copyright (c) 2026, Elia Group (https://www.eliagroup.eu)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.*;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Prevents package dependencies that would become cyclic module dependencies once the repository is split into
 * modules.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class PackageLayeringTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.powsybl.openloadflow");
    }

    @Test
    void networkDoesNotDependOnOpenLoadFlowParameters() {
        noClasses().that().resideInAPackage("com.powsybl.openloadflow.network..")
                .should().dependOnClassesThat().belongToAnyOf(OpenLoadFlowParameters.class)
                .check(classes);
    }

    @Test
    void networkDoesNotDependOnSecurityAnalysis() {
        noClasses().that().resideInAPackage("com.powsybl.openloadflow.network..")
                .should().dependOnClassesThat().resideInAPackage("com.powsybl.openloadflow.sa..")
                .check(classes);
    }

    @Test
    void networkDoesNotDependOnAcDcEquations() {
        noClasses().that().resideInAPackage("com.powsybl.openloadflow.network..")
                .should().dependOnClassesThat().resideInAnyPackage("com.powsybl.openloadflow.ac.equations..",
                        "com.powsybl.openloadflow.dc.equations..")
                .check(classes);
    }

    @Test
    void equationsDoNotDependOnLoadFlow() {
        noClasses().that().resideInAnyPackage("com.powsybl.openloadflow.equations..",
                        "com.powsybl.openloadflow.ac.equations..", "com.powsybl.openloadflow.dc.equations..")
                .should().dependOnClassesThat().resideInAPackage("com.powsybl.openloadflow.lf..")
                .check(classes);
    }

    @Test
    void utilDoesNotDependOnOtherPackages() {
        noClasses().that().resideInAPackage("com.powsybl.openloadflow.util")
                .should().dependOnClassesThat(resideInAPackage("com.powsybl.openloadflow..")
                        .and(resideOutsideOfPackage("com.powsybl.openloadflow.util")))
                .check(classes);
    }

    @Test
    void graphOnlyDependsOnUtil() {
        // also checks test classes, as graph tests will move with graph to a lower module
        JavaClasses graphClasses = new ClassFileImporter().importPackages("com.powsybl.openloadflow.graph");
        noClasses().that().resideInAPackage("com.powsybl.openloadflow.graph..")
                .should().dependOnClassesThat(resideInAPackage("com.powsybl.openloadflow..")
                        .and(resideOutsideOfPackages("com.powsybl.openloadflow.graph..", "com.powsybl.openloadflow.util")))
                .check(graphClasses);
    }

    @Test
    void networkTestsOnlyDependOnLowerLayers() {
        // checks test classes, as network tests will move with network to a lower module
        JavaClasses networkClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.ONLY_INCLUDE_TESTS)
                .importPackages("com.powsybl.openloadflow.network");
        noClasses().that().resideInAPackage("com.powsybl.openloadflow.network..")
                .should().dependOnClassesThat(resideInAPackage("com.powsybl.openloadflow..")
                        .and(resideOutsideOfPackages("com.powsybl.openloadflow.network..", "com.powsybl.openloadflow.graph..",
                                "com.powsybl.openloadflow.util")))
                .check(networkClasses);
    }

    @Test
    void equationsTestsOnlyDependOnLowerLayers() {
        // checks test classes, as equations tests will move with equations to a lower module
        JavaClasses equationsClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.ONLY_INCLUDE_TESTS)
                .importPackages("com.powsybl.openloadflow.equations",
                "com.powsybl.openloadflow.ac.equations", "com.powsybl.openloadflow.dc.equations", "com.powsybl.openloadflow.adm");
        noClasses().that().resideInAnyPackage("com.powsybl.openloadflow.equations..", "com.powsybl.openloadflow.ac.equations..",
                        "com.powsybl.openloadflow.dc.equations..", "com.powsybl.openloadflow.adm..")
                .should().dependOnClassesThat(resideInAPackage("com.powsybl.openloadflow..")
                        .and(resideOutsideOfPackages("com.powsybl.openloadflow.equations..", "com.powsybl.openloadflow.ac.equations..",
                                "com.powsybl.openloadflow.dc.equations..", "com.powsybl.openloadflow.adm..",
                                "com.powsybl.openloadflow.network..", "com.powsybl.openloadflow.graph..", "com.powsybl.openloadflow.util")))
                .check(equationsClasses);
    }
}
