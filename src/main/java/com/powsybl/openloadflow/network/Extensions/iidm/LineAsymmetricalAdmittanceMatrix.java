package com.powsybl.openloadflow.network.Extensions.iidm;

public class LineAsymmetricalAdmittanceMatrix {

    // We define the admittance matrix by the matrix verifying the relation:
    //
    // [If]   [Yff Yfg Yfh] [Vf]
    // [Ig] = [Ygf Ygg Ygh].[vg]
    // [Ih]   [Yhf Yhg Yhh] [vh]
    //
    // where (f,g,h) could be: (A,B,C) in 3-phase representation or
    //                         (0,1,2) in 3-sequence Fortescue representation

    // used to define Yabc or Y012
    private final double y11x;
    private final double y11y;
    private final double y12x;
    private final double y12y;
    private final double y13x;
    private final double y13y;

    private final double y21x;
    private final double y21y;
    private final double y22x;
    private final double y22y;
    private final double y23x;
    private final double y23y;

    private final double y31x;
    private final double y31y;
    private final double y32x;
    private final double y32y;
    private final double y33x;
    private final double y33y;

    LineAsymmetricalAdmittanceMatrix(double y11x, double y11y, double y12x, double y12y, double y13x, double y13y,
                     double y21x, double y21y, double y22x, double y22y, double y23x, double y23y,
                     double y31x, double y31y, double y32x, double y32y, double y33x, double y33y) {

        this.y11x = y11x;
        this.y11y = y11y;
        this.y12x = y12x;
        this.y12y = y12y;
        this.y13x = y13x;
        this.y13y = y13y;

        this.y21x = y21x;
        this.y21y = y21y;
        this.y22x = y22x;
        this.y22y = y22y;
        this.y23x = y23x;
        this.y23y = y23y;

        this.y31x = y31x;
        this.y31y = y31y;
        this.y32x = y32x;
        this.y32y = y32y;
        this.y33x = y33x;
        this.y33y = y33y;
    }
}
