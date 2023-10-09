package com.powsybl.openloadflow.network;

public class GeneratorReactivePowerControl extends ReactivePowerControl<LfBus> {
    private static final int PRIORITY = 0;

    public GeneratorReactivePowerControl(LfBranch controlledBranch, ControlledSide controlledSide, double targetValue) {
        super(targetValue, Type.GENERATOR, PRIORITY, controlledBranch, controlledSide);
    }

    @Override
    public void addControllerElement(LfBus controllerBus) {
        super.addControllerElement(controllerBus);
        controllerBus.setReactivePowerControl(this);
    }

}
