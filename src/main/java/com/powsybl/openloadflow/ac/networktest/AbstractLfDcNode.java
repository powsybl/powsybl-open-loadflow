package com.powsybl.openloadflow.ac.networktest;

import com.powsybl.openloadflow.network.AbstractElement;
import com.powsybl.openloadflow.network.ElementType;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.util.Evaluable;
import com.powsybl.openloadflow.util.PerUnit;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public abstract class AbstractLfDcNode extends AbstractElement implements LfDcNode {

    protected final List<LfDcLine> lfdclines = new ArrayList<>();
    protected final List<LfVscConverterStationV2> vscConverterStations = new ArrayList<>();
    protected Evaluable v;
    protected double vdc;
    protected double pdc;
    protected Evaluable p;

    protected double nominalV;

    protected AbstractLfDcNode(LfNetwork network, double nominalV) {

        super(network);
        this.nominalV = nominalV;
    }

    @Override
    public ElementType getType() {
        return ElementType.DC_NODE;
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
    public double getTargetP() {
        return pdc / PerUnit.SB;
    }

    @Override
    public void setTargetP(double pdc) {
        this.pdc = pdc;
    }

    @Override
    public void setVdc(double vdc) {
        this.vdc = vdc;
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
    public double getTargetV() {
        return vdc;
    }

    @Override
    public void setTargetV(double vdc) {
        this.vdc = vdc;
    }

    @Override
    public double getNominalV() {
        return nominalV;
    }
}
