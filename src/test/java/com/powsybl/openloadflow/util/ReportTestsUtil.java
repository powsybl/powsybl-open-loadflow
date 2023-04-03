/**
 * Copyright (c) 2023, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.util;

import com.google.common.io.ByteStreams;
import com.powsybl.commons.reporter.ReporterModel;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

import static com.powsybl.commons.test.TestUtil.normalizeLineSeparator;

/**
 * @author Bertrand Rix <bertrand.rix at artelys.com>
 */
public final class ReportTestsUtil {

    private ReportTestsUtil() {
    }

    public static boolean compareReportWithReference(ReporterModel reporter, InputStream ref) throws IOException {
        StringWriter sw = new StringWriter();
        reporter.export(sw);

        String refLogExport = normalizeLineSeparator(new String(ByteStreams.toByteArray(ref), StandardCharsets.UTF_8));
        String logExport = normalizeLineSeparator(sw.toString());
        return refLogExport.equals(logExport);
    }
}
