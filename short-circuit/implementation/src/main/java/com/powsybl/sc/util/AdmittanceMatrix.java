/**
 * Copyright (c) 2022, Jean-Baptiste Heyberger & Geoffroy Jamgotchian
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.util;

import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.math.matrix.LUDecomposition;
import com.powsybl.math.matrix.Matrix;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.openloadflow.equations.*;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.util.VoltageInitializer;
import org.apache.commons.math3.complex.Complex;

import java.util.*;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class AdmittanceMatrix implements AutoCloseable {

    public class AdmittanceSystem {

        //created to extract a subset of the equationSystem to easily create admittance subMatrices for the reduction problem while keeping consistency on the global equation system
        private Set<Integer> numRowBusses;

        private Set<Integer> numColBusses;

        public Map<Equation<VariableType, EquationType>, Integer> eqToRowNum;

        public Map<Variable<VariableType>, Integer> varToColNum;

        public boolean isSubAdmittance;

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

            isSubAdmittance = !numRowBusses.isEmpty() || !numColBusses.isEmpty(); //if false then build the full admittance system based on the equationSystem infos

            if (isSubAdmittance) {
                int nbRow = 0;
                int nbCol = 0;
                for (var eq : equationSystem.getIndex().getSortedSingleEquationsToSolve()) {
                    int numBusEq = eq.getElementNum();
                    if (numRowBusses.contains(numBusEq)) {
                        eqToRowNum.put(eq, nbRow++);
                    }
                }

                for (Variable<VariableType> v : equationSystem.getIndex().getSortedVariablesToFind()) {
                    int numBusVar = v.getElementNum();
                    if (numColBusses.contains(numBusVar)) {
                        varToColNum.put(v, nbCol++);
                    }
                }
            }
        }
    }

    private final EquationSystem<VariableType, EquationType> equationSystem;

    private final LfNetwork lfNetwork;

    private final MatrixFactory matrixFactory;

    private Matrix matrix;

    private LUDecomposition lu;

    private Set<LfBus> rowBusses;

    private Set<LfBus> columnBusses;

    private AdmittanceSystem admSys;

    private List<Integer> busNumToRowR; //given a number of bus, provides the Row and Column to the matrix
    private List<Integer> busNumToColR;
    private List<Integer> busNumToRowI; //given a number of bus, provides the Row and Column to the matrix
    private List<Integer> busNumToColI;

    public AdmittanceMatrix(EquationSystem<VariableType, EquationType> equationSystem, MatrixFactory matrixFactory, LfNetwork network) {
        this.equationSystem = Objects.requireNonNull(equationSystem);
        this.matrixFactory = Objects.requireNonNull(matrixFactory);
        this.admSys = new AdmittanceSystem();
        this.lfNetwork = Objects.requireNonNull(network);
        initAdmittanceSystem();
    }

    public AdmittanceMatrix(EquationSystem<VariableType, EquationType> equationSystem, MatrixFactory matrixFactory, Set<LfBus> rowBusses, Set<LfBus> columnBusses, LfNetwork network) {
        this.equationSystem = Objects.requireNonNull(equationSystem);
        this.matrixFactory = Objects.requireNonNull(matrixFactory);
        this.rowBusses = Objects.requireNonNull(rowBusses);
        this.columnBusses = Objects.requireNonNull(columnBusses);
        this.admSys = new AdmittanceSystem();
        this.lfNetwork = Objects.requireNonNull(network);
        initAdmittanceSystem();
    }

    public AdmittanceSystem getAdmSys() {
        return admSys;
    }

    public EquationSystem<VariableType, EquationType> getEquationSystem() {
        return equationSystem;
    }

    private void clear() {
        matrix = null;
        if (lu != null) {
            lu.close();
        }
        lu = null;
    }

    public int getRowCount() {
        int rowCount = equationSystem.getIndex().getSortedSingleEquationsToSolve().size();
        if (admSys.isSubAdmittance) {
            rowCount = admSys.eqToRowNum.size();
        }
        return rowCount;
    }

    public int getColCount() {
        int columnCount = equationSystem.getIndex().getSortedVariablesToFind().size();
        if (admSys.isSubAdmittance) {
            columnCount = admSys.varToColNum.size();
        }
        return columnCount;
    }

    private Map<Variable<VariableType>, List<EquationTerm<VariableType, EquationType>>> indexTermsByVariable(Equation<VariableType, EquationType> eq) {
        Map<Variable<VariableType>, List<EquationTerm<VariableType, EquationType>>> termsByVariable = new TreeMap<>();
        for (EquationTerm<VariableType, EquationType> term : eq.getTerms()) {
            for (Variable<VariableType> v : term.getVariables()) {
                termsByVariable.computeIfAbsent(v, k -> new ArrayList<>())
                        .add(term);
            }
        }
        return termsByVariable;
    }

    private void initAdmittanceSystem() {
        //if if no busses specified in input, we build the admittance of the full system
        int rowCount = getRowCount();
        int columnCount = getColCount();

        //intialization of accessors
        busNumToRowR = new ArrayList<>();
        busNumToColR = new ArrayList<>();
        busNumToRowI = new ArrayList<>();
        busNumToColI = new ArrayList<>();
        for (int i = 0; i < 2 * lfNetwork.getBuses().size(); i++) {
            busNumToRowR.add(0);
            busNumToColR.add(0);
            busNumToRowI.add(0);
            busNumToColI.add(0);
        }

        int estimatedNonZeroValueCount = rowCount * 3;
        matrix = matrixFactory.create(columnCount, rowCount, estimatedNonZeroValueCount); //matrix is the transposed of the standard admittance matrix

        for (var eq : equationSystem.getIndex().getSortedSingleEquationsToSolve()) {
            int yRow = eq.getColumn(); // equations are the rows of Y (and the columns of "matrix", the transposed of Y)
            if (admSys.isSubAdmittance && admSys.eqToRowNum.containsKey(eq)) {
                yRow = admSys.eqToRowNum.get(eq); // the matrix is the transposed of the admittance matrix as equations represent the columns and the vars represent the rows
            }

            //Init of the row accessors
            int busNum = eq.getElementNum();
            EquationType eqType = eq.getType();
            if (eqType == EquationType.BUS_YR) {
                busNumToRowR.set(busNum, yRow);
            } else if (eqType == EquationType.BUS_YI) {
                busNumToRowI.set(busNum, yRow);
            }

            if (!admSys.isSubAdmittance || admSys.eqToRowNum.containsKey(eq)) {
                for (Map.Entry<Variable<VariableType>, List<EquationTerm<VariableType, EquationType>>> e2 : indexTermsByVariable(eq).entrySet()) {
                    Variable<VariableType> v = e2.getKey();
                    int yColumn = v.getRow(); // vars are the columns of Y (and the rows of "matrix", the transposed of Y)
                    if (admSys.isSubAdmittance && admSys.varToColNum.containsKey(v)) {
                        yColumn = admSys.varToColNum.get(v); // the matrix is the transposed of the admittance matrix
                    }
                    if (!admSys.isSubAdmittance || admSys.varToColNum.containsKey(v)) {
                        for (EquationTerm<VariableType, EquationType> equationTerm : e2.getValue()) {
                            double value = ((LinearEquationTerm) equationTerm).getCoefficient(v);
                            matrix.add(yColumn, yRow, value); //matrix is here the transposed of Y
                        }
                    }
                }
            }
        }

        //Init of the column accessors
        for (Variable<VariableType> v : equationSystem.getIndex().getSortedVariablesToFind()) {
            int busNum = v.getElementNum();
            VariableType varType = v.getType();

            int yColumn = v.getRow(); // vars are the columns of Y (and the rows of "matrix", the transposed of Y)
            if (admSys.isSubAdmittance && admSys.varToColNum.containsKey(v)) {
                yColumn = admSys.varToColNum.get(v); // the matrix is the transposed of the admittance matrix
            }
            if (varType == VariableType.BUS_VR) {
                busNumToColR.set(busNum, yColumn);
            } else if (varType == VariableType.BUS_VI) {
                busNumToColI.set(busNum, yColumn);
            }
        }

    }

    private double[] createStateVector(LfNetwork network, VoltageInitializer initializer) {
        double[] x = new double[equationSystem.getIndex().getSortedVariablesToFind().size()];
        for (Variable<VariableType> v : equationSystem.getIndex().getSortedVariablesToFind()) {
            switch (v.getType()) {
                case BUS_VR:
                    x[v.getRow()] = initializer.getMagnitude(network.getBus(v.getElementNum())) * Math.cos(Math.toRadians(initializer.getAngle(network.getBus(v.getElementNum()))));
                    break;

                case BUS_VI:
                    x[v.getRow()] = initializer.getMagnitude(network.getBus(v.getElementNum())) * Math.sin(Math.toRadians(initializer.getAngle(network.getBus(v.getElementNum()))));
                    break;

                default:
                    throw new IllegalStateException("Unknown variable type " + v.getType());
            }
        }
        return x;
    }

    private Matrix initVoltageVector(LfNetwork network, VoltageInitializer voltageInitializer) {
        //if if no busses specified in input, we build the voltage vector of the full system
        int columnCount = getColCount();

        double[] sv = createStateVector(network, voltageInitializer);
        double[] vPart = new double[columnCount];
        Matrix mV;

        if (admSys.isSubAdmittance) {
            for (Variable<VariableType> v : equationSystem.getIndex().getSortedVariablesToFind()) {
                int row = v.getRow();
                if (admSys.varToColNum.containsKey(v)) {
                    vPart[admSys.varToColNum.get(v)] = sv[row];
                }
            }
            mV = Matrix.createFromRow(vPart, matrixFactory);
        } else {
            mV = Matrix.createFromRow(sv, matrixFactory);
        }

        return mV;
    }

    public Map<Integer, Complex> getDeltaV(DenseMatrix m, int numColumn) {
        Map<Integer, Complex> tmpV = new HashMap<>();
        for (Variable<VariableType> v : equationSystem.getIndex().getSortedVariablesToFind()) {
            int row = v.getRow();
            VariableType type = v.getType();
            if (admSys.isSubAdmittance) {
                if (admSys.varToColNum.containsKey(v)) {
                    row = admSys.varToColNum.get(v);
                } else {
                    throw new IllegalArgumentException("Could not update variable V num = " + v.getElementNum() + ", index not found in the subsystem");
                }
            }

            // m result is [ r -x ] ---> BUS_VR
            //             [ x  r ] ---> BUS_VI
            Complex defaultV = new Complex(0.);
            if (!tmpV.containsKey(v.getElementNum())) {
                tmpV.put(v.getElementNum(), defaultV);
            }
            if (type == VariableType.BUS_VR) {
                Complex oldV = tmpV.get(v.getElementNum());
                Complex delta = new Complex(m.get(row, 2 * numColumn), -m.get(row, 2 * numColumn + 1));
                tmpV.put(v.getElementNum(), oldV.add(delta));
            }
        }

        return tmpV;
    }

    public List<DenseMatrix> getDeltaVFortescue(List<DenseMatrix> busNum2Dv, DenseMatrix md, DenseMatrix mo, DenseMatrix mi) {
        for (Variable<VariableType> v : equationSystem.getIndex().getSortedVariablesToFind()) {
            int row = v.getRow();
            VariableType type = v.getType();
            int busNum = v.getElementNum();
            if (admSys.isSubAdmittance) {
                if (admSys.varToColNum.containsKey(v)) {
                    row = admSys.varToColNum.get(v);
                } else {
                    throw new IllegalArgumentException("Could not update variable V num = " + v.getElementNum() + ", index not found in the subsystem");
                }
            }

            if (type == VariableType.BUS_VR) {
                busNum2Dv.get(busNum).add(0, 0, mo.get(row, 0));
                busNum2Dv.get(busNum).add(2, 0, md.get(row, 0));
                busNum2Dv.get(busNum).add(4, 0, mi.get(row, 0));

            } else if (type == VariableType.BUS_VI) {
                busNum2Dv.get(busNum).add(1, 0, mo.get(row, 0));
                busNum2Dv.get(busNum).add(3, 0, md.get(row, 0));
                busNum2Dv.get(busNum).add(5, 0, mi.get(row, 0));
            }

        }

        return busNum2Dv;
    }

    public int getRowBus(int numBus, EquationType eqType) {
        int yRow = 0;
        if (eqType == EquationType.BUS_YR) {
            yRow = busNumToRowR.get(numBus);
        } else if (eqType == EquationType.BUS_YI) {
            yRow = busNumToRowI.get(numBus);
        }

        return yRow; //TODO : send an exception when bus is not found in equation set
    }

    public int getColBus(int numBus, VariableType varType) {
        int yColumn = 0;
        if (varType == VariableType.BUS_VR) {
            yColumn = busNumToColR.get(numBus);
        } else if (varType == VariableType.BUS_VI) {
            yColumn = busNumToColI.get(numBus);
        }

        return yColumn; //TODO : send an exception when bus is not found in var set
    }

    public Matrix getMatrix() {
        return matrix;
    }

    public Matrix getVoltageVector(LfNetwork network, VoltageInitializer voltageInitializer) {
        return initVoltageVector(network, voltageInitializer);
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

    public DenseMatrix transpose() {
        return matrix.toDense().transpose();
    }

    @Override
    public void close() {
        clear();
    }

}
