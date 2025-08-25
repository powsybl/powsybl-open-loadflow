package com.powsybl.openloadflow.ac.networktest;

import com.powsybl.openloadflow.network.AbstractElement;
import com.powsybl.openloadflow.network.ElementType;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.util.Evaluable;
import com.powsybl.openloadflow.util.PerUnit;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public abstract class AbstractLfDcNode extends AbstractElement implements LfDcNode {

    protected final List<LfDcLine> lfDcLines = new ArrayList<>();

    protected final List<LfAcDcConverter> vscConverterStations = new ArrayList<>();

    protected Evaluable calculatedV;

    protected double v;

    protected double p;

    protected Evaluable calculatedP;

    protected double nominalV;

    protected AbstractLfDcNode(LfNetwork network, double nominalV, double v) {

        super(network);
        this.nominalV = nominalV;
        this.v = v;
    }

    @Override
    public ElementType getType() {
        return ElementType.DC_NODE;
    }

    @Override
    public void addLfDcLine(LfDcLine lfdcline) {
        lfDcLines.add(Objects.requireNonNull(lfdcline));
    }

    public List<LfAcDcConverter> getVscConverterStations() {
        return vscConverterStations;
    }

    @Override
    public void setP(double p) {
        this.p = p;
    }

    @Override
    public double getTargetP() {
        return p / PerUnit.SB;
    }

    @Override
    public void setV(Evaluable calculatedV) {
        this.calculatedV = calculatedV;
    }

    @Override
    public double getV() {
        return v;
    }

    @Override
    public void setV(double v) {
        this.v = v;
    }

    @Override
    public void setP(Evaluable calculatedP) {
        this.calculatedP = calculatedP;
    }

    @Override
    public double getTargetV() {
        return v;
    }

    @Override
    public void setTargetV(double vdc) {
        this.v = vdc;
    }

    @Override
    public double getNominalV() {
        return nominalV;
    }

    @Override
    public double getP() {
        return p;
    }

    @Override
    public List<LfDcLine> getDcLines() {
        return lfDcLines;
    }
}
