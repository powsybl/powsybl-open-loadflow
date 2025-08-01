package com.powsybl.openloadflow.ac.networktest;

import com.powsybl.iidm.network.HvdcLine;
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

    protected Evaluable p1 = NAN;

    protected Evaluable q1 = NAN;

    protected Evaluable i1 = NAN;

    protected Evaluable p2 = NAN;

    protected Evaluable q2 = NAN;

    protected Evaluable i2 = NAN;

    protected Evaluable v1 = NAN;

    protected Evaluable v2 = NAN;
    protected Evaluable closedP1 = NAN;
    protected Evaluable closedP2 = NAN;
    private double r = Double.NaN;

    protected AbstractLfDcLine(LfNetwork network, LfDcNode dcNode1, LfDcNode dcNode2, HvdcLine hvdcLine) {
        super(network);
        this.dcNode1 = dcNode1;
        dcNode1.addLfDcLine(this);
        this.dcNode2 = dcNode2;
        dcNode2.addLfDcLine(this);
        this.r = hvdcLine.getR();

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
    public Evaluable getP1() {
        return this.p1;
    }

    @Override
    public void setP1(Evaluable p1) {
        this.p1 = Objects.requireNonNull(p1);
    }

    @Override
    public Evaluable getP2() {
        return this.p2;
    }

    @Override
    public void setP2(Evaluable p2) {
        this.p2 = Objects.requireNonNull(p2);
    }

    @Override
    public double getR() {
        return r;
    }

    @Override
    public void setClosedP1(Evaluable closedP1) {
        this.closedP1 = closedP1;
    }

    @Override
    public void setClosedP2(Evaluable closedP2) {
        this.closedP2 = closedP2;
    }

    @Override
    public Evaluable getI1() {
        return i1;
    }

    @Override
    public void setI1(Evaluable i1) {
        this.i1 = Objects.requireNonNull(i1);
    }

    @Override
    public Evaluable getI2() {
        return i2;
    }

    @Override
    public void setI2(Evaluable i2) {
        this.i2 = Objects.requireNonNull(i2);
    }

    @Override
    public Evaluable getV1() {
        return v1;
    }

    @Override
    public void setV1(Evaluable v1) {
        this.v1 = Objects.requireNonNull(v1);
    }

    @Override
    public Evaluable getV2() {
        return v2;
    }

    @Override
    public void setV2(Evaluable v2) {
        this.v2 = Objects.requireNonNull(v2);
    }

    @Override
    public PiModel getPiModel() {
        return null;
    }
}
