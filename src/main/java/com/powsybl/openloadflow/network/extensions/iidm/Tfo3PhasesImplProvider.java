package com.powsybl.openloadflow.network.extensions.iidm;

import com.google.auto.service.AutoService;
import com.powsybl.commons.extensions.ExtensionAdderProvider;
import com.powsybl.iidm.network.TwoWindingsTransformer;

@AutoService(ExtensionAdderProvider.class)
public class Tfo3PhasesImplProvider implements ExtensionAdderProvider<TwoWindingsTransformer, Tfo3Phases, Tfo3PhasesAdder> {
    @Override
    public String getImplementationName() {
        return "Default";
    }

    @Override
    public String getExtensionName() {
        return Tfo3Phases.NAME;
    }

    @Override
    public Class<Tfo3PhasesAdder> getAdderClass() {
        return Tfo3PhasesAdder.class;
    }

    @Override
    public Tfo3PhasesAdder newAdder(TwoWindingsTransformer t2w) {
        return new Tfo3PhasesAdder(t2w);
    }
}
