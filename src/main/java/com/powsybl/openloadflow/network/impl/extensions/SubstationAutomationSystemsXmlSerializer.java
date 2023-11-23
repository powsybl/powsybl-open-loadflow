/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.impl.extensions;

import com.google.auto.service.AutoService;
import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.extensions.AbstractExtensionSerDe;
import com.powsybl.commons.extensions.ExtensionSerDe;
import com.powsybl.commons.io.DeserializerContext;
import com.powsybl.commons.io.SerializerContext;
import com.powsybl.commons.io.TreeDataReader;
import com.powsybl.commons.io.TreeDataWriter;
import com.powsybl.iidm.network.Substation;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
@AutoService(ExtensionSerDe.class)
public class SubstationAutomationSystemsXmlSerializer extends AbstractExtensionSerDe<Substation, SubstationAutomationSystems> {

    public SubstationAutomationSystemsXmlSerializer() {
        super(SubstationAutomationSystems.NAME, "network", SubstationAutomationSystems.class, "substationAutomationSystems.xsd",
                "http://www.powsybl.org/schema/iidm/ext/substation_automation_systems/1_0", "sas");
    }

    @Override
    public void write(SubstationAutomationSystems systems, SerializerContext serializerContext) {
        for (var oms : systems.getOverloadManagementSystems()) {
            TreeDataWriter writer = serializerContext.getWriter();
            writer.writeStartNode(getNamespaceUri(), "overloadManagementSystem");
            writer.writeBooleanAttribute("enabled", oms.isEnabled());
            writer.writeStringAttribute("monitoredLineId", oms.getMonitoredLineId());
            writer.writeDoubleAttribute("threshold", oms.getThreshold());
            writer.writeStringAttribute("switchIdToOperate", oms.getSwitchIdToOperate());
            writer.writeBooleanAttribute("switchOpen", oms.isSwitchOpen());
            writer.writeEndNode();
        }
    }

    @Override
    public SubstationAutomationSystems read(Substation substation, DeserializerContext deserializerContext) {
        SubstationAutomationSystemsAdder adder = substation.newExtension(SubstationAutomationSystemsAdder.class);
        TreeDataReader reader = deserializerContext.getReader();
        reader.readChildNodes(elementName -> {
            if (!elementName.equals("overloadManagementSystem")) {
                throw new PowsyblException("Unknown element name '" + elementName + "' in '" + SubstationAutomationSystems.NAME + "'");
            }
            boolean enabled = reader.readBooleanAttribute("enabled");
            String monitoredLineId = reader.readStringAttribute("monitoredLineId");
            double threshold = reader.readDoubleAttribute("threshold");
            String switchIdToOperate = reader.readStringAttribute("switchIdToOperate");
            boolean switchOpen = reader.readBooleanAttribute("switchOpen");
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
