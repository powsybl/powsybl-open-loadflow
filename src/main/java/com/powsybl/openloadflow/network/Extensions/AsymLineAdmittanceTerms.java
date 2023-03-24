package com.powsybl.openloadflow.network.Extensions;

import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.openloadflow.util.Fortescue;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class AsymLineAdmittanceTerms {

    // This class is made to build and access the admittance terms that will be used to fill the Jacobian :
    // The following formulation approach is used :
    //                                        1      ________        2
    // [ Io1 ]             [ Vo1 ]            o-----|        |-------o
    // [ Id1 ]             [ Vd1 ]            d-----|  Yodi  |-------d
    // [ Ii1 ]             [ Vi1 ]            i-----|________|-------i
    // [ Io2 ] = [Yodi] *  [ Vo2 ]
    // [ Id2 ]             [ Vd2 ]
    // [ Ii2 ]             [ Vi2 ]
    //
    // Given that at bus n where j are all neighbouring busses:
    // Sum[j](Sd_nj) = P_n + j.Q_n = Sum[j](Vd_n.Id_nj*)
    //               Po_n + j.Qo_n = Sum[j](Vo_n.Io_nj*)
    //               Pi_n + j.Qi_n = Sum[j](Vi_n.Ii_nj*)
    //
    // Substituting [I] by [Yodi]*[V] allows to know the equations terms that will fill the jacobian matrix
    //
    // Step 1: Get [Yodi]
    // ------------------
    // First step is to compute [ Yodi ] from a 3-phase description because this is how we can describe unbalances of phases for a line:
    // For each a,b,c phase we know the following relation (only true for lines with no mutual inductances, otherwise we must handle full [Yabc] matrix):
    // [Ia1]   [ ya11 ya12 ]   [Va1]
    // [Ia2] = [ ya21 ya22 ] * [Va2]
    //            with (for a line only)  :  ya11 = ga1 + j.ba1 + 1/za   , ya12 = -1/za   ,   ya21 = -1/za   ,  ya22 = ga2 + j.ba2 + 1/za
    //
    // From the fortescue transformation we have:
    // [Ga]         [Go]
    // [Gb] = [F] * [Gd]
    // [Gc]         [Gi]
    //     where [G] might be [V] or [I]
    //     where [F] is the fortescue transformation matrix
    //
    // Therefore we have:
    //                           [ya11  0    0  ya12  0   0  ]
    //                           [  0  yb11  0    0  yb12 0  ]
    //          [inv(F)   O  ]   [  0   0   yc11  0   0  yc12]   [ F  0 ]
    // [Yodi] = [  0   inv(F)] * [ya21  0    0  ya22  0   0  ] * [ 0  F ]
    //                           [  0  yb21  0    0  yb22 0  ]
    //                           [  0   0   yc21  0   0  yc22]
    //
    // [Yodi] is a complex matrix
    //
    // Step 2: Define the generic term that will be used to make the link between [Yodi] and S[o,d,i] the apparent power
    // -----------------------------------------------------------------------------------------------------------------
    // We define T(i,j,g,h) = rho_i * rho_j * exp(j(a_i-a_j)) * y*_ij_gh * V_gi * V*_hj
    //    where i,j are line's ends i,j included in {1,2}
    //    where g,h are fortescue sequences g,h included in {o,d,i}
    //
    //
    // Step 3 : express the expanded value of T(i,j,g,h):
    // --------------------------------------------------
    // T(i,j,g,h) =     rho_i * rho_j * V_gi * V_hj * yx_ij_gh * cos(a_i - a_j + th_gi - th_hj)
    //                - rho_i * rho_j * V_gi * V_hj * yy_ij_gh * sin(a_i - a_j + th_gi - th_hj)
    //             -j(  rho_i * rho_j * V_gi * V_hj * yx_ij_gh * sin(a_i - a_j + th_gi - th_hj)
    //                + rho_i * rho_j * V_gi * V_hj * yy_ij_gh * cos(a_i - a_j + th_gi - th_hj) )
    //
    // Step 4 : express the apparent powers with T():
    // ----------------------------------------------
    // S_o_12 = T(1,1,o,o) + T(1,1,o,d) + T(1,1,o,i) - T(1,2,o,o) - T(1,2,o,d) - T(1,2,o,i)
    // S_d_12 = T(1,1,d,o) + T(1,1,d,d) + T(1,1,d,i) - T(1,2,d,o) - T(1,2,d,d) - T(1,2,d,i)
    // S_i_12 = T(1,1,i,o) + T(1,1,i,d) + T(1,1,i,i) - T(1,2,i,o) - T(1,2,i,d) - T(1,2,i,i)
    //
    // Step 5 : make the link between y_ij_gh in T() and [Yodi]:
    // ---------------------------------------------------------
    // By construction we have :
    //          [ y_11_oo y_11_od y_11_oi y_12_oo y_12_od y_12_oi ]
    //          [ y_11_do y_11_dd y_11_di y_12_do y_12_dd y_12_di ]
    // [Yodi] = [ y_11_io y_11_id y_11_ii y_12_io y_12_id y_12_ii ]
    //          [ y_21_oo y_21_od y_21_oi y_22_oo y_22_od y_22_oi ]
    //          [ y_21_do y_21_dd y_21_di y_22_do y_22_dd y_22_di ]
    //          [ y_21_io y_21_id y_21_ii y_22_io y_22_id y_22_ii ]

    public AsymLineAdmittanceTerms(AsymLine asymLine) {

        this.mYabc = buildYabcAdmittanceMatrix(asymLine);
        //System.out.println("Yabc = ");
        //mYabc.print(System.out);
        this.mYodi = buildYodiMatrix(mYabc,
                buildTwoBlocsMatrix(Fortescue.getFortescueMatrix()),
                buildTwoBlocsMatrix(Fortescue.getFortescueInverseMatrix()));
        //System.out.println("Yodi = ");
        //mYodi.print(System.out);

    }

    private DenseMatrix mYabc;
    private DenseMatrix mYodi;

    public static DenseMatrix buildYabcAdmittanceMatrix(AsymLine asymLine) {

        DenseMatrix y = new DenseMatrix(12, 12);

        AsymLine.AsymLinePhase phaseA = asymLine.getLinePhaseA();
        double rA = phaseA.getrPhase(); // TODO : give right pu from iidm
        double xA = phaseA.getxPhase();
        double gA = rA / (rA * rA + xA * xA);
        double bA = -xA / (rA * rA + xA * xA);
        if (phaseA.isPhaseOpen()) {
            gA = 0.;
            bA = 0.;
        }

        AsymLine.AsymLinePhase phaseB = asymLine.getLinePhaseB();
        double rB = phaseB.getrPhase(); // TODO : give right pu from iidm
        double xB = phaseB.getxPhase();
        double gB = rB / (rB * rB + xB * xB);
        double bB = -xB / (rB * rB + xB * xB);
        if (phaseB.isPhaseOpen()) {
            gB = 0.;
            bB = 0.;
        }

        AsymLine.AsymLinePhase phaseC = asymLine.getLinePhaseC();
        double rC = phaseC.getrPhase(); // TODO : give right pu from iidm
        double xC = phaseC.getxPhase();
        double gC = rC / (rC * rC + xC * xC);
        double bC = -xC / (rC * rC + xC * xC);
        if (phaseC.isPhaseOpen()) {
            gC = 0.;
            bC = 0.;
        }

        //bloc ya11
        y.add(0, 0, gA);
        y.add(0, 1, -bA);
        y.add(1, 0, bA);
        y.add(1, 1, gA);

        //bloc ya12
        y.add(0, 6, -gA);
        y.add(0, 7, bA);
        y.add(1, 6, -bA);
        y.add(1, 7, -gA);

        //bloc yb11
        y.add(2, 2, gB);
        y.add(2, 3, -bB);
        y.add(3, 2, bB);
        y.add(3, 3, gB);

        //bloc yb12
        y.add(2, 8, -gB);
        y.add(2, 9, bB);
        y.add(3, 8, -bB);
        y.add(3, 9, -gB);

        //bloc yc11
        y.add(4, 4, gC);
        y.add(4, 5, -bC);
        y.add(5, 4, bC);
        y.add(5, 5, gC);

        //bloc yc12
        y.add(4, 10, -gC);
        y.add(4, 11, bC);
        y.add(5, 10, -bC);
        y.add(5, 11, -gC);

        //-----
        //bloc ya22
        y.add(6, 6, gA);
        y.add(6, 7, -bA);
        y.add(7, 6, bA);
        y.add(7, 7, gA);

        //bloc ya21
        y.add(6, 0, -gA);
        y.add(6, 1, bA);
        y.add(7, 0, -bA);
        y.add(7, 1, -gA);

        //bloc yb22
        y.add(8, 8, gB);
        y.add(8, 9, -bB);
        y.add(9, 8, bB);
        y.add(9, 9, gB);

        //bloc yb21
        y.add(8, 2, -gB);
        y.add(8, 3, bB);
        y.add(9, 2, -bB);
        y.add(9, 3, -gB);

        //bloc yc22
        y.add(10, 10, gC);
        y.add(10, 11, -bC);
        y.add(11, 10, bC);
        y.add(11, 11, gC);

        //bloc yc21
        y.add(10, 4, -gC);
        y.add(10, 5, bC);
        y.add(11, 4, -bC);
        y.add(11, 5, -gC);

        return y;
    }

    public static DenseMatrix buildTwoBlocsMatrix(DenseMatrix m66) {
        // expected 6x6 matrix in input to build a 12x12 matrix
        DenseMatrix mFbloc = new DenseMatrix(12, 12);
        for (int i = 0; i < 6; i++) {
            for (int j = 0; j < 6; j++) {
                mFbloc.add(i, j, m66.get(i, j));
                mFbloc.add(i + 6, j + 6, m66.get(i, j));
            }
        }

        return mFbloc;
    }

    public static DenseMatrix buildYodiMatrix(DenseMatrix yabc, DenseMatrix fBloc, DenseMatrix fInverseBloc) {
        DenseMatrix yabcF = yabc.times(fBloc);
        DenseMatrix yodi = fInverseBloc.times(yabcF);

        // clean matrix in case after fortescue and inverse multiplication
        for (int i = 0; i < 12; i++) {
            for (int j = 0; j < 12; j++) {
                if (Math.abs(yodi.get(i, j)) < 0.00000001) {
                    yodi.set(i, j, 0.);
                }
            }
        }

        return yodi;
    }

    public DenseMatrix getmYodi() {
        return mYodi;
    }

    public DenseMatrix getmYabc() {
        return mYabc;
    }
}
