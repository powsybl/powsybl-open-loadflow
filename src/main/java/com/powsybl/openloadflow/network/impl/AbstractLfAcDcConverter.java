package com.powsybl.openloadflow.network.impl;
import com.powsybl.iidm.network.AcDcConverter;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.util.Evaluable;
import com.powsybl.openloadflow.util.PerUnit;

import java.util.List;

public abstract class AbstractLfAcDcConverter extends AbstractElement implements LfAcDcConverter {

    protected Evaluable calculatedPac;

    protected Evaluable calculatedQac;

    protected Evaluable calculatedIconv;

    protected final double targetP;

    protected double pAc;

    protected double qAc;

    protected double targetVac;

    protected final List<Double> lossFactors;

    protected double targetVdc;

    protected final AcDcConverter.ControlMode controlMode;

    protected final boolean isBipolar;

    protected final LfDcNode dcNode1;

    protected final LfDcNode dcNode2;

    protected final LfBus bus1;

    public AbstractLfAcDcConverter(AcDcConverter<?> converter, LfNetwork network, LfDcNode dcNode1, LfDcNode dcNode2, LfBus bus1) {
        super(network);
        this.dcNode1 = dcNode1;
        this.dcNode2 = dcNode2;
        dcNode1.addConverter(this);
        if (dcNode2 != null) {
            dcNode2.setNeutralPole(true);
            dcNode2.addConverter(this);
        }
        this.bus1 = bus1;
        this.lossFactors = List.of(converter.getIdleLoss(), converter.getSwitchingLoss(), converter.getResistiveLoss());
        this.controlMode = converter.getControlMode();
        this.targetP = converter.getTargetP() / PerUnit.SB;
        targetVdc = converter.getTargetVdc() / dcNode1.getNominalV();
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
        return pAc;
    }

    @Override
    public void setPac(double pac) {
        this.pAc = pac;
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

    @Override
    public double getQac() {
        return qAc;
    }

    @Override
    public void setQac(double qac) {
        this.qAc = qac;
    }

    @Override
    public Evaluable getCalculatedIconv() {
        return calculatedIconv;
    }

    @Override
    public void setCalculatedIconv(Evaluable iconv) {
        calculatedIconv = iconv;
    }

    @Override
    public Evaluable getCalculatedPac() {
        return calculatedPac;
    }

    @Override
    public void setCalculatedPac(Evaluable p) {
        calculatedPac = p;
    }

    @Override
    public Evaluable getCalculatedQac() {
        return calculatedQac;
    }

    @Override
    public void setCalculatedQac(Evaluable q) {
        calculatedQac = q;
    }
}
