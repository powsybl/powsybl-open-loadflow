package com.powsybl.openloadflow.ac.networktest;

import com.powsybl.iidm.network.AcDcConverter;
import com.powsybl.iidm.network.VoltageSourceConverter;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.impl.Ref;
import com.powsybl.openloadflow.util.PerUnit;

import java.util.List;
import java.util.Objects;

public class LfAcDcConverterImpl extends AbstractLfAcDcConverter {

    private final Ref<AcDcConverter<?>> converterRef;

    protected double targetVdc;

    protected double targetVac;

    protected List<Double> lossFactors;

    protected ConverterStationMode converterMode;

    protected AcDcConverter.ControlMode controlMode;

    protected boolean isVoltageRegulatorOn = false;

    int num = -1;

    public LfAcDcConverterImpl(AcDcConverter<?> converter, LfNetwork network, LfDcNode dcNode1, LfDcNode dcNode2, LfBus bus1, LfBus bus2,
                               LfNetworkParameters parameters) {
        super(network, dcNode1, dcNode2, bus1, bus2);
        this.converterRef = Ref.create(converter, parameters.isCacheEnabled());
        this.lossFactors = List.of(converter.getIdleLoss(), converter.getSwitchingLoss(), converter.getResistiveLoss());
        this.controlMode = converter.getControlMode();
        if(converter instanceof VoltageSourceConverter){
            this.isVoltageRegulatorOn = ((VoltageSourceConverter) converter).isVoltageRegulatorOn();
        }
        this.targetP = converter.getTargetP()/PerUnit.SB;
        if (targetP > 0) {
            converterMode = ConverterStationMode.INVERTER;
        } else {
            converterMode = ConverterStationMode.RECTIFIER;
        }

        if (controlMode == AcDcConverter.ControlMode.V_DC) {
            targetVdc = converter.getTargetVdc()/ dcNode1.getNominalV();
        }
    }

    public static LfAcDcConverterImpl create(AcDcConverter<?> acDcConverter, LfNetwork network, LfDcNode dcNode1, LfDcNode dcNode2, LfBus bus1, LfBus bus2, LfNetworkParameters parameters) {
        Objects.requireNonNull(acDcConverter);
        Objects.requireNonNull(network);
        Objects.requireNonNull(parameters);
        return new LfAcDcConverterImpl(acDcConverter, network, dcNode1, dcNode2, bus1, bus2, parameters);

    }

    AcDcConverter<?> getConverter() {
        return converterRef.get();
    }

    @Override
    public String getId() {
        return getConverter().getId();
    }

    @Override
    public void setNum(int num) {
        this.num = num;
    }

    @Override
    public int getNum() {
        return num;
    }

    @Override
    public List<Double> getLossFactors() {
        return lossFactors;
    }

    @Override
    public ConverterStationMode getConverterMode() {
        return converterMode;
    }

    @Override
    public AcDcConverter.ControlMode getControlMode() {
        return controlMode;
    }

    @Override
    public boolean isVoltageRegulatorOn() {
        return isVoltageRegulatorOn;
    }

    @Override
    public double getTargetVdcControl() {
        return targetVdc;
    }
}
