/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.impl;

import com.google.auto.service.AutoService;
import com.google.common.base.Stopwatch;
import com.powsybl.iidm.network.*;
import com.powsybl.iidm.network.ThreeWindingsTransformer.Leg;
import com.powsybl.iidm.network.util.LinkData;
import com.powsybl.openloadflow.network.*;
import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.complex.ComplexUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.powsybl.openloadflow.util.Markers.PERFORMANCE_MARKER;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
@AutoService(LfNetworkLoader.class)
public class LfNetworkLoaderImpl implements LfNetworkLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger(LfNetworkLoaderImpl.class);

    private static class LoadingContext {

        private final Set<Branch> branchSet = new LinkedHashSet<>();

        private final List<DanglingLine> danglingLines = new ArrayList<>();

        private final Set<ThreeWindingsTransformer> t3wtSet = new LinkedHashSet<>();
    }

    private static void createBuses(List<Bus> buses, boolean voltageRemoteControl, LfNetwork lfNetwork,
                                    LoadingContext loadingContext, LfNetworkLoadingReport report) {
        int[] voltageControllerCount = new int[1];
        Map<LfBusImpl, String> controllerBusToControlledBusId = new LinkedHashMap<>();

        for (Bus bus : buses) {
            LfBusImpl lfBus = createBus(bus, voltageRemoteControl, loadingContext, report, voltageControllerCount, controllerBusToControlledBusId);
            lfNetwork.addBus(lfBus);
        }

        if (voltageControllerCount[0] == 0) {
            LOGGER.error("Network {} has no equipment to control voltage", lfNetwork.getNum());
        }

        // set controller -> controlled link
        for (Map.Entry<LfBusImpl, String> e : controllerBusToControlledBusId.entrySet()) {
            LfBusImpl controllerBus = e.getKey();
            String controlledBusId = e.getValue();
            LfBus controlledBus = lfNetwork.getBusById(controlledBusId);
            controllerBus.setControlledBus((LfBusImpl) controlledBus);
        }
    }

    private static LfBusImpl createBus(Bus bus, boolean voltageRemoteControl, LoadingContext loadingContext, LfNetworkLoadingReport report,
                                       int[] voltageControllerCount, Map<LfBusImpl, String> controllerBusToControlledBusId) {
        LfBusImpl lfBus = LfBusImpl.create(bus);

        bus.visitConnectedEquipments(new DefaultTopologyVisitor() {

            private void visitBranch(Branch branch) {
                loadingContext.branchSet.add(branch);
            }

            @Override
            public void visitLine(Line line, Branch.Side side) {
                visitBranch(line);
            }

            @Override
            public void visitTwoWindingsTransformer(TwoWindingsTransformer transformer, Branch.Side side) {
                visitBranch(transformer);
            }

            @Override
            public void visitThreeWindingsTransformer(ThreeWindingsTransformer transformer, ThreeWindingsTransformer.Side side) {
                loadingContext.t3wtSet.add(transformer);
            }

            private double checkVoltageRemoteControl(Injection injection, Terminal regulatingTerminal, double previousTargetV) {
                double scaleV = 1;
                Bus controlledBus = regulatingTerminal.getBusView().getBus();
                Bus connectedBus = injection.getTerminal().getBusView().getBus();
                if (controlledBus == null || connectedBus == null) {
                    return scaleV;
                }
                String controlledBusId = controlledBus.getId();
                String connectedBusId = connectedBus.getId();
                if (!Objects.equals(controlledBusId, connectedBusId)) {
                    if (voltageRemoteControl) {
                        // controller to controlled bus link will be set later because controlled bus might not have
                        // been yet created
                        controllerBusToControlledBusId.put(lfBus, controlledBusId);
                    } else {
                        double remoteNominalV = regulatingTerminal.getVoltageLevel().getNominalV();
                        double localNominalV = injection.getTerminal().getVoltageLevel().getNominalV();
                        scaleV = localNominalV / remoteNominalV;
                        LOGGER.warn("Remote voltage control is not activated. The voltage target of " +
                                        "{} ({}) with remote control is rescaled from {} to {}",
                                injection.getId(), injection.getType(), previousTargetV, previousTargetV * scaleV);
                    }
                }
                return scaleV;
            }

            @Override
            public void visitGenerator(Generator generator) {
                double scaleV = checkVoltageRemoteControl(generator, generator.getRegulatingTerminal(), generator.getTargetV());
                lfBus.addGenerator(generator, scaleV, report);
                if (generator.isVoltageRegulatorOn()) {
                    voltageControllerCount[0]++;
                }
            }

            @Override
            public void visitLoad(Load load) {
                lfBus.addLoad(load);
            }

            @Override
            public void visitShuntCompensator(ShuntCompensator sc) {
                lfBus.addShuntCompensator(sc);
            }

            @Override
            public void visitDanglingLine(DanglingLine danglingLine) {
                loadingContext.danglingLines.add(danglingLine);
            }

            @Override
            public void visitStaticVarCompensator(StaticVarCompensator staticVarCompensator) {
                double scaleV = checkVoltageRemoteControl(staticVarCompensator, staticVarCompensator.getRegulatingTerminal(),
                        staticVarCompensator.getVoltageSetPoint());
                lfBus.addStaticVarCompensator(staticVarCompensator, scaleV, report);
            }

            @Override
            public void visitBattery(Battery battery) {
                lfBus.addBattery(battery);
            }

            @Override
            public void visitHvdcConverterStation(HvdcConverterStation<?> converterStation) {
                switch (converterStation.getHvdcType()) {
                    case VSC:
                        VscConverterStation vscConverterStation = (VscConverterStation) converterStation;
                        lfBus.addVscConverterStation(vscConverterStation, report);
                        if (vscConverterStation.isVoltageRegulatorOn()) {
                            voltageControllerCount[0]++;
                        }
                        break;
                    case LCC:
                        lfBus.addLccConverterStation((LccConverterStation) converterStation);
                        break;
                    default:
                        throw new IllegalStateException("Unknown HVDC converter station type: " + converterStation.getHvdcType());
                }
            }
        });

        return lfBus;
    }

    private static void addBranch(LfNetwork lfNetwork, LfBranch lfBranch, LfNetworkLoadingReport report) {
        boolean connectedToSameBus = lfBranch.getBus1() == lfBranch.getBus2();
        if (connectedToSameBus) {
            LOGGER.trace("Discard branch '{}' because connected to same bus at both ends", lfBranch.getId());
            report.branchesDiscardedBecauseConnectedToSameBusAtBothEnds++;
        } else {
            if (lfBranch.getPiModel().getZ() == 0) {
                LOGGER.trace("Branch {} is non impedant", lfBranch.getId());
                report.nonImpedantBranches++;
            }
            lfNetwork.addBranch(lfBranch);
        }
    }

    private static void createBranches(LfNetwork lfNetwork, LoadingContext loadingContext, LfNetworkLoadingReport report) {
        for (Branch branch : loadingContext.branchSet) {
            LfBus lfBus1 = getLfBus(branch.getTerminal1(), lfNetwork);
            LfBus lfBus2 = getLfBus(branch.getTerminal2(), lfNetwork);
            addBranch(lfNetwork, LfBranchImpl.create(branch, lfBus1, lfBus2), report);
        }

        for (DanglingLine danglingLine : loadingContext.danglingLines) {
            LfDanglingLineBus lfBus2 = new LfDanglingLineBus(danglingLine);
            lfNetwork.addBus(lfBus2);
            LfBus lfBus1 = getLfBus(danglingLine.getTerminal(), lfNetwork);
            addBranch(lfNetwork, LfDanglingLineBranch.create(danglingLine, lfBus1, lfBus2), report);

            Complex v2 = danglingLineBusVoltage(lfBus1, danglingLine);
            lfBus2.setV(v2.abs() / lfBus2.getNominalV());
            lfBus2.setAngle(Math.toDegrees(v2.getArgument()));
        }

        for (ThreeWindingsTransformer t3wt : loadingContext.t3wtSet) {
            LfStarBus lfBus0 = new LfStarBus(t3wt);
            lfNetwork.addBus(lfBus0);
            LfBus lfBus1 = getLfBus(t3wt.getLeg1().getTerminal(), lfNetwork);
            LfBus lfBus2 = getLfBus(t3wt.getLeg2().getTerminal(), lfNetwork);
            LfBus lfBus3 = getLfBus(t3wt.getLeg3().getTerminal(), lfNetwork);
            addBranch(lfNetwork, LfLegBranch.create(lfBus1, lfBus0, t3wt, t3wt.getLeg1()), report);
            addBranch(lfNetwork, LfLegBranch.create(lfBus2, lfBus0, t3wt, t3wt.getLeg2()), report);
            addBranch(lfNetwork, LfLegBranch.create(lfBus3, lfBus0, t3wt, t3wt.getLeg3()), report);

            Complex v0 = t3wtStarBusVoltage(lfBus1, lfBus2, lfBus3, t3wt);
            lfBus0.setV(v0.abs() / lfBus0.getNominalV());
            lfBus0.setAngle(Math.toDegrees(v0.getArgument()));
        }
    }

    private static Complex danglingLineBusVoltage(LfBus lfBus1, DanglingLine danglingLine) {
        double r = danglingLine.getR();
        double x = danglingLine.getX();
        double g1 = danglingLine.getG();
        double b1 = danglingLine.getB();
        double g2 = 0.0;
        double b2 = 0.0;
        double p0 = -danglingLine.getP0();
        double q0 = -danglingLine.getQ0();
        Complex v1 = ComplexUtils.polar2Complex(lfBus1.getV() * lfBus1.getNominalV(), Math.toRadians(lfBus1.getAngle()));

        if (p0 == 0.0 && q0 == 0.0) {
            LinkData.BranchAdmittanceMatrix adm = LinkData.calculateBranchAdmittance(r, x, 1.0, 0.0, 1.0, 0.0, new Complex(g1, b1), new Complex(g2, b2));
            return adm.y21().multiply(v1).negate().divide(adm.y22());
        }

        // Two buses Loadflow
        Complex ytr = new Complex(r, x).reciprocal();
        Complex ysh2 = new Complex(g2, b2);
        Complex zt = ytr.add(ysh2).reciprocal();
        Complex v0 = ytr.multiply(v1).divide(ytr.add(ysh2));
        double v02 = v0.abs() * v0.abs();

        double sigmar = (zt.getImaginary() * q0 + zt.getReal() * p0) / v02;
        double sigmai = (zt.getImaginary() * p0 - zt.getReal() * q0) / v02;
        double d = 0.25 + sigmar - sigmai * sigmai;
        // Collapsed network
        if (d < 0) {
            return new Complex(1.0 * lfBus1.getNominalV(), 0.0);
        }

        return new Complex(0.5 + Math.sqrt(d), sigmai).multiply(v0);
    }

    private static Complex t3wtStarBusVoltage(LfBus lfBus1, LfBus lfBus2, LfBus lfBus3, ThreeWindingsTransformer t3wt) {
        double ratedU0 = t3wt.getRatedU0();

        double rhof = 1.0;
        double alphaf = 0.0;
        boolean twtSplitShuntAdmittance = false;

        double r1 = getR(t3wt.getLeg1());
        double x1 = getX(t3wt.getLeg1());
        double r2 = getR(t3wt.getLeg2());
        double x2 = getX(t3wt.getLeg2());
        double r3 = getR(t3wt.getLeg3());
        double x3 = getX(t3wt.getLeg3());

        double g11 = getG1(t3wt.getLeg1(), twtSplitShuntAdmittance);
        double b11 = getB1(t3wt.getLeg1(), twtSplitShuntAdmittance);
        double g12 = getG2(t3wt.getLeg1(), twtSplitShuntAdmittance);
        double b12 = getB2(t3wt.getLeg1(), twtSplitShuntAdmittance);
        double g21 = getG1(t3wt.getLeg2(), twtSplitShuntAdmittance);
        double b21 = getB1(t3wt.getLeg2(), twtSplitShuntAdmittance);
        double g22 = getG2(t3wt.getLeg2(), twtSplitShuntAdmittance);
        double b22 = getB2(t3wt.getLeg2(), twtSplitShuntAdmittance);
        double g31 = getG1(t3wt.getLeg3(), twtSplitShuntAdmittance);
        double b31 = getB1(t3wt.getLeg3(), twtSplitShuntAdmittance);
        double g32 = getG2(t3wt.getLeg3(), twtSplitShuntAdmittance);
        double b32 = getB2(t3wt.getLeg3(), twtSplitShuntAdmittance);

        double rho1 = rho(t3wt.getLeg1(), ratedU0);
        double alpha1 = alpha(t3wt.getLeg1());
        double rho2 = rho(t3wt.getLeg2(), ratedU0);
        double alpha2 = alpha(t3wt.getLeg2());
        double rho3 = rho(t3wt.getLeg3(), ratedU0);
        double alpha3 = alpha(t3wt.getLeg3());

        LinkData.BranchAdmittanceMatrix admLeg1 = LinkData.calculateBranchAdmittance(r1, x1, 1 / rho1, -alpha1, 1 / rhof, -alphaf, new Complex(g11, b11), new Complex(g12, b12));
        LinkData.BranchAdmittanceMatrix admLeg2 = LinkData.calculateBranchAdmittance(r2, x2, 1 / rho2, -alpha2, 1 / rhof, -alphaf, new Complex(g21, b21), new Complex(g22, b22));
        LinkData.BranchAdmittanceMatrix admLeg3 = LinkData.calculateBranchAdmittance(r3, x3, 1 / rho3, -alpha3, 1 / rhof, -alphaf, new Complex(g31, b31), new Complex(g32, b32));

        if (lfBus1 != null && lfBus2 != null && lfBus3 != null) {
            return t3wtStarBusVoltageThreeLegsConnected(lfBus1, lfBus2, lfBus3, admLeg1, admLeg2, admLeg3);
        } else if (lfBus1 != null && lfBus2 != null) {
            return t3wtStarBusVoltageTwoLegsConnected(lfBus1, lfBus2, admLeg1, admLeg2, admLeg3);
        } else if (lfBus1 != null && lfBus3 != null) {
            return t3wtStarBusVoltageTwoLegsConnected(lfBus1, lfBus3, admLeg1, admLeg3, admLeg2);
        } else if (lfBus2 != null && lfBus3 != null) {
            return t3wtStarBusVoltageTwoLegsConnected(lfBus2, lfBus3, admLeg2, admLeg3, admLeg1);
        } else if (lfBus1 != null) {
            t3wtStarBusVoltageOneLegConnected(lfBus1, admLeg1, admLeg2, admLeg3);
        } else if (lfBus2 != null) {
            t3wtStarBusVoltageOneLegConnected(lfBus2, admLeg2, admLeg1, admLeg3);
        } else if (lfBus3 != null) {
            t3wtStarBusVoltageOneLegConnected(lfBus3, admLeg3, admLeg1, admLeg2);
        }

        return new Complex(0.0, 0.0);
    }

    private static Complex t3wtStarBusVoltageOneLegConnected(LfBus lfBusC,
            LinkData.BranchAdmittanceMatrix admLegC, LinkData.BranchAdmittanceMatrix admLeg1O,
            LinkData.BranchAdmittanceMatrix admLeg2O) {

        Complex vc = new Complex(lfBusC.getV() * lfBusC.getNominalV() * Math.cos(Math.toRadians(lfBusC.getAngle())),
                lfBusC.getV() * lfBusC.getNominalV() * Math.sin(Math.toRadians(lfBusC.getAngle())));

        Complex ysh1O = kronAntenna(admLeg1O.y11(), admLeg1O.y12(), admLeg1O.y21(), admLeg1O.y22(), true);
        Complex ysh2O = kronAntenna(admLeg2O.y11(), admLeg2O.y12(), admLeg2O.y21(), admLeg2O.y22(), true);

        return admLegC.y21().multiply(vc).negate().divide(admLegC.y22().add(ysh1O).add(ysh2O));
    }

    private static Complex t3wtStarBusVoltageTwoLegsConnected(LfBus lfBus1C, LfBus lfBus2C,
            LinkData.BranchAdmittanceMatrix admLeg1C, LinkData.BranchAdmittanceMatrix admLeg2C,
            LinkData.BranchAdmittanceMatrix admLegO) {

        Complex v1c = new Complex(lfBus1C.getV() * lfBus1C.getNominalV() * Math.cos(Math.toRadians(lfBus1C.getAngle())),
                lfBus1C.getV() * lfBus1C.getNominalV() * Math.sin(Math.toRadians(lfBus1C.getAngle())));
        Complex v2c = new Complex(lfBus2C.getV() * lfBus2C.getNominalV() * Math.cos(Math.toRadians(lfBus2C.getAngle())),
                lfBus2C.getV() * lfBus2C.getNominalV() * Math.sin(Math.toRadians(lfBus2C.getAngle())));

        Complex yshO = kronAntenna(admLegO.y11(), admLegO.y12(), admLegO.y21(), admLegO.y22(), true);
        return (admLeg1C.y21().multiply(v1c).add(admLeg2C.y21().multiply(v2c))).negate()
                .divide(admLeg1C.y22().add(admLeg2C.y22()).add(yshO));
    }

    private static Complex t3wtStarBusVoltageThreeLegsConnected(LfBus lfBus1, LfBus lfBus2, LfBus lfBus3,
            LinkData.BranchAdmittanceMatrix admLeg1, LinkData.BranchAdmittanceMatrix admLeg2,
            LinkData.BranchAdmittanceMatrix admLeg3) {

        Complex v1 = new Complex(lfBus1.getV() * lfBus1.getNominalV() * Math.cos(Math.toRadians(lfBus1.getAngle())),
                lfBus1.getV() * lfBus1.getNominalV() * Math.sin(Math.toRadians(lfBus1.getAngle())));
        Complex v2 = new Complex(lfBus2.getV() * lfBus2.getNominalV() * Math.cos(Math.toRadians(lfBus2.getAngle())),
                lfBus2.getV() * lfBus2.getNominalV() * Math.sin(Math.toRadians(lfBus2.getAngle())));
        Complex v3 = new Complex(lfBus3.getV() * lfBus3.getNominalV() * Math.cos(Math.toRadians(lfBus3.getAngle())),
                lfBus3.getV() * lfBus3.getNominalV() * Math.sin(Math.toRadians(lfBus3.getAngle())));

        return admLeg1.y21().multiply(v1).add(admLeg2.y21().multiply(v2))
                .add(admLeg3.y21().multiply(v3)).negate()
                .divide(admLeg1.y22().add(admLeg2.y22()).add(admLeg3.y22()));
    }

    public static Complex kronAntenna(Complex y11, Complex y12, Complex y21, Complex y22, boolean isOpenFrom) {
        Complex ysh = Complex.ZERO;

        if (isOpenFrom) {
            if (!y11.equals(Complex.ZERO)) {
                ysh = y22.subtract(y21.multiply(y12).divide(y11));
            }
        } else {
            if (!y22.equals(Complex.ZERO)) {
                ysh = y11.subtract(y12.multiply(y21).divide(y22));
            }
        }
        return ysh;
    }

    private static double rho(Leg leg, double ratedU0) {
        double rho = ratedU0 / leg.getRatedU();
        if (leg.getRatioTapChanger() != null) {
            rho *= leg.getRatioTapChanger().getCurrentStep().getRho();
        }
        if (leg.getPhaseTapChanger() != null) {
            rho *= leg.getPhaseTapChanger().getCurrentStep().getRho();
        }
        return rho;
    }

    private static double alpha(Leg leg) {
        return leg.getPhaseTapChanger() != null ? Math.toRadians(leg.getPhaseTapChanger().getCurrentStep().getAlpha()) : 0f;
    }

    private static double getValue(double initialValue, double rtcStepValue, double ptcStepValue) {
        return initialValue * (1 + rtcStepValue / 100) * (1 + ptcStepValue / 100);
    }

    private static double getR(Leg leg) {
        return getValue(leg.getR(),
            leg.getRatioTapChanger() != null ? leg.getRatioTapChanger().getCurrentStep().getR() : 0,
            leg.getPhaseTapChanger() != null ? leg.getPhaseTapChanger().getCurrentStep().getR() : 0);
    }

    private static double getX(Leg leg) {
        return getValue(leg.getX(),
            leg.getRatioTapChanger() != null ? leg.getRatioTapChanger().getCurrentStep().getX() : 0,
            leg.getPhaseTapChanger() != null ? leg.getPhaseTapChanger().getCurrentStep().getX() : 0);
    }

    private static double getG1(Leg leg, boolean twtSplitShuntAdmittance) {
        return getValue(twtSplitShuntAdmittance ? leg.getG() / 2 : leg.getG(),
            leg.getRatioTapChanger() != null ? leg.getRatioTapChanger().getCurrentStep().getG() : 0,
            leg.getPhaseTapChanger() != null ? leg.getPhaseTapChanger().getCurrentStep().getG() : 0);
    }

    private static double getB1(Leg leg, boolean twtSplitShuntAdmittance) {
        return getValue(twtSplitShuntAdmittance ? leg.getB() / 2 : leg.getB(),
            leg.getRatioTapChanger() != null ? leg.getRatioTapChanger().getCurrentStep().getB() : 0,
            leg.getPhaseTapChanger() != null ? leg.getPhaseTapChanger().getCurrentStep().getB() : 0);
    }

    private static double getG2(Leg leg, boolean twtSplitShuntAdmittance) {
        return getValue(twtSplitShuntAdmittance ? leg.getG() / 2 : 0.0,
            leg.getRatioTapChanger() != null ? leg.getRatioTapChanger().getCurrentStep().getG() : 0,
            leg.getPhaseTapChanger() != null ? leg.getPhaseTapChanger().getCurrentStep().getG() : 0);
    }

    private static double getB2(Leg leg, boolean twtSplitShuntAdmittance) {
        return getValue(twtSplitShuntAdmittance ? leg.getB() / 2 : 0.0,
            leg.getRatioTapChanger() != null ? leg.getRatioTapChanger().getCurrentStep().getB() : 0,
            leg.getPhaseTapChanger() != null ? leg.getPhaseTapChanger().getCurrentStep().getB() : 0);
    }

    private static LfBus getLfBus(Terminal terminal, LfNetwork lfNetwork) {
        Bus bus = terminal.getBusView().getBus();
        return bus != null ? lfNetwork.getBusById(bus.getId()) : null;
    }

    private static LfNetwork create(MutableInt num, List<Bus> buses, SlackBusSelector slackBusSelector, boolean voltageRemoteControl) {
        LfNetwork lfNetwork = new LfNetwork(num.getValue(), slackBusSelector);
        num.increment();

        LoadingContext loadingContext = new LoadingContext();
        LfNetworkLoadingReport report = new LfNetworkLoadingReport();

        createBuses(buses, voltageRemoteControl, lfNetwork, loadingContext, report);
        createBranches(lfNetwork, loadingContext, report);

        if (report.generatorsDiscardedFromVoltageControlBecauseNotStarted > 0) {
            LOGGER.warn("{} generators have been discarded from voltage control because not started",
                    report.generatorsDiscardedFromVoltageControlBecauseNotStarted);
        }
        if (report.generatorsDiscardedFromVoltageControlBecauseMaxReactiveRangeIsTooSmall > 0) {
            LOGGER.warn("{} generators have been discarded from voltage control because of a too small max reactive range",
                    report.generatorsDiscardedFromVoltageControlBecauseMaxReactiveRangeIsTooSmall);
        }
        if (report.generatorsDiscardedFromActivePowerControlBecauseTargetPLesserOrEqualsToZero > 0) {
            LOGGER.warn("{} generators have been discarded from active power control because of a targetP <= 0",
                    report.generatorsDiscardedFromActivePowerControlBecauseTargetPLesserOrEqualsToZero);
        }
        if (report.generatorsDiscardedFromActivePowerControlBecauseTargetPGreaterThenMaxP > 0) {
            LOGGER.warn("{} generators have been discarded from active power control because of a targetP > maxP",
                    report.generatorsDiscardedFromActivePowerControlBecauseTargetPGreaterThenMaxP);
        }
        if (report.generatorsDiscardedFromActivePowerControlBecauseMaxPNotPlausible > 0) {
            LOGGER.warn("{} generators have been discarded from active power control because of maxP not plausible",
                    report.generatorsDiscardedFromActivePowerControlBecauseMaxPNotPlausible);
        }
        if (report.branchesDiscardedBecauseConnectedToSameBusAtBothEnds > 0) {
            LOGGER.warn("{} branches have been discarded because connected to same bus at both ends",
                    report.branchesDiscardedBecauseConnectedToSameBusAtBothEnds);
        }
        if (report.nonImpedantBranches > 0) {
            LOGGER.warn("{} branches are non impedant", report.nonImpedantBranches);
        }

        return lfNetwork;
    }

    @Override
    public Optional<List<LfNetwork>> load(Object network, SlackBusSelector slackBusSelector, boolean voltageRemoteControl) {
        Objects.requireNonNull(network);
        Objects.requireNonNull(slackBusSelector);

        if (network instanceof Network) {
            Stopwatch stopwatch = Stopwatch.createStarted();

            Map<Pair<Integer, Integer>, List<Bus>> buseByCc = new TreeMap<>();
            for (Bus bus : ((Network) network).getBusView().getBuses()) {
                Component cc = bus.getConnectedComponent();
                Component sc = bus.getSynchronousComponent();
                if (cc != null && sc != null) {
                    buseByCc.computeIfAbsent(Pair.of(cc.getNum(), sc.getNum()), k -> new ArrayList<>()).add(bus);
                }
            }

            MutableInt num = new MutableInt(0);
            List<LfNetwork> lfNetworks = buseByCc.entrySet().stream()
                    .filter(e -> e.getKey().getLeft() == ComponentConstants.MAIN_NUM)
                    .map(e -> create(num, e.getValue(), slackBusSelector, voltageRemoteControl))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            stopwatch.stop();
            LOGGER.debug(PERFORMANCE_MARKER, "LF networks created in {} ms", stopwatch.elapsed(TimeUnit.MILLISECONDS));

            return Optional.of(lfNetworks);
        }

        return Optional.empty();
    }
}
