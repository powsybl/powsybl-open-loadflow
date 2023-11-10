/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
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
import com.powsybl.iidm.network.Substation;

import javax.xml.stream.XMLStreamException;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
@AutoService(ExtensionXmlSerializer.class)
public class SubstationAutomationSystemsXmlSerializer extends AbstractExtensionXmlSerializer<Substation, SubstationAutomationSystems> {

    public SubstationAutomationSystemsXmlSerializer() {
        super(SubstationAutomationSystems.NAME, "network", SubstationAutomationSystems.class, true, "substationAutomationSystems.xsd",
                "http://www.powsybl.org/schema/iidm/ext/substation_automation_systems/1_0", "sas");
    }

    @Override
    public void write(SubstationAutomationSystems systems, XmlWriterContext context) throws XMLStreamException {
        for (var oms : systems.getOverloadManagementSystems()) {
            context.getWriter().writeEmptyElement(getNamespaceUri(), "overloadManagementSystem");
            context.getWriter().writeAttribute("enabled", Boolean.toString(oms.isEnabled()));
            context.getWriter().writeAttribute("monitoredLineId", oms.getMonitoredLineId());
            XmlUtil.writeDouble("threshold", oms.getThreshold(), context.getWriter());
            context.getWriter().writeAttribute("switchIdToOperate", oms.getSwitchIdToOperate());
            context.getWriter().writeAttribute("switchOpen", Boolean.toString(oms.isSwitchOpen()));
        }
    }

    @Override
    public SubstationAutomationSystems read(Substation substation, XmlReaderContext context) throws XMLStreamException {
        SubstationAutomationSystemsAdder adder = substation.newExtension(SubstationAutomationSystemsAdder.class);
        XmlUtil.readUntilEndElement(getExtensionName(), context.getReader(), () -> {
            boolean enabled = XmlUtil.readBoolAttribute(context.getReader(), "enabled");
            String monitoredLineId = context.getReader().getAttributeValue(null, "monitoredLineId");
            double threshold = XmlUtil.readDoubleAttribute(context.getReader(), "threshold");
            String switchIdToOperate = context.getReader().getAttributeValue(null, "switchIdToOperate");
            boolean switchOpen = XmlUtil.readBoolAttribute(context.getReader(), "switchOpen");
            adder.newOverloadManagementSystem()
                    .withEnabled(enabled)
                    .withMonitoredLineId(monitoredLineId)
                    .withThreshold(threshold)
                    .withSwitchIdToOperate(switchIdToOperate)
                    .withSwitchOpen(switchOpen)
                    .add();
        });
        return adder.add();
    }
}
