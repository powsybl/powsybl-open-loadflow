package com.powsybl.openloadflow.ac.networktest;

import com.google.errorprone.annotations.NoAllocation;
import com.powsybl.iidm.network.HvdcLine;
import com.powsybl.iidm.network.ReactiveLimits;
import com.powsybl.iidm.network.VscConverterStation;
import com.powsybl.iidm.network.util.HvdcUtils;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.impl.*;
import com.powsybl.openloadflow.util.Evaluable;
import com.powsybl.openloadflow.util.PerUnit;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class LfVscConverterStationV2Impl extends AbstractLfGenerator implements LfVscConverterStationV2 {

    private final Ref<VscConverterStation> stationRef;

    private final double lossFactor;

    private final boolean hvdcDandlingInIidm;

    protected LfBus Bus;

    protected LfDcNode dcNode;

    protected double targetVdc;

    protected double targetPdc;

    protected boolean isPcontrolled;

    protected Evaluable pdc;

    int num = -1;

    public LfVscConverterStationV2Impl(VscConverterStation station, LfNetwork network, LfNetworkParameters parameters, LfNetworkLoadingReport report) {
        super(network, HvdcUtils.getConverterStationTargetP(station) / PerUnit.SB, parameters);
        this.hvdcDandlingInIidm = HvdcConverterStations.isHvdcDanglingInIidm(station);
        this.stationRef = Ref.create(station, parameters.isCacheEnabled());
        this.lossFactor = station.getLossFactor();
        network.addVscConverterStation(this);
        // local control only
        if (station.isVoltageRegulatorOn()) {
            setVoltageControl(station.getVoltageSetpoint(), station.getTerminal(), station.getRegulatingTerminal(), parameters, report);
        }
    }

    public static LfVscConverterStationV2Impl create(VscConverterStation station, LfNetwork network, LfNetworkParameters parameters, LfNetworkLoadingReport report) {
        Objects.requireNonNull(station);
        Objects.requireNonNull(network);
        Objects.requireNonNull(parameters);
        return new LfVscConverterStationV2Impl(station, network, parameters, report);
    }

    VscConverterStation getStation() {
        return stationRef.get();
    }


    @Override
    public double getTargetP() {
//            return super.getTargetP();
        return getTargetPdc();
    }

    @Override
    public void setInitialTargetP(double initialTargetP) {
        // no-op
    }

    @Override
    public void setInitialTargetPToTargetP() {
        // no-op
    }

    @Override
    public double getLossFactor() {
        return lossFactor;
    }

    @Override
    public String getId() {
        return getStation().getId();
    }

    @Override
    public double getTargetQ() {
        return Networks.zeroIfNan(getStation().getReactivePowerSetpoint()) / PerUnit.SB;
    }

    @Override
    public double getMinP() {
        HvdcLine hvdcLine = getStation().getHvdcLine();
        return hvdcLine != null ? -hvdcLine.getMaxP() / PerUnit.SB : -Double.MAX_VALUE;
    }

    @Override
    public double getMaxP() {
        HvdcLine hvdcLine = getStation().getHvdcLine();
        return hvdcLine != null ? hvdcLine.getMaxP() / PerUnit.SB : Double.MAX_VALUE;
    }

    @Override
    protected Optional<ReactiveLimits> getReactiveLimits() {
        return Optional.of(getStation().getReactiveLimits());
    }

    @Override
    public void updateState(LfNetworkStateUpdateParameters parameters) {
        var station = getStation();
        station.getTerminal()
                .setQ(Double.isNaN(calculatedQ) ? -getTargetQ() * PerUnit.SB : -calculatedQ * PerUnit.SB);
    }

    @Override
    public int getReferencePriority() {
        // never selected
        return -1;
    }

    @Override
    public void addBus(LfBus bus){
        this.bus = bus;
    }

    @Override
    public LfBus getBus(){
        return bus;
    }


    @Override
    public void addDcNode(LfDcNode dcNode) {
       this.dcNode = dcNode;
    }

    @Override
    public LfDcNode getDcNode() {
        return dcNode;
    }

    @Override
    public void setTargetPdc(double p){
        isPcontrolled = true;
        this.targetPdc = p;
    }

    @Override
    public void setTargetVdc(double v){
        isPcontrolled = false;
        this.targetVdc = v;
    }

    @Override
    public double getTargetPdc(){
            return targetPdc;
    }

    @Override
    public double getTargetVdc(){
            return targetVdc;
    }

    @Override
    public boolean isPControlled(){
        return isPcontrolled;
    }

    @Override
    public void setPdc(Evaluable p) {
        this.pdc = p;
    }

    @Override
    public Evaluable getPdc() {
        return this.pdc;
    }

    @Override
    public void setNum(int num) {
        this.num = num;
    }

    @Override
    public int getNum() {
        return num;
    }
}