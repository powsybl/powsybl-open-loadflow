/*
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */

package com.powsybl.openloadflow.ac.equations.vector;
import com.powsybl.math.matrix.Matrix;
import com.powsybl.openloadflow.ac.equations.AbstractClosedBranchAcFlowEquationTerm;
import com.powsybl.openloadflow.ac.equations.AcEquationType;
import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.equations.*;
import com.powsybl.openloadflow.network.AbstractLfNetworkListener;
import com.powsybl.openloadflow.network.ElementType;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfNetwork;
import net.jafama.DoubleWrapper;
import net.jafama.FastMath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.DoubleSupplier;
import java.util.stream.IntStream;

/**
 * @author Didier Vidal {@literal <didier.vidal_externe at rte-france.com>}
 *
 * A data container that contains primitive type arrays that can be iterrated
 * efficiently to avoid memory cache misses
 * foccusses on P and Q derived by V and Phi. Other combinations are not vectorized.
 */
public class AcVectorEngine implements StateVectorListener, EquationSystemListener, EquationSystemIndexListener, VectorEngine<AcVariableType> {

    private static final Logger LOGGER = LoggerFactory.getLogger(AcVectorEngine.class);

    private final EquationSystem<AcVariableType, AcEquationType> equationSystem;

    public final boolean[] networkDataInitialized;
    public final double[] b1;
    public final double[] b2;
    public final double[] g1;
    public final double[] g2;
    public final double[] y;
    public final double[] ksi;
    public final double[] g12;
    public final double[] b12;

    // branch computed values
    private final double[] a1Evaluated;
    private final double[] r1Evaluated;
    public final double[] sinKsi;
    public final double[] cosKsi;
    private final double[] theta2;
    private final double[] sinTheta2;
    private final double[] cosTheta2;
    private final double[] theta1;
    private final double[] sinTheta1;
    private final double[] cosTheta1;

    // possibly computed input values
    public final double[] a1;
    public final double[] r1;

    // variables
    public final Variable<AcVariableType>[] v1Var;
    private final double[] v1;
    public final Variable<AcVariableType>[] v2Var;
    private final double[] v2;
    public final Variable<AcVariableType>[] ph1Var;
    private final double[] ph1;
    public final Variable<AcVariableType>[] ph2Var;
    private final double[] ph2;

    // indexes to compute derivatives
    private boolean equationDataValid;
    private boolean equationOrderValid;
    private boolean variableValuesValid;
    private boolean variableRowDataValid;
    private int[] sortedEquationIndexArray;
    private int[] variableCountPerEquation;
    private int[] variablePerEquationIndex;
    private Variable<AcVariableType>[] variablesPerEquation;
    private int[] variableRowPerEquation;
    private double[] deriveResultPerVariableAndEquation;
    private int[] matrixIndexPerVariableAndEquation;
    private EquationTerm<AcVariableType, AcEquationType>[] termsByVariableAndEquation;

    // term data ordered by branch
    record TermData(EquationTerm<AcVariableType, AcEquationType> term,
                    boolean isBranch,
                    int termElementNum,
                    int equationElementNum,
                    int indexForResult,
                    Variable<AcVariableType> v) {
        int getBranchNum() {
            return isBranch ? termElementNum : -1;
        }
    }

    // equation replicated data for quick access
    private boolean[] equationActiveStatus;
    private int[] equationColumn;
    private double[] evalResultPerEquation;

    // term replicated data for quick access
    private boolean[] termActiveStatus;
    public DoubleSupplier[] a1TermSupplier;
    public DoubleSupplier[] r1TermSupplier;

    // sorted term data per equation (for eval)
    private EquationTerm<AcVariableType, AcEquationType>[] sortedTermsForEval;
    private int[] termVectorIndexForEval;
    private int[] termEquationVectorIndexForEval;
    private int[] termByEvalResultIndex;
    private int[] termByEquationBranchNumForEval;
    private VecToVal[] termVecToValForEval;

