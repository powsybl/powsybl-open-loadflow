package com.powsybl.openloadflow.ac.networktest;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.util.Evaluable;

import java.util.List;

public abstract class AbstractLfAcDcConverter extends AbstractElement implements LfAcDcConverter {
    LfDcNode dcNode1;

    LfDcNode dcNode2;

    LfBus bus1;

    LfBus bus2;

    protected double targetP;


    protected double P;

    protected double targetVac;

    protected List<Double> lossFactors;

    protected Evaluable pAc;

    public AbstractLfAcDcConverter(LfNetwork network, LfDcNode dcNode1, LfDcNode dcNode2, LfBus bus1, LfBus bus2) {
        super(network);
        this.dcNode1 =dcNode1;
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
    public void setTargetP(double p) {
        targetP = p;
    }

    @Override
    public void setPac(Evaluable p) {
        pAc = p;
    }
}
