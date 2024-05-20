package com.powsybl.openloadflow.ac;
import com.artelys.knitro.api.*;
import com.artelys.knitro.api.callbacks.KNEvalFCCallback;

import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;


public class ExampleNetwork
{
    private static class ProblemNetwork extends KNProblem
    {
        private class callbackEvalFC extends KNEvalFCCallback
        {
            @Override
            public void evaluateFC(final List<Double> x, final List<Double> obj, final List<Double> c)
            {
                obj.set(0,0.0);

                // Contraintes non linéraires
                // Theta
                double theta12 = x.get(1)-x.get(3); //Theta1 - Theta2
                double theta13 = x.get(1)-x.get(5);
                double theta14 = x.get(1)-x.get(7);
                double theta23 = x.get(3)-x.get(5);
                double theta34 = x.get(5)-x.get(7) ;
                double theta21 = -theta12;
                double theta31 = -theta13;
                double theta41 = -theta14;
                double theta32 = -theta23;
                double theta43 = -theta34;

                // Voltages
                double V1V2 = x.get(0)*x.get(2); //V1*V2
                double V1V3 = x.get(0)*x.get(4);
                double V1V4 = x.get(0)*x.get(6);
                double V2V3 = x.get(2)*x.get(4);
                double V3V4 = x.get(4)*x.get(6);

                // Contraintes par lignes
                // 1====2
                c.set(8,10*V1V2*Math.sin(theta12)); //P12
                c.set(9,-10*V1V2*Math.cos(theta12)); //Q12
                c.set(10,10*V1V2*Math.sin(theta21)); //P21
                c.set(11,-10*V1V2*Math.cos(theta21)); //Q21
                //1====3
                c.set(12,10*V1V3*Math.sin(theta13)); //P13
                c.set(13,-10*V1V3*Math.cos(theta13)); //Q13
                c.set(14,10*V1V3*Math.sin(theta31)); //P31
                c.set(15,-10*V1V3*Math.cos(theta31)); //Q31
                //1====4
                c.set(16,10*V1V4*Math.sin(theta14)); //P14
                c.set(17,-10*V1V4*Math.cos(theta14)); //Q14
                c.set(18,10*V1V4*Math.sin(theta41)); //P41
                c.set(19,-10*V1V4*Math.cos(theta41)); //Q41
                //2====3
                c.set(20,10*V2V3*Math.sin(theta23)); //P23
                c.set(21,-10*V2V3*Math.cos(theta23)); //Q23
                c.set(22,10*V2V3*Math.sin(theta32)); //P32
                c.set(23,-10*V2V3*Math.cos(theta32)); //Q32
                //3====4
                c.set(24,10*V3V4*Math.sin(theta34)); //P34
                c.set(25,-10*V3V4*Math.cos(theta34)); //Q34
                c.set(26,10*V3V4*Math.sin(theta43)); //P43
                c.set(27,-10*V3V4*Math.cos(theta43)); //Q43


            }
        }

//        private class callbackEvalG extends KNEvalGACallback
//        {
//            @Override
//            public void evaluateGA(final List<Double> x, final List<Double> objGrad, final List<Double> jac)
//            {
//            }
//        }

//        private class callbackEvalH extends KNEvalHCallback
//        {
//            @Override
//            public void evaluateH(final List<Double> x, final double sigma, final List<Double> lambda, List<Double> hess)
//            {
//                /* Evaluate the hessian of the nonlinear objective.
//                 *  Note: Since the Hessian is symmetric, we only provide the
//                 *        nonzero elements in the upper triangle (plus diagonal).
//                 *        These are provided in row major ordering as specified
//                 *        by the setting KN_DENSE_ROWMAJOR in "KN_set_cb_hess()".
//                 *  Note: The Hessian terms for the quadratic constraints
//                 *        will be added internally by Knitro to form
//                 *        the full Hessian of the Lagrangian. */
//            }
//        }

