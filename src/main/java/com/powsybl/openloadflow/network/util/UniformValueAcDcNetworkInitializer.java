package com.powsybl.openloadflow.network.util;

import com.powsybl.openloadflow.network.LfAcDcConverter;
import com.powsybl.openloadflow.network.LfDcNode;
import com.powsybl.openloadflow.network.LfVoltageSourceConverter;

public class UniformValueAcDcNetworkInitializer implements AcDcNetworkInitializer {

    @Override
    public double getMagnitude(LfDcNode dcNode) {
        if (dcNode.isNeutralPole()) {
            return 0.0;
        }
        return 1.0;
    }

    @Override
    public double getReactivePower(LfVoltageSourceConverter converter) {
        return 1.0;
    }

    @Override
    public double getActivePower(LfAcDcConverter converter) {
        return 1.0;
    }
}
