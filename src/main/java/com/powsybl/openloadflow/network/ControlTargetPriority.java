package com.powsybl.openloadflow.network;

public enum ControlTargetPriority {
    GENERATOR(0),
    TRANSFORMER(1),
    SHUNT(2);

    private final int priority;

    ControlTargetPriority(int priority) {
        this.priority = priority;
    }

    public int getPriority() {
        return priority;
    }
}
