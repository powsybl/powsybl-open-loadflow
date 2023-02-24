package com.powsybl.openloadflow.network.Extensions.iidm;

import com.google.auto.service.AutoService;
import com.powsybl.commons.extensions.ExtensionAdderProvider;
import com.powsybl.iidm.network.Load;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
@AutoService(ExtensionAdderProvider.class)
public class LoadUnbalancedAdderImplProvider implements ExtensionAdderProvider<Load, LoadUnbalanced, LoadUnbalancedAdder> {

    @Override
    public String getImplementationName() {
        return "Default";
    }

    @Override
    public String getExtensionName() {
        return LoadUnbalanced.NAME;
    }

    @Override
    public Class<LoadUnbalancedAdder> getAdderClass() {
        return LoadUnbalancedAdder.class;
    }

    @Override
    public LoadUnbalancedAdder newAdder(Load load) {
        return new LoadUnbalancedAdder(load);
    }
}