    // sorted term data per equation and variable (for der)
    private EquationTerm<AcVariableType, AcEquationType>[] sortedTermsForDer;
    private int[] termVectorIndexForDer;
    private int[] termEquationVectorIndexForDer;
    private Variable<AcVariableType>[] termVariable;
    private int[] termByVariableDeriveResultIndex;
    private int[] termByVariableBranchNumForDer;
    private VecToVal[] termVecToValForDer;

    private Map<Integer, String> branchNameByBranchNum = new HashMap<>();

    public AcVectorEngine(LfNetwork network, EquationSystem<AcVariableType, AcEquationType> equationSystem) {
        this.equationSystem = equationSystem;
        if (equationSystem != null) {
            equationSystem.setVectorEngine(this);
        }

        if (network != null) {
            network.addListener(new AbstractLfNetworkListener() {
                @Override
                public void onTapPositionChange(LfBranch branch, int oldPosition, int newPosition) {
                    a1[branch.getNum()] = branch.getPiModel().getA1();
                    r1[branch.getNum()] = branch.getPiModel().getR1();
                }
            });
            network.getBranches().forEach(b -> branchNameByBranchNum.put(b.getNum(), b.getId()));
        }

        int branchCount = network == null ? 1 : network.getBranches().size();

        b1 = new double[branchCount];
        b2 = new double[branchCount];
        g1 = new double[branchCount];
        g2 = new double[branchCount];
        y = new double[branchCount];
        ksi = new double[branchCount];
        g12 = new double[branchCount];
        b12 = new double[branchCount];
        networkDataInitialized = new boolean[branchCount];

        a1 = new double[branchCount];
        r1 = new double[branchCount];

        a1TermSupplier = new DoubleSupplier[branchCount];
        r1TermSupplier = new DoubleSupplier[branchCount];
        a1Evaluated = new double[branchCount];
        r1Evaluated = new double[branchCount];
        sinKsi = new double[branchCount];
        cosKsi = new double[branchCount];
        theta2 = new double[branchCount];
        sinTheta2 = new double[branchCount];
        cosTheta2 = new double[branchCount];
        theta1 = new double[branchCount];
        sinTheta1 = new double[branchCount];
        cosTheta1 = new double[branchCount];

        v1Var = new Variable[branchCount];
        v1 = new double[branchCount];
        v2Var = new Variable[branchCount];
        v2 = new double[branchCount];
        ph1Var = new Variable[branchCount];
        ph1 = new double[branchCount];
        ph2Var = new Variable[branchCount];
        ph2 = new double[branchCount];

        if (equationSystem != null) {
            equationSystem.getStateVector().addListener(this);
            equationSystem.addListener(this);
            equationSystem.getIndex().addListener(this);
        }
    }

    // Events related to StateVector listeners

    @Override
    public void onStateUpdate() {
        variableValuesValid = false;
    }

    // Events related to EquationSystemIndex listeners

    @Override
    public void onVariableChange(Variable variable, ChangeType changeType) {
        variableRowDataValid = false;
    }

    @Override
    public void onEquationChange(Equation equation, ChangeType changeType) {
        // Already managed by EquationSystemListener
    }

    @Override
    public void onEquationTermChange(EquationTerm term) {
        // Already managed by EquationSystemListener
    }

    // Events related to EquationSystem listeners

    @Override
    public void onEquationChange(Equation equation, EquationEventType eventType) {
        switch (eventType) {
            case EQUATION_CREATED:
            case EQUATION_REMOVED:
                equationDataValid = false;
                break;
            case EQUATION_ACTIVATED:
            case EQUATION_DEACTIVATED:
                if (equation.getVectorIndex() >= 0) {
                    equationActiveStatus[equation.getVectorIndex()] = equation.isActive();
                }
            case EQUATION_COLUMN_CHANGED:
                if (equation.getVectorIndex() >= 0) {
                    equationColumn[equation.getVectorIndex()] = equation.getColumn();
                }
                equationOrderValid = false;
                break;
        }
    }

