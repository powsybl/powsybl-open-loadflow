/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.iidm.network.ShuntCompensator;
import com.powsybl.iidm.network.StaticVarCompensator;
import com.powsybl.iidm.network.Terminal;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.util.Evaluable;

import java.util.Objects;

import static com.powsybl.openloadflow.util.EvaluableConstants.NAN;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class LfShuntImpl extends AbstractElement implements LfShunt {

    private final double b;

    private Evaluable q = NAN;

    private final String id;

    private final Terminal terminal;

    public LfShuntImpl(ShuntCompensator shuntCompensator, LfNetwork network) {
        super(network);
        Objects.requireNonNull(shuntCompensator);
        double nominalV = shuntCompensator.getTerminal().getVoltageLevel().getNominalV();
        double zb = nominalV * nominalV / PerUnit.SB;
        this.b = shuntCompensator.getB() * zb;
        this.id = shuntCompensator.getId();
        this.terminal = shuntCompensator.getTerminal();
    }

    public LfShuntImpl(StaticVarCompensator svc, double b, LfNetwork network) {
        super(network);
        double nominalV = svc.getTerminal().getVoltageLevel().getNominalV();
        double zb = nominalV * nominalV / PerUnit.SB;
        this.b = b * zb;
        this.id = svc.getId() + "_shunt";
        this.terminal = svc.getTerminal();
    }

    @Override
    public ElementType getType() {
        return ElementType.SHUNT_COMPENSATOR;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public double getB() {
        return b;
    }

    @Override
    public void setQ(Evaluable q) {
        this.q = Objects.requireNonNull(q);
    }

    @Override
    public Evaluable getQ() {
        return q;
    }

    @Override
    public void updateState() {
        terminal.setQ(q.eval() * PerUnit.SB);
    }
}
