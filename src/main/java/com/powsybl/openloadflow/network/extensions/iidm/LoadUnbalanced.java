package com.powsybl.openloadflow.network.extensions.iidm;

import com.powsybl.commons.extensions.AbstractExtension;
import com.powsybl.iidm.network.Load;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class LoadUnbalanced extends AbstractExtension<Load> {

    // This class is used as an extension of a "classical" balanced direct load
    // we store here the deltas of power that will build the unblalanced loads. The reference is the positive sequence load stored in "Load"
    private final double deltaPa;
    private final double deltaQa;
    private final double deltaPb;
    private final double deltaQb;
    private final double deltaPc;
    private final double deltaQc;

    public static final String NAME = "loadUnbalanced";

    @Override
    public String getName() {
        return NAME;
    }

    public LoadUnbalanced(Load load, double deltaPa, double deltaQa, double deltaPb, double deltaQb, double deltaPc, double deltaQc) {
        super(load);
        this.deltaPa = deltaPa;
        this.deltaPb = deltaPb;
        this.deltaPc = deltaPc;
        this.deltaQa = deltaQa;
        this.deltaQb = deltaQb;
        this.deltaQc = deltaQc;
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
}