    @Override
    public void onEquationTermChange(EquationTerm term, EquationTermEventType eventType) {
        if (eventType == EquationTermEventType.EQUATION_TERM_ACTIVATED || eventType == EquationTermEventType.EQUATION_TERM_DEACTIVATED) {
            if (term.getVectorIndex() >= 0) {
                termActiveStatus[term.getVectorIndex()] = term.isActive();
            }
        } else if(eventType == EquationTermEventType.EQUATION_TERM_ADDED) {
            equationDataValid = false;
        }
    }

    @Override
    public void evalLhs(double[] array) {
        if (!equationDataValid) {
            initEquationData();
        }

        if (!variableValuesValid) {
            updateVariables();
        }

        evalSortedTerms();

        for (int eqIndex = 0; eqIndex < equationActiveStatus.length; eqIndex++) {
            if (equationActiveStatus[eqIndex]) {
                int col = equationColumn[eqIndex];
                array[col] = evalResultPerEquation[eqIndex];
            }
        }
    }

    private void updateVariables() {
        StateVector stateVector = equationSystem.getStateVector();
        for (int i = 0; i < v1Var.length; i++) {
            v1[i] = v1Var[i] != null && v1Var[i].getRow() >= 0 ? stateVector.get(v1Var[i].getRow()) : Double.NaN;
            v2[i] = v2Var[i] != null && v2Var[i].getRow() >= 0 ? stateVector.get(v2Var[i].getRow()) : Double.NaN;
            ph1[i] = ph1Var[i] != null && ph1Var[i].getRow() >= 0 ? stateVector.get(ph1Var[i].getRow()) : Double.NaN;
            ph2[i] = ph2Var[i] != null && ph2Var[i].getRow() >= 0 ? stateVector.get(ph2Var[i].getRow()) : Double.NaN;
        }
        updateBranchesAngles();
        variableValuesValid = true;
    }

    private void updateBranchesAngles() {
        DoubleWrapper wrapper = new DoubleWrapper();
        for (int branchNum = 0; branchNum < theta1.length; branchNum++) {
            a1Evaluated[branchNum] = a1TermSupplier[branchNum] == null ? a1[branchNum] : a1TermSupplier[branchNum].getAsDouble();
            r1Evaluated[branchNum] = r1TermSupplier[branchNum] == null ? r1[branchNum] : r1TermSupplier[branchNum].getAsDouble();
            theta2[branchNum] = AbstractClosedBranchAcFlowEquationTerm.theta2(ksi[branchNum], ph1[branchNum], a1Evaluated[branchNum], ph2[branchNum]);
            sinTheta2[branchNum] = FastMath.sinAndCos(theta2[branchNum], wrapper);
            cosTheta2[branchNum] = wrapper.value;
            theta1[branchNum] = AbstractClosedBranchAcFlowEquationTerm.theta1(ksi[branchNum], ph1[branchNum], a1Evaluated[branchNum], ph2[branchNum]);
            sinTheta1[branchNum] = FastMath.sinAndCos(theta1[branchNum], wrapper);
            cosTheta1[branchNum] = wrapper.value;
        }
    }

    private void updateVariableRows() {
        for (int i = 0; i < variablesPerEquation.length; i++) {
            variableRowPerEquation[i] = variablesPerEquation[i].getRow();
        }
    }

