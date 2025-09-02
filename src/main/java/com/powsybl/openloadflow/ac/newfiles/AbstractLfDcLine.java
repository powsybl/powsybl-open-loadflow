package com.powsybl.openloadflow.ac.newfiles;

import com.powsybl.openloadflow.network.AbstractElement;
import com.powsybl.openloadflow.network.ElementType;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.PiModel;
import com.powsybl.openloadflow.util.Evaluable;

import java.util.Objects;

import static com.powsybl.openloadflow.util.EvaluableConstants.NAN;

public abstract class AbstractLfDcLine extends AbstractElement implements LfDcLine {

    protected final LfDcNode dcNode1;

    protected final LfDcNode dcNode2;

    private double r = Double.NaN;

    protected Evaluable i1 = NAN;

    protected Evaluable i2 = NAN;

    protected Evaluable p1 = NAN;

    protected Evaluable p2 = NAN;

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
        //TODO : find a better way to avoid PiModel in DcLine Equation Terms
        return null;
    }

    @Override
    public void setI1(Evaluable i1) {
        this.i1 = Objects.requireNonNull(i1);
    }

    @Override
    public Evaluable getI1() {
        return i1;
    }

    @Override
    public void setI2(Evaluable i2) {
        this.i2 = Objects.requireNonNull(i2);
    }

    @Override
    public Evaluable getI2() {
        return i2;
    }

    @Override
    public void setP1(Evaluable p1) {
        this.p1 = Objects.requireNonNull(p1);
    }

    @Override
    public Evaluable getP1() {
        return p1;
    }

    @Override
    public void setP2(Evaluable p2) {
        this.p2 = Objects.requireNonNull(p2);
    }

    @Override
    public Evaluable getP2() {
        return p2;
    }
}
