/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.impl.extensions;

import com.google.auto.service.AutoService;
import com.powsybl.commons.extensions.AbstractExtensionXmlSerializer;
import com.powsybl.commons.extensions.ExtensionXmlSerializer;
import com.powsybl.commons.xml.XmlReaderContext;
import com.powsybl.commons.xml.XmlUtil;
import com.powsybl.commons.xml.XmlWriterContext;
import com.powsybl.iidm.network.StaticVarCompensator;

import javax.xml.stream.XMLStreamException;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
@AutoService(ExtensionXmlSerializer.class)
public class VoltageSlopeReactivePowerControlXmlSerializer extends AbstractExtensionXmlSerializer<StaticVarCompensator, VoltageSlopeReactivePowerControl> {

    public VoltageSlopeReactivePowerControlXmlSerializer() {
        super("voltageSlopeReactivePowerControl", "network", VoltageSlopeReactivePowerControl.class, false, "voltageSlopeReactivePowerControl.xsd",
                "http://www.powsybl.org/schema/iidm/ext/voltage_slope_reactive_power_control/1_0", "sbrpc");
    }

    @Override
    public void write(VoltageSlopeReactivePowerControl control, XmlWriterContext context) throws XMLStreamException {
        XmlUtil.writeDouble("slope", control.getSlope(), context.getExtensionsWriter());
    }

    @Override
    public VoltageSlopeReactivePowerControl read(StaticVarCompensator svc, XmlReaderContext context) {
        double slope = XmlUtil.readDoubleAttribute(context.getReader(), "slope");
        svc.newExtension(VoltageSlopeReactivePowerControlAdder.class)
                .withSlope(slope)
                .add();
        return svc.getExtension(VoltageSlopeReactivePowerControl.class);
    }
}
