/**
 * Copyright (c) 2025, SuperGrid Institute (http://www.supergrid-institute.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.iidm.network.DcLine;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.util.PerUnit;

import java.util.Objects;

/**
 * @author Denis Bonnand {@literal <denis.bonnand at supergrid-institute.com>}
 */
public class LfDcLineImpl extends AbstractLfDcLine {

    private final Ref<DcLine> dcLineRef;

    public LfDcLineImpl(LfDcNode dcNode1, LfDcNode dcNode2, LfNetwork network, DcLine dcLine, LfNetworkParameters parameters) {
        super(network, dcNode1, dcNode2, dcLine.getR());
        this.dcLineRef = Ref.create(dcLine, parameters.isCacheEnabled());
    }

    public static LfDcLineImpl create(DcLine dcLine, LfNetwork network, LfDcNode dcNode1, LfDcNode dcNode2,
                                      LfNetworkParameters parameters) {
        Objects.requireNonNull(network);
        Objects.requireNonNull(dcLine);
        Objects.requireNonNull(parameters);
        return new LfDcLineImpl(dcNode1, dcNode2, network, dcLine, parameters);
    }

    private DcLine getDcLine() {
        return dcLineRef.get();
    }

    @Override
    public String getId() {
        return getDcLine().getId();
    }

    @Override
    public void updateState(LfNetworkStateUpdateParameters parameters, LfNetworkUpdateReport updateReport) {
        if (isDisabled()) {
            updateFlows(Double.NaN, Double.NaN, Double.NaN, Double.NaN);
        } else {
            updateFlows(i1.eval(), i2.eval(), p1.eval(), p2.eval());
        }
    }

    @Override
    public void updateFlows(double i1, double i2, double p1, double p2) {
        var dcLine = getDcLine();

        dcLine.getDcTerminal1().setI(i1 * PerUnit.ibDc(dcNode1.getNominalV()));
        dcLine.getDcTerminal2().setI(i2 * PerUnit.ibDc(dcNode2.getNominalV()));
        dcLine.getDcTerminal1().setP(p1 * PerUnit.SB);
        dcLine.getDcTerminal2().setP(p2 * PerUnit.SB);
    }
}
