package com.powsybl.openloadflow.network.util;

import com.powsybl.openloadflow.network.LfAcDcConverter;
import com.powsybl.openloadflow.network.LfDcBus;
import com.powsybl.openloadflow.network.LfVoltageSourceConverter;

public interface AcDcNetworkInitializer {
    double getReactivePower(LfVoltageSourceConverter converter);

    double getActivePower(LfAcDcConverter converter);

    double getMagnitude(LfDcBus dcBus);
}
