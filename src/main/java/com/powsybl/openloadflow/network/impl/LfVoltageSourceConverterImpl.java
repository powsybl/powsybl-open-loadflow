/**
 * Copyright (c) 2025, SuperGrid Institute (http://www.supergrid-institute.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.iidm.network.VoltageSourceConverter;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.util.PerUnit;

import java.util.Objects;

/**
 * @author Denis Bonnand {@literal <denis.bonnand at supergrid-institute.com>}
 */
public class LfVoltageSourceConverterImpl extends AbstractLfAcDcConverter implements LfVoltageSourceConverter {

    private final Ref<VoltageSourceConverter> converterRef;

    protected final boolean isVoltageRegulatorOn;

    protected double targetQ;

    public LfVoltageSourceConverterImpl(VoltageSourceConverter converter, LfNetwork network, LfDcNode dcNode1, LfDcNode dcNode2, LfBus bus1,
                                        LfNetworkParameters parameters) {
        super(converter, network, dcNode1, dcNode2, bus1);
        bus1.addConverter(this);
        this.converterRef = Ref.create(converter, parameters.isCacheEnabled());
        this.isVoltageRegulatorOn = converter.isVoltageRegulatorOn();
        if (isVoltageRegulatorOn) {
            this.targetVac = converter.getVoltageSetpoint() / bus1.getNominalV();
        } else {
            this.targetQ = converter.getReactivePowerSetpoint() / PerUnit.SB;
        }
    }

    public static LfVoltageSourceConverterImpl create(VoltageSourceConverter acDcConverter, LfNetwork network, LfDcNode dcNode1, LfDcNode dcNode2, LfBus bus1, LfNetworkParameters parameters) {
        Objects.requireNonNull(network);
        Objects.requireNonNull(acDcConverter);
        Objects.requireNonNull(dcNode1);
        Objects.requireNonNull(dcNode2);
        Objects.requireNonNull(bus1);
        Objects.requireNonNull(parameters);
        return new LfVoltageSourceConverterImpl(acDcConverter, network, dcNode1, dcNode2, bus1, parameters);

    }

    VoltageSourceConverter getConverter() {
        return converterRef.get();
    }

    @Override
    public boolean isVoltageRegulatorOn() {
        return isVoltageRegulatorOn;
    }

    @Override
    public double getTargetQ() {
        return targetQ;
    }

    @Override
    public String getId() {
        return getConverter().getId();
    }

    @Override
    public void updateState(LfNetworkStateUpdateParameters parameters, LfNetworkUpdateReport updateReport) {
        if (isDisabled()) {
            updateFlows(Double.NaN, Double.NaN, Double.NaN, Double.NaN);
        } else {
            updateFlows(calculatedIconv1.eval(), calculatedIconv2.eval(), calculatedPac.eval(), calculatedQac.eval());
        }
    }

    @Override
    public void updateFlows(double iConv1, double iConv2, double pAc, double qAc) {
        var converter = getConverter();
        double v1 = converter.getDcTerminal1().getDcNode().getV() / dcNode1.getNominalV();
        double v2 = converter.getDcTerminal2().getDcNode().getV() / dcNode2.getNominalV();
        converter.getDcTerminal1().setI(iConv1 * PerUnit.ib(dcNode1.getNominalV()));
        converter.getDcTerminal2().setI(iConv2 * PerUnit.ib(dcNode2.getNominalV()));
        converter.getDcTerminal1().setP(iConv1 * v1 * PerUnit.SB);
        converter.getDcTerminal2().setP(iConv2 * v2 * PerUnit.SB);
        converter.getTerminal1().setP(pAc * PerUnit.SB);
        converter.getTerminal1().setQ(qAc * PerUnit.SB);
    }
}
