package com.powsybl.openloadflow.network;

public class AcDcConverterVoltageControl extends VoltageControl<LfBus> {

    public AcDcConverterVoltageControl(LfBus controlledBus, int targetPriority, double targetValue) {
        super(targetValue, Type.AC_DC_CONVERTER, targetPriority, controlledBus);
    }

}
