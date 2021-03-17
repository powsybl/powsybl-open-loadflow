/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow;

import com.powsybl.commons.reporter.TypedValue;

/**
 * @author Florian Dupuy <florian.dupuy at rte-france.com>
 */
public final class OpenLoadFlowReportConstants {

    private OpenLoadFlowReportConstants() {
    }

    public static final TypedValue INFO_SEVERITY = new TypedValue("OLF_INFO", TypedValue.INFO_LOGLEVEL);
    public static final TypedValue ERROR_SEVERITY = new TypedValue("OLF_ERROR", TypedValue.ERROR_LOGLEVEL);
}
