/**
 * Copyright (c) 2022, Jean-Baptiste Heyberger & Geoffroy Jamgotchian
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.implementation;

import com.powsybl.math.matrix.ComplexMatrix;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.PiModel;
import com.powsybl.sc.util.*;
import com.powsybl.sc.util.extensions.AdmittanceConstants;
import com.powsybl.shortcircuit.FortescueValue;
import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.complex.ComplexUtils;
import org.apache.commons.math3.util.FastMath;

import java.util.*;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class ShortCircuitResult {

    public enum FortescueType {
        DIRECT,
        HOMOPOLAR,
        INVERSE
    }

    public class CommonSupportResult {

        private LfBus lfBus2; // FIXME : might be wrongly overwritten in the "resultsPerFault" presentation

        private Complex eth2;

        private FortescueValue i2Fortescue; //fortescue vector of currents
        private FortescueValue v2Fortescue; //fortescue vector of voltages

        CommonSupportResult(LfBus lfBus2, Complex eth2,
                            Complex i2d, Complex i2o, Complex i2i,
                            Complex dv2d, Complex dv2o, Complex dv2i) {
            this.lfBus2 = lfBus2;
            this.eth2 = eth2;

            this.i2Fortescue = new FortescueValue(i2d.abs(), i2o.abs(), i2i.abs(), i2d.getArgument(), i2o.getArgument(), i2i.getArgument());

            //construction of the fortescue vector vFortescue = t[Vh, Vd, Vi]
            Complex vd = eth2.add(dv2d);
            this.v2Fortescue = new FortescueValue(vd.abs(), dv2o.abs(), dv2i.abs(), vd.getArgument(), dv2o.getArgument(), dv2i.getArgument());
        }
    }

    private LfBus lfBus; // FIXME : might be wrongly overwritten in the "resultsPerFault" presentation

    private LfNetwork lfNetwork;

    private ShortCircuitNorm norm;

    private Complex zd; // equivalent direct impedance
    private Complex zi; // equivalent inverse impedance
    private Complex zh; // equivalent homopolar impedance
    private Complex zd20hz;
    private Complex zh20hz;

    private Complex eth;

    private FortescueValue iFortescue; //fortescue vector of currents
    private FortescueValue vFortescue; //fortescue vector of voltages

    private boolean isVoltageProfileUpdated;
    private List<FortescueValue> busNum2Dv;
    private Map<LfBranch, FortescueValue> branchDi1;
    private Map<LfBranch, FortescueValue> branchDi2;

    // This contains the equivalent admittance of feeders's injectors, they are built when the AdmittanceEquationSystem is built and put in input of the sc result
    private FeedersAtNetwork eqSysFeedersDirect;
    private FeedersAtNetwork eqSysFeedersHomopolar;

    private Map<LfBus, FeedersAtBusResult> feedersResultDirect; // For each feeder in eqSysFeedersDirect, this map contains the results
    private Map<LfBus, FeedersAtBusResult> feedersResultsHomopolar;
    private Map<LfBus, FeedersAtBusResult> feedersResultsInverse;

    private ShortCircuitFault shortCircuitFault;

    private CommonSupportResult commonSupportResult; // used only for biphased with common support faults

    public ShortCircuitResult(ShortCircuitFault shortCircuitFault, LfBus lfBus,
                              Complex id, Complex zth, Complex eth, Complex dv,
                              FeedersAtNetwork eqSysFeeders, ShortCircuitNorm norm, Complex zth20hz) {
        // Called for a balanced short circuit calculation
        this.lfBus = lfBus;
        this.eqSysFeedersDirect = eqSysFeeders;
        this.shortCircuitFault = shortCircuitFault;
        this.norm = norm;

        this.zd = zth;
        this.zi = new Complex(0., 0.);
        this.zh = new Complex(0., 0.);
        this.zd20hz = zth20hz;
        this.zh20hz = new Complex(0., 0.);

        this.eth = eth;

        this.iFortescue = new FortescueValue(id.abs(), id.getArgument());

        Complex vd = eth.add(dv);
        this.vFortescue = new FortescueValue(vd.abs(), vd.getArgument());

        isVoltageProfileUpdated = false;
    }

    public ShortCircuitResult(ShortCircuitFault shortCircuitFault, LfBus lfBus,
                              Complex id, Complex io, Complex ii,
                              Complex zd, Complex zo, Complex zi,
                              Complex vdInit, Complex dv, Complex dvo, Complex dvi,
                              FeedersAtNetwork eqSysFeedersDirect, FeedersAtNetwork eqSysFeedersHomopolar, ShortCircuitNorm norm, Complex zth20hz, Complex zoth20hz) {
        // Called for an unbalanced short circuit calculation with no common support
        this.lfBus = lfBus;
        this.eqSysFeedersDirect = eqSysFeedersDirect;
        this.eqSysFeedersHomopolar = eqSysFeedersHomopolar;
        this.shortCircuitFault = shortCircuitFault;
        this.norm = norm;

        this.zd = zd;
        this.zi = zi;
        this.zh = zo;
        this.zd20hz = zth20hz;
        this.zh20hz = zoth20hz;

        this.eth = vdInit;

        this.iFortescue = new FortescueValue(id.abs(), io.abs(), ii.abs(), id.getArgument(), io.getArgument(), ii.getArgument());

        Complex vd = eth.add(dv);
        Complex vh = dvo;
        Complex vi = dvi;

        this.vFortescue = new FortescueValue(vd.abs(), vh.abs(), vi.abs(), vd.getArgument(), vh.getArgument(), vi.getArgument());

        isVoltageProfileUpdated = false;

    }

    public ShortCircuitResult(ShortCircuitFault shortCircuitFault, LfBus lfBus,
                              Complex id, Complex io, Complex ii,
                              Complex zd, Complex zo, Complex zi,
                              Complex vdInit, Complex dvd, Complex dvo, Complex dvi,
                              FeedersAtNetwork eqSysFeedersDirect, FeedersAtNetwork eqSysFeedersHomopolar, ShortCircuitNorm norm,
                              Complex i2d, Complex i2o, Complex i2i,
                              Complex v2dinit, Complex dv2d, Complex dv2o, Complex dv2i,
                              LfBus lfBus2, Complex zth20hz, Complex zoth20hz) {
        // Called for an unbalanced short circuit calculation with a common support
        this(shortCircuitFault, lfBus,
                id, io, ii,
                zd, zo, zi,
                vdInit, dvd, dvo, dvi,
                eqSysFeedersDirect, eqSysFeedersHomopolar, norm, zth20hz, zoth20hz);

        this.commonSupportResult = new CommonSupportResult(lfBus2, v2dinit,
                i2d, i2o, i2i, dv2d, dv2o, dv2i);

    }

    public Complex getZd() {
        return zd;
    }

    public Complex getEth() {
        return eth;
    }

    public Complex getZh() {
        return zh;
    }

    public Complex getZd20hz() {
        return zd20hz;
    }

    public Complex getZh20hz() {
        return zh20hz;
    }

    public ComplexMatrix getDiFromDv(LfBranch branch, FortescueValue dv1Fort, FortescueValue dv2Fort, FortescueType fType) {
        AdmittanceEquationSystem.AdmittanceType admType = AdmittanceEquationSystem.AdmittanceType.ADM_THEVENIN;
        if (fType == FortescueType.HOMOPOLAR) {
            admType = AdmittanceEquationSystem.AdmittanceType.ADM_THEVENIN_HOMOPOLAR;
        }

        ComplexMatrix yBranch = getAdmittanceMatrixBranch(branch, admType);
        Complex y11 = yBranch.get(0, 0);
        Complex y12 = yBranch.get(0, 1);
        Complex y21 = yBranch.get(1, 0);
        Complex y22 = yBranch.get(1, 1);

        Complex dv1 = ComplexUtils.polar2Complex(dv1Fort.getPositiveMagnitude(), dv1Fort.getPositiveAngle());
        Complex dv2 = ComplexUtils.polar2Complex(dv2Fort.getPositiveMagnitude(), dv2Fort.getPositiveAngle());
        if (fType == FortescueType.HOMOPOLAR) {
            dv1 = ComplexUtils.polar2Complex(dv1Fort.getZeroMagnitude(), dv1Fort.getZeroAngle());
            dv2 = ComplexUtils.polar2Complex(dv2Fort.getZeroMagnitude(), dv2Fort.getZeroAngle());
        } else if (fType == FortescueType.INVERSE) {
            dv1 = ComplexUtils.polar2Complex(dv1Fort.getNegativeMagnitude(), dv1Fort.getNegativeAngle());
            dv2 = ComplexUtils.polar2Complex(dv2Fort.getNegativeMagnitude(), dv2Fort.getNegativeAngle());
        }
        Complex di1 = y11.multiply(dv1).add(y12.multiply(dv2));
        Complex di2 = y21.multiply(dv1).add(y22.multiply(dv2));

        ComplexMatrix di = new ComplexMatrix(2, 1);
        di.set(0, 0, di1);
        di.set(1, 0, di2);

        return di;

    }

    public void updateFeedersResult() {
        if (!isVoltageProfileUpdated) {
            return;
        }
        // Building the structure to support the feeders result, a FeederResult is built from each Feeder in input
        feedersResultDirect = new HashMap<>(); // TODO : homopolar
        feedersResultsHomopolar = new HashMap<>();
        feedersResultsInverse = new HashMap<>();
        for (LfBus bus : lfNetwork.getBuses()) {
            // Init of feeder results
            FeedersAtBus busFeedersDirect = eqSysFeedersDirect.busToFeeders.get(bus);
            FeedersAtBusResult feedersAtBusResultDirect = new FeedersAtBusResult(busFeedersDirect);
            feedersResultDirect.put(bus, feedersAtBusResultDirect);

            if (shortCircuitFault.getType() == ShortCircuitFault.ShortCircuitType.TRIPHASED_GROUND) {
                continue;
            }

            // Homopolar
            FeedersAtBus busFeedersHomopolar = eqSysFeedersHomopolar.busToFeeders.get(bus);
            FeedersAtBusResult feedersAtBusResultHomopolar = new FeedersAtBusResult(busFeedersHomopolar);
            feedersResultsHomopolar.put(bus, feedersAtBusResultHomopolar);

            // Inverse
            FeedersAtBus busFeedersInverse = eqSysFeedersDirect.busToFeeders.get(bus); // for now we use direct feeder impedance to compute inverse current
            FeedersAtBusResult feedersAtBusResultInverse = new FeedersAtBusResult(busFeedersInverse);
            feedersResultsInverse.put(bus, feedersAtBusResultInverse);

        }

        // For each branch, we build the sum of currents at busses from branches
        // 1- Input is the voltage delta at each end of the branch
        // 2- Then we compute dI = [Ybranch].dV
        // 3- Then dI is added to the sum of current of bus
        // 4- The resulting sum of current at each bus is the current coming from branches,
        // which is equal to the current at bus injectors (Kirchhoff's law)
        // 5- Given the admittance of each feeder at bus, computed during building of AdmittanceEquationSystem,
        // we can then deduce the current contribution of each feeder, which is stored in feeder result
        branchDi1 = new HashMap<>();
        branchDi2 = new HashMap<>();

        for (LfBranch branch : lfNetwork.getBranches()) {
            LfBus bus1 = branch.getBus1();
            LfBus bus2 = branch.getBus2();
            if (bus1 != null && bus2 != null) {
                int busNum1 = bus1.getNum();
                int busNum2 = bus2.getNum();
                FortescueValue dv1Fort = busNum2Dv.get(busNum1);
                FortescueValue dv2Fort = busNum2Dv.get(busNum2);

                // Direct
                ComplexMatrix di = getDiFromDv(branch, dv1Fort, dv2Fort, FortescueType.DIRECT);
                Complex di1 = di.get(0, 0);
                Complex di2 = di.get(1, 0);

                FeedersAtBusResult resultDirectBus1Feeders = feedersResultDirect.get(bus1); // TODO : homopolar
                FeedersAtBusResult resultDirectBus2Feeders = feedersResultDirect.get(bus2); // TODO : homopolar
                // Feeders : compute the sum of currents from branches at each bus
                // dI coming from branch to the bus are added to the actual sum of current at bus
                resultDirectBus1Feeders.addItofeedersSum(di1);
                resultDirectBus2Feeders.addItofeedersSum(di2);

                if (shortCircuitFault.getType() == ShortCircuitFault.ShortCircuitType.TRIPHASED_GROUND) {
                    branchDi1.put(branch, new FortescueValue(di1.abs(), di1.getArgument()));
                    branchDi2.put(branch, new FortescueValue(di2.abs(), di2.getArgument()));
                    continue;
                }

                // Homopolar
                ComplexMatrix dio = getDiFromDv(branch, dv1Fort, dv2Fort, FortescueType.HOMOPOLAR);
                Complex dio1 = dio.get(0, 0);
                Complex dio2 = dio.get(1, 0);

                FeedersAtBusResult resultHomopolarBus1Feeders = feedersResultsHomopolar.get(bus1); // TODO : homopolar
                FeedersAtBusResult resultHomopolarBus2Feeders = feedersResultsHomopolar.get(bus2); // TODO : homopolar
                // Feeders : compute the sum of currents from branches at each bus
                // dI coming from branch to the bus are added to the actual sum of current at bus
                resultHomopolarBus1Feeders.addItofeedersSum(dio1);
                resultHomopolarBus2Feeders.addItofeedersSum(dio2);

                // Inverse
                ComplexMatrix dii = getDiFromDv(branch, dv1Fort, dv2Fort, FortescueType.INVERSE);
                Complex dii1 = dii.get(0, 0);
                Complex dii2 = dii.get(1, 0);

                FeedersAtBusResult resultInverseBus1Feeders = feedersResultsInverse.get(bus1); // TODO : homopolar
                FeedersAtBusResult resultInverseBus2Feeders = feedersResultsInverse.get(bus2); // TODO : homopolar
                // Feeders : compute the sum of currents from branches at each bus
                // dI coming from branch to the bus are added to the actual sum of current at bus
                resultInverseBus1Feeders.addItofeedersSum(dii1);
                resultInverseBus2Feeders.addItofeedersSum(dii2);

                branchDi1.put(branch, new FortescueValue(di1.abs(), dio1.abs(), dii1.abs(),
                        di1.getArgument(), dio1.getArgument(), dii1.getArgument()));
                branchDi2.put(branch, new FortescueValue(di2.abs(), dio2.abs(), dii2.abs(),
                        di2.getArgument(), dio2.getArgument(), dii2.getArgument()));

            }
        }

        // computing feeders contribution of each feeder at bus from the sum of currents at bus
        // and based on the admittance dispatch key of feeders
        for (LfBus bus : lfNetwork.getBuses()) {
            FeedersAtBusResult busFeedersDirect = feedersResultDirect.get(bus);
            busFeedersDirect.updateContributions();

            if (shortCircuitFault.getType() == ShortCircuitFault.ShortCircuitType.TRIPHASED_GROUND) {
                continue;
            }

            FeedersAtBusResult busFeedersHomopolar = feedersResultsHomopolar.get(bus);
            busFeedersHomopolar.updateContributions();

            FeedersAtBusResult busFeedersInverse = feedersResultsInverse.get(bus);
            busFeedersInverse.updateContributions();

        }
    }

    public Complex getId() {
        return ComplexUtils.polar2Complex(iFortescue.getPositiveMagnitude(), iFortescue.getPositiveAngle());
    }

    public Complex getIo() {
        return ComplexUtils.polar2Complex(iFortescue.getZeroMagnitude(), iFortescue.getZeroAngle());
    }

    public Complex getVd() {
        return ComplexUtils.polar2Complex(vFortescue.getPositiveMagnitude(), vFortescue.getPositiveAngle());
    }

    public FortescueValue getiFortescue() {
        return iFortescue;
    }

    public FortescueValue getvFortescue() {
        return vFortescue;
    }

    public LfBus getLfBus() {
        return lfBus;
    }

    public LfNetwork getLfNetwork() {
        return lfNetwork;
    }

    public Map<LfBus, FeedersAtBusResult> getFeedersResultDirect() {
        return feedersResultDirect;
    }

    public Map<LfBus, FeedersAtBusResult> getFeedersResultsHomopolar() {
        return feedersResultsHomopolar;
    }

    public Map<LfBus, FeedersAtBusResult> getFeedersResultsInverse() {
        return feedersResultsInverse;
    }

    public List<FortescueValue> getBusNum2Dv() {
        return busNum2Dv;
    }

    public Map<LfBranch, FortescueValue> getBranchDi1() {
        return branchDi1;
    }

    public Map<LfBranch, FortescueValue> getBranchDi2() {
        return branchDi2;
    }

    public boolean isVoltageProfileUpdated() {
        return isVoltageProfileUpdated;
    }

    public ShortCircuitFault getShortCircuitFault() {
        return shortCircuitFault;
    }

    public double getIbase() {
        // I(A) = I(pu) * I(base)
        // I(base) = SB(MVA) * 10e6 / (VB(kV) * 10e3)
        return 1000. * 100. / lfBus.getNominalV();
    }

    public double getPeakCoef(double r, double x) {
        if (x == 0.) {
            throw new IllegalArgumentException("Peak current could not be computed because Xth is zero = ");
        }
        return getPeakCoef(r / x);
    }

    public double getPeakCoef(double rOverx) {
        return 1.02 + 0.98 * Math.exp(-3. * rOverx);
    }

    public double getPeakCoefb() {
        double r = zd.getReal();
        double x = zd.getImaginary();
        if (x == 0.) {
            throw new IllegalArgumentException("Peak current could not be computed because Xth is zero = ");
        }
        double factor = 1.15;
        if (r / x < 0.3) {
            factor = 1.;
        }
        double coef = getPeakCoef(r, x) * factor;
        if (lfBus.getNominalV() <= 20.) {
            coef = Math.min(coef, 1.8);
        } else {
            coef = Math.min(coef, 2.0);
        }
        return coef;
    }

    public double getPeakCoefcDirect() {
        // this is the simplified formula only using direct 20Hz Thevenin impedance
        double rc = zd20hz.getReal();
        double xc = zd20hz.getImaginary();

        if (xc == 0.) {
            throw new IllegalArgumentException("Peak current could not be computed because Xth is zero = ");
        }
        return getPeakCoef(rc / xc * 20. / 50.);
    }

    public double getPeakCoefc() {
        double rc = zd20hz.getReal();
        double xc = zd20hz.getImaginary();
        double roc = zh20hz.getReal();
        double xoc = zh20hz.getImaginary();

        if (xc == 0.) {
            throw new IllegalArgumentException("Peak current could not be computed because Xth is zero = ");
        }
        return getPeakCoef((2. * rc + roc) / (2. * xc + xoc) * 20. / 50.);
    }

    public double getIpeakc() {
        return getIk().abs() * getPeakCoefc() * Math.sqrt(2.);
    }

    public Complex getIk() {

        ShortCircuitFault.ShortCircuitType faultType = shortCircuitFault.getType();
        Complex ik;

        switch (faultType) {

            case ShortCircuitFault.ShortCircuitType.MONOPHASED:
                ik = getIk1pp();
                break;
            case ShortCircuitFault.ShortCircuitType.TRIPHASED_GROUND:
                ik = getIkpp();
                break;
            case ShortCircuitFault.ShortCircuitType.BIPHASED:
                ik = getIk2pp();
                break;
            case ShortCircuitFault.ShortCircuitType.BIPHASED_GROUND:
                ik = getIke2epp();
                break;
            default:
                ik = getIk();
        }

        return ik;
    }

    public Complex getIkpp() {
        // for a Triphased fault, by definition Ik"(A) = c * Un / (sqrt(3) * Zk)
        double c = norm.getCmaxVoltageFactor(lfBus.getNominalV());
        Complex id = ComplexUtils.polar2Complex(iFortescue.getPositiveMagnitude(), iFortescue.getPositiveAngle()); // id(pu) = Eth(pu) / Zk(pu) // TODO: check if radians
        // Ik"(kA) = c * id(pu) * I(base) / (sqrt(3) * 1000) : the 1000 factor is to move from A to kA
        return id.multiply(c).divide(Math.sqrt(3.) * 1000.).multiply(getIbase());
    }

    public Complex getIk1pp() {
        // for a Monphased fault, by definition Ik"1(A) = c * Un * sqrt(3) / Zk
        double c = norm.getCmaxVoltageFactor(lfBus.getNominalV());
        Complex id = ComplexUtils.polar2Complex(iFortescue.getPositiveMagnitude(), iFortescue.getPositiveAngle()); // id(pu) = Eth(pu) / Zk(pu) // TODO: check if radians
        // Ik"1(kA) = c * id(pu) * sqrt(3) * I(base) / 1000 : the 1000 factor is to move from A to kA
        return id.multiply(c).divide(1000.).multiply(Math.sqrt(3.) * getIbase());
    }

    public Complex getIk2pp() {
        // for a biphased fault (no ground), by definition Ik2" = c * Un / abs(Zd + Zi + Zf)
        // given that Ib = j * sqrt(3) * tM * [Vinit] / (Zdf + Zif +Zf)  and that Id = Ib * j / sqrt(3) we have Ik2" = c * Id
        double c = norm.getCmaxVoltageFactor(lfBus.getNominalV());
        Complex id = ComplexUtils.polar2Complex(iFortescue.getPositiveMagnitude(), iFortescue.getPositiveAngle());
        return id.multiply(c).divide(1000.).multiply(getIbase());
    }

    public Complex getIk2el2pp() {
        // for a biphased ground fault , by definition Ik2EL2" = c * Ib / sqrt(3)
        // given that Ib = Io + a².Id + a.Ii
        Complex a = new Complex(-0.5, FastMath.sqrt(3.) / 2);
        double c = norm.getCmaxVoltageFactor(lfBus.getNominalV());
        Complex id = ComplexUtils.polar2Complex(iFortescue.getPositiveMagnitude(), iFortescue.getPositiveAngle());
        Complex io = ComplexUtils.polar2Complex(iFortescue.getZeroMagnitude(), iFortescue.getZeroAngle());
        Complex ii = ComplexUtils.polar2Complex(iFortescue.getNegativeMagnitude(), iFortescue.getNegativeAngle());
        Complex ib = io.add(a.multiply(a).multiply(id)).add(ii.multiply(a));
        return ib.multiply(c).divide(1000. * Math.sqrt(3.)).multiply(getIbase());
    }

    public Complex getIk2el3pp() {
        // for a biphased ground fault , by definition Ik2EL3" = c * Ic / sqrt(3)
        // given that Ic = Io + a.Id + a².Ii
        Complex a = new Complex(-0.5, FastMath.sqrt(3.) / 2);
        double c = norm.getCmaxVoltageFactor(lfBus.getNominalV());
        Complex id = ComplexUtils.polar2Complex(iFortescue.getPositiveMagnitude(), iFortescue.getPositiveAngle());
        Complex io = ComplexUtils.polar2Complex(iFortescue.getZeroMagnitude(), iFortescue.getZeroAngle());
        Complex ii = ComplexUtils.polar2Complex(iFortescue.getNegativeMagnitude(), iFortescue.getNegativeAngle());
        Complex ic = io.add(a.multiply(a).multiply(ii)).add(id.multiply(a));
        return ic.multiply(c).divide(1000. * Math.sqrt(3.)).multiply(getIbase());
    }

    public Complex getIke2epp() {
        // by definition IkE2E" = Ik2EL3" + Ik2EL2"
        return getIk2el2pp().add(getIk2el3pp());
    }

    public Complex getDefaultIk() {
        // Ik is a default value used for testing
        // IccBase = sqrt(3) * Eth(pu) / Zth(pu) * SB(MVA) * 10e6 / (VB(kV) * 10e3)
        double magnitudeIccBase = Math.sqrt(3.) * iFortescue.getPositiveMagnitude() * 1000. * 100. / lfBus.getNominalV();
        double angleIcc = iFortescue.getPositiveAngle();
        double magnitudeIcc = magnitudeIccBase;
        Complex val = ComplexUtils.polar2Complex(magnitudeIcc, angleIcc);

        // Ik = c * Un / (sqrt(3) * Zk) = c / sqrt(3) * Eth(pu) / Zth(pu) * Sb / Vb
        return val.multiply(norm.getCmaxVoltageFactor(lfBus.getNominalV()) / 1000.);
    }

    public Complex getSk() {
        //Sk" = |Unom|*Ik"*sqrt(3)
        return getIk().multiply(Math.sqrt(3) * lfBus.getV() * lfBus.getNominalV());
    }

    public double getMaxRoverX() {
        double ratio = 0.0;
        double epsilon = 0.000001;
        for (LfBranch branch : getLfNetwork().getBranches()) {
            Complex zBranch = new Complex(branch.getPiModel().getR(), branch.getPiModel().getX());
            FortescueValue di1 = getBranchDi1().get(branch);
            FortescueValue di2 = getBranchDi2().get(branch);
            if (zBranch.getImaginary() < epsilon) {
                continue;
            } else if (di1.getPositiveMagnitude() > epsilon || di2.getPositiveMagnitude() > epsilon) {
                // we only look at ratio for branches with significant short circuit currents
                ratio = Math.max(ratio, zBranch.getReal() / zBranch.getImaginary());
            }
        }
        return ratio;
    }

    public void setTrueVoltageProfileUpdate() {
        isVoltageProfileUpdated = true;
    }

    public void createEmptyFortescueVoltageVector(int nbBusses) {
        List<FortescueValue> busNum2Dv = new ArrayList<>();
        for (int i = 0; i < nbBusses; i++) {
            FortescueValue mdV = new FortescueValue(0., 0.);
            busNum2Dv.add(mdV);
        }
        this.busNum2Dv = busNum2Dv;
    }

    public void fillVoltageInFortescueVector(int busNum, Complex dV) {
        this.busNum2Dv.set(busNum, new FortescueValue(dV.abs(), dV.getArgument()));
    }

    public void fillVoltageInFortescueVector(int busNum, Complex dVd, Complex dVo, Complex dVi) {
        this.busNum2Dv.set(busNum, new FortescueValue(dVd.abs(), dVo.abs(), dVi.abs(), dVd.getArgument(), dVo.getArgument(), dVi.getArgument()));
    }

    public void setLfNetwork(LfNetwork lfNetwork) {
        this.lfNetwork = lfNetwork;
    }

    static ComplexMatrix getAdmittanceMatrixBranch(LfBranch branch,
                                                   AdmittanceEquationSystem.AdmittanceType admittanceType) {

        // TODO : code duplicated with the admittance equation system, should be un-duplicated
        PiModel piModel = branch.getPiModel();
        if (piModel.getX() == 0) {
            throw new IllegalArgumentException("Branch '" + branch.getId() + "' has reactance equal to zero");
        }

        if (piModel.getZ() == 0) {
            throw new IllegalArgumentException("Branch '" + branch.getId() + "' has Z equal to zero");
        }
        Complex z = new Complex(piModel.getR(), piModel.getX());
        Complex y1 = new Complex(piModel.getG1(), piModel.getB1());
        Complex y2 = new Complex(piModel.getG2(), piModel.getB1());
        Complex rho = ComplexUtils.polar2Complex(piModel.getR1(), Math.toRadians(piModel.getA1()));

        double admCoef = 1.;
        if (admittanceType == AdmittanceEquationSystem.AdmittanceType.ADM_THEVENIN_HOMOPOLAR) {
            admCoef = AdmittanceConstants.COEF_XO_XD; // Xo = 3 * Xd as a first approximation : TODO : improve when more data available
            rho = new Complex(rho.getReal(), 0.); // In homopolar, the phase shift is canceled TODO : verify
        }
        Complex y11 = y1.add(z.reciprocal()).multiply(rho.abs() * rho.abs()).multiply(admCoef);
        Complex y12 = rho.conjugate().multiply(z.reciprocal()).multiply(-1.).multiply(admCoef); // TODO : check if case of phase shift if it is the conjugate of direct y
        Complex y21 = rho.multiply(z.reciprocal()).multiply(-1.).multiply(admCoef);
        Complex y22 = y2.add(z.reciprocal()).multiply(admCoef);

        ComplexMatrix admittance = new ComplexMatrix(2, 2);
        admittance.set(0, 0, y11);
        admittance.set(0, 1, y12);
        admittance.set(1, 0, y21);
        admittance.set(1, 1, y22);
        return admittance;
    }
}
