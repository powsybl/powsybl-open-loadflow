package com.powsybl.openloadflow.ac.newfiles;

import com.powsybl.iidm.network.VoltageSourceConverter;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.LfNetworkParameters;
import com.powsybl.openloadflow.network.impl.Ref;
import com.powsybl.openloadflow.util.PerUnit;

import java.util.Objects;

public class LfVoltageSourceConverterImpl extends AbstractLfAcDcConverter implements LfVoltageSourceConverter {

    private final Ref<VoltageSourceConverter> converterRef;

    protected boolean isVoltageRegulatorOn;

    protected double targetQ;

    int num = -1;

    public LfVoltageSourceConverterImpl(VoltageSourceConverter converter, LfNetwork network, LfDcNode dcNode1, LfDcNode dcNode2, LfBus bus1,
                                        LfNetworkParameters parameters) {
        super(converter, network, dcNode1, dcNode2, bus1);
        bus1.addConverter(this);
        this.converterRef = Ref.create(converter, parameters.isCacheEnabled());
        this.isVoltageRegulatorOn = converter.isVoltageRegulatorOn();
        if (isVoltageRegulatorOn) {
            this.targetVac = converter.getVoltageSetpoint() / bus1.getNominalV();
        } else {
            this.targetQ = converter.getReactivePowerSetpoint() / PerUnit.SB;
        }
    }

    public static LfVoltageSourceConverterImpl create(VoltageSourceConverter acDcConverter, LfNetwork network, LfDcNode dcNode1, LfDcNode dcNode2, LfBus bus1, LfNetworkParameters parameters) {
        Objects.requireNonNull(network);
        Objects.requireNonNull(acDcConverter);
        Objects.requireNonNull(dcNode1);
        Objects.requireNonNull(bus1);
        Objects.requireNonNull(parameters);
        return new LfVoltageSourceConverterImpl(acDcConverter, network, dcNode1, dcNode2, bus1, parameters);

    }

    VoltageSourceConverter getConverter() {
        return converterRef.get();
    }

    @Override
    public boolean isVoltageRegulatorOn() {
        return isVoltageRegulatorOn;
    }

    @Override
    public double getTargetQ() {
        return targetQ;
    }

    @Override
    public void setTargetQ(double q) {
        targetQ = q;
    }

    @Override
    public String getId() {
        return getConverter().getId();
    }

    @Override
    public int getNum() {
        return num;
    }

    @Override
    public void setNum(int num) {
        this.num = num;
    }
}
