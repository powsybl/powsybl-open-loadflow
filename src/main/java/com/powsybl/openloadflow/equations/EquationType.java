/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.equations;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public enum EquationType {
    BUS_P("p", SubjectType.BUS),
    BUS_Q("q", SubjectType.BUS),
    BUS_V("v", SubjectType.BUS),
    BUS_PHI("\u03C6", SubjectType.BUS),
    BRANCH_P("t", SubjectType.BRANCH),
    BRANCH_I("i", SubjectType.BRANCH),
    ZERO_Q("z_q", SubjectType.BUS),
    ZERO_V("z_v", SubjectType.BRANCH),
    ZERO_PHI("z_\u03C6", SubjectType.BRANCH),
    ZERO_RHO1("z_\u03C1", SubjectType.BRANCH);

    private final String symbol;

    private final SubjectType subjectType;

    EquationType(String symbol, SubjectType subjectType) {
        this.symbol = symbol;
        this.subjectType = subjectType;
    }

    public String getSymbol() {
        return symbol;
    }

    public SubjectType getSubjectType() {
        return subjectType;
    }
}
