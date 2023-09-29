package com.powsybl.openloadflow.lf.outerloop;

public class DistributedSlackContextData {
    private double distributedActivePower = 0.0;

    public double getDistributedActivePower() {
        return distributedActivePower;
    }

    public void addDistributedActivePower(double addedDistributedActivePower) {
        distributedActivePower += addedDistributedActivePower;
    }
}
