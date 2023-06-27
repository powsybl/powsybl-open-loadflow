/**
 * Copyright (c) 2023, Jean-Baptiste Heyberger <jbheyberger at gmail.com> ,
 *                     Geoffroy Jamgotchian <geoffroy.jamgotchian at gmail.com>
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.extensions.iidm;

import com.powsybl.commons.extensions.AbstractExtensionAdder;
import com.powsybl.iidm.network.Load;
import com.powsybl.iidm.network.extensions.WindingConnectionType;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class LoadUnbalancedAdder extends AbstractExtensionAdder<Load, LoadUnbalanced> {

    private double deltaPa = 0.;
    private double deltaQa = 0.;
    private double deltaPb = 0.;
    private double deltaQb = 0.;
    private double deltaPc = 0.;
    private double deltaQc = 0.;
    private WindingConnectionType connectionType = WindingConnectionType.Y_GROUNDED;
    private LoadType loadType = LoadType.CONSTANT_POWER;

    public LoadUnbalancedAdder(Load load) {
        super(load);
    }

    @Override
    public Class<? super LoadUnbalanced> getExtensionClass() {
        return LoadUnbalanced.class;
    }

    @Override
    protected LoadUnbalanced createExtension(Load load) {
        return new LoadUnbalanced(load, connectionType, deltaPa, deltaQa, deltaPb, deltaQb, deltaPc, deltaQc, loadType);
    }

    public LoadUnbalancedAdder withPa(double deltaPa) {
        this.deltaPa = deltaPa;
        return this;
    }

    public LoadUnbalancedAdder withQa(double deltaQa) {
        this.deltaQa = deltaQa;
        return this;
    }

    public LoadUnbalancedAdder withPb(double deltaPb) {
        this.deltaPb = deltaPb;
        return this;
    }

    public LoadUnbalancedAdder withQb(double deltaQb) {
        this.deltaQb = deltaQb;
        return this;
    }

    public LoadUnbalancedAdder withPc(double deltaPc) {
        this.deltaPc = deltaPc;
        return this;
    }

    public LoadUnbalancedAdder withQc(double deltaQc) {
        this.deltaQc = deltaQc;
        return this;
    }

    public LoadUnbalancedAdder withConnectionType(WindingConnectionType windingConnectionType) {
        this.connectionType = windingConnectionType;
        return this;
    }

    public LoadUnbalancedAdder withLoadType(LoadType loadType) {
        this.loadType = loadType;
        return this;
    }

}
