package com.powsybl.openloadflow.network.util;

import com.powsybl.commons.PowsyblException;
import com.powsybl.openloadflow.network.LfAcDcConverter;
import com.powsybl.openloadflow.network.LfDcNode;
import com.powsybl.openloadflow.network.LfVoltageSourceConverter;

public class PreviousValueAcDcNetworkInitializer implements AcDcNetworkInitializer {

    private final UniformValueAcDcNetworkInitializer defaultVoltageInitializer = new UniformValueAcDcNetworkInitializer();

    private final boolean defaultToUniformValue;

    public PreviousValueAcDcNetworkInitializer() {
        this(false);
    }

    public PreviousValueAcDcNetworkInitializer(boolean defaultToUniformValue) {
        this.defaultToUniformValue = defaultToUniformValue;
    }

    @Override
    public double getReactivePower(LfVoltageSourceConverter converter) {
        double q = converter.getQac();
        if (Double.isNaN(q)) {
            if (defaultToUniformValue) {
                return defaultVoltageInitializer.getReactivePower(converter);
            } else {
                throw new PowsyblException("Reactive Power is undefined for converter '" + converter.getId() + "'");
            }
        }
        return q;
    }

    @Override
    public double getActivePower(LfAcDcConverter converter) {
        double p = converter.getPac();
        if (Double.isNaN(p)) {
            if (defaultToUniformValue) {
                return defaultVoltageInitializer.getActivePower(converter);
            } else {
                throw new PowsyblException("Active Power is undefined for converter '" + converter.getId() + "'");
            }
        }
        return p;
    }

    @Override
    public double getMagnitude(LfDcNode dcNode) {
        double v = dcNode.getV();
        if (Double.isNaN(v)) {
            if (defaultToUniformValue) {
                return defaultVoltageInitializer.getMagnitude(dcNode);
            } else {
                throw new PowsyblException("Voltage is undefined for dcNode '" + dcNode.getId() + "'");
            }
        }
        return v;
    }
}
