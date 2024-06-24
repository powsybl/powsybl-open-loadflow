///**
// * Copyright (c) 2019, RTE (http://www.rte-france.com)
// * This Source Code Form is subject to the terms of the Mozilla Public
// * License, v. 2.0. If a copy of the MPL was not distributed with this
// * file, You can obtain one at http://mozilla.org/MPL/2.0/.
// * SPDX-License-Identifier: MPL-2.0
// */
//
//package com.powsybl.openloadflow.ac.solver;
//import com.powsybl.openloadflow.ac.AcloadFlowEngine;
//import com.powsybl.openloadflow.ac.equations.AcVariableType;
//import com.powsybl.openloadflow.ac.solver.KnitroSolver;
//import com.artelys.knitro.api.*;
//import com.artelys.knitro.api.callbacks.*;
//import com.powsybl.openloadflow.ac.equations.AcEquationType;
//import com.powsybl.openloadflow.equations.EquationTerm;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//import java.util.*;
//
///**
// * @author Jeanne Archambault {@literal <jeanne.archambault at artelys.com>}
// */
//
//public final class SolverUtils {
//
//    private static final Logger LOGGER = LoggerFactory.getLogger(KnitroSolver.class);
//
//    public static List<List<Integer>> addConstraint(AcEquationType typeEq, int equationId, List<EquationTerm<AcVariableType, AcEquationType>> terms) {
//        switch (typeEq) {
//            case BUS_TARGET_P :
//                addConstraintTargetPTargetQDummyTargetPDummyTargetQ(typeEq, equationId, terms);
//            case BUS_TARGET_Q :
//                addConstraintTargetPTargetQDummyTargetPDummyTargetQ(terms);
//            case DUMMY_TARGET_P :
//                addConstraintTargetPTargetQDummyTargetPDummyTargetQ( terms);
//            case DUMMY_TARGET_Q :
//                addConstraintTargetPTargetQDummyTargetPDummyTargetQ(terms);
//            case ZERO_V :
//                addConstraintZeroV(terms);
//            case ZERO_PHI:
//                addConstraintZeroPhi(terms);
//            case DISTR_Q :
//                addConstraintDistrQ(terms);
//        }
//    }
//
//    public static void addConstraintTargetPTargetQDummyTargetPDummyTargetQ(AcEquationType typeEq, int equationId, List<EquationTerm<AcVariableType, AcEquationType>> terms) {
//        // get the variable V/Theta corresponding to the constraint
//        int idVar = terms.get(0).getVariables().get(0).getRow();
//        addConstraintLinearPart(equationId, idVar, 1.0);
//        logAddConstraint(equationId,typeEq,idVar);
//    }
//
//    public static void addConstraintZeroV(){
//
//    }
//
//    public static void addConstraintZeroPhi(){
//
//    }
//
//    public static void addConstraintDistrQ(){
//
//    }
//
//    public static void logAddConstraint(int equationId, AcEquationType typeEq, int idVar) {
//        if (LOGGER.isTraceEnabled()) {
//            LOGGER.trace("Adding linear constraint n° {} of type {}, with variable {}", equationId, typeEq, idVar);
//        }
//    }
//
//}
