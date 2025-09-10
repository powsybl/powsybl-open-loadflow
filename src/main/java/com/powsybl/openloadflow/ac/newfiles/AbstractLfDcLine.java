package com.powsybl.openloadflow.ac.newfiles;

import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.util.Evaluable;

import java.util.Objects;

import static com.powsybl.openloadflow.util.EvaluableConstants.NAN;

public abstract class AbstractLfDcLine extends AbstractElement implements LfDcLine {

    protected final LfDcNode dcNode1;

    protected final LfDcNode dcNode2;

    private double r = Double.NaN;

    protected Evaluable p1 = NAN;

    protected Evaluable i1 = NAN;

    protected Evaluable p2 = NAN;

    protected Evaluable i2 = NAN;

    protected Evaluable closedP1 = NAN;

    protected Evaluable closedI1 = NAN;

    protected Evaluable closedP2 = NAN;

    protected Evaluable closedI2 = NAN;

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
    public Evaluable getClosedP1() {
        return closedP1;
    }

    @Override
    public void setClosedP1(Evaluable closedP1) {
        this.closedP1 = Objects.requireNonNull(closedP1);
    }

    @Override
    public Evaluable getClosedI1() {
        return closedI1;
    }

    @Override
    public void setClosedI1(Evaluable closedI1) {
        this.closedI1 = Objects.requireNonNull(closedI1);
    }

    @Override
    public Evaluable getClosedP2() {
        return closedP2;
    }

    @Override
    public void setClosedP2(Evaluable closedP2) {
        this.closedP2 = Objects.requireNonNull(closedP2);
    }

    @Override
    public Evaluable getClosedI2() {
        return closedI2;
    }

    @Override
    public void setClosedI2(Evaluable closedI2) {
        this.closedI2 = Objects.requireNonNull(closedI2);
    }

}
