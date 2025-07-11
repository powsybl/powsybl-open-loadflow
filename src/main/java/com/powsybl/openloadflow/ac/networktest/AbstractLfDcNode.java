package com.powsybl.openloadflow.ac.networktest;

import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.util.Evaluable;

import java.util.*;

//import static com.powsybl.openloadflow.util.EvaluableConstants.NAN;
public abstract class AbstractLfDcNode extends AbstractElement implements LfDcNode {

    protected Evaluable v;

    protected double vdc;

    protected double pdc;

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
    }

    public List<LfVscConverterStationV2> getVscConverterStations() {
        return vscConverterStations;
    }

    @Override
    public void setPdc(double pdc) {
        this.pdc = pdc;
    }

    @Override
    public Evaluable getPdc() {
        return this.p;
    }

    @Override
    public void setTargetP(double pdc) {
        this.pdc = pdc;
    }

    @Override
    public double getTargetP() {
        return pdc;
    }

    @Override
    public void setVdc(double vdc) {
        this.vdc = vdc;
    }

    @Override
    public Evaluable getVdc() {
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
    public void setTargetV(double vdc) {
        this.vdc = vdc;
    }

    @Override
    public double getTargetV() {
        return vdc;
    }

    @Override
    public double getv() {
        return vdc;
    }

    @Override
    public double getp() {
        return pdc;
    }
}
