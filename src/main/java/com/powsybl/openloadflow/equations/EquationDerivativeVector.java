package com.powsybl.openloadflow.equations;

import com.fasterxml.jackson.core.TreeNode;

import java.util.List;

/**
 * @author Florian Dupuy {@literal <florian.dupuy at rte-france.com>}
 */
class EquationDerivativeVector {

    private final int[] termArrayNums;
    private final int[] termNums;

    // cache
    private final int[][] rowRefs;
    protected final int[] rows;
    protected final int[] termElementNum;
    protected final double[] values;
    private final double[][] termDerValues;

    public EquationDerivativeVector(List<EquationDerivativeElement<?>> elements, EquationArray<?,?> equationArray) {
        int size = elements.size();

        // compute all derivatives for each of the term array
        var termArrays = equationArray.getTermArrays();
        double[][][] termDerValuesByArrayIndex = new double[termArrays.size()][][];
        for (int i = 0; i < termArrays.size(); i++) {
            termDerValuesByArrayIndex[i] = termArrays.get(i).evalDer();
        }

        termArrayNums = new int[size];
        termNums = new int[size];
        rowRefs = new int[size][1];
        rows = new int[size];
        termElementNum = new int[size];
        values = new double[size];
        termDerValues = new double[size][];
        int[] localIndexes = new int[size];
        for (int i = 0; i < size; i++) {
            EquationDerivativeElement<?> element = elements.get(i);
            termArrayNums[i] = element.termArrayNum;
            termNums[i] = element.termNum;
            var termArray = termArrays.get(element.termArrayNum);
            termElementNum[i] = termArray.getTermElementNum(element.termNum);
            Variable<?> variable = element.derivative.getVariable();
            rowRefs[i] = variable.getRowRef();
            localIndexes[i] = element.derivative.getLocalIndex();
        }

        for (int i = 0; i < termNums.length; i++) {
            rows[i] = rowRefs[i][0];
        }
        for (int i = 0; i < termNums.length; i++) {
            termDerValues[i] = termDerValuesByArrayIndex[termArrayNums[i]][localIndexes[i]];
        }
    }

    void update(EquationArray<?,?> equationArray) {
        var termArrays = equationArray.getTermArrays();
        for (int i = 0; i < termNums.length; i++) {
            int termNum = termNums[i];
            // get term array to which this term belongs
            int termArrayNum = termArrayNums[i];
            var termArray = termArrays.get(termArrayNum);

            // skip inactive terms and get term derivative value
            if (termArray.isTermActive(termNum)) {
                // add value (!!! we can have multiple terms contributing to same matrix element)
                values[i] = termDerValues[i][termElementNum[i]];
            } else {
                values[i] = 0.0;
            }
        }
        for (int i = 0; i < termNums.length; i++) {
            rows[i] = rowRefs[i][0];
        }
    }
}
