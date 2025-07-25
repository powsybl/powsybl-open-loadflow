/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.util;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.commons.test.PowsyblTestReportResourceBundle;
import org.junit.jupiter.api.Test;
import java.util.Locale;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Alice Caron {@literal <alice.caron at rte-france.com>}
 */
class ReportsTest {

    @Test
    void useReportWithFranceLocaleTest() {
        ReportNode reportNode = ReportNode.newRootReportNode()
                .withLocale(Locale.FRANCE)
                .withResourceBundles(PowsyblTestReportResourceBundle.TEST_BASE_NAME)
                .withMessageTemplate("testReport")
                .build();
        assertEquals("Rapport de test en français", reportNode.getMessage());
    }

    @Test
    void useReportWithJVMLocaleTest() {
        ReportNode reportNode = ReportNode.newRootReportNode()
                .withLocale(Locale.ENGLISH)
                .withResourceBundles(PowsyblTestReportResourceBundle.TEST_BASE_NAME)
                .withMessageTemplate("testReport")
                .build();
        assertEquals("Test Report", reportNode.getMessage());
    }
}
