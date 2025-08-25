package com.powsybl.openloadflow.ac.networktest;
import com.powsybl.openloadflow.network.AbstractElement;
import com.powsybl.openloadflow.network.ElementType;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;

import java.util.List;

public abstract class AbstractLfAcDcConverter extends AbstractElement implements LfAcDcConverter {
    protected double targetP;

    protected double pdc;

    protected double targetVac;

    protected List<Double> lossFactors;

    protected double iConv;

    LfDcNode dcNode1;

    LfDcNode dcNode2;

    LfBus bus1;

    LfBus bus2;

    public AbstractLfAcDcConverter(LfNetwork network, LfDcNode dcNode1, LfDcNode dcNode2, LfBus bus1, LfBus bus2) {
        super(network);
        this.dcNode1 = dcNode1;
        this.dcNode2 = dcNode2;
        this.bus1 = bus1;
        this.bus2 = bus2;
    }


    @Override
    public LfBus getBus1() {
        return bus1;
    }

    @Override
    public LfBus getBus2() {
        return bus2;
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
}
