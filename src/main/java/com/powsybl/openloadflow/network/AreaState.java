package com.powsybl.openloadflow.network;

public class AreaState extends ElementState<LfArea> {

    double interchangeTarget;

    public AreaState(LfArea element) {
        super(element);
        this.interchangeTarget = element.getInterchangeTarget();
    }

    public static AreaState save(LfArea area) {
        return new AreaState(area);
    }

    @Override
    public void restore() {
        element.setInterchangeTarget(interchangeTarget);
    }
}
