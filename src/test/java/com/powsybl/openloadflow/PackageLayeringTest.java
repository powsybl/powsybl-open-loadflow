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
}
