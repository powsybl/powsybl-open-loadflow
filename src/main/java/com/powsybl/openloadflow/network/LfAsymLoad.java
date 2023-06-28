package com.powsybl.openloadflow.network;

import com.powsybl.openloadflow.network.extensions.AsymBusLoadType;
import com.powsybl.openloadflow.network.extensions.LegConnectionType;

public class LfAsymLoad {

    private final LfBus bus;

    private final LegConnectionType loadConnectionType; // how loads are connected between each other
    private final AsymBusLoadType loadType;
    private double totalDeltaPa;
    private double totalDeltaQa;
    private double totalDeltaPb;
    private double totalDeltaQb;
    private double totalDeltaPc;
    private double totalDeltaQc;

    public LfAsymLoad(LfBus bus, AsymBusLoadType loadType, LegConnectionType loadConnectionType, double totalDeltaPa, double totalDeltaQa, double totalDeltaPb, double totalDeltaQb, double totalDeltaPc, double totalDeltaQc) {
        this.bus = bus;
        this.loadType = loadType;
        this.loadConnectionType = loadConnectionType;
        this.totalDeltaPa = totalDeltaPa;
        this.totalDeltaQa = totalDeltaQa;
        this.totalDeltaPb = totalDeltaPb;
        this.totalDeltaQb = totalDeltaQb;
        this.totalDeltaPc = totalDeltaPc;
        this.totalDeltaQc = totalDeltaQc;
    }

    public double getPa() {
        return totalDeltaPa;
    }

    public double getPb() {
        return totalDeltaPb;
    }

    public double getPc() {
        return totalDeltaPc;
    }

    public double getQa() {
        return totalDeltaQa;
    }

    public double getQb() {
        return totalDeltaQb;
    }

    public double getQc() {
        return totalDeltaQc;
    }

    public AsymBusLoadType getLoadType() {
        return loadType;
    }

    public LegConnectionType getLoadConnectionType() {
        return loadConnectionType;
    }

    public LfBus getBus() {
        return bus;
    }

    public void addPa(double pa) {
        totalDeltaPa = totalDeltaPa + pa;
    }

    public void addPb(double pb) {
        totalDeltaPb = totalDeltaPb + pb;
    }

    public void addPc(double pc) {
        totalDeltaPc = totalDeltaPc + pc;
    }

    public void addQa(double qa) {
        totalDeltaQa = totalDeltaQa + qa;
    }

    public void addQb(double qb) {
        totalDeltaQb = totalDeltaQb + qb;
    }

    public void addQc(double qc) {
        totalDeltaQc = totalDeltaQc + qc;
    }

    public void addSabc(double pa, double qa, double pb, double qb, double pc, double qc) {
        addPa(pa);
        addQa(qa);
        addPb(pb);
        addQb(qb);
        addPc(pc);
        addQc(qc);
    }
}
