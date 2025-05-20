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
    private final int[] localIndexes;
    protected final double[] values;

    public EquationDerivativeVector(List<EquationDerivativeElement<?>> elements) {
        int size = elements.size();
        termArrayNums = new int[size];
        termNums = new int[size];
        rowRefs = new int[size][1];
        rows = new int[size];
        localIndexes = new int[size];
        values = new double[size];
        for (int i = 0; i < size; i++) {
            EquationDerivativeElement<?> element = elements.get(i);
            termArrayNums[i] = element.termArrayNum;
            termNums[i] = element.termNum;
            localIndexes[i] = element.derivative.getLocalIndex();
            Variable<?> variable = element.derivative.getVariable();
            rowRefs[i] = variable.getRowRef();
        }
    }

    void update(EquationArray<?,?> equationArray) {
        // compute all derivatives for each of the term array
        var termArrays = equationArray.getTermArrays();
        double[][][] termDerValuesByArrayIndex = new double[termArrays.size()][][];
        for (int i = 0; i < termArrays.size(); i++) {
            termDerValuesByArrayIndex[i] = termArrays.get(i).evalDer();
        }

        for (int i = 0; i < termNums.length; i++) {
            int termNum = termNums[i];
            // get term array to which this term belongs
            int termArrayNum = termArrayNums[i];
            var termArray = termArrays.get(termArrayNum);

            // skip inactive terms and get term derivative value
            if (termArray.isTermActive(termNum)) {
                // add value (!!! we can have multiple terms contributing to same matrix element)
                double[][] termDerValues = termDerValuesByArrayIndex[termArrayNum];
                int termElementNum = termArray.getTermElementNum(termNum);
                values[i] = termDerValues[localIndexes[i]][termElementNum];
            } else {
                values[i] = 0.0;
            }
        }
        for (int i = 0; i < termNums.length; i++) {
            rows[i] = rowRefs[i][0];
        }
    }
}
