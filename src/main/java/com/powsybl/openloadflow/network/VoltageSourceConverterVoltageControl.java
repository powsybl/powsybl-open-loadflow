package com.powsybl.openloadflow.network;

public class VoltageSourceConverterVoltageControl extends VoltageControl<LfBus> {

    public VoltageSourceConverterVoltageControl(LfBus controlledBus, int targetPriority, double targetValue) {
        super(targetValue, Type.VOLTAGE_SOURCE_CONVERTER, targetPriority, controlledBus);
    }

}
