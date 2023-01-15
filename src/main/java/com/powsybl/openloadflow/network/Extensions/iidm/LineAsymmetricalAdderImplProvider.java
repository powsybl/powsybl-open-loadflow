package com.powsybl.openloadflow.network.Extensions.iidm;

import com.google.auto.service.AutoService;
import com.powsybl.commons.extensions.ExtensionAdderProvider;
import com.powsybl.iidm.network.Line;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
@AutoService(ExtensionAdderProvider.class)
public class LineAsymmetricalAdderImplProvider implements ExtensionAdderProvider<Line, LineAsymmetrical, LineAsymmetricalAdder> {

    @Override
    public String getImplementationName() {
        return "Default";
    }

    @Override
    public String getExtensionName() {
        return LineAsymmetrical.NAME;
    }

    @Override
    public Class<LineAsymmetricalAdder> getAdderClass() {
        return LineAsymmetricalAdder.class;
    }

    @Override
    public LineAsymmetricalAdder newAdder(Line line) {
        return new LineAsymmetricalAdder(line);
    }

}
