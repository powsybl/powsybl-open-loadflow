package com.powsybl.openloadflow.network.impl;

import com.powsybl.openloadflow.network.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public abstract class AbstractLfDcNode extends AbstractElement implements LfDcNode {

    protected final List<LfDcLine> lfDcLines = new ArrayList<>();

    protected final List<LfAcDcConverter> converters = new ArrayList<>();

    protected double v;

    protected final double nominalV;

    protected boolean isNeutralPole = false;

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

    @Override
    public void addConverter(LfAcDcConverter converter) {
        converters.add(converter);
    }

    @Override
    public double getV() {
        return v / nominalV;
    }

    @Override
    public void setV(double v) {
        this.v = v * nominalV;
    }

    @Override
    public double getNominalV() {
        return nominalV;
    }

    @Override
    public boolean isNeutralPole() {
        return isNeutralPole;
    }

    @Override
    public void setNeutralPole(boolean isNeutralPole) {
        this.isNeutralPole = isNeutralPole;
    }
}
