package com.powsybl.openloadflow.network.extensions.iidm;

import com.powsybl.commons.extensions.AbstractExtensionAdder;
import com.powsybl.iidm.network.Load;

public class LoadAsymmetrical2Adder extends AbstractExtensionAdder<Load, LoadAsymmetrical2> {

    //private WindingConnectionType connectionType = WindingConnectionType.Y_GROUNDED;
    private LoadType loadType = LoadType.CONSTANT_POWER;

    public LoadAsymmetrical2Adder(Load load) {
        super(load);
    }

    @Override
    public Class<? super LoadAsymmetrical2> getExtensionClass() {
        return LoadAsymmetrical2.class;
    }

    @Override
    protected LoadAsymmetrical2 createExtension(Load load) {
        return new LoadAsymmetrical2(load, loadType);
    }

    public LoadAsymmetrical2Adder withLoadType(LoadType loadType) {
        this.loadType = loadType;
        return this;
    }
}
