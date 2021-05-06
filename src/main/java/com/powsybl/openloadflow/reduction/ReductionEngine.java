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

}
