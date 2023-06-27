package com.powsybl.openloadflow.network.extensions.iidm;

import com.google.auto.service.AutoService;
import com.powsybl.commons.extensions.ExtensionAdderProvider;
import com.powsybl.iidm.network.Bus;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
@AutoService(ExtensionAdderProvider.class)
public class BusAsymmetricalAdderImplProvider implements ExtensionAdderProvider<Bus, BusAsymmetrical, BusAsymmetricalAdder> {

    @Override
    public String getImplementationName() {
        return "Default";
    }

    @Override
    public String getExtensionName() {
        return LineAsymmetrical.NAME;
    }

    @Override
    public Class<BusAsymmetricalAdder> getAdderClass() {
        return BusAsymmetricalAdder.class;
    }

    @Override
    public BusAsymmetricalAdder newAdder(Bus bus) {
        return new BusAsymmetricalAdder(bus);
    }

}
