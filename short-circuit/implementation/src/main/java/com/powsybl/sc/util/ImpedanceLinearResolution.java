/**
 * Copyright (c) 2022, Jean-Baptiste Heyberger & Geoffroy Jamgotchian
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.util;

import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.complex.ComplexUtils;
import org.apache.commons.math3.util.Pair;

import java.util.*;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class ImpedanceLinearResolution {

    // This class is used to resolve problems with a similar structure
    // [ Vof ] = -tM * inv(Yo) * M * [ Iof ]
    // [ Vdf ] = -tM * inv(Yd) * M * [ Idf ] + tM * [ V(init) ]
    // [ Vif ] = -tM * inv(Yd) * M * [ Iif ]

    //
    // [ Vx ]                        [ Ix ]           [ Vx_init ]
    // [ Vy ] = -t[En]*inv(Y)*[En] * [ Iy ] + t[En] * [ Vy_init ]
    //

    private final LfNetwork network;

    private final ImpedanceLinearResolutionParameters parameters;

    public final Map<LfBus, ImpedanceLinearResolutionResult> results = new HashMap<>();

    public ImpedanceLinearResolution(LfNetwork network, ImpedanceLinearResolutionParameters parameters) {
        this.network = Objects.requireNonNull(network);
        this.parameters = Objects.requireNonNull(parameters);
    }

    public record ImpedanceUpperTriangle(Complex z12, Complex z21, Complex z22) {
    }

    public class ImpedanceLinearResolutionResult {

        private final LfBus bus;
        private final Complex zthEq;
        private final Complex eth; // Thevenin voltage
        private final Complex zthEq20Hz;

        // zknf corresponds to extracted impedance term z(k,nf) from inv(Y) to be able to compute Vk = z(k,nf).icc.
        // k is the bus index of the voltage Vk we want to compute and nf is the index of faulted bus
        private Complex zknf;

        // This is a map to easily access the impedance terms resulting from inv(Y), extracting only useful z(i,j) terms
        // the key stores the number of the bus k at which we want to compute the voltage from formula Vk = z(k,nf).icc, the value stores the resolved value [Res] = inv(Y)*[En],
        // with n of vector [En] corresponding to the studied short circuit fault and values at lines of [Res] corresponding real and imaginary parts at bus in key
        private Map<Integer, Complex> busToZknf;

        private FeedersAtNetwork eqSysFeeders;

        private List<TwoBusImpedanceLinearResolutionResult> twoBusResults; // we store here all necessary information for all two-bus faults with first bus equal to LfBus = bus

        ImpedanceLinearResolutionResult(LfBus bus, Complex zth, Complex eth, Complex zthEq20Hz) {
            this.bus = bus;
            this.zthEq = zth;
            this.eth = eth;
            this.zthEq20Hz = zthEq20Hz;
        }

        public LfBus getBus() {
            return bus;
        }

        public Complex getEth() {
            return eth;
        }

        public Complex getZthEq() {
            return zthEq;
        }

        public Complex getZthEq20Hz() {
            return zthEq20Hz;
        }

        public Map<Integer, Complex> getBusToZknf() {
            return busToZknf;
        }

        public Complex getZknf() {
            return zknf;
        }

        public FeedersAtNetwork getEqSysFeeders() {
            return eqSysFeeders;
        }

        public List<TwoBusImpedanceLinearResolutionResult> getTwoBusResults() {
            return twoBusResults;
        }

        public void updatezknf(Complex enBus) {
            this.zknf = enBus;
        }

        public void updateWithVoltagesdelta(AdmittanceMatrix y, DenseMatrix dEn, int numDef, FeedersAtNetwork feeders) {
            busToZknf = y.getDeltaV(dEn, numDef);
            eqSysFeeders = feeders; // contains necessary data to update the contribution of feeders for each short circuit
        }

        public void printResult() {
            System.out.println(" Zth(" + bus.getId() + ") = " + zthEq);

            if (parameters.isVoltageUpdate()) {
                /*for (Map.Entry<Integer, Double> b : dvr1.entrySet()) {
                    int busi = b.getKey();
                    double dv = b.getValue();
                    System.out.println(" busNum[" + busi + "] : dvr = " +  dv);
                    System.out.println(" busNum[" + busi + "] : dvi = " +  dvi1.get(busi));
                }*/
            }
        }

        public void addTwoBusResult(LfBus bus2, Complex initV2, ImpedanceUpperTriangle zT, ImpedanceUpperTriangle zTAt20Hz, int numBus2Fault) {
            // numBus2Fault is store to easily get the extraction vector for the second bus, in order to compute the full voltage export if required
            TwoBusImpedanceLinearResolutionResult twoBusResult = new TwoBusImpedanceLinearResolutionResult(
                    bus2, initV2, zT, zTAt20Hz, numBus2Fault);

            if (twoBusResults == null) {
                twoBusResults = new ArrayList<>();
            }
            twoBusResults.add(twoBusResult);
        }
    }

    public static void checkMatrixExtractionConsistency(Complex z1, Complex z2, LfBus lfBus1, LfBus lfBus2) {
        double epsilon = 0.00001;

        String bus1Id = lfBus1.getId();
        String bus2Id = lfBus2.getId();

        if (Math.abs(z1.getReal() - z2.getReal()) > epsilon) {
            throw new IllegalArgumentException("Impedance block values rth : z11 and Z22 of nodes Bus1 = {" + bus1Id + "} and Bus2 = {" + bus2Id
                    + "} have inconsitant values z11= " + z1.getReal() + " y1i2i=" + z2.getReal());
        }

        if (Math.abs(z2.getImaginary() - z1.getImaginary()) > epsilon) {
            throw new IllegalArgumentException("Impedance block values xth : z12 and Z21 of nodes Bus1 = {" + bus1Id + "} and Bus2 = {" + bus2Id
                    + "} have inconsitant values z12= " + z1.getImaginary() + " z21=" + z2.getImaginary());
        }
    }

    public static void checkMatrixExtractionConsistency(Complex z1, Complex z2, LfBus lfBus1) {
        // case of diagonal matrix terms
        checkMatrixExtractionConsistency(z1, z2, lfBus1, lfBus1);
    }

    public static LfBus getLfBusFromIidmBranch(String iidmBranchId, int branchSide, LfNetwork lfNetwork) {
        LfBus bus = null;
        for (LfBranch lfBranch : lfNetwork.getBranches()) {
            String branchId = lfBranch.getId();
            LfBranch.BranchType lfType = lfBranch.getBranchType();

            if (lfType == LfBranch.BranchType.LINE || lfType == LfBranch.BranchType.TRANSFO_2) {
                if (iidmBranchId.equals(branchId)) {
                    if (branchSide == 1) {
                        bus = lfBranch.getBus1();

                    } else {
                        bus = lfBranch.getBus2();
                    }
                    break;
                }
            } else if (lfType == LfBranch.BranchType.TRANSFO_3_LEG_1) {
                String legId = iidmBranchId + "_leg_1";
                if (legId.equals(branchId) && branchSide == 1) {
                    // assumption made: side 2 bus is always the star bus of the T3W
                    bus = lfBranch.getBus1();
                    break;
                }

            } else if (lfType == LfBranch.BranchType.TRANSFO_3_LEG_2) {
                String legId = iidmBranchId + "_leg_2";
                if (legId.equals(branchId) && branchSide == 2) {
                    // assumption made: side 2 bus is always the star bus of the T3W
                    bus = lfBranch.getBus1();
                    break;
                }

            } else if (lfType == LfBranch.BranchType.TRANSFO_3_LEG_3) {
                String legId = iidmBranchId + "_leg_3";
                if (legId.equals(branchId) && branchSide == 3) {
                    // assumption made: side 2 bus is always the star bus of the T3W
                    bus = lfBranch.getBus1();
                    break;
                }
            }
        }
        return bus;

    }

    public void run() {

        FeedersAtNetwork equationsSystemFeeders = new FeedersAtNetwork();
        EquationSystem<VariableType, EquationType> equationSystem
                = AdmittanceEquationSystem.create(network, new VariableSet<>(), parameters.getAdmittanceType(), parameters.getTheveninVoltageProfileType(),
                parameters.getTheveninPeriodType(), parameters.isTheveninIgnoreShunts(), equationsSystemFeeders, parameters.getAcLoadFlowParameters(),
                        AdmittanceEquationSystem.FrequencyType.FREQ_50_HZ);

        FeedersAtNetwork equationsSystemFeeders20hz = new FeedersAtNetwork();
        EquationSystem<VariableType, EquationType> equationSystem20hz
                = AdmittanceEquationSystem.create(network, new VariableSet<>(), parameters.getAdmittanceType(), parameters.getTheveninVoltageProfileType(),
                parameters.getTheveninPeriodType(), parameters.isTheveninIgnoreShunts(), equationsSystemFeeders20hz, parameters.getAcLoadFlowParameters(),
                        AdmittanceEquationSystem.FrequencyType.FREQ_20_HZ);

        List<LfBus> inputBuses = new ArrayList<>();
        List<Pair<LfBus, LfBus>> busPairList = new ArrayList<>();

        for (CalculationLocation locationInfo : parameters.getCalculationLocations()) {
            String iidmBranchId = locationInfo.getIidmBusInfo().getKey();
            int branchSide = locationInfo.getIidmBusInfo().getValue();

            LfBus bus1 = getLfBusFromIidmBranch(iidmBranchId, branchSide, network);
            if (bus1 != null) {
                if (!inputBuses.contains(bus1)) {
                    inputBuses.add(bus1);
                }
                locationInfo.setLfBusInfo(bus1.getId());
            }
            boolean needsSecondBus = locationInfo.getLocationType() == CalculationLocation.LocationType.LINE
                    || locationInfo.getLocationType() == CalculationLocation.LocationType.BIPHASED_COMMON_SUPPORT;
            if (needsSecondBus) {
                String iidmBranchId2 = locationInfo.getIidmBus2Info().getKey();
                int branchSide2 = locationInfo.getIidmBus2Info().getValue();
                LfBus bus2 = getLfBusFromIidmBranch(iidmBranchId2, branchSide2, network);
                if (bus2 != null) {
                    if (!inputBuses.contains(bus2)) {
                        inputBuses.add(bus2);
                    }
                    locationInfo.setLfBus2Info(bus2.getId());
                    if (bus1 != null) {
                        busPairList.add(new Pair<>(bus1, bus2));
                    }
                }
            }
        }

        // Build of the structure of the extraction matrices
        //               <------------------->  N
        //          ^ [ .....   0    0   ..... ]
        //          | [         0    0         ]
        //          | [                        ]     M = y.getRowCount()
        // [En] = M | [         1    0         ]     N = 2 * inputBusses.size()
        //          | [         0    1         ]
        //          | [                        ]
        //          | [         0    0         ]
        //          - [ .....   0    0   ......]
        //                      ^    ^
        //                  En_x_k   |
        //                        En_y_k
        //
        //  - En_x_k is the vector t[ 0 0 ... 0 0 1 0 0 0 ... 0 0 ] where 1 corresponds to the line/column of the bus k where the real part of Z matrix is modelled
        //  - En_y_k is the vector t[ 0 0 ... 0 0 0 1 0 0 ... 0 0 ] where 1 corresponds to the line/column of the bus k where the imaginary part of Z matrix is modelled

        // Step 1 : build the extraction vectors
        try (AdmittanceMatrix yd = new AdmittanceMatrix(equationSystem, parameters.getMatrixFactory(), network);
             AdmittanceMatrix yd20hz = new AdmittanceMatrix(equationSystem20hz, parameters.getMatrixFactory(), network)) {

            DenseMatrix en = new DenseMatrix(yd.getRowCount(), 2 * inputBuses.size());
            DenseMatrix en20hz = new DenseMatrix(yd.getRowCount(), 2 * inputBuses.size());
            List<Integer> tEn2Col = new ArrayList<>();

            int numBusFault = 0;
            for (LfBus lfBus : inputBuses) {

                int yRowx = yd.getRowBus(lfBus.getNum(), EquationType.BUS_YR);
                int yColx = yd.getColBus(lfBus.getNum(), VariableType.BUS_VR);
                int yRowy = yd.getRowBus(lfBus.getNum(), EquationType.BUS_YI);
                int yColy = yd.getColBus(lfBus.getNum(), VariableType.BUS_VI);

                //Step 2: fill the extraction matrices based on each extraction vector
                // [tEn_x][1,j]= 1 if j = yColRth and 0 else
                // [tEn_y][1,j]= 1 if j = yColXth and 0 else
                //tEn.add(2 * numBusFault, yColx, 1.0);
                //tEn.add(2 * numBusFault + 1, yColy, 1.0);

                //the extraction matrix tEn is replaced by a list to directly get the elements rth and xth in inv(Y) * En as tEn is very sparse
                tEn2Col.add(yColx);
                tEn2Col.add(yColy);

                // [En_x][i,1]= 1 if i = yRowRth and 0 else
                // [En_y][i,1]= 1 if i = yRowXth and 0 else
                en.add(yRowx, 2 * numBusFault, 1.0);
                en.add(yRowy, 2 * numBusFault + 1, 1.0);
                en20hz.add(yRowx, 2 * numBusFault, 1.0);
                en20hz.add(yRowy, 2 * numBusFault + 1, 1.0);

                numBusFault++;
            }

            //Step 3 : use the LU inversion of Y to get Rth and Xth
            DenseMatrix zfromLu = en;
            yd.solveTransposed(zfromLu);

            DenseMatrix zfromLu20Hz = en20hz;
            yd20hz.solveTransposed(zfromLu20Hz);

            // Each diagonal bloc of tEn * inv(Y) * En is:
            //     [Zkk] = [ r -x ]
            //             [ x  r ]

            //DenseMatrix z = (DenseMatrix) tEn.times(en);

            Complex eth = new Complex(1.0);

            numBusFault = 0;
            for (LfBus lfBus : inputBuses) {

                int yRow1x = yd.getRowBus(lfBus.getNum(), EquationType.BUS_YR);
                int yRow1y = yd.getRowBus(lfBus.getNum(), EquationType.BUS_YI);

                if (parameters.getTheveninVoltageProfileType() == AdmittanceEquationSystem.AdmittanceVoltageProfileType.CALCULATED) {
                    eth = ComplexUtils.polar2Complex(lfBus.getV(), Math.toRadians(lfBus.getAngle()));
                }

                // This is equivalent to get the diagonal blocks of tEn * inv(Y) * En but taking advantage of the sparsity of tEn
                // The diagonal terms of the impedance matrix are the Thevenin impedance at each corresponding bus
                Complex zth = new Complex(zfromLu.get(tEn2Col.get(2 * numBusFault), 2 * numBusFault), -zfromLu.get(tEn2Col.get(2 * numBusFault), 1 + 2 * numBusFault));
                Complex zthBis = new Complex(zfromLu.get(tEn2Col.get(1 + 2 * numBusFault), 1 + 2 * numBusFault), zfromLu.get(tEn2Col.get(1 + 2 * numBusFault), 2 * numBusFault));
                //     [Zth_kk] = [ rth -xth ] --> Zth
                //                [ xth  rth ] --> Zth_Bis
                checkMatrixExtractionConsistency(zth, zthBis, lfBus);

                Complex zth20Hz = new Complex(zfromLu20Hz.get(tEn2Col.get(2 * numBusFault), 2 * numBusFault), -zfromLu20Hz.get(tEn2Col.get(2 * numBusFault), 1 + 2 * numBusFault));

                ImpedanceLinearResolutionResult res = new ImpedanceLinearResolutionResult(lfBus, zth, eth, zth20Hz);

                //step 4 : add deltaVoltage vectors if required
                //extract values at the faulting bus that will be used to compute the post-fault voltage delta at bus

                // [ V ] = -inv(Y) * M * [ Icc ] + [ V(init) ]
                // Where is the short circuit current vector [Icc] = [ 0 ; 0 .... 0 ; icc ; 0 .... 0 ; 0 ] with non zero term corresponds to faulted node nf
                // Where [V] is the resulting voltage profile avec short circuit, [V] = [ V1 ; V2 ; ..... ; Vk ; .... ; Vm ]
                // Vk = z(k,nf) . icc
                // we need then to extract z(k,nf) if we want to compute updated Vk from computed icc value

                Complex zknf = new Complex(zfromLu.get(yRow1x, 2 * numBusFault), zfromLu.get(yRow1y, 2 * numBusFault));

                res.updatezknf(zknf);

                // handle biphased common support faults extra data
                for (Pair<LfBus, LfBus> busPair : busPairList) {
                    LfBus bus1 = busPair.getKey();
                    if (bus1 == lfBus) {
                        // lfbus is also the first bus for a biphased common support, we store as an extension necessary additional data for the linear resolution post-processing
                        LfBus bus2 = busPair.getValue();
                        int yCol1x = yd.getColBus(lfBus.getNum(), VariableType.BUS_VR);
                        int yCol1y = yd.getColBus(lfBus.getNum(), VariableType.BUS_VI);
                        int yCol2x = yd.getColBus(bus2.getNum(), VariableType.BUS_VR);
                        int yCol2y = yd.getColBus(bus2.getNum(), VariableType.BUS_VI);

                        int numBus2Fault = 0; // get the right column of extraction matrix of bus2
                        boolean bus2found = false;
                        for (LfBus lfBus2 : inputBuses) {
                            if (lfBus2 == bus2) {
                                bus2found = true;
                                break;
                            }
                            numBus2Fault++;
                        }

                        if (!bus2found) {
                            throw new IllegalArgumentException(" Second bus = " + bus2.getId() + " of bi-phased or branch fault : not found in the extraction matrix");
                        }

                        Complex z22 = new Complex(zfromLu.get(yCol2x, 2 * numBus2Fault), -zfromLu.get(yCol2x, 2 * numBus2Fault + 1));
                        Complex z22bis = new Complex(zfromLu.get(yCol2y, 2 * numBus2Fault + 1), zfromLu.get(yCol2y, 2 * numBus2Fault));

                        Complex z21 = new Complex(zfromLu.get(yCol2x, 2 * numBusFault), -zfromLu.get(yCol2x, 2 * numBusFault + 1));
                        Complex z21bis = new Complex(zfromLu.get(yCol2y, 2 * numBusFault + 1), zfromLu.get(yCol2y, 2 * numBusFault));

                        Complex z12 = new Complex(zfromLu.get(yCol1x, 2 * numBus2Fault), -zfromLu.get(yCol1x, 2 * numBus2Fault + 1));
                        Complex z12bis = new Complex(zfromLu.get(yCol1y, 2 * numBus2Fault + 1), zfromLu.get(yCol1y, 2 * numBus2Fault));

                        // By construction we have for each block
                        //
                        // Zij = [ rij  -xij ] --> enZ
                        //       [ xij   rij ] --> enZbis
                        //
                        // We need to check consistency of terms enZ and enZbis for each block
                        checkMatrixExtractionConsistency(z22, z22bis, lfBus, bus2);
                        checkMatrixExtractionConsistency(z21, z21bis, lfBus, bus2);
                        checkMatrixExtractionConsistency(z12, z12bis, lfBus, bus2);

                        // Same extraction, at 20Hz: yd and yd20hz share the same column/row layout
                        // (same network structure, only the admittance values differ with frequency),
                        // exactly like zth20Hz reuses tEn2Col (built from yd) to index into zfromLu20Hz.
                        Complex z22At20Hz = new Complex(zfromLu20Hz.get(yCol2x, 2 * numBus2Fault), -zfromLu20Hz.get(yCol2x, 2 * numBus2Fault + 1));
                        Complex z22bis20Hz = new Complex(zfromLu20Hz.get(yCol2y, 2 * numBus2Fault + 1), zfromLu20Hz.get(yCol2y, 2 * numBus2Fault));

                        Complex z21At20Hz = new Complex(zfromLu20Hz.get(yCol2x, 2 * numBusFault), -zfromLu20Hz.get(yCol2x, 2 * numBusFault + 1));
                        Complex z21bis20Hz = new Complex(zfromLu20Hz.get(yCol2y, 2 * numBusFault + 1), zfromLu20Hz.get(yCol2y, 2 * numBusFault));

                        Complex z12At20Hz = new Complex(zfromLu20Hz.get(yCol1x, 2 * numBus2Fault), -zfromLu20Hz.get(yCol1x, 2 * numBus2Fault + 1));
                        Complex z12bis20Hz = new Complex(zfromLu20Hz.get(yCol1y, 2 * numBus2Fault + 1), zfromLu20Hz.get(yCol1y, 2 * numBus2Fault));

                        checkMatrixExtractionConsistency(z22At20Hz, z22bis20Hz, lfBus, bus2);
                        checkMatrixExtractionConsistency(z21At20Hz, z21bis20Hz, lfBus, bus2);
                        checkMatrixExtractionConsistency(z12At20Hz, z12bis20Hz, lfBus, bus2);

                        Complex eth2 = new Complex(1.0);
                        if (parameters.getTheveninVoltageProfileType() == AdmittanceEquationSystem.AdmittanceVoltageProfileType.CALCULATED) {
                            eth2 = ComplexUtils.polar2Complex(bus2.getV(), Math.toRadians(bus2.getAngle()));
                        }

                        res.addTwoBusResult(bus2, eth2, new ImpedanceUpperTriangle(z12, z21, z22), new ImpedanceUpperTriangle(z22At20Hz, z21At20Hz, z12At20Hz), numBus2Fault);
                    }
                }

                //if required, do the same for all busses from the grid
                if (parameters.isVoltageUpdate()) {
                    // This equivalent to store  inv(Y)*[En]
                    res.updateWithVoltagesdelta(yd, zfromLu, numBusFault, equationsSystemFeeders);
                    if (res.twoBusResults != null) {
                        // update for each biphased common support fault
                        for (TwoBusImpedanceLinearResolutionResult twoBusResultPart : res.twoBusResults) {
                            twoBusResultPart.updateWithVoltagesdelta2(yd, zfromLu);
                        }
                    }
                }

                //res.printResult();

                this.results.put(lfBus, res);
                numBusFault++;
            }
        }
    }

}
