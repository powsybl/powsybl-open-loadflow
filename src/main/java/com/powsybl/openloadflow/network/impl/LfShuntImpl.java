/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.iidm.network.ShuntCompensator;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.util.Evaluable;

import java.util.List;
import java.util.Objects;

import static com.powsybl.openloadflow.util.EvaluableConstants.NAN;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class LfShuntImpl extends AbstractElement implements LfShunt {

    private final List<ShuntCompensator> shuntCompensators;

    private final double b;

    private Evaluable q = NAN;

    protected LfBus bus;

    public LfShuntImpl(List<ShuntCompensator> shuntCompensators, LfNetwork network) {
        super(network);
        if (shuntCompensators.isEmpty()) {
            throw new IllegalArgumentException("Empty shunt compensator list");
        }
        this.shuntCompensators = Objects.requireNonNull(shuntCompensators);
        double nominalV = shuntCompensators.get(0).getTerminal().getVoltageLevel().getNominalV(); // has to be the same for all shunts
        double zb = nominalV * nominalV / PerUnit.SB;
        b = shuntCompensators.stream()
                .mapToDouble(ShuntCompensator::getB)
                .map(aB -> aB * zb)
                .sum();
    }

    @Override
    public ElementType getType() {
        return ElementType.SHUNT_COMPENSATOR;
    }

    @Override
    public String getId() {
        return shuntCompensators.get(0).getTerminal().getVoltageLevel().getId() + "_shunt_compensators";
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
    public void setBus(LfBus bus) {
        this.bus = bus;
    }

    @Override
    public void updateState() {
        double vSquare = bus.getV() * bus.getV() * bus.getNominalV() * bus.getNominalV();
        System.out.println(vSquare);
        for (ShuntCompensator sc : shuntCompensators) {
            sc.getTerminal().setQ(-sc.getB() * vSquare);
        }
    }
}