    private void initEquationData() {
        // reset all term vector index
        if (termsByVariableAndEquation != null) {
            Arrays.stream(termsByVariableAndEquation).forEach(t -> t.setVectorIndex(-1));
        }

        Collection<Equation<AcVariableType, AcEquationType>> equationList = equationSystem.getEquations()
                .stream().sorted(Comparator.comparingInt(Equation::getElementNum)).toList();
        //Collection<Equation<AcVariableType, AcEquationType>> equationList = equationSystem.getIndex().getSortedEquationsToSolve();
        int equationCount = equationList.size();
        sortedEquationIndexArray = new int[equationCount];
        variableCountPerEquation = new int[equationCount];
        equationActiveStatus = new boolean[equationCount];
        equationColumn = new int[equationCount];
        evalResultPerEquation = new double[equationCount];
        int index = 0;
        int variableIndexSize = 0;
        int termCount = 0;
        for (Equation<AcVariableType, AcEquationType> e : equationList) {
            e.setVectorIndex(index);
            equationActiveStatus[index] = e.isActive();
            equationColumn[index] = e.getColumn();
            int equationVariableCount = e.getVariableCount();
            variableIndexSize += equationVariableCount;
            variableCountPerEquation[index] = equationVariableCount;
            termCount += e.getTerms().size();
            index += 1;
        }
        termActiveStatus = new boolean[termCount];
        variablePerEquationIndex = new int[equationCount];
        variablesPerEquation = new Variable[variableIndexSize];
        variableRowPerEquation = new int[variableIndexSize];
        matrixIndexPerVariableAndEquation = new int[variableIndexSize];
        deriveResultPerVariableAndEquation = new double[variableIndexSize];
        List<EquationTerm<AcVariableType, AcEquationType>> termsByVariableAndEquationList = new ArrayList<>();
        List<TermData> termDataListForDer = new ArrayList<>();
        List<TermData> termDataListForEval = new ArrayList<>();
        int indexVar = 0;
        int indexEq = 0;
        int indexForTermData = 0;
        for (Equation<AcVariableType, AcEquationType> e : equationList) {
            for (EquationTerm<AcVariableType, AcEquationType> t : e.getTerms()) {
                t.setVectorIndex(indexForTermData);
                termActiveStatus[indexForTermData] = t.isActive();
                if (t instanceof AbstractClosedBranchAcFlowEquationTerm brTerm) {
                    a1TermSupplier[brTerm.getElementNum()] = brTerm.getA1Supplier();
                    r1TermSupplier[brTerm.getElementNum()] = brTerm.getR1Supplier();
                    r1[brTerm.getElementNum()] = brTerm.r1();
                    a1[brTerm.getElementNum()] = brTerm.a1();
                }
                // All term data must be created
                // Accelerated terms are evaluated in evalSortedTermsVec
                // Non accerelated terms are evaluated in evalSortedTermsObj
                termDataListForEval.add(new TermData(t,
                        t.getElementType() == ElementType.BRANCH,
                        t.getElementNum(),  // branch num
                        e.getElementNum(),  // bus num
                        indexEq,
                        null));
                indexForTermData += 1;
            }
            variablePerEquationIndex[indexEq] = indexVar;
            for (Variable<AcVariableType> v : e.getVariables()) {
                variablesPerEquation[indexVar] = v;
                variableRowPerEquation[indexVar] = v.getRow();
                for (EquationTerm<AcVariableType, AcEquationType> t : e.getTerms(v)) {
                    termsByVariableAndEquationList.add(t);
                    termDataListForDer.add(new TermData(t,
                            t.getElementType() == ElementType.BRANCH,
                            t.getElementNum(), // branch num
                            e.getElementNum(), // bus num
                            indexVar,
                            v));
                }
                indexVar += 1;
            }
            indexEq += 1;
        }
        termsByVariableAndEquation = termsByVariableAndEquationList.toArray(new EquationTerm[0]);

        termDataListForDer.sort((t1, t2) -> {
            // To optimize memory access during term evaluation or derivation,
            // branch equation term  are sorted by branch then by bus.

            // Branch elements together, sorted by termElementNum then by bus, then by variable
            int compare = -Boolean.compare(t1.isBranch, t2.isBranch); // Put branch terms first
            if (compare != 0) {
                return compare;
            }

            // For branch terms, element num is branchNum. This is the second sorting key
            compare = t1.termElementNum - t2.termElementNum;
            if (compare != 0) {
                return compare;
            }

            // For branch term, equation num is busNum. This is the third sorting key.
            compare = t1.equationElementNum - t2.equationElementNum;
            if (compare != 0) {
                return compare;
            }

            return t1.v.compareTo(t2.v);

        });

        int termByVariableCount = termDataListForDer.size();
        sortedTermsForDer = new EquationTerm[termByVariableCount];
        termVectorIndexForDer = new int[termByVariableCount];
        termEquationVectorIndexForDer = new int[termByVariableCount];
        termVariable = new Variable[termByVariableCount];
        termByVariableDeriveResultIndex = new int[termByVariableCount];
        termByVariableBranchNumForDer = new int[termByVariableCount];
        termVecToValForDer = new VecToVal[termByVariableCount];
        int sortedTermIndex = 0;
        for (TermData termData : termDataListForDer) {
            sortedTermsForDer[sortedTermIndex] = termData.term;
            termVectorIndexForDer[sortedTermIndex] = termData.term.getVectorIndex();
            termEquationVectorIndexForDer[sortedTermIndex] = termData.term.getEquation().getVectorIndex();
            termVariable[sortedTermIndex] = termData.v;
            termByVariableBranchNumForDer[sortedTermIndex] = termData.getBranchNum();
            termVecToValForDer[sortedTermIndex] = termData.term.getVecToVal(termData.v);
            termByVariableDeriveResultIndex[sortedTermIndex] = termData.indexForResult;
            sortedTermIndex += 1;
        }

        termDataListForEval.sort((t1, t2) -> {
            // Branch elements together, sorted by termElementNum then by bus, then by variable
            int compare = -Boolean.compare(t1.isBranch, t2.isBranch); // Put branch terms first
            if (compare != 0) {
                return compare;
            }

            compare = t1.termElementNum - t2.termElementNum;
            if (compare != 0) {
                return compare;
            }

            return t1.equationElementNum - t2.equationElementNum;

        });

        int termByEquationCount = termDataListForEval.size();
        sortedTermsForEval = new EquationTerm[termByEquationCount];
        termVectorIndexForEval = new int[termByEquationCount];
        termEquationVectorIndexForEval = new int[termByEquationCount];
        termByEvalResultIndex = new int[termByEquationCount];
        termByEquationBranchNumForEval = new int[termByEquationCount];
        termVecToValForEval = new VecToVal[termByEquationCount];
        sortedTermIndex = 0;
        for (TermData termData : termDataListForEval) {
            sortedTermsForEval[sortedTermIndex] = termData.term;
            termVectorIndexForEval[sortedTermIndex] = termData.term.getVectorIndex();
            termEquationVectorIndexForEval[sortedTermIndex] = termData.term.getEquation().getVectorIndex();
            termByEquationBranchNumForEval[sortedTermIndex] = termData.term instanceof AbstractClosedBranchAcFlowEquationTerm ? termData.termElementNum : -1;
            termVecToValForEval[sortedTermIndex] = termData.term.getVecToVal(null);
            termByEvalResultIndex[sortedTermIndex] = termData.indexForResult;
            sortedTermIndex += 1;
        }
        updateVariables();
        variableValuesValid = true;
        equationDataValid = true;
    }

