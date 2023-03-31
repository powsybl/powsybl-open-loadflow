package com.powsybl.openloadflow.network.Extensions;

import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.openloadflow.util.Fortescue;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class AsymLineAdmittanceMatrix {

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

    private DenseMatrix mYabc;
    private DenseMatrix mY012;

    public AsymLineAdmittanceMatrix(AsymLine asymLine, Fortescue.ComponentType componentType) {

        if (componentType == Fortescue.ComponentType.FORTESCUE) {
            // input values are given in fortescue component, we build first Yodi and deduce Yabc

            this.mY012 = buildYadmittanceMatrix(asymLine);
            this.mYabc = productMatrixM1M2M3(buildTwoBlocsMatrix(Fortescue.getFortescueMatrix()), mYabc,
                    buildTwoBlocsMatrix(Fortescue.getFortescueInverseMatrix()));
            // if one phase or more are disconnected we need to update Yabc and then Yodi
            boolean isOpen = false;
            if (asymLine.isOpenA()) {
                // we cancel all lines and columns that impact Va or Ia
                cancelComponentMatrix(mYabc, 1);
                isOpen = true;
            }

            if (asymLine.isOpenB()) {
                // we cancel all lines and columns that impact Va or Ia
                cancelComponentMatrix(mYabc, 2);
                isOpen = true;
            }

            if (asymLine.isOpenC()) {
                // we cancel all lines and columns that impact Va or Ia
                cancelComponentMatrix(mYabc, 3);
                isOpen = true;
            }

            if (isOpen) {
                this.mY012 = productMatrixM1M2M3(buildTwoBlocsMatrix(Fortescue.getFortescueInverseMatrix()), mYabc,
                        buildTwoBlocsMatrix(Fortescue.getFortescueMatrix()));
            }
        } else {
            // input values are given in ABC component, we build first Yabc and deduce Yodi
            this.mYabc = buildYadmittanceMatrix(asymLine);
            this.mY012 = productMatrixM1M2M3(buildTwoBlocsMatrix(Fortescue.getFortescueInverseMatrix()), mYabc,
                    buildTwoBlocsMatrix(Fortescue.getFortescueMatrix()));
        }
    }

    public DenseMatrix buildYadmittanceMatrix(AsymLine asymLine) {
        if (asymLine.getAdmittanceTerms() != null) {
            AsymLineAdmittanceTerms admittanceTerms = asymLine.getAdmittanceTerms();
            return buildYadmittanceMatrix(admittanceTerms);
        } else if (asymLine.getPiValues() != null) {
            AsymLinePiValues piValues = asymLine.getPiValues();
            return buildYadmittanceMatrix(asymLine, piValues);
        } else {
            throw new IllegalStateException("Could not build Yabc of line : ");
        }
    }

    public DenseMatrix buildYadmittanceMatrix(AsymLineAdmittanceTerms admTerms) {
        DenseMatrix mY = new DenseMatrix(12, 12);
        add22Bloc(admTerms.getY11().getFirst(), admTerms.getY11().getSecond(), 1, 1, mY);
        add22Bloc(admTerms.getY12().getFirst(), admTerms.getY12().getSecond(), 1, 2, mY);
        add22Bloc(admTerms.getY13().getFirst(), admTerms.getY13().getSecond(), 1, 3, mY);

        add22Bloc(admTerms.getY21().getFirst(), admTerms.getY21().getSecond(), 2, 1, mY);
        add22Bloc(admTerms.getY22().getFirst(), admTerms.getY22().getSecond(), 2, 2, mY);
        add22Bloc(admTerms.getY23().getFirst(), admTerms.getY23().getSecond(), 2, 3, mY);

        add22Bloc(admTerms.getY31().getFirst(), admTerms.getY31().getSecond(), 3, 1, mY);
        add22Bloc(admTerms.getY32().getFirst(), admTerms.getY32().getSecond(), 3, 2, mY);
        add22Bloc(admTerms.getY33().getFirst(), admTerms.getY33().getSecond(), 3, 3, mY);

        return mY;
    }

    public DenseMatrix buildYadmittanceMatrix(AsymLine asymLine, AsymLinePiValues piValues) {

        DenseMatrix mY = new DenseMatrix(12, 12);

        double r1 = piValues.piComponent1.getR(); // TODO : give right pu from iidm
        double x1 = piValues.piComponent1.getX();
        double g1i = piValues.piComponent1.getG1();
        double g1j = piValues.piComponent1.getG2();
        double b1i = piValues.piComponent1.getB1();
        double b1j = piValues.piComponent1.getB2();
        double g1ij = r1 / (r1 * r1 + x1 * x1);
        double b1ij = -x1 / (r1 * r1 + x1 * x1);
        if (asymLine.isOpenA()) {
            g1ij = 0.;
            b1ij = 0.;
            g1i = 0.;
            g1j = 0;
            b1i = 0;
            b1j = 0;
        }
        double g1ji = g1ij;
        double b1ji = b1ij;

        double r2 = piValues.piComponent2.getR();  // TODO : give right pu from iidm
        double x2 = piValues.piComponent2.getX();
        double g2i = piValues.piComponent2.getG1();
        double g2j = piValues.piComponent2.getG2();
        double b2i = piValues.piComponent2.getB1();
        double b2j = piValues.piComponent2.getB2();
        double g2ij = r2 / (r2 * r2 + x2 * x2);
        double b2ij = -x2 / (r2 * r2 + x2 * x2);
        if (asymLine.isOpenB()) {
            g2ij = 0.;
            b2ij = 0.;
            g2i = 0.;
            g2j = 0;
            b2i = 0;
            b2j = 0;
        }
        double g2ji = g2ij;
        double b2ji = b2ij;

        double r3 = piValues.piComponent3.getR(); // TODO : give right pu from iidm
        double x3 = piValues.piComponent3.getX();
        double g3i = piValues.piComponent3.getG1();
        double g3j = piValues.piComponent3.getG2();
        double b3i = piValues.piComponent3.getB1();
        double b3j = piValues.piComponent3.getB2();
        double g3ij = r3 / (r3 * r3 + x3 * x3);
        double b3ij = -x3 / (r3 * r3 + x3 * x3);
        if (asymLine.isOpenC()) {
            g3ij = 0.;
            b3ij = 0.;
            g3i = 0.;
            g3j = 0;
            b3i = 0;
            b3j = 0;
        }
        double g3ji = g3ij;
        double b3ji = b3ij;

        // TODO : add gi, gj, bi, bj in the matrix
        //bloc ya11
        add22Bloc(g1ij + g1i, b1ij + b1i, 1, 1, mY);
        //bloc ya12
        add22Bloc(-g1ij, -b1ij, 1, 4, mY);
        //bloc yb11
        add22Bloc(g2ij + g2i, b2ij + b2i, 2, 2, mY);
        //bloc yb12
        add22Bloc(-g2ij, -b2ij, 2, 5, mY);
        //bloc yc11
        add22Bloc(g3ij + g3i, b3ij + b3i, 3, 3, mY);
        //bloc yc12
        add22Bloc(-g3ij, -b3ij, 3, 6, mY);

        //-----
        //bloc ya22
        add22Bloc(g1ji + g1j, b1ji + b1j, 4, 4, mY);
        //bloc ya21
        add22Bloc(-g1ji, -b1ji, 4, 1, mY);
        //bloc yb22
        add22Bloc(g2ji + g2j, b2ji + b2j, 5, 5, mY);
        //bloc yb21
        add22Bloc(-g2ji, -b2ji, 5, 2, mY);
        //bloc yc22
        add22Bloc(g3ji + g3j, b3ji + b3j, 6, 6, mY);
        //bloc yc21
        add22Bloc(-g3ji, -b3ji, 6, 3, mY);

        return mY;
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

    public static void cancelLineMatrix(DenseMatrix m, int iCancel) {
        for (int j = 0; j < m.getColumnCount(); j++) {
            m.set(iCancel, j, 0);
        }
    }

    public static void cancelColumnMatrix(DenseMatrix m, int jCancel) {
        for (int i = 0; i < m.getRowCount(); i++) {
            m.set(i, jCancel, 0);
        }
    }

    public static void cancelComponentMatrix(DenseMatrix m, int component) {

        cancelLineMatrix(m, 2 * component - 2);
        cancelLineMatrix(m, 2 * component - 1);
        cancelLineMatrix(m, 2 * component + 4);
        cancelLineMatrix(m, 2 * component + 5);

        cancelColumnMatrix(m, 2 * component - 2);
        cancelColumnMatrix(m, 2 * component - 1);
        cancelColumnMatrix(m, 2 * component + 4);
        cancelColumnMatrix(m, 2 * component + 5);
    }

    public static void add22Bloc(double mx, double my, int i, int j, DenseMatrix m) {
        m.add(2 * (i - 1), 2 * (j - 1), mx);
        m.add(2 * (i - 1), 2 * (j - 1) + 1, -my);
        m.add(2 * (i - 1) + 1, 2 * (j - 1), my);
        m.add(2 * (i - 1) + 1, 2 * (j - 1) + 1, mx);
    }

    public static DenseMatrix productMatrixM1M2M3(DenseMatrix m1, DenseMatrix m2, DenseMatrix m3) {
        DenseMatrix m2M3 = m2.times(m3);
        DenseMatrix mResult = m1.times(m2M3);

        // clean matrix in case after fortescue and inverse multiplication
        for (int i = 0; i < mResult.getRowCount(); i++) {
            for (int j = 0; j < mResult.getColumnCount(); j++) {
                if (Math.abs(mResult.get(i, j)) < 0.00000001) {
                    mResult.set(i, j, 0.);
                }
            }
        }

        return mResult;
    }

    public DenseMatrix getmY012() {
        return mY012;
    }

    public DenseMatrix getmYabc() {
        return mYabc;
    }

    public static boolean isAdmittanceDecoupled(DenseMatrix m) {
        // checking values of extra diagonal bloc term to see if equations between the three sequences are independant
        return checkBloc(m, 1, 2) || checkBloc(m, 1, 3) || checkBloc(m, 1, 5) || checkBloc(m, 1, 6)
                || checkBloc(m, 2, 1) || checkBloc(m, 2, 3) || checkBloc(m, 2, 4) || checkBloc(m, 2, 6)
                || checkBloc(m, 3, 1) || checkBloc(m, 3, 2) || checkBloc(m, 3, 4) || checkBloc(m, 3, 5)
                || checkBloc(m, 4, 2) || checkBloc(m, 4, 3) || checkBloc(m, 4, 5) || checkBloc(m, 4, 6)
                || checkBloc(m, 5, 1) || checkBloc(m, 5, 3) || checkBloc(m, 5, 4) || checkBloc(m, 5, 6)
                || checkBloc(m, 6, 1) || checkBloc(m, 6, 2) || checkBloc(m, 6, 4) || checkBloc(m, 6, 5);
    }

    public static boolean checkBloc(DenseMatrix m, int i, int j) {
        double epsilon = 0.00000001;
        double residual = Math.abs(m.get(2 * i - 2, 2 * j - 2)) + Math.abs(m.get(2 * i - 1, 2 * j - 2));
        return residual > epsilon;
    }
}
