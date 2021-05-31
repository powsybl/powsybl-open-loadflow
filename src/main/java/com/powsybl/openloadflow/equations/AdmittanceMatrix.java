package com.powsybl.openloadflow.equations;

//import com.powsybl.commons.PowsyblException;

import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.math.matrix.LUDecomposition;
import com.powsybl.math.matrix.Matrix;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.reduction.ReductionEngine;

import java.util.*;

/**
 * @author Jean-Baptiste Heyberger <jean-baptiste.heyberger at rte-france.com>
 */
public class AdmittanceMatrix implements AutoCloseable {

    private class AdmittanceSystem {

        //created to extract a subset of the equationSystem to easily create admittance subMatrices for the reduction problem while keeping consistency on the global equation system
        private Set<Integer> numRowBusses;

        private Set<Integer> numColBusses;

        private Map<Equation, Integer> eqToRowNum;

        private Map<Variable, Integer> varToColNum;

        private boolean isSubAdmittance;

        AdmittanceSystem() {

            numRowBusses = new HashSet<>();
            numColBusses = new HashSet<>();

            eqToRowNum = new HashMap<>();
            varToColNum = new HashMap<>();

            //Convert rowBusses and columnBusses Sets into eq number Sets
            if (rowBusses != null) {
                for (LfBus b : rowBusses) {
                    numRowBusses.add(b.getNum());
                }
            }

            if (columnBusses != null) {
                for (LfBus b : columnBusses) {
                    numColBusses.add(b.getNum());
                }
            }

            isSubAdmittance = numRowBusses.size() > 0 || numColBusses.size() > 0; //if false then build the full admittance system based on the equationSystem infos

            if (isSubAdmittance) {
                int nbRow = 0;
                int nbCol = 0;
                for (Map.Entry<Equation, NavigableMap<Variable, List<EquationTerm>>> e : equationSystem.getSortedEquationsToSolve().entrySet()) {
                    Equation eq = e.getKey();
                    int numBusEq = eq.getNum();
                    if (numRowBusses.contains(numBusEq)) {
                        eqToRowNum.put(eq, nbRow++);
                    }
                }

                for (Variable v : equationSystem.getSortedVariablesToFind()) {
                    int numBusVar = v.getNum();
                    if (numColBusses.contains(numBusVar)) {
                        varToColNum.put(v, nbCol++);
                    }
                }
            }
        }
    }

    private final EquationSystem equationSystem;

    private final MatrixFactory matrixFactory;

    private Matrix matrix;

    private LUDecomposition lu;

    private Set<LfBus> rowBusses;

    private Set<LfBus> columnBusses;

    private AdmittanceSystem admSys;

    public AdmittanceMatrix(EquationSystem equationSystem, MatrixFactory matrixFactory) {
        this.equationSystem = Objects.requireNonNull(equationSystem);
        this.matrixFactory = Objects.requireNonNull(matrixFactory);
        this.admSys = new AdmittanceSystem();
        initAdmittanceSystem();
    }

    public AdmittanceMatrix(EquationSystem equationSystem, MatrixFactory matrixFactory, Set<LfBus> rowBusses, Set<LfBus> columnBusses) {
        this.equationSystem = Objects.requireNonNull(equationSystem);
        this.matrixFactory = Objects.requireNonNull(matrixFactory);
        this.rowBusses = Objects.requireNonNull(rowBusses);
        this.columnBusses = Objects.requireNonNull(columnBusses);
        this.admSys = new AdmittanceSystem();
        initAdmittanceSystem();
    }

    private void clear() {
        matrix = null;
        if (lu != null) {
            lu.close();
        }
        lu = null;
    }

    private void initAdmittanceSystem() {
        //if if no busses specified in input, we build the admittance of the full system
        int rowCount = equationSystem.getSortedEquationsToSolve().size();
        int columnCount = equationSystem.getSortedVariablesToFind().size();
        if (admSys.isSubAdmittance) {
            rowCount = admSys.eqToRowNum.size();
            columnCount = admSys.varToColNum.size();
        }

        //equationSystem.testEquations();

        int estimatedNonZeroValueCount = rowCount * 3;
        matrix = matrixFactory.create(rowCount, columnCount, estimatedNonZeroValueCount);

        for (Map.Entry<Equation, NavigableMap<Variable, List<EquationTerm>>> e : equationSystem.getSortedEquationsToSolve().entrySet()) {
            Equation eq = e.getKey();
            int column = eq.getColumn();
            if (admSys.isSubAdmittance && admSys.eqToRowNum.containsKey(eq)) {
                column = admSys.eqToRowNum.get(eq);
            }
            if (!admSys.isSubAdmittance || admSys.eqToRowNum.containsKey(eq)) {
                for (Map.Entry<Variable, List<EquationTerm>> e2 : e.getValue().entrySet()) {
                    Variable var = e2.getKey();
                    int row = var.getRow();
                    if (admSys.isSubAdmittance && admSys.varToColNum.containsKey(var)) {
                        row = admSys.varToColNum.get(var);
                    }
                    if (!admSys.isSubAdmittance || admSys.varToColNum.containsKey(var)) {
                        for (EquationTerm equationTerm : e2.getValue()) {
                            double value = equationTerm.der(var); //use of the derivative function but it is not a derivative for admittance matrix
                            matrix.add(row, column, value);
                        }
                    }
                }
            }
        }
    }

