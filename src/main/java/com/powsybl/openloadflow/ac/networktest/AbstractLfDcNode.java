package com.powsybl.openloadflow.ac.networktest;

import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.util.Evaluable;

import java.util.*;

import static com.powsybl.openloadflow.util.EvaluableConstants.NAN;
public abstract class AbstractLfDcNode extends AbstractElement implements LfDcNode {

    protected Evaluable v;

    protected double V;

    protected double P;

    protected Evaluable calculatedV;

    protected final List<LfDcLine> lfdclines = new ArrayList<>();

    protected final List<LfVscConverterStationV2> vscConverterStations = new ArrayList<>();

    protected Evaluable p;

    protected AbstractLfDcNode(LfNetwork network) {
        super(network);
    }

    @Override
    public ElementType getType() {
        return ElementType.DC_NODE;
    }

    @Override
    public Evaluable getCalculatedV() {
        return calculatedV;
    }

    @Override
    public void setCalculatedV(Evaluable calculatedV) {
        this.calculatedV = Objects.requireNonNull(calculatedV);
    }

    @Override
    public List<LfDcLine> getLfDcLines() {
        return lfdclines;
    }

    @Override
    public void addLfDcLine(LfDcLine lfdcline) {
        lfdclines.add(Objects.requireNonNull(lfdcline));
    }

    @Override
    public void addVscConverterStation(LfVscConverterStationV2Impl vsccs, LfBus lfBus) {
        vscConverterStations.add(Objects.requireNonNull(vsccs));
        vsccs.addBus(lfBus);
        vsccs.addDcNode(this);
        double P = vsccs.getTargetPdc();
        double V = vsccs.getTargetVdc();
        if (vsccs.isPcontrolled) {
            this.setTargetP(P);
        } else {
            this.setTargetV(V);
        }
    }

    public List<LfVscConverterStationV2> getVscConverterStations(){
        return vscConverterStations;
    }


    @Override
    public void setP(double P) {
        this.P = P;
    }

    @Override
    public Evaluable getP() {
        return this.p;
    }

    @Override
    public void setTargetP(double P) {
        this.P = P;
    }

    @Override
    public double getTargetP() {
        return P;
    }

    @Override
    public void setV(double V) {
        this.V = V;
    }

    @Override
    public Evaluable getV() {
        return v;
    }

    @Override
    public void setV(Evaluable v) {
        this.v = v;
    }

    @Override
    public void setP(Evaluable p) {
        this.p = p;
    }

    @Override
    public void setTargetV(double V) {
        this.V = V;
    }

    @Override
    public double getTargetV() {
        return V;
    }

    @Override
    public double getv() {
        return V;
    }

    @Override
    public double getp() {
        return P;
    }
}
