package com.powsybl.openloadflow.reduction;

import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.math.matrix.Matrix;
import com.powsybl.openloadflow.equations.*;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.reduction.equations.ReductionEquationSystem;
import com.powsybl.openloadflow.reduction.equations.ReductionEquationSystemCreationParameters;
import com.powsybl.openloadflow.util.MatrixUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * @author JB Heyberger <jean-baptiste.heyberger at rte-france.com>
 */
public class ReductionEngine {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReductionEngine.class);

    private final List<LfNetwork> networks;

    private final ReductionParameters parameters;

    public List<LfNetwork> getNetworks() {
        return networks;
    }

    private ReductionHypotheses reductionHypo;

    public ReductionHypotheses getReductionHypo() {
        return reductionHypo;
    }

    private ReductionResults results;

    public ReductionResults getReductionResults() {
        return results;
    }

    public class ReductionResults {

        private Matrix minusYeq;

        private Map<Integer, Double> busNumToRealIeq;

        private Map<Integer, Double>  busNumToImagIeq;

        private Map<Integer, Integer> yeqRowNumToBusNum; //gives the bus number from the row number of the Y submatrix in input

        private Map<Integer, EquationType> yeqRowNumToBusType; //gives the type of equation from the row number of the Y submatrix in input

        private Map<Integer, Integer> yeqColNumToBusNum; //gives the bus number from the row number of the Y submatrix in input

        private Map<Integer, VariableType> yeqColNumToBusType; //gives the type of variable from the row number of the Y submatrix in input

        ReductionResults(Matrix minusYeq) {
            this.minusYeq = minusYeq;
            busNumToRealIeq = new HashMap<>();
            busNumToImagIeq = new HashMap<>();
            yeqRowNumToBusNum = new HashMap<>();
            yeqRowNumToBusType = new HashMap<>();
            yeqColNumToBusNum = new HashMap<>();
            yeqColNumToBusType = new HashMap<>();
        }

        public Matrix getMinusYeq() {
            return minusYeq;
        }

        public Map<Integer, Double> getBusNumToRealIeq() {
            return busNumToRealIeq;
        }

        public Map<Integer, Double> getBusNumToImagIeq() {
            return busNumToImagIeq;
        }

        public Map<Integer, Integer> getYeqRowNumToBusNum() {
            return yeqRowNumToBusNum;
        }

        public Map<Integer, EquationType> getYeqRowNumToBusType() {
            return yeqRowNumToBusType;
        }

        public Map<Integer, Integer> getYeqColNumToBusNum() {
            return yeqColNumToBusNum;
        }

        public Map<Integer, VariableType> getYeqColNumToBusType() {
            return yeqColNumToBusType;
        }

        void printReductionResults() {
            for (Map.Entry<Integer, Double> i : getBusNumToRealIeq().entrySet()) {
                int nb = i.getKey();
                System.out.println("Bus = " + nb + " has Ieq real =  " + i.getValue());
            }
            for (Map.Entry<Integer, Double> i : getBusNumToImagIeq().entrySet()) {
                int nb = i.getKey();
                System.out.println("Bus = " + nb + " has Ieq imag =  " + i.getValue());
            }

            for (int i = 0; i < minusYeq.getRowCount(); i++) {
                System.out.println("RowYeq[" + i + "] = bus Num  " + getYeqRowNumToBusNum().get(i) + " bus Eq type " + getYeqRowNumToBusType().get(i));
            }
            for (int i = 0; i < minusYeq.getRowCount(); i++) {
                System.out.println("ColYeq[" + i + "] = bus Num  " + getYeqColNumToBusNum().get(i) + " bus Var type " + getYeqColNumToBusType().get(i));
            }

            System.out.println("===> -Yeq =");
            minusYeq.print(System.out);
        }

    }

    public class ReductionHypotheses {

        private List<YijBlock> yijBlocks;

        public List<EquivalentBranch> eqBranches;

        public List<EquivalentShunt> eqShunts;

        public List<EquivalentLoad> eqLoads;

        private class YijBlock {

            private int bus1;
            private int bus2;
            private double y1r2r;
            private double y1r2i;
            private double y1i2r;
            private double y1i2i;
            private double y2r1r;
            private double y2r1i;
            private double y2i1i;
            private double y2i1r;

            YijBlock(int busi, int busj) {

                this.bus1 = busi;
                this.bus2 = busj;
                y1r2r = 0;
                y1r2i = 0;
                y1i2r = 0;
                y1i2i = 0;
                y2r1r = 0;
                y2r1i = 0;
                y2i1i = 0;
                y2i1r = 0;

            }
        }

        private YijBlock createYijBlock(int busi, int busj) {
            YijBlock yb = new YijBlock(busi, busj);
            return yb;
        }

        ReductionHypotheses() {
            yijBlocks = new ArrayList<YijBlock>();
            eqBranches = new ArrayList<EquivalentBranch>();
            eqShunts = new ArrayList<EquivalentShunt>();
            eqLoads = new ArrayList<EquivalentLoad>();
        }

        private YijBlock getYijBlock(int i, int j) {

            if (yijBlocks.isEmpty()) {
                return null;
            }
/*
            ListIterator<YijBlock> itYb = yijBlocks.listIterator();
            {
                yb0 = itYb.next();
            }
            while  (itYb.hasNext() && ((itYb.next().busi == i && itYb.next().busj == j) || (itYb.next().busi == j && itYb.next().busj == i) ))
*/
            YijBlock yb0 = null;
            for (YijBlock yb : yijBlocks) {
                if ((yb.bus1 == i && yb.bus2 == j) || (yb.bus1 == j && yb.bus2 == i)) {
                    yb0 = yb;
                    break;
                }
            }

            return yb0;
        }

        public class EquivalentBranch {

            public double rEq;
            public double xEq;
            public double alphaEq;
            public int bus1Eq;
            public int bus2Eq;

            EquivalentBranch(int busi, int busj, double r, double x, double alpha) {

                this.bus1Eq = busi;
                this.bus2Eq = busj;
                this.rEq = r;
                this.xEq = x;
                this.alphaEq = alpha;

            }
        }

        private EquivalentBranch createEquivalentBranch(int busi, int busj, double r, double x, double alpha) {
            EquivalentBranch eqBr = new EquivalentBranch(busi, busj, r, x, alpha);
            return eqBr;
        }

        public class EquivalentShunt {
            public double gEq;
            public double bEq;
            public int busEq;

            EquivalentShunt(int bus, double g, double b) {
                this.busEq = bus;
                this.gEq = g;
                this.bEq = b;
            }
        }

        private EquivalentShunt createEquivalentShunt(int bus, double g, double b) {
            EquivalentShunt eqSh = new EquivalentShunt(bus, g, b);
            return eqSh;
        }

        public class EquivalentLoad {
            public double pEq;
            public double qEq;
            public int busEq;

            EquivalentLoad(int bus, double p, double q) {
                this.busEq = bus;
                this.pEq = p;
                this.qEq = q;
            }
        }

        private EquivalentLoad createEquivalentLoad(int bus, double p, double q) {
            EquivalentLoad eqLoad = new EquivalentLoad(bus, p, q);
            return eqLoad;
        }
    }

    private Set<LfBus> extBusses;

    private Set<LfBus> borderBusses;

    public ReductionEngine(LfNetwork network, MatrixFactory matrixFactory, List<String> externalVoltageLevels) {
        this.networks = Collections.singletonList(network);
        parameters = new ReductionParameters(new FirstSlackBusSelector(), matrixFactory, externalVoltageLevels);
        extBusses = new HashSet<>();
        borderBusses = new HashSet<>();
    }

    public ReductionEngine(Object network, ReductionParameters parameters) {
        this.networks = LfNetwork.load(network, new LfNetworkParameters(parameters.getSlackBusSelector()));
        this.parameters = Objects.requireNonNull(parameters);
        extBusses = new HashSet<>();
        borderBusses = new HashSet<>();
    }

    public void run() {
        LfNetwork network = networks.get(0);

        // List and tag external  + boarder + internal nodes from external voltagelevels list given in input
        defineZones(network);

        ReductionEquationSystemCreationParameters creationParameters = new ReductionEquationSystemCreationParameters(true, false);
        EquationSystem equationSystem = ReductionEquationSystem.create(network, new VariableSet(), creationParameters);

        VoltageInitializer voltageInitializer = parameters.getVoltageInitializer();

        // Reduction problem:
        // The Grid is decomposed in 3 zones: internal (i), border (b), external (e) which is to be reduced
        // Matrix decomposition gives:
        //                    [[Yii] [Yib] [ 0 ]]   [Vi]   [Ii]
        // [Y]*[V] = [I] <=>  [[Ybi] [Ybb] [Ybe]] * [Vb] = [Ib]
        //                    [[ 0 ] [Yeb] [Yee]]   [Ve]   [Ie]
        //
        // Use Gaussian elimination to remove external part (e):
        // [[Yii] [Yib ]]   [Ii ]
        // [[Ybi] [Ybb']] = [Ib']
        // with
        // Ybb' = Ybb + Yeq where Yeq = -Ybe * inv(Yee) * Yeb
        // Ib' = Ib + Ieq where Ieq = -Ybe * inv(Yee) * Ie
        // Resolving the reduction problem is equivalent to find Ieq and Yeq

        AdmittanceMatrix yeb = new AdmittanceMatrix(equationSystem, parameters.getMatrixFactory(), extBusses, borderBusses);
        AdmittanceMatrix yee = new AdmittanceMatrix(equationSystem, parameters.getMatrixFactory(), extBusses, extBusses);
        AdmittanceMatrix ybe = new AdmittanceMatrix(equationSystem, parameters.getMatrixFactory(), borderBusses, extBusses);
        AdmittanceMatrix ybb = new AdmittanceMatrix(equationSystem, parameters.getMatrixFactory(), borderBusses, borderBusses);

        // Step 1: Get [Ie]:
        // [Ie] = [Yeb]*[Vb] + [Yee]*[Ve] or t[Ie] = t[Vb]*t[Yeb] + t[Ve]*t[Yee]
        Matrix tmYeb = yeb.getMatrix(); //tmYeb is the transposed of [Yeb]
        Matrix tmVb = yeb.getVoltageVector(voltageInitializer); //tmVb is the transposed of the column vector [Vb]

        Matrix tmYee = yee.getMatrix(); //tmYee is the transposed of [Yee]
        Matrix tmVe = yee.getVoltageVector(voltageInitializer); //tmVe is the transposed of the column vector [Ve]

        Matrix tmYebVb = tmVb.times(tmYeb);
        Matrix tmYeeVe = tmVe.times(tmYee);

        Matrix tmIe = addRowVector(tmYebVb, tmYeeVe);
        //System.out.println("===> tmIe =");
        //tmIe.print(System.out);

        //Step 2: Solve for [W] the following equation: [Yee]*[W] = [Yeb] or t[W]*t[Yee] = t[Yeb]
        DenseMatrix dTmYeb = tmYeb.toDense();
        yee.solveTransposed(dTmYeb);

        //Step 3: Compute -[Yeq] = [Ybe]*[W] or -t[Yeq] = t[W]*t[Ybe]
        Matrix tmYbe = ybe.getMatrix(); //tmYbe is the transposed of [Ybe]
        Matrix tmMinusYeq = dTmYeb.times(tmYbe);

        //Step 4: Solve for [X] the following equation [Yee]*[X] = [Ie] or t[X]*t[Yee] = t[Ie]
        double[] x = rowVectorToDouble(tmIe);
        yee.solveTransposed(x);

        //Step 5: Compute -[Ieq] = [Ybe]*[X] or -t[Ieq] = t[X]*t[Ybe]
        Matrix mX = MatrixUtil.createFromRow(x, parameters.getMatrixFactory());
        Matrix tmMinusIeq = mX.times(tmYbe);
        //System.out.println("===> -Ieq =");
        //tmMinusIeq.print(System.out);

        // Reintegration of the Matrix results into the admittance system:
        // [Ieq] and [Yeq] are of the dimension of [Ib] and [Ybb] respectively, we use Ybb admittance system as the structure for the reintegration of the results
        results = new ReductionResults(tmMinusYeq);
        ybb.processResults(rowVectorToDouble(tmMinusIeq), results);
        results.printReductionResults();

        generateReductionHypotheses();
    }

    private Matrix addRowVector(Matrix m1, Matrix m2) {
        DenseMatrix m1d = m1.toDense();
        DenseMatrix m2d = m2.toDense();
        for (int j = 0; j < m1d.getColumnCount(); j++) {
            m1d.set(0, j, m1d.get(0, j) + m2d.get(0, j));
        }
        return m1d;
    }

    public double[] rowVectorToDouble(Matrix m1) {
        DenseMatrix m1d = m1.toDense();
        double[] v = new double[m1d.getColumnCount()];
        for (int j = 0; j < m1d.getColumnCount(); j++) {
            v[j] = m1d.get(0, j);
        }
        return v;
    }

    private void defineZones(LfNetwork network) {
        for (LfBranch br : network.getBranches()) {
            if (br.getBus1() != null && br.getBus2() == null) {
                if (parameters.getExternalVoltageLevels().contains(br.getBus1().getVoltageLevelId())) {
                    extBusses.add(br.getBus1());
                }
            } else if (br.getBus2() != null && br.getBus1() == null) {
                if (parameters.getExternalVoltageLevels().contains(br.getBus2().getVoltageLevelId())) {
                    extBusses.add(br.getBus2());
                }
            } else if (br.getBus2() != null && br.getBus1() != null) {
                LfBus b1 = br.getBus1();
                LfBus b2 = br.getBus2();
                boolean isB1Ext = parameters.getExternalVoltageLevels().contains(b1.getVoltageLevelId());
                boolean isB2Ext = parameters.getExternalVoltageLevels().contains(b2.getVoltageLevelId());
                if (isB1Ext && isB2Ext) {
                    extBusses.add(b1);
                    extBusses.add(b2);
                } else if (isB1Ext) {
                    extBusses.add(b1);
                    borderBusses.add(b2);
                } else if (isB2Ext) {
                    extBusses.add(b2);
                    borderBusses.add(b1);
                }
            }
        }

        //check built zones
        for (LfBus b : extBusses) {
            System.out.println("Bus = " + b.getId() + " is ext  ");
        }

        for (LfBus b : borderBusses) {
            System.out.println("Bus = " + b.getId() + " is border  ");
        }

    }

    // Example to compute full sytem nodal current injectors I = Y*V
    public void computeCurrentInjections() {
        //test with full sytem compute I = Y*V

        LfNetwork network = networks.get(0);

        ReductionEquationSystemCreationParameters creationParameters = new ReductionEquationSystemCreationParameters(true, false);
        EquationSystem equationSystem = ReductionEquationSystem.create(network, new VariableSet(), creationParameters);

        VoltageInitializer voltageInitializer = parameters.getVoltageInitializer();

        AdmittanceMatrix a = new AdmittanceMatrix(equationSystem, parameters.getMatrixFactory());
        Matrix a1 = a.getMatrix();
        Matrix mV = a.getVoltageVector(voltageInitializer);
        System.out.println("v = ");
        mV.print(System.out);

        Matrix mI = mV.times(a1);
        System.out.println("i = ");
        mI.print(System.out);
    }

    public void generateReductionHypotheses() {

        LfNetwork network = networks.get(0);

        reductionHypo = new ReductionHypotheses();
        DenseMatrix m = results.minusYeq.toDense();

        //step 1: extract values of [Yeq] binding upper and under extra-diagonal terms together if they have the same nodes
        //
        // [I1r]   [ y1r1r y1r1i y1r2r y1r2i ]         [ g1+g12 -b1-b12   -g12    b12  ]   [V1r]
        // [I1i]   [ y1i1r y1i1i y1i2r y1i2i ]         [ b1+b12 g1+g12    -b12   -g12  ]   [V1i]
        // [I2r] = [ y2r1r y2r1i y2r2r y2r2i ] * [V] = [ -g21    b21    g2+g21 -b2-b21 ] * [V2r]
        // [I2i]   [ y2i1r y2i1i y2i2r y2i2i ]         [ -b21    -g21   b2+b21  g2+g21 ]   [V2i]
        //
        //
        HashMap<Integer, ReductionHypotheses.YijBlock > busNumtToDiagonalBlock = new HashMap<>(); //use for a direct access to diaganal blocks from bus num
        for (int mi = 0; mi < results.minusYeq.getRowCount(); mi++) {

            int busi = results.yeqRowNumToBusNum.get(mi);
            EquationType typei = results.yeqRowNumToBusType.get(mi);

            for (int mj = 0; mj < results.minusYeq.getRowCount(); mj++) {
                int busj = results.yeqColNumToBusNum.get(mj);

                if (Math.abs(m.get(mi, mj)) > 0.00001) { //no block created or modified if matrix term is zero

                    VariableType typej = results.yeqColNumToBusType.get(mj);

                    //Create or update Yij block
                    ReductionHypotheses.YijBlock yb = reductionHypo.getYijBlock(busi, busj);
                    if (yb == null) {
                        yb = reductionHypo.createYijBlock(busi, busj);
                        reductionHypo.yijBlocks.add(yb);
                        if (busi == busj) {
                            busNumtToDiagonalBlock.put(busi, yb);
                        }
                    }
                    if (busi == yb.bus1) { //in the case where busi = busj only y1r2r,y1r2i,y1i2i,y1i2r  are useful
                        if (typei == EquationType.BUS_YR && typej == VariableType.BUS_VR) {
                            yb.y1r2r = -m.get(mi, mj);
                        } else if (typei == EquationType.BUS_YR && typej == VariableType.BUS_VI) {
                            yb.y1r2i = -m.get(mi, mj);
                        } else if (typei == EquationType.BUS_YI && typej == VariableType.BUS_VI) {
                            yb.y1i2i = -m.get(mi, mj);
                        } else if (typei == EquationType.BUS_YI && typej == VariableType.BUS_VR) {
                            yb.y1i2r = -m.get(mi, mj);
                        }
                    } else if (busi == yb.bus2) {
                        if (typei == EquationType.BUS_YR && typej == VariableType.BUS_VR) {
                            yb.y2r1r = -m.get(mi, mj);
                        } else if (typei == EquationType.BUS_YR && typej == VariableType.BUS_VI) {
                            yb.y2r1i = -m.get(mi, mj);
                        } else if (typei == EquationType.BUS_YI && typej == VariableType.BUS_VI) {
                            yb.y2i1i = -m.get(mi, mj);
                        } else if (typei == EquationType.BUS_YI && typej == VariableType.BUS_VR) {
                            yb.y2i1r = -m.get(mi, mj);
                        }

                    }
                }
            }
        }

        //step 2: check the consistency of the blocks since some terms must be equal to deduce an equivalent branch
        //step 3: deduce g12, b12, g21, b21 and then g1, b1, g2, b2
        for (ReductionHypotheses.YijBlock yb : reductionHypo.yijBlocks) {
            //step 2
            double epsilon = 0.00001;
            if (Math.abs(yb.y1r2r - yb.y1i2i) > epsilon) {
                throw new IllegalArgumentException("Admittance block values y1r2r and y1i2i of nodes num {" + yb.bus1 + ";" + yb.bus2 + "} have inconsitant values y1r2r= " + yb.y1r2r + " y1i2i=" + yb.y1i2i);
            }
            if (Math.abs(yb.y1i2r + yb.y1r2i) > epsilon) {
                throw new IllegalArgumentException("Admittance block values y1i2r and y1r2i of nodes num {" + yb.bus1 + ";" + yb.bus2 + "} have inconsitant values");
            }
            if (Math.abs(yb.y2r1r - yb.y2i1i) > epsilon) {
                throw new IllegalArgumentException("Admittance block values y2r1r and y2i1i of nodes num {" + yb.bus1 + ";" + yb.bus2 + "} have inconsitant values");
            }
            if (Math.abs(yb.y2i1r + yb.y2r1i) > epsilon) {
                throw new IllegalArgumentException("Admittance block values y2i1r and y2r1i of nodes num {" + yb.bus1 + ";" + yb.bus2 + "} have inconsitant values");
            }

        }

        for (ReductionHypotheses.YijBlock yb : reductionHypo.yijBlocks) {
            //step 3
            double g12 = 0;
            double b12 = 0;
            double g21 = 0;
            double b21 = 0;

            if (yb.bus1 != yb.bus2) {
                g12 = -yb.y1r2r;
                b12 = yb.y1r2i;
                g21 = -yb.y2r1r;
                b21 = yb.y2r1i;

                //remove g12 from diagonal term y1r2r = g1 + sum(g1i)
                busNumtToDiagonalBlock.get(yb.bus1).y1r2r = busNumtToDiagonalBlock.get(yb.bus1).y1r2r - g12;
                //remove b12 from diagonal term y1i2r = b1 + sum(b1i)
                busNumtToDiagonalBlock.get(yb.bus1).y1i2r = busNumtToDiagonalBlock.get(yb.bus1).y1i2r - b12;
                //remove g21 from diagonal term y1r2r = g2 + sum(g2i)
                busNumtToDiagonalBlock.get(yb.bus2).y1r2r = busNumtToDiagonalBlock.get(yb.bus2).y1r2r - g21;
                //remove b21 from diagonal term y1i2r = b2 + sum(b2i)
                busNumtToDiagonalBlock.get(yb.bus2).y1i2r = busNumtToDiagonalBlock.get(yb.bus2).y1i2r - b21;

                //deduce branch characteristics from g12, b12, g21, b21
                // we use the hypothesis that rho = 1 but in case nominal voltage values of voltage levels are not the same, we will have to create a transformer with rho = 1
                double denom = g21 * g12 + b21 * b12;
                double alpha = 0.;
                if (denom > 0.0001) {
                    alpha = 0.5 * Math.atan((b21 * g12 - b12 * g21) / denom);
                }

                double r = (g12 * Math.cos(alpha) - b12 * Math.sin(alpha)) / (g12 * g12 + b12 * b12);
                double x = -(b12 * Math.cos(alpha) + g12 * Math.sin(alpha)) / (g12 * g12 + b12 * b12);

                ReductionHypotheses.EquivalentBranch eqBr = reductionHypo.createEquivalentBranch(yb.bus1, yb.bus2, r, x, alpha);
                reductionHypo.eqBranches.add(eqBr);
                //System.out.println("Equivalent branch r=" + r + " x=" + x + " at busses = " + yb.bus1 + ";" + yb.bus2);
            }

        }

        //step4: generate hypotheses of creation of equivalent shunts for remaining g1, b1 and g2, b2
        for (Map.Entry<Integer, ReductionHypotheses.YijBlock > diagBlock : busNumtToDiagonalBlock.entrySet()) {
            int busNum = diagBlock.getKey();
            ReductionHypotheses.YijBlock yb = diagBlock.getValue();
            double g1 = 0;
            double b1 = 0;
            g1 = yb.y1r2r;
            b1 = yb.y1i2r;
            if (g1 > 0.00001 || b1 > 0.00001) {
                ReductionHypotheses.EquivalentShunt eqSh = reductionHypo.createEquivalentShunt(busNum, g1, b1);
                reductionHypo.eqShunts.add(eqSh);
                //System.out.println("Equivalent shunt g=" + g1 + " b=" + b1 + " at bus num=" + busNum);
            }
        }

        //step5: generation of equivalent load injections from equivalent currents
        for (Map.Entry<Integer, Double > ieq : results.busNumToRealIeq.entrySet()) {
            int busNum = ieq.getKey();
            double ir = ieq.getValue();
            double ii = results.busNumToImagIeq.get(busNum);

            double vr = parameters.getVoltageInitializer().getMagnitude(network.getBus(busNum)) * Math.cos(Math.toRadians(parameters.getVoltageInitializer().getAngle(network.getBus(busNum))));
            double vi = parameters.getVoltageInitializer().getMagnitude(network.getBus(busNum)) * Math.sin(Math.toRadians(parameters.getVoltageInitializer().getAngle(network.getBus(busNum))));

            double pEq = -(vr * ir + vi * ii);
            double qEq = ii * vr - vi * ir;

            if (pEq > 0.00001 || qEq > 0.00001) {
                ReductionHypotheses.EquivalentLoad eqLoad = reductionHypo.createEquivalentLoad(busNum, pEq, qEq);
                reductionHypo.eqLoads.add(eqLoad);
                //System.out.println("Equivalent load P=" + pEq + " Q=" + qEq + " at bus num=" + busNum);
            }
        }
    }
}