    private Matrix initVoltageVector(VoltageInitializer voltageInitializer) {
        //if if no busses specified in input, we build the voltage vector of the full system
        int columnCount = equationSystem.getSortedVariablesToFind().size();
        if (admSys.isSubAdmittance) {
            columnCount = admSys.varToColNum.size();
        }

        double[] v = equationSystem.createStateVector(voltageInitializer);
        double[] vPart = new double[columnCount];
        Matrix mV;

        if (admSys.isSubAdmittance) {
            for (Variable var : equationSystem.getSortedVariablesToFind()) {
                int row = var.getRow();
                if (admSys.varToColNum.containsKey(var)) {
                    vPart[admSys.varToColNum.get(var)] = v[row];
                }
            }
            mV = Matrix.createFromRow(vPart, matrixFactory);
        } else {
            mV = Matrix.createFromRow(v, matrixFactory);
        }

        return mV;
    }

    public void processResults(double[] minusIeq, ReductionEngine.ReductionResults rRes) {

        if (!admSys.isSubAdmittance) {
            throw new IllegalArgumentException("Result reintegration of a reduction problem are only relevant for a subsystem defined by the border area");
        }

        int rowCount = admSys.eqToRowNum.size();
        int columnCount = admSys.varToColNum.size();

        if (rowCount != minusIeq.length) {
            throw new IllegalArgumentException("Ieq must be of the same dimension than the number of rows of the admittance matrix");
        }
        for (Map.Entry<Equation, NavigableMap<Variable, List<EquationTerm>>> e : equationSystem.getSortedEquationsToSolve().entrySet()) {
            Equation eq = e.getKey();
            int numBus = eq.getNum();
            if (admSys.eqToRowNum.containsKey(eq)) {
                int row = admSys.eqToRowNum.get(eq);
                rRes.getYeqRowNumToBusNum().put(row, numBus);
                if (eq.getType() == EquationType.BUS_YR) {
                    rRes.getBusNumToRealIeq().put(numBus, -minusIeq[row]);
                    rRes.getYeqRowNumToBusType().put(row, EquationType.BUS_YR);
                } else if (eq.getType() == EquationType.BUS_YI) {
                    rRes.getBusNumToImagIeq().put(numBus, -minusIeq[row]);
                    rRes.getYeqRowNumToBusType().put(row, EquationType.BUS_YI);
                }
            }
        }

        if (rowCount != columnCount) {
            throw new IllegalArgumentException("Reduction algorithm must provide a square Yeq Matrix");
        }
        for (Variable v : equationSystem.getSortedVariablesToFind()) {
            if (admSys.varToColNum.containsKey(v)) {
                int col = admSys.varToColNum.get(v);
                rRes.getYeqColNumToBusNum().put(col, v.getNum());
                if (v.getType() == VariableType.BUS_VR) {
                    rRes.getYeqColNumToBusType().put(col, VariableType.BUS_VR);
                } else if (v.getType() == VariableType.BUS_VI) {
                    rRes.getYeqColNumToBusType().put(col, VariableType.BUS_VI);
                }
            }
        }
    }

    public Matrix getMatrix() {
        return matrix;
    }

    public Matrix getVoltageVector(VoltageInitializer voltageInitializer) {
        return initVoltageVector(voltageInitializer);
    }

    private LUDecomposition getLUDecomposition() {
        if (lu == null) {
            lu = matrix.decomposeLU();
        }
        return lu;
    }

    public void solveTransposed(double[] b) {
        getLUDecomposition().solveTransposed(b);
    }

    public void solveTransposed(DenseMatrix b) {
        getLUDecomposition().solveTransposed(b);
    }

    @Override
    public void close() {
        clear();
    }

}