    private void sortEquations() {
        Iterator<Integer> it = IntStream.range(0, equationColumn.length).boxed()
                .sorted((i1, i2) -> equationColumn[i1] - equationColumn[i2]).iterator();
        int i = 0;
        while (it.hasNext()) {
            sortedEquationIndexArray[i] = it.next();
            i += 1;
        }
        equationOrderValid = true;
    }

    @Override
    public void der(boolean update, Matrix matrix) {

        if (!equationDataValid) {
            initEquationData();
            equationOrderValid = false;
        }

        if (!equationOrderValid) {
            sortEquations();
        }

        if (!variableRowDataValid) {
            updateVariableRows();
        }

        if (!variableValuesValid) {
            updateVariables();
        }

        derSortedTerms();

        for (int sortedEqIndex = 0; sortedEqIndex < equationActiveStatus.length; sortedEqIndex++) {
            int eqIndex = sortedEquationIndexArray[sortedEqIndex];
        //for (int eqIndex = 0; eqIndex < equationActiveStatus.length; eqIndex++) {
            if (equationActiveStatus[eqIndex]) {
                int col = equationColumn[eqIndex];
                int varEnd = variablePerEquationIndex[eqIndex] + variableCountPerEquation[eqIndex];
                for (int varIndex = variablePerEquationIndex[eqIndex]; varIndex < varEnd; varIndex++) {
                    if (variableRowPerEquation[varIndex] >= 0) {
                        if (update) {
                            matrix.addAtIndex(matrixIndexPerVariableAndEquation[varIndex], deriveResultPerVariableAndEquation[varIndex]);
                        } else {
                            matrixIndexPerVariableAndEquation[varIndex] = matrix.addAndGetIndex(variableRowPerEquation[varIndex], col, deriveResultPerVariableAndEquation[varIndex]);
                        }
                    }
                }
            }
        }
    }

