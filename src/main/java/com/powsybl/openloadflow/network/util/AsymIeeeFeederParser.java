package com.powsybl.openloadflow.network.util;

import com.powsybl.iidm.network.*;
import com.powsybl.iidm.network.extensions.*;
import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.openloadflow.network.extensions.iidm.*;
import com.powsybl.openloadflow.util.ComplexMatrix;
import com.univocity.parsers.annotations.Parsed;
import com.univocity.parsers.common.processor.BeanListProcessor;
import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;
import org.apache.commons.math3.complex.Complex;
import org.joda.time.DateTime;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class AsymIeeeFeederParser {

    private AsymIeeeFeederParser() {
    }

    private static <T> List<T> parseCsv(String resourceName, Class<T> clazz) {
        try (Reader inputReader = new InputStreamReader(Objects.requireNonNull(AsymIeeeFeederParser.class.getResourceAsStream(resourceName)), StandardCharsets.UTF_8)) {
            BeanListProcessor<T> rowProcessor = new BeanListProcessor<>(clazz);
            CsvParserSettings settings = new CsvParserSettings();
            settings.setHeaderExtractionEnabled(true);
            settings.setProcessor(rowProcessor);
            CsvParser parser = new CsvParser(settings);
            parser.parse(inputReader);
            return rowProcessor.getBeans();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static class LineData {
        @Parsed(field = "NodeA")
        String nodeA;

        @Parsed(field = "NodeB")
        String nodeB;

        @Parsed(field = "Length")
        double length;

        @Parsed(field = "Config")
        String config;
    }

    public static class TfoData {
        @Parsed(field = "NodeA")
        String nodeA;

        @Parsed(field = "NodeB")
        String nodeB;

        @Parsed(field = "Config")
        String config;
    }

    public static class GenData {
        @Parsed(field = "GenName")
        String genName;

        @Parsed(field = "BusName")
        String busName;

        @Parsed(field = "SetPoint")
        double setPoint;

    }

    public static class BusData {
        @Parsed(field = "BusName")
        String busName;

        @Parsed(field = "UNom")
        double uNom;

    }

    public static class LoadData {
        @Parsed(field = "Node")
        String busName;

        @Parsed(field = "Load")
        String loadType;

        @Parsed(field = "Ph-1")
        double ph1P;

        @Parsed(field = "Ph-1Q")
        double ph1Q;

        @Parsed(field = "Ph-2")
        double ph2P;

        @Parsed(field = "Ph-2Q")
        double ph2Q;

        @Parsed(field = "Ph-3")
        double ph3P;

        @Parsed(field = "Ph-3Q")
        double ph3Q;

    }

    public static class DistriLoadData {
        @Parsed(field = "NodeA")
        String busNameA;

        @Parsed(field = "NodeB")
        String busNameB;

        @Parsed(field = "Load")
        String loadType;

        @Parsed(field = "Ph-1")
        double ph1P;

        @Parsed(field = "Ph-1Q")
        double ph1Q;

        @Parsed(field = "Ph-2")
        double ph2P;

        @Parsed(field = "Ph-2Q")
        double ph2Q;

        @Parsed(field = "Ph-3")
        double ph3P;

        @Parsed(field = "Ph-3Q")
        double ph3Q;
    }

    public static class LineConfigData {
        @Parsed(field = "Config")
        String config;

        @Parsed(field = "PhaseA")
        int phaseA;

        @Parsed(field = "PhaseB")
        int phaseB;

        @Parsed(field = "PhaseC")
        int phaseC;

        @Parsed
        double r11;

        @Parsed
        double x11;

        @Parsed
        double r12;

        @Parsed
        double x12;

        @Parsed
        double r13;

        @Parsed
        double x13;

        @Parsed
        double r22;

        @Parsed
        double x22;

        @Parsed
        double r23;

        @Parsed
        double x23;

        @Parsed
        double r33;

        @Parsed
        double x33;

        @Parsed
        double b11;

        @Parsed
        double b12;

        @Parsed
        double b13;

        @Parsed
        double b22;

        @Parsed
        double b23;

        @Parsed
        double b33;
    }

    public static class TfoConfigData {
        @Parsed(field = "Config")
        String config;

        @Parsed(field = "SkVA")
        double skva;

        @Parsed(field = "kV-high")
        double kvHigh;

        @Parsed(field = "Winding-high")
        String windingHigh;

        @Parsed(field = "kV-low")
        double kvLow;

        @Parsed(field = "Winding-low")
        String windingLow;

        @Parsed(field = "R")
        double r;

        @Parsed(field = "X")
        double x;

    }

    public static class RegulatorData {
        @Parsed(field = "Line")
        String line;

        @Parsed(field = "RhoA")
        double rhoA;

        @Parsed(field = "RhoB")
        double rhoB;

        @Parsed(field = "RhoC")
        double rhoC;

    }

    private static String getBusId(String busName) {
        return "Bus-" + busName;
    }

    private static String getVoltageLevelId(String busName) {
        return "VoltageLevel-" + busName;
    }

    private static String getSubstationId(String busName) {
        return "Substation-" + busName;
    }

    private static void createBuses(Network network, Map<String, String> firstBusTfo, String path) {
        for (BusData busData : parseCsv(path + "Bus.csv", BusData.class)) {

            String substationId = getSubstationId(busData.busName);
            if (firstBusTfo.containsKey(busData.busName)) {
                substationId = getSubstationId(firstBusTfo.get(busData.busName));
            }
            Substation s = network.getSubstation(substationId);
            if (s == null) {
                s = network.newSubstation()
                        .setId(substationId)
                        .add();
            }
            VoltageLevel vl = s.newVoltageLevel()
                    .setId(getVoltageLevelId(busData.busName))
                    .setTopologyKind(TopologyKind.BUS_BREAKER)
                    .setNominalV(busData.uNom)
                    .add();
            Bus bus = vl.getBusBreakerView().newBus()
                    .setId(getBusId(busData.busName))
                    .add();

            bus.setV(busData.uNom).setAngle(0.);

            // default settings for bus extensions, will be modified depending on the type of connected equipment :
            bus.newExtension(BusAsymmetricalAdder.class)
                    .withBusVariableType(BusVariableType.WYE)
                    .withHasPhaseA(false)
                    .withHasPhaseB(false)
                    .withHasPhaseC(false)
                    .withPositiveSequenceAsCurrent(true)
                    .withFortescueRepresentation(true)
                    .add();
        }
    }

    private static void createGenerators(Network network, String path) {
        for (GenData gen : parseCsv(path + "Gen.csv", GenData.class)) {
            VoltageLevel vl = network.getVoltageLevel(getVoltageLevelId(gen.busName));
            Generator generator = vl.newGenerator()
                    .setId(gen.genName)
                    .setBus(getBusId(gen.busName))
                    .setMinP(-100.0)
                    .setMaxP(200)
                    .setTargetP(0.0)
                    .setTargetV(vl.getNominalV() * gen.setPoint) // TODO : TEST
                    .setVoltageRegulatorOn(true)
                    .add();

            Complex zz = new Complex(0.0001, 0.0001);
            Complex zn = new Complex(0.0001, 0.0001);

            generator.newExtension(GeneratorFortescueAdder.class)
                    .withRz(zz.getReal())
                    .withXz(zz.getImaginary())
                    .withRn(zn.getReal())
                    .withXn(zn.getImaginary())
                    .add();

            // modification of bus extension due to generating unit
            network.getBusBreakerView().getBus(getBusId(gen.busName)).getExtension(BusAsymmetrical.class).setPositiveSequenceAsCurrent(false);

        }
    }

    private static List<TfoData> parseTfos(String path) {

        List<TfoData> listTfos = new ArrayList<>();
        for (TfoData tfo : parseCsv(path + "Tfo.csv", TfoData.class)) {
            listTfos.add(tfo);
        }

        return listTfos;
    }

    private static Map<String, String> getFirstBusTfo(List<TfoData> listTfos) {
        // used to create a substation with the name of the first bus for a tfo
        Map<String, String> firstBusTfo = new HashMap<>();
        for (TfoData tfo : listTfos) {
            firstBusTfo.put(tfo.nodeB, tfo.nodeA);
        }

        return firstBusTfo;
    }

    private static void createLoad(Network network, String loadName, String busName, String loadType,
                                   double pa, double qa, double pb, double qb, double pc, double qc) {
        VoltageLevel vl = network.getVoltageLevel(getVoltageLevelId(busName));
        Load load = vl.newLoad()
                .setId(loadName)
                .setBus(getBusId(busName))
                .setP0(0.)
                .setQ0(0.)
                .newZipModel()
                    .setC0p(1)
                    .setC0q(1)
                    .setC1p(0)
                    .setC1q(0)
                    .setC2p(0)
                    .setC2q(0)
                    .add()
                .add();

        var extensionBus = network.getBusBreakerView().getBus(getBusId(busName)).getExtension(BusAsymmetrical.class);
        LoadConnectionType loadConnectionType;
        ZipLoadModel zipLoadModel = (ZipLoadModel) load.getModel().orElseThrow();
        if (loadType.equals("Y-PQ")) {
            loadConnectionType = LoadConnectionType.Y;
        } else if (loadType.equals("D-PQ")) {
            loadConnectionType = LoadConnectionType.DELTA;
        } else if (loadType.equals("Y-Z")) {
            loadConnectionType = LoadConnectionType.Y;
            zipLoadModel.setC0p(0);
            zipLoadModel.setC0q(0);
            zipLoadModel.setC2p(1);
            zipLoadModel.setC2q(1);
            extensionBus.setFortescueRepresentation(false);
        } else if (loadType.equals("D-Z")) {
            loadConnectionType = LoadConnectionType.DELTA;
            zipLoadModel.setC0p(0);
            zipLoadModel.setC0q(0);
            zipLoadModel.setC2p(1);
            zipLoadModel.setC2q(1);
            extensionBus.setFortescueRepresentation(false);
        } else if (loadType.equals("Y-I")) {
            loadConnectionType = LoadConnectionType.Y;
            zipLoadModel.setC0p(0);
            zipLoadModel.setC0q(0);
            zipLoadModel.setC1p(1);
            zipLoadModel.setC1q(1);
            extensionBus.setFortescueRepresentation(false);
        } else if (loadType.equals("D-I")) {
            loadConnectionType = LoadConnectionType.DELTA;
            zipLoadModel.setC0p(0);
            zipLoadModel.setC0q(0);
            zipLoadModel.setC1p(1);
            zipLoadModel.setC1q(1);
            extensionBus.setFortescueRepresentation(false);
        } else {
            throw new IllegalStateException("Unknown load type in csv at bus : " + busName);
        }

        load.newExtension(LoadAsymmetricalAdder.class)
                .withDeltaPa(pa / 1000.)
                .withDeltaQa(qa / 1000.)
                .withDeltaPb(pb / 1000.)
                .withDeltaQb(qb / 1000.)
                .withDeltaPc(pc / 1000.)
                .withDeltaQc(qc / 1000.)
                .withConnectionType(loadConnectionType)
                .add();

    }

    private static void createLoads(Network network, String path) {

        for (LoadData loadData : parseCsv(path + "SpotLoad.csv", LoadData.class)) {

            String loadName = "LOAD_" + loadData.busName + "-" + loadData.loadType;

            createLoad(network, loadName, loadData.busName, loadData.loadType,
                    loadData.ph1P, loadData.ph1Q, loadData.ph2P, loadData.ph2Q, loadData.ph3P, loadData.ph3Q);
        }
    }

    private static void createDistriLoads(Network network, String path) {

        for (DistriLoadData loadData : parseCsv(path + "DistributedLoad.csv", DistriLoadData.class)) {
            String loadName = "LOAD_" + loadData.busNameA + "-" + loadData.busNameB + "-" + loadData.loadType;

            createLoad(network, loadName, loadData.busNameA, loadData.loadType,
                    loadData.ph1P, loadData.ph1Q, loadData.ph2P, loadData.ph2Q, loadData.ph3P, loadData.ph3Q);
        }
    }

    private static void createLines(Network network, String path) {
        Map<String, LineConfigData> lineConfig = new HashMap<>();
        for (LineConfigData lineCode : parseCsv(path + "LineConfig.csv", LineConfigData.class)) {
            lineConfig.put(lineCode.config, lineCode);
        }
        Map<String, RegulatorData> regulatorDataMap = new HashMap<>();
        for (RegulatorData regulatorData : parseCsv(path + "Regulator.csv", RegulatorData.class)) {
            String lineName = "Line-" + regulatorData.line;
            regulatorDataMap.put(lineName, regulatorData);
        }

        for (LineData line : parseCsv(path + "Line.csv", LineData.class)) {
            LineConfigData lineConfigData = lineConfig.get(line.config);
            var l = network.newLine()
                    .setId("Line-" + line.nodeA + "-" + line.nodeB)
                    .setVoltageLevel1(getVoltageLevelId(line.nodeA))
                    .setBus1(getBusId(line.nodeA))
                    .setVoltageLevel2(getVoltageLevelId(line.nodeB))
                    .setBus2(getBusId(line.nodeB))
                    .setR(1.0 * line.length / 1000)
                    .setX(1.0 * line.length / 1000)
                    .add();

            l.newExtension(LineFortescueAdder.class)
                    .withOpenPhaseA(false)
                    .withOpenPhaseB(false)
                    .withOpenPhaseC(false)
                    .withRz(1.0 * line.length / 1000)
                    .withXz(1.0 * line.length / 1000)
                    .add();

            double micro = 0.000001;
            double feetInMile = 5280;
            double yCoef = 1. / 3.;

            // building of Yabc from given Y impedance matrix Zy
            ComplexMatrix zy = new ComplexMatrix(3, 3);
            zy.set(1, 1, new Complex(lineConfigData.r11, lineConfigData.x11));
            zy.set(1, 2, new Complex(lineConfigData.r12, lineConfigData.x12));
            zy.set(1, 3, new Complex(lineConfigData.r13, lineConfigData.x13));
            zy.set(2, 1, new Complex(lineConfigData.r12, lineConfigData.x12));
            zy.set(2, 2, new Complex(lineConfigData.r22, lineConfigData.x22));
            zy.set(2, 3, new Complex(lineConfigData.r23, lineConfigData.x23));
            zy.set(3, 1, new Complex(lineConfigData.r13, lineConfigData.x13));
            zy.set(3, 2, new Complex(lineConfigData.r23, lineConfigData.x23));
            zy.set(3, 3, new Complex(lineConfigData.r33, lineConfigData.x33));

            ComplexMatrix b = new ComplexMatrix(3, 3);

            b.set(1, 1, new Complex(0, micro * lineConfigData.b11));
            b.set(1, 2, new Complex(0, micro * lineConfigData.b12));
            b.set(1, 3, new Complex(0, micro * lineConfigData.b13));
            b.set(2, 1, new Complex(0, micro * lineConfigData.b12));
            b.set(2, 2, new Complex(0, micro * lineConfigData.b22));
            b.set(2, 3, new Complex(0, micro * lineConfigData.b23));
            b.set(3, 1, new Complex(0, micro * lineConfigData.b13));
            b.set(3, 2, new Complex(0, micro * lineConfigData.b23));
            b.set(3, 3, new Complex(0, micro * lineConfigData.b33));

            boolean hasPhaseA = lineConfigData.phaseA == 1;
            boolean hasPhaseB = lineConfigData.phaseB == 1;
            boolean hasPhaseC = lineConfigData.phaseC == 1;

            ComplexMatrix yabc = LineAsymmetrical.getAdmittanceMatrixFromImpedanceAndBmatrix(zy, b,
                    hasPhaseA, hasPhaseB, hasPhaseC, line.length / feetInMile);
            if (regulatorDataMap.containsKey(l.getId())) {
                // take into account effect of regulator
                RegulatorData regulatorData = regulatorDataMap.get(l.getId());
                ComplexMatrix rho = new ComplexMatrix(6, 6);
                rho.set(1, 1, new Complex(regulatorData.rhoA, 0.));
                rho.set(2, 2, new Complex(regulatorData.rhoB, 0.));
                rho.set(3, 3, new Complex(regulatorData.rhoC, 0.));
                rho.set(4, 4, new Complex(1., 0.));
                rho.set(5, 5, new Complex(1., 0.));
                rho.set(6, 6, new Complex(1., 0.));
                DenseMatrix yabcRho = rho.getRealCartesianMatrix().times(yabc.getRealCartesianMatrix().times(rho.getRealCartesianMatrix()));
                yabc = ComplexMatrix.getComplexMatrixFromRealCartesian(yabcRho);
            }

            l.newExtension(LineAsymmetricalAdder.class)
                    .withYabc(ComplexMatrix.getMatrixScaled(yabc, yCoef))
                    .add();

            // modification of bus extension depending on line connections:
            var extensionBus1 = network.getBusBreakerView().getBus(getBusId(line.nodeA)).getExtension(BusAsymmetrical.class);
            var extensionBus2 = network.getBusBreakerView().getBus(getBusId(line.nodeB)).getExtension(BusAsymmetrical.class);

            extensionBus1.setHasPhaseA(extensionBus1.isHasPhaseA() || hasPhaseA);
            extensionBus1.setHasPhaseB(extensionBus1.isHasPhaseB() || hasPhaseB);
            extensionBus1.setHasPhaseC(extensionBus1.isHasPhaseC() || hasPhaseC);

            extensionBus2.setHasPhaseA(extensionBus2.isHasPhaseA() || hasPhaseA);
            extensionBus2.setHasPhaseB(extensionBus2.isHasPhaseB() || hasPhaseB);
            extensionBus2.setHasPhaseC(extensionBus2.isHasPhaseC() || hasPhaseC);

        }
    }

    private static void createTfos(Network network, String path) {
        Map<String, TfoConfigData> tfoConfigDataMap = new HashMap<>();
        for (TfoConfigData tfoConfig : parseCsv(path + "TfoConfig.csv", TfoConfigData.class)) {
            tfoConfigDataMap.put(tfoConfig.config, tfoConfig);
        }
        for (TfoData tfoData : parseCsv(path + "Tfo.csv", TfoData.class)) {
            TfoConfigData tfoConfigData = tfoConfigDataMap.get(tfoData.config);
            double ratedUhigh = tfoConfigData.kvHigh;
            double ratedUlow = tfoConfigData.kvLow;
            double sBase = tfoConfigData.skva / 1000.;
            double rTpc = tfoConfigData.r;
            double xTpc = tfoConfigData.x;
            double zBase = ratedUhigh * ratedUlow / sBase;
            double rT = rTpc * zBase / 100.;
            double xT = xTpc * zBase / 100.;

            var tfo = network.getSubstation(getSubstationId(tfoData.nodeA)).newTwoWindingsTransformer()
                    .setId("Tfo-" + tfoData.nodeA + "-" + tfoData.nodeB)
                    .setVoltageLevel1(network.getVoltageLevel(getVoltageLevelId(tfoData.nodeA)).getId())
                    .setBus1(getBusId(tfoData.nodeA))
                    .setConnectableBus1(getBusId(tfoData.nodeA))
                    .setRatedU1(ratedUhigh)
                    .setVoltageLevel2(network.getVoltageLevel(getVoltageLevelId(tfoData.nodeB)).getId())
                    .setBus2(getBusId(tfoData.nodeB))
                    .setConnectableBus2(getBusId(tfoData.nodeB))
                    .setRatedU2(ratedUlow)
                    .setR(rT)
                    .setX(xT)
                    .setG(0.0D)
                    .setB(0.0D)
                    .setRatedS(sBase)
                    .add();

            WindingConnectionType windingConnectionType1;
            if (tfoConfigData.windingHigh.equals("Gr.Y") || tfoConfigData.windingHigh.equals("Gr.W")) {
                windingConnectionType1 = WindingConnectionType.Y_GROUNDED;
            } else {
                windingConnectionType1 = WindingConnectionType.DELTA;
            }

            WindingConnectionType windingConnectionType2;
            if (tfoConfigData.windingLow.equals("Gr.Y") || tfoConfigData.windingLow.equals("Gr.W")) {
                windingConnectionType2 = WindingConnectionType.Y_GROUNDED;
            } else {
                windingConnectionType2 = WindingConnectionType.DELTA;
            }

            tfo.newExtension(TwoWindingsTransformerFortescueAdder.class)
                    .withRz(rT)
                    .withXz(xT)
                    .withConnectionType1(windingConnectionType1)
                    .withConnectionType2(windingConnectionType2)
                    .withGroundingX1(0.0000)
                    .withGroundingX2(0.0000)
                    .withFreeFluxes(true)
                    .add();

            Complex zPhase = new Complex(rTpc, xTpc).multiply(zBase / 3. / 100.);
            Complex yPhase = new Complex(0., 0.);

            tfo.newExtension(Tfo3PhasesAdder.class)
                    .withIsOpenPhaseA1(false)
                    .withIsOpenPhaseB1(false)
                    .withIsOpenPhaseC1(false)
                    .withYa(buildSinglePhaseAdmittanceMatrix(zPhase, yPhase, yPhase))
                    .withYb(buildSinglePhaseAdmittanceMatrix(zPhase, yPhase, yPhase))
                    .withYc(buildSinglePhaseAdmittanceMatrix(zPhase, yPhase, yPhase))
                    .add();

            // modification of bus extension depending on line connections:
            var extensionBus1 = network.getBusBreakerView().getBus(getBusId(tfoData.nodeA)).getExtension(BusAsymmetrical.class);
            var extensionBus2 = network.getBusBreakerView().getBus(getBusId(tfoData.nodeB)).getExtension(BusAsymmetrical.class);

            extensionBus1.setHasPhaseA(true);
            extensionBus1.setHasPhaseB(true);
            extensionBus1.setHasPhaseC(true);

            extensionBus2.setHasPhaseA(true);
            extensionBus2.setHasPhaseB(true);
            extensionBus2.setHasPhaseC(true);

            extensionBus1.setFortescueRepresentation(true);
            extensionBus2.setFortescueRepresentation(true);
        }
    }

    public static ComplexMatrix buildSinglePhaseAdmittanceMatrix(Complex z, Complex y1, Complex y2) {
        ComplexMatrix cm = new ComplexMatrix(2, 2);
        cm.set(1, 1, y1.add(z.reciprocal()));
        cm.set(1, 2, z.reciprocal().multiply(-1.));
        cm.set(2, 1, z.reciprocal().multiply(-1.));
        cm.set(2, 2, y2.add(z.reciprocal()));

        return cm;
    }

    public static Network create(String path) {
        return create(NetworkFactory.findDefault(), path);
    }

    public static Network create(NetworkFactory networkFactory, String path) {
        Network network = networkFactory.createNetwork("EuropeanLvTestFeeder", "csv");
        network.setCaseDate(DateTime.parse("2023-04-11T23:59:00.000+01:00"));

        // for substation naming when there are tfos:
        List<TfoData> listTfos = parseTfos(path);
        Map<String, String> firstBusTfo = getFirstBusTfo(listTfos);

        createBuses(network, firstBusTfo, path);
        createLines(network, path);
        createGenerators(network, path);
        createLoads(network, path);
        createDistriLoads(network, path);
        createTfos(network, path);

        return network;
    }

}