        ProblemNetwork() throws KNException
        {
            super(28, 28);
            List<Integer> listVarTypes = new ArrayList<>(Collections.nCopies(28, KNConstants.KN_VARTYPE_CONTINUOUS));
            List<Double> listVarLoBnds = new ArrayList<>(Collections.nCopies(28, -KNConstants.KN_INFINITY));
            List<Double> listVarUpBnds = new ArrayList<>(Collections.nCopies(28, KNConstants.KN_INFINITY));

            setVarTypes(listVarTypes);
            setVarLoBnds(listVarLoBnds);
//            setXInitial(Arrays.asList(0,1,2,3,4,5,6,7),Arrays.asList(1.,0.,1.,0.,1.,0.,1.,0.));
            setVarUpBnds(listVarUpBnds);
            setVarNames(Arrays.asList("V1","Theta1","V2","Theta2","V3","Theta3","V4","Theta4",
                                    "P12","Q12","P13","Q13","P14","Q14",
                                    "P21","Q21","P23","Q23",
                                    "P31","Q31","P32","Q32","P34","Q34",
                                    "P41","Q41","P43","Q43"));
            // 28 variables, les 8 premières : Vi Thetai ; puis 20 variables de puissance : Pij Qij
            // 28 contraintes, 8 sur les targets V, Theta, Pin, Qin et 20 sur les puissances Pij et Qij (4 * 5 branches = 10)


            // Constraints
            // Targets
            addConstraintLinearPart(0,0,1.0); //V1
            addConstraintLinearPart(1,1,1.0); //Theta1
            addConstraintLinearPart(2, Arrays.asList(14, 16), Arrays.asList(1.0, 1.0)); //P2
            addConstraintLinearPart(3, Arrays.asList(15, 17), Arrays.asList(1.0, 1.0)); //Q2
            addConstraintLinearPart(4, Arrays.asList(18, 20, 22), Arrays.asList(1.0, 1.0, 1.0 )); //P3
            addConstraintLinearPart(5, Arrays.asList(19, 21, 23), Arrays.asList(1.0, 1.0, 1.0 )); //Q3            addConstraintLinearPart(4, Arrays.asList(18, 20, 22), Arrays.asList(1.0, 1.0,1.0 )); //P3
            addConstraintLinearPart(6, Arrays.asList(24, 26), Arrays.asList(1.0, 1.0 )); //P4
            addConstraintLinearPart(7, 6,1.0); //V4

            // Puissances Qij : ajout de la partie quadratique
            addConstraintQuadraticPart(9,0,0,10); //Q12
            addConstraintQuadraticPart(11,2,2,10); //Q21
            addConstraintQuadraticPart(13,0,0,10); //Q13
            addConstraintQuadraticPart(15,4,4,10); //Q31
            addConstraintQuadraticPart(17,0,0,10); //Q14
            addConstraintQuadraticPart(19,6,6,10); //Q41
            addConstraintQuadraticPart(21,2,2,10); //Q23
            addConstraintQuadraticPart(23,4,4,10); //Q32
            addConstraintQuadraticPart(25,4,4,10); //Q34
            addConstraintQuadraticPart(27,6,6,10); //Q43


            // Puissances Pij et Qij : ajout de la partie linéaire (membre droit Pij/Qij)
            for (int i = 8; i <= 27; i++) {
                addConstraintLinearPart(i, i, -1.0);
            }

            // RHS
            setConEqBnds(Arrays.asList(1.0,0.0,-0.01,0.0,-0.04,0.0,0.0,1.0,
                                        0.0,0.0,0.0,0.0,0.0,0.0,
                                        0.0,0.0,0.0,0.0,
                                        0.0,0.0,0.0,0.0,0.0,0.0,
                                        0.0,0.0,0.0,0.0));


            // Register the callback
            setObjEvalCallback(new callbackEvalFC());
            setMainCallbackCstIndexes(Arrays.asList(0,1,2,3,4,5,6,7,8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27));


//
//            KNEvalGACallback callbackGrad = new ExampleNLP1.ProblemNLP1.callbackEvalG();
//            setGradEvalCallback(callbackGrad);
//
//            KNEvalHCallback callbackHess = new ExampleNLP1.ProblemNLP1.callbackEvalH();
//            setHessEvalCallback(callbackHess);
        }
    }


    public static void main(String args[]) throws KNException
    {
        // Create a problem instance.
        ExampleNetwork.ProblemNetwork instance = new ExampleNetwork.ProblemNetwork();
        // Create a solver
        KNSolver solver = new KNSolver(instance);

        solver.getBaseProblem();

        solver.initProblem();

        solver.setParam(KNConstants.KN_PARAM_PRESOLVE_LEVEL,KNConstants.KN_PRESOLVE_LEVEL_2);
//        solver.setParam(KNConstants.KN_PARAM_PRESOLVEOP_SUBSTITUTION,KNConstants.KN_PRESOLVEOP_SUBSTITUTION_ALL); pas de paramètres de subsitution ?
        solver.setParam(KNConstants.KN_PARAM_STRAT_WARM_START, KNConstants.KN_STRAT_WARM_START_YES);
        solver.solve();

        KNSolution solution = solver.getSolution();
        List<Double> constraintValues = solver.getConstraintValues();
        List<String> variableNames = solver.getVariableNames();

        System.out.println("Optimal objective value  = " + solution.getObjValue());
        System.out.println("Optimal x");
        for (int i=0; i<solution.getX().size(); i++)
        {
            System.out.format(" x[%d] = %f%n", i, solution.getX().get(i));
        }
//
//        System.out.println("Optimal objective value  = " + solution.getObjValue());
//        System.out.println("Optimal x (with corresponding multiplier)");
//        for (int i=0; i < instance.getNumVars(); i++)
//        {
//            if(!(variableNames.get(i).equals("")))
//            {
//                System.out.print(variableNames.get(i));
//                System.out.format(" = %f (lambda = %f)%n", solution.getX().get(i), solution.getLambda().get(i));
//            }
//            else
//            {
//                System.out.format(" x[%d] = %f (lambda = %f)%n", i, solution.getX().get(i), solution.getLambda().get(i));
//            }
//        }
        System.out.println("Optimal constraint values (with corresponding multiplier)");
        for (int i=0; i < instance.getNumCons(); i++)
        {
            System.out.format(" c[%d] = %f (lambda = %f)%n", i, constraintValues.get(i), solution.getLambda().get(i));
        }
        System.out.format("Feasibility violation    = %f%n", solver.getAbsFeasError());
        System.out.format("Optimality violation     = %f%n", solver.getAbsOptError());
    }



}
