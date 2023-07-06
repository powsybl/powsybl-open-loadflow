package com.powsybl.openloadflow.network.extensions.iidm;

import com.powsybl.commons.extensions.AbstractExtension;
import com.powsybl.iidm.network.Load;

public class LoadAsymmetrical2 extends AbstractExtension<Load> {

    // This class is used as an extension of a "classical" balanced direct load
    private final LoadType loadType;

    public static final String NAME = "loadAsymmetrical2";

    @Override
    public String getName() {
        return NAME;
    }

    public LoadAsymmetrical2(Load load, LoadType loadType) {
        super(load);
        this.loadType = loadType;
    }

    public LoadType getLoadType() {
        return loadType;
    }
}
