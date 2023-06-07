package com.powsybl.openloadflow.dc.woodburry;

public abstract class NetworkShift {

    public enum ShiftType {
        BRANCH_ADMITTANCE_SHIFT
    }

    protected final String elementId;

    protected NetworkShift(String elementId) {
        this.elementId = elementId;
    }

    public abstract ShiftType getType();

    public final String getElementId() {
        return elementId;
    }
}
