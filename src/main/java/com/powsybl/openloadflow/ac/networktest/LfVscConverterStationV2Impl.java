package com.powsybl.openloadflow.ac.networktest;

import com.powsybl.iidm.network.HvdcLine;
import com.powsybl.iidm.network.ReactiveLimits;
import com.powsybl.iidm.network.VscConverterStation;
import com.powsybl.iidm.network.util.HvdcUtils;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.impl.*;
import com.powsybl.openloadflow.util.Evaluable;
import com.powsybl.openloadflow.util.PerUnit;


import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class LfVscConverterStationV2Impl extends AbstractLfGenerator implements LfVscConverterStationV2 {

    private final Ref<VscConverterStation> stationRef;

    protected LfDcNode dcNode;

    protected double targetVdc;

    protected double targetVac;

    protected boolean isPcontrolled;

    protected boolean isControllingVAc = false;

    protected Evaluable pdc;

    protected List<Double> lossFactors;

    protected boolean isDcNodeConnectedSide1;

    int num = -1;

    public LfVscConverterStationV2Impl(VscConverterStation station, LfNetwork network, LfNetworkParameters parameters, LfNetworkLoadingReport report, List<Double> lossFactors) {
        super(network, HvdcUtils.getConverterStationTargetP(station) / PerUnit.SB, parameters);
        this.stationRef = Ref.create(station, parameters.isCacheEnabled());
        this.lossFactors = lossFactors;
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
        return new LfVscConverterStationV2Impl(station, network, parameters, report, List.of(6.62, 1.8, 1.98, 3.00));
    }


    public static LfVscConverterStationV2Impl create(VscConverterStation station, LfNetwork network, LfNetworkParameters parameters, LfNetworkLoadingReport report, List<Double> lossFactors) {
        Objects.requireNonNull(station);
        Objects.requireNonNull(network);
        Objects.requireNonNull(parameters);
        return new LfVscConverterStationV2Impl(station, network, parameters, report, lossFactors);
    }

    VscConverterStation getStation() {
        return stationRef.get();
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
    public void addBus(LfBus bus) {
        this.bus = bus;
    }

    @Override
    public LfBus getaBus() {
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
    public void setTargetP(double p) {
        targetP = p;
    }

    @Override
    public void setTargetPacControl(double p) {
        isPcontrolled = true;
        targetP = p/PerUnit.SB;
    }

    @Override
    public void setTargetVdcControl(double v) {
        isPcontrolled = false;
        this.targetVdc = v;
    }

    @Override
    public double getTargetVdcControl() {
        return targetVdc/this.getDcNode().getNominalV();
    }

    @Override
    public boolean isPControlled() {
        return isPcontrolled;
    }

    @Override
    public void setPac(Evaluable p) {
        this.pdc = p;
    }

    @Override
    public void setNum(int num) {
        this.num = num;
    }

    @Override
    public int getNum() {
        return num;
    }

    @Override
    public void setTargetVac(double V) {
        this.targetVac = V;
        isControllingVAc = true;
    }

    @Override
    public double getTargetVac() {
        return targetVac/this.getaBus().getNominalV();
    }

    @Override
    public boolean isControllingVAc() {
        return isControllingVAc;
    }

    @Override
    public List<Double> getLossFactors() {
        return lossFactors;
    }

    @Override
    public boolean isDcNodeConnectedSide1() {
        return isDcNodeConnectedSide1;
    }
}
