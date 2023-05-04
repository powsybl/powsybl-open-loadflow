package com.powsybl.openloadflow.network;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public enum Side {
    ONE(1),
    TWO(2);

    private final int num;

    Side(int num) {
        this.num = num;
    }

    public int getNum() {
        return num;
    }
}
