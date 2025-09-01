package com.powsybl.openloadflow.ac.networktest;

import com.powsybl.openloadflow.network.AbstractElement;
import com.powsybl.openloadflow.network.ElementType;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.PiModel;

public abstract class AbstractLfDcLine extends AbstractElement implements LfDcLine {

    protected final LfDcNode dcNode1;

    protected final LfDcNode dcNode2;

    private double r = Double.NaN;

    protected AbstractLfDcLine(LfNetwork network, LfDcNode dcNode1, LfDcNode dcNode2, double r) {
        super(network);
        this.dcNode1 = dcNode1;
        dcNode1.addLfDcLine(this);
        this.dcNode2 = dcNode2;
        dcNode2.addLfDcLine(this);
        this.r = r;
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
    public ElementType getType() {
        return ElementType.DC_LINE;
    }

    @Override
    public double getR() {
        return r;
    }

    @Override
    public PiModel getPiModel() {
        return null;
    }
}