    private void evalSortedTerms() {
        Arrays.fill(evalResultPerEquation, 0);
        evalSortedTermsVec();
        evalSortedTermsObj();
    }

    private void evalSortedTermsVec() {
        int branchNum = -1;
        for (int termIndex = 0; termIndex < sortedTermsForEval.length; termIndex++) {
            if (equationActiveStatus[termEquationVectorIndexForEval[termIndex]] &&
                    termActiveStatus[termVectorIndexForEval[termIndex]]) {
                branchNum = termByEquationBranchNumForEval[termIndex];
                if (termVecToValForEval[termIndex] != null) {
                    evalResultPerEquation[termByEvalResultIndex[termIndex]] +=
                        termVecToValForEval[termIndex].value(v1[branchNum], v2[branchNum], sinKsi[branchNum], cosKsi[branchNum], sinTheta2[branchNum], cosTheta2[branchNum], sinTheta1[branchNum], cosTheta1[branchNum],
                                b1[branchNum], b2[branchNum],
                                g1[branchNum], g2[branchNum], y[branchNum], g12[branchNum], b12[branchNum], a1Evaluated[branchNum], r1Evaluated[branchNum],
                                branchNum, branchNameByBranchNum);
                }
            }
        }
    }

    private void evalSortedTermsObj() {
        for (int termIndex = 0; termIndex < sortedTermsForEval.length; termIndex++) {
            if (equationActiveStatus[termEquationVectorIndexForEval[termIndex]] &&
                    termActiveStatus[termVectorIndexForEval[termIndex]]) {
                if (termVecToValForEval[termIndex] == null) {
                    evalResultPerEquation[termByEvalResultIndex[termIndex]] += sortedTermsForEval[termIndex].evalLhs();
                }

            }
        }
    }

    private void derSortedTerms() {
        Arrays.fill(deriveResultPerVariableAndEquation, 0);
        derSortedTermsVec();
        derSortedTermsObj();
    }

    private void derSortedTermsVec() {
        int branchNum = -1;
        for (int termIndex = 0; termIndex < sortedTermsForDer.length; termIndex++) {
            if (equationActiveStatus[termEquationVectorIndexForDer[termIndex]] &&
                    termActiveStatus[termVectorIndexForDer[termIndex]]) {
                branchNum = termByVariableBranchNumForDer[termIndex];
                if (termVecToValForDer[termIndex] != null) {
                    deriveResultPerVariableAndEquation[termByVariableDeriveResultIndex[termIndex]] +=
                            termVecToValForDer[termIndex].value(v1[branchNum], v2[branchNum], sinKsi[branchNum], cosKsi[branchNum], sinTheta2[branchNum], cosTheta2[branchNum], sinTheta1[branchNum], cosTheta1[branchNum],
                                    b1[branchNum], b2[branchNum],
                                    g1[branchNum], g2[branchNum], y[branchNum], g12[branchNum], b12[branchNum], a1Evaluated[branchNum], r1Evaluated[branchNum],
                                    branchNum, branchNameByBranchNum);
                }
            }
        }
    }

    private void derSortedTermsObj() {
        for (int termIndex = 0; termIndex < sortedTermsForDer.length; termIndex++) {
            if (equationActiveStatus[termEquationVectorIndexForDer[termIndex]] &&
                    termActiveStatus[termVectorIndexForDer[termIndex]]) {
                if (termVecToValForDer[termIndex] == null) {
                    deriveResultPerVariableAndEquation[termByVariableDeriveResultIndex[termIndex]] += sortedTermsForDer[termIndex].der(termVariable[termIndex]);
                }

            }
        }
    }

}
