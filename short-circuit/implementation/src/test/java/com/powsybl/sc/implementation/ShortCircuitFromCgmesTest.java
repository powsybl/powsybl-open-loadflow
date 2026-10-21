/**
 * Copyright (c) 2026, Jean-Baptiste Heyberger
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.implementation;

import com.powsybl.cgmes.conformity.CgmesConformity1Catalog;
import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.sc.cgmes.CgmesShortCircuitImportPostProcessor;
import org.apache.commons.math3.complex.Complex;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
class ShortCircuitFromCgmesTest {

    private Map<String, String> busNameToId;

    private LoadFlow.Runner loadFlowRunner;

    private LoadFlowParameters parametersLf;

    private MatrixFactory matrixFactory;

    @BeforeEach
    void setUp() {
        parametersLf = new LoadFlowParameters();
        matrixFactory = new DenseMatrixFactory();
        loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(matrixFactory));
        busNameToId = new HashMap<>();
        busNameToId.put("Bus1", "adee76cd-b2b9-48ac-8fd4-0d205a435f59");
        busNameToId.put("Bus9", "87c0d153-e308-4b2b-92a4-4fad53ab1ff9");
        busNameToId.put("Bus2", "b3d3b4ad-02af-4490-8748-70f6c9a23734");
        busNameToId.put("Bus8", "03163ede-7eec-457f-8641-365982227d7c");
        busNameToId.put("Bus3", "c8726716-e182-4373-b83e-8f60070078cb");
        busNameToId.put("Bus5", "37edd845-456f-4c3e-98d5-19af0c1cef1e");
        busNameToId.put("Bus6", "764e0b8a-f2af-4092-b6aa-b4a19e55db98");
        busNameToId.put("Bus7", "cd84fa40-ef63-422d-8ee0-d0a0f806719e");
        busNameToId.put("Bus10", "c7eda3d2-e92d-4935-8166-5e045d3de045");
        busNameToId.put("Bus11", "7f5515b2-ca6b-45af-93ee-f196686f0c66");
        busNameToId.put("Bus4", "c0adab49-d445-4609-a1a3-ebe4ef297cc8");
    }

    @Test
    void triphasedTest() {

        Network network = getCgmesNetwork();

        ShortCircuitNormIec shortCircuitNormIec = new ShortCircuitNormIec();
        shortCircuitNormIec.applyNormToNetwork(network); // this modifies the characteristics of some iidm equipments

        // TODO : use it for unit tests
        //double xG1 = 0.4116;
        //double rG1 = 0.002;
        //double xG2 = 0.1764;
        //double rG2 = 0.005;
        //double rG3 = 0.01779;
        //double xG3 = 1.089623;
        //double kG3 = 0.988320;
        //double kt56 = 0.974870;
        //double ktab = 0.928072;
        //double ktac = 0.985856;
        //double ktbc = 1.002890;
        //double xQ1 = 6.319335;
        //double rQ1 = 0.631933;
        //double xQ2 = 4.344543;
        //double rQ2 = 0.434454;
        //double pM1ScLoad = 2.89929;
        //double qM1ScLoad = 28.9929;
        //double pM2ScLoad = 2.40235;
        //double qM2ScLoad = 24.0235;
        //double kG1 = 0.99597;
        //double kG2 = 0.876832;

        ShortCircuitEngineParameters scbParameters = new ShortCircuitEngineParameters(new LoadFlowParameters()
                .setTwtSplitShuntAdmittance(true),
                matrixFactory,
                ShortCircuitEngineParameters.AnalysisType.SELECTIVE,
                buildTriphasedFaultList(),
                true,
                ShortCircuitEngineParameters.VoltageProfileType.NOMINAL,
                false,
                ShortCircuitEngineParameters.PeriodType.SUB_TRANSIENT,
                shortCircuitNormIec);
        ShortCircuitBalancedEngine scbEngine = new ShortCircuitBalancedEngine(network, scbParameters);

        Map<String, Double> values = getrunResultBalanced(scbEngine);

        // I"k = 1/sqrt(3) * cmax * Un /(Zeq)
        assertEquals(40.64478476116188, values.get("sc1"), 0.001); // bus 1 : expected in IEC doc = 40.6447 kA and in CGMES doc = 40.6375 kA
        assertEquals(31.783052222534174, values.get("sc2"), 0.0001); // bus 2 : expected in doc =  31.7831 kA  and in CGMES doc = 31.6939 kA
        assertEquals(19.672955775750143, values.get("sc3"), 0.00001); // bus 3 : expected in doc =  19.673 kA and in CGMES doc = 19.5243 kA
        assertEquals(16.227655866910894, values.get("sc4"), 0.00001); // bus 4 : expected in doc =  16.2277 kA and in CGMES doc = 16.1686 kA
        assertEquals(33.18941481677016, values.get("sc5"), 0.0001); // bus 5 : expected in doc =  33.1894 kA and in CGMES doc = 33.0764 kA
        assertEquals(37.56287899040728, values.get("sc6"), 0.001); // bus 6 : expected in doc =  37.5629 kA and in CGMES doc = 37.5547 kA

        assertEquals(25.589463480212533, values.get("sc7"), 0.0001); // bus 7 : expected in doc =  25.5895 kA and in CGMES doc = 25.5862 kA
        assertEquals(25.534284158956773, values.get("sc7z"), 0.0001); // bus 7 : with z not zero

        assertEquals(13.577771545200052, values.get("sc8"), 0.0001); // bus 8 : expected in doc =  13.5778 kA and in CGMES doc = 13.632 kA
        assertEquals(13.53390016463997, values.get("sc8z"), 0.0001); // bus 8 : with z not zero
    }

    @Test
    void monophasedTest() {
        Network network = getCgmesNetwork();

        ShortCircuitNormIec shortCircuitNormIec = new ShortCircuitNormIec();
        shortCircuitNormIec.applyNormToNetwork(network); // this modifies the characteristics of some iidm equipments

        // FIXME : check if there is a mistake in the CGMES input data example:
        //  for T4, by definition :
        //  ro_mvk = Kt_bc * (ro_b + ro_c)
        //  Ro_T / R_T = (ro_b + ro_c) / r_ab = 1.0
        //  then ro_mvk = Kt_bc * r_ab * Ro_T / R_T = 0.107281
        //  the input values ro_b and ro_c does not give ro_mvk = 0.107281
        //  for Ro_T / R_T = 1.0 , does it mean that r_b =? ro_b and r_c =? ro_c, maybe not...
        //  keeping the values provided in input, short circuit at bus2 varies from 15.9722 kA ( = the reference) to 15.981 kA
        //  if we want to keep the reference result, we need to modify the ratio of ro_b/r_b and ro_c/r_c equal to : double coeffRoT4 = 0.107281 / (rT4b + rT4c) 120. /120. ;

        ShortCircuitEngineParameters scbParameters = new ShortCircuitEngineParameters(new LoadFlowParameters()
                .setTwtSplitShuntAdmittance(true),
                matrixFactory,
                ShortCircuitEngineParameters.AnalysisType.SELECTIVE,
                buildMonophasedFaultList(),
                true,
                ShortCircuitEngineParameters.VoltageProfileType.NOMINAL,
                false,
                ShortCircuitEngineParameters.PeriodType.SUB_TRANSIENT,
                shortCircuitNormIec);
        ShortCircuitUnbalancedEngine scbEngine = new ShortCircuitUnbalancedEngine(network, scbParameters);

        Map<String, Double> values = getRunResultUnbalanced(scbEngine);

        //I"k = sqrt(3) * cmax * Un /(Zeq)
        assertEquals(15.98099, values.get("sc1"), 0.001); // bus 2 : expected doc value : 15.9722 kA FIXME : corrected reference = 15.98099 kA
        assertEquals(15.84557828853817, values.get("sc1z"), 0.001);

        assertEquals(10.410558286260768, values.get("sc2"), 0.001); // bus 3 : expected doc value : 10.4106 kA
        assertEquals(10.351980448941902, values.get("sc2z"), 0.001);

        assertEquals(9.049787523396647, values.get("sc3"), 0.001); // bus 4 : expected doc value : 9.0498 kA
        assertEquals(9.004908000730614, values.get("sc3z"), 0.001);

        assertEquals(17.0467196, values.get("sc4"), 0.001); // bus 5 : expected doc value : 17.0452 kA FIXME : corrected reference = 17.0467196 kA
        assertEquals(16.89219367449058, values.get("sc4z"), 0.001);
    }

    @Test
    void triphasedTestLoadFlow() {
        // This test shows the differences using voltage computed by load flow instead of uniform IEC norm
        Network network = getCgmesNetwork();
        loadFlowRunner.run(network, parametersLf);

        ShortCircuitNormIec shortCircuitNormIec = new ShortCircuitNormIec();
        shortCircuitNormIec.applyNormToNetwork(network); // this modifies the characteristics of some iidm equipments

        ShortCircuitEngineParameters scbParameters = new ShortCircuitEngineParameters(new LoadFlowParameters()
                .setTwtSplitShuntAdmittance(true),
                matrixFactory,
                ShortCircuitEngineParameters.AnalysisType.SELECTIVE,
                buildTriphasedFaultList(),
                true,
                ShortCircuitEngineParameters.VoltageProfileType.CALCULATED,
                false,
                ShortCircuitEngineParameters.PeriodType.SUB_TRANSIENT,
                shortCircuitNormIec);
        ShortCircuitBalancedEngine scbEngine = new ShortCircuitBalancedEngine(network, scbParameters);

        Map<String, Double> values = getrunResultBalanced(scbEngine);

        // I"k = 1/sqrt(3) * cmax * Un /(Zeq)
        assertEquals(40.723429420095876, values.get("sc1"), 0.001); // bus 1 : expected in IEC doc = 40.6447 kA and in CGMES doc = 40.6375 kA
        assertEquals(32.99735645040225, values.get("sc2"), 0.0001); // bus 2 : expected in doc =  31.7831 kA  and in CGMES doc = 31.6939 kA
        assertEquals(20.432180704312042, values.get("sc3"), 0.00001); // bus 3 : expected in doc =  19.673 kA and in CGMES doc = 19.5243 kA
        assertEquals(16.862496851561012, values.get("sc4"), 0.00001); // bus 4 : expected in doc =  16.2277 kA and in CGMES doc = 16.1686 kA
        assertEquals(34.452883063364084, values.get("sc5"), 0.0001); // bus 5 : expected in doc =  33.1894 kA and in CGMES doc = 33.0764 kA
        assertEquals(38.83333954687385, values.get("sc6"), 0.001); // bus 6 : expected in doc =  37.5629 kA and in CGMES doc = 37.5547 kA

        assertEquals(26.09914297220819, values.get("sc7"), 0.0001); // bus 7 : expected in doc =  25.5895 kA and in CGMES doc = 25.5862 kA
        assertEquals(26.04323218798688, values.get("sc7z"), 0.0001); // bus 7 : with z not zero

        assertEquals(12.923781384471045, values.get("sc8"), 0.0001); // bus 8 : expected in doc =  13.5778 kA and in CGMES doc = 13.632 kA
        assertEquals(12.882024685656189, values.get("sc8z"), 0.0001); // bus 8 : with z not zero
    }

    public List<ShortCircuitFault> buildTriphasedFaultList() {

        Complex zFault = new Complex(0.);
        ShortCircuitFaultImpedance scz = new ShortCircuitFaultImpedance(zFault);
        List<ShortCircuitFault> faultList = new ArrayList<>();
        ShortCircuitFault sc1 = new ShortCircuitFault(busNameToId.get("Bus1"), "sc1", busNameToId.get("Bus1"), scz, ShortCircuitFault.ShortCircuitType.TRIPHASED_GROUND);
        faultList.add(sc1);
        ShortCircuitFault sc2 = new ShortCircuitFault(busNameToId.get("Bus2"), "sc2", busNameToId.get("Bus2"), scz, ShortCircuitFault.ShortCircuitType.TRIPHASED_GROUND);
        faultList.add(sc2);
        ShortCircuitFault sc3 = new ShortCircuitFault(busNameToId.get("Bus3"), "sc3", busNameToId.get("Bus3"), scz, ShortCircuitFault.ShortCircuitType.TRIPHASED_GROUND);
        faultList.add(sc3);
        ShortCircuitFault sc4 = new ShortCircuitFault(busNameToId.get("Bus4"), "sc4", busNameToId.get("Bus4"), scz, ShortCircuitFault.ShortCircuitType.TRIPHASED_GROUND);
        faultList.add(sc4);
        ShortCircuitFault sc5 = new ShortCircuitFault(busNameToId.get("Bus5"), "sc5", busNameToId.get("Bus5"), scz, ShortCircuitFault.ShortCircuitType.TRIPHASED_GROUND);
        faultList.add(sc5);
        ShortCircuitFault sc6 = new ShortCircuitFault(busNameToId.get("Bus6"), "sc6", busNameToId.get("Bus6"), scz, ShortCircuitFault.ShortCircuitType.TRIPHASED_GROUND);
        faultList.add(sc6);
        ShortCircuitFault sc7 = new ShortCircuitFault(busNameToId.get("Bus7"), "sc7", busNameToId.get("Bus7"), scz, ShortCircuitFault.ShortCircuitType.TRIPHASED_GROUND);
        faultList.add(sc7);
        ShortCircuitFault sc8 = new ShortCircuitFault(busNameToId.get("Bus8"), "sc8", busNameToId.get("Bus8"), scz, ShortCircuitFault.ShortCircuitType.TRIPHASED_GROUND);
        faultList.add(sc8);

        Complex zFault2 = new Complex(0.0002, 0.0005);
        ShortCircuitFaultImpedance scz2 = new ShortCircuitFaultImpedance(zFault2);
        ShortCircuitFault sc7z = new ShortCircuitFault(busNameToId.get("Bus7"), "sc7z", busNameToId.get("Bus7"), scz2, ShortCircuitFault.ShortCircuitType.TRIPHASED_GROUND);
        faultList.add(sc7z);
        ShortCircuitFault sc8z = new ShortCircuitFault(busNameToId.get("Bus8"), "sc8z", busNameToId.get("Bus8"), scz2, ShortCircuitFault.ShortCircuitType.TRIPHASED_GROUND);
        faultList.add(sc8z);

        return faultList;
    }

    public List<ShortCircuitFault> buildMonophasedFaultList() {
        Complex zFault = new Complex(0.);
        ShortCircuitFaultImpedance scz = new ShortCircuitFaultImpedance(zFault);
        List<ShortCircuitFault> faultList = new ArrayList<>();
        ShortCircuitFault sc1 = new ShortCircuitFault(busNameToId.get("Bus2"), "sc1", busNameToId.get("Bus2"), scz, ShortCircuitFault.ShortCircuitType.MONOPHASED);
        faultList.add(sc1);
        ShortCircuitFault sc2 = new ShortCircuitFault(busNameToId.get("Bus3"), "sc2", busNameToId.get("Bus3"), scz, ShortCircuitFault.ShortCircuitType.MONOPHASED);
        faultList.add(sc2);
        ShortCircuitFault sc3 = new ShortCircuitFault(busNameToId.get("Bus4"), "sc3", busNameToId.get("Bus4"), scz, ShortCircuitFault.ShortCircuitType.MONOPHASED);
        faultList.add(sc3);
        ShortCircuitFault sc4 = new ShortCircuitFault(busNameToId.get("Bus5"), "sc4", busNameToId.get("Bus5"), scz, ShortCircuitFault.ShortCircuitType.MONOPHASED);
        faultList.add(sc4);
        zFault = new Complex(0.0001, 0.0003);
        ShortCircuitFaultImpedance sczImpedant = new ShortCircuitFaultImpedance(zFault);
        ShortCircuitFault sc1z = new ShortCircuitFault(busNameToId.get("Bus2"), "sc1z", busNameToId.get("Bus2"), sczImpedant, ShortCircuitFault.ShortCircuitType.MONOPHASED);
        faultList.add(sc1z);
        ShortCircuitFault sc2z = new ShortCircuitFault(busNameToId.get("Bus3"), "sc2z", busNameToId.get("Bus3"), sczImpedant, ShortCircuitFault.ShortCircuitType.MONOPHASED);
        faultList.add(sc2z);
        ShortCircuitFault sc3z = new ShortCircuitFault(busNameToId.get("Bus4"), "sc3z", busNameToId.get("Bus4"), sczImpedant, ShortCircuitFault.ShortCircuitType.MONOPHASED);
        faultList.add(sc3z);
        ShortCircuitFault sc4z = new ShortCircuitFault(busNameToId.get("Bus5"), "sc4z", busNameToId.get("Bus5"), sczImpedant, ShortCircuitFault.ShortCircuitType.MONOPHASED);
        faultList.add(sc4z);

        return faultList;
    }

    public Map<String, Double> getRunResultUnbalanced(ShortCircuitUnbalancedEngine scbEngine) {
        scbEngine.run();
        Map<String, Double> values = new HashMap<>();
        for (Map.Entry<ShortCircuitFault, ShortCircuitResult> res : scbEngine.getResultsPerFault().entrySet()) {
            values.put(res.getKey().getFaultId(), res.getValue().getIk().abs());
            ShortCircuitResultPrinter scp = new ShortCircuitResultPrinter(res.getValue());
            scp.printShortCircuitResult();
        }
        return values;
    }

    public Map<String, Double> getrunResultBalanced(ShortCircuitBalancedEngine scbEngine) {

        scbEngine.run();
        Map<String, Double> results = new HashMap<>();
        for (Map.Entry<ShortCircuitFault, ShortCircuitResult> res : scbEngine.getResultsPerFault().entrySet()) {
            results.put(res.getKey().getFaultId(), res.getValue().getIk().abs());
            ShortCircuitResultPrinter scp = new ShortCircuitResultPrinter(res.getValue());
            scp.printShortCircuitResult();
        }
        return results;
    }

    public Network getCgmesNetwork() {
        Properties parameters = new Properties();
        parameters.setProperty("iidm.import.cgmes.post-processors", CgmesShortCircuitImportPostProcessor.NAME);
        return Network.read(CgmesConformity1Catalog.miniBusBranch().dataSource(), parameters);

    }

}

