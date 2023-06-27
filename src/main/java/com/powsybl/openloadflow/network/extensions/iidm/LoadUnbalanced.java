/**
 * Copyright (c) 2023, Jean-Baptiste Heyberger <jbheyberger at gmail.com> ,
 *                     Geoffroy Jamgotchian <geoffroy.jamgotchian at gmail.com>
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.extensions.iidm;

import com.powsybl.commons.extensions.AbstractExtension;
import com.powsybl.iidm.network.Load;
import com.powsybl.iidm.network.extensions.WindingConnectionType;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class LoadUnbalanced extends AbstractExtension<Load> {

    // This class is used as an extension of a "classical" balanced direct load
    // we store here the deltas of power that will build the unbalanced loads. The reference is the positive sequence load stored in "Load"
    private final double deltaPa;
    private final double deltaQa;
    private final double deltaPb;
    private final double deltaQb;
    private final double deltaPc;
    private final double deltaQc;
    private final WindingConnectionType connectionType;
    private final LoadType loadType;

    public static final String NAME = "loadUnbalanced";

    @Override
    public String getName() {
        return NAME;
    }

    public LoadUnbalanced(Load load, WindingConnectionType loadConnectionType, double deltaPa, double deltaQa, double deltaPb, double deltaQb, double deltaPc, double deltaQc, LoadType loadType) {
        super(load);
        this.deltaPa = deltaPa;
        this.deltaPb = deltaPb;
        this.deltaPc = deltaPc;
        this.deltaQa = deltaQa;
        this.deltaQb = deltaQb;
        this.deltaQc = deltaQc;
        this.connectionType = loadConnectionType;
        this.loadType = loadType;
    }

    public double getDeltaPa() {
        return deltaPa;
    }

    public double getDeltaPb() {
        return deltaPb;
    }

    public double getDeltaPc() {
        return deltaPc;
    }

    public double getDeltaQa() {
        return deltaQa;
    }

    public double getDeltaQb() {
        return deltaQb;
    }

    public double getDeltaQc() {
        return deltaQc;
    }

    public WindingConnectionType getConnectionType() {
        return connectionType;
    }

    public LoadType getLoadType() {
        return loadType;
    }
}
