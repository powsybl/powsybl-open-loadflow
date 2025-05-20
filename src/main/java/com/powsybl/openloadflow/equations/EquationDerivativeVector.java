package com.powsybl.openloadflow.equations;

import java.util.List;

/**
 * @author Florian Dupuy {@literal <florian.dupuy at rte-france.com>}
 */
class EquationDerivativeVector {

    protected final int[] termArrayNums;
    private final int[] termNums;

    // cache
    protected final boolean[] termActives;
    protected final int[] termElementNums;
    protected final int[][] rows;
    protected final int[] localIndexes;

    public EquationDerivativeVector(List<EquationDerivativeElement<?>> elements) {
        int size = elements.size();
        termArrayNums = new int[size];
        termNums = new int[size];
        rows = new int[size][1];
        termActives = new boolean[size];
        termElementNums = new int[size];
        localIndexes = new int[size];
        for (int i = 0; i < size; i++) {
            EquationDerivativeElement<?> element = elements.get(i);
            termArrayNums[i] = element.termArrayNum;
            termNums[i] = element.termNum;
            localIndexes[i] = element.derivative.getLocalIndex();
            Variable<?> variable = element.derivative.getVariable();
            rows[i] = variable.getRowRef();
        }
    }

    void update(EquationArray<?,?> equationArray) {
        for (int i = 0; i < termNums.length; i++) {
            int termNum = termNums[i];
            int termArrayNum = termArrayNums[i];
            var termArray = equationArray.getTermArrays().get(termArrayNum);
            termActives[i] = termArray.isTermActive(termNum);
            termElementNums[i] = termArray.getTermElementNum(termNum);
        }
    }

    public int getRow(int i) {
        return rows[i][0];
    }
}
