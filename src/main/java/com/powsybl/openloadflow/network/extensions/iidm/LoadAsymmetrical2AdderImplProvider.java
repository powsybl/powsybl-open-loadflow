package com.powsybl.openloadflow.network.extensions.iidm;

import com.google.auto.service.AutoService;
import com.powsybl.commons.extensions.ExtensionAdderProvider;
import com.powsybl.iidm.network.Load;

@AutoService(ExtensionAdderProvider.class)
public class LoadAsymmetrical2AdderImplProvider implements ExtensionAdderProvider<Load, LoadAsymmetrical2, LoadAsymmetrical2Adder> {

    @Override
    public String getImplementationName() {
        return "Default";
    }

    @Override
    public String getExtensionName() {
        return LoadAsymmetrical2.NAME;
    }

    @Override
    public Class<LoadAsymmetrical2Adder> getAdderClass() {
        return LoadAsymmetrical2Adder.class;
    }

    @Override
    public LoadAsymmetrical2Adder newAdder(Load load) {
        return new LoadAsymmetrical2Adder(load);
    }

}
