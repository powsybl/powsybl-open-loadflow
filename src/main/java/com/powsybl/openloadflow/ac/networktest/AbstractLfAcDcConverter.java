package com.powsybl.openloadflow.ac.networktest;
import com.powsybl.iidm.network.AcDcConverter;
import com.powsybl.openloadflow.network.AbstractElement;
import com.powsybl.openloadflow.network.ElementType;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.util.PerUnit;

import java.util.List;

public abstract class AbstractLfAcDcConverter extends AbstractElement implements LfAcDcConverter {

    protected double targetP;

    protected double pdc;

    protected double targetVac;

    protected List<Double> lossFactors;

    protected double iConv;

    protected double targetVdc;

    protected AcDcConverter.ControlMode controlMode;

    protected boolean isVoltageRegulatorOn = false;

    protected boolean isBipolar;

    LfDcNode dcNode1;

    LfDcNode dcNode2;

    LfBus bus1;

    public AbstractLfAcDcConverter(AcDcConverter<?> converter, LfNetwork network, LfDcNode dcNode1, LfDcNode dcNode2, LfBus bus1) {
        super(network);
        this.dcNode1 = dcNode1;
        this.dcNode2 = dcNode2;
        this.bus1 = bus1;
        this.lossFactors = List.of(converter.getIdleLoss(), converter.getSwitchingLoss(), converter.getResistiveLoss());
        this.controlMode = converter.getControlMode();
        this.targetP = converter.getTargetP() / PerUnit.SB;
        if (controlMode == AcDcConverter.ControlMode.V_DC) {
            targetVdc = converter.getTargetVdc() / dcNode1.getNominalV();
        }
        isBipolar = converter.getDcTerminal2().isConnected();
    }

    @Override
    public LfBus getBus1() {
        return bus1;
    }


    @Override
    public LfDcNode getDcNode1() {
        return dcNode1;
    }

    @Override
    public LfDcNode getDcNode2() {
        return dcNode2;
    }

    @Override
    public double getTargetP() {
        return targetP;
    }

    @Override
    public void setTargetP(double p) {
        targetP = p;
    }

    @Override
    public double getTargetVac() {
        return targetVac;
    }

    @Override
    public List<Double> getLossFactors() {
        return lossFactors;
    }

    @Override
    public ElementType getType() {
        return ElementType.CONVERTER;
    }

    @Override
    public double getPac() {
        return pdc;
    }

    @Override
    public void setPac(double pdc) {
        this.pdc = pdc;
    }

    @Override
    public double getIConv() {
        return iConv;
    }

    @Override
    public void setIConv(double iConv) {
        this.iConv = iConv;
    }

    @Override
    public int getNum() {
        return num;
    }

    @Override
    public void setNum(int num) {
        this.num = num;
    }

    @Override
    public AcDcConverter.ControlMode getControlMode() {
        return controlMode;
    }

    @Override
    public double getTargetVdc() {
        return targetVdc;
    }

    @Override
    public boolean isBipolar() {
        return isBipolar;
    }
}
