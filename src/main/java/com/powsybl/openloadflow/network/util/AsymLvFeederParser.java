package com.powsybl.openloadflow.network.util;

import com.powsybl.commons.PowsyblException;
import com.powsybl.iidm.network.*;
import com.powsybl.iidm.network.extensions.*;
import com.powsybl.openloadflow.network.extensions.iidm.AsymmetricalBranchConnector;
import com.powsybl.openloadflow.network.extensions.iidm.BusVariableType;
import com.powsybl.openloadflow.network.extensions.iidm.LineAsymmetricalAdder;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class AsymLvFeederParser {
    private AsymLvFeederParser() {
    }

    private static <T> List<T> parseCsv(String resourceName, Class<T> clazz) {
        try (Reader inputReader = new InputStreamReader(Objects.requireNonNull(AsymLvFeederParser.class.getResourceAsStream(resourceName)), StandardCharsets.UTF_8)) {
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

    private static String getBusId(int busName) {
        return "Bus-" + busName;
    }

    private static String getVoltageLevelId(int busName) {
        return "VoltageLevel-" + busName;
    }

    private static String getSubstationId(int busName) {
        return "Substation-" + busName;
    }

    public static class BusCoord {
        @Parsed(field = "Busname")
        int busName;

        @Parsed
        double x;

        @Parsed
        double y;
    }

    public static class Line {
        @Parsed(field = "Name")
        String name;

        @Parsed(field = "Bus1")
        int bus1;

        @Parsed(field = "Bus2")
        int bus2;

        @Parsed(field = "Phases")
        String phases;

        @Parsed(field = "Length")
        double length;

        @Parsed(field = "Units")
        String units;

        @Parsed(field = "LineCode")
        String code;
    }

    public static class LineCode {
        @Parsed(field = "Name")
        String name;

        @Parsed
        int nphases;

        @Parsed(field = "R1")
        double r1;

        @Parsed(field = "X1")
        double x1;

        @Parsed(field = "R0")
        double r0;

        @Parsed(field = "X0")
        double x0;

        @Parsed(field = "C1")
        double c1;

        @Parsed(field = "C0")
        double c0;

        @Parsed(field = "Units")
        String units;
    }

    public static class Load {
        @Parsed(field = "Name")
        String name;

        @Parsed
        int numPhases;

        @Parsed(field = "Bus")
        int bus;

        @Parsed
        char phases;

        @Parsed
        double kV;

        @Parsed(field = "Model")
        int model;

        @Parsed(field = "Connection")
        String connection;

        @Parsed
        double kW;

        @Parsed(field = "PF")
        double pf;

        @Parsed(field = "Yearly")
        String yearly;
    }

    public static class Transformer {
        @Parsed(field = "Name")
        String name;

        @Parsed
        int phases;

        @Parsed
        String bus1;

        @Parsed
        int bus2;

        @Parsed(field = "kV_pri")
        double kvPri;

        @Parsed(field = "kV_sec")
        double kvSec;

        @Parsed(field = "MVA")
        double mva;

        @Parsed(field = "Conn_pri")
        String connPri;

        @Parsed(field = "Conn_sec")
        String connSec;

        @Parsed(field = "%XHL")
        double xhl;

        @Parsed(field = "% resistance")
        double resistance;
    }

    private static void createBuses(Network network, String path, Map<String, AsymmetricalBranchConnector> bus2Connector) {
        for (BusCoord busCoord : parseCsv(path + "Buscoords.csv", BusCoord.class)) {
            String substationId = getSubstationId(busCoord.busName);
            Substation s = network.getSubstation(substationId);
            if (s == null) {
                s = network.newSubstation()
                        .setId(substationId)
                        .add();
            }
            VoltageLevel vl = s.newVoltageLevel()
                    .setId(getVoltageLevelId(busCoord.busName))
                    .setTopologyKind(TopologyKind.BUS_BREAKER)
                    .setNominalV(1)
                    .add();
            Bus bus = vl.getBusBreakerView().newBus()
                    .setId(getBusId(busCoord.busName))
                    .add();

            // default settings for connectors, will be modified depending on the type of connected equipment :
            AsymmetricalBranchConnector connector = new AsymmetricalBranchConnector(BusVariableType.WYE,
                    true, true, true, true, true);

            bus2Connector.put(bus.getId(), connector);
        }
    }

    private static void createLines(Network network, String path, Map<String, AsymmetricalBranchConnector> bus2Connector) {
        Map<String, LineCode> lineCodes = new HashMap<>();
        for (LineCode lineCode : parseCsv(path + "LineCodes.csv", LineCode.class)) {
            lineCodes.put(lineCode.name, lineCode);
        }
        double coeff = 1 / 1000.;
        for (Line line : parseCsv(path + "Lines.csv", Line.class)) {
            LineCode lineCode = lineCodes.get(line.code);
            var l = network.newLine()
                    .setId("Line-" + line.bus1 + "-" + line.bus2)
                    .setVoltageLevel1(getVoltageLevelId(line.bus1))
                    .setBus1(getBusId(line.bus1))
                    .setVoltageLevel2(getVoltageLevelId(line.bus2))
                    .setBus2(getBusId(line.bus2))
                    .setR(lineCode.r1 * line.length * coeff)
                    .setX(lineCode.x1 * line.length * coeff)
                    .add();
            l.newExtension(LineFortescueAdder.class)
                    .withRz(lineCode.r0 * line.length * coeff)
                    .withXz(lineCode.x0 * line.length * coeff)
                    .add();
            l.newExtension(LineAsymmetricalAdder.class)
                    .withAsymConnector1(bus2Connector.get(getBusId(line.bus1)))
                    .withAsymConnector2(bus2Connector.get(getBusId(line.bus2)))
                    .add();
        }
    }

    private static LoadConnectionType getConnectionType(Load load) {
        if (load.connection.equals("wye")) {
            return LoadConnectionType.Y;
        }
        throw new PowsyblException("Unknown load connection: " + load.connection);
    }

    private static void createLoads(Network network, String path) {
        for (Load load : parseCsv(path + "Loads.csv", Load.class)) {
            var vl = network.getVoltageLevel(getVoltageLevelId(load.bus));
            double p0 = load.kW / 1000;
            double q0 = p0 * load.pf;
            var l = vl.newLoad()
                    .setId("Load-" + load.bus)
                    .setBus(getBusId(load.bus))
                    .setP0(0.)
                    .setQ0(0.)
                    .add();
            double defaultLoad = 0.000;
            double deltaPa = defaultLoad;
            double deltaQa = defaultLoad;
            double deltaPb = defaultLoad;
            double deltaQb = defaultLoad;
            double deltaPc = defaultLoad;
            double deltaQc = defaultLoad;
            switch (load.phases) {
                case 'A':
                    deltaPa += p0;
                    deltaQa += q0;
                    break;
                case 'B':
                    deltaPb += p0;
                    deltaQb += q0;
                    break;
                case 'C':
                    deltaPc += p0;
                    deltaQc += q0;
                    break;
                default:
                    throw new PowsyblException("Unknown phase: " + load.phases);
            }
            l.newExtension(LoadAsymmetricalAdder.class)
                    .withConnectionType(getConnectionType(load))
                    .withDeltaPa(deltaPa)
                    .withDeltaQa(deltaQa)
                    .withDeltaPb(deltaPb)
                    .withDeltaQb(deltaQb)
                    .withDeltaPc(deltaPc)
                    .withDeltaQc(deltaQc)
                    .add();

        }
    }

    private static void createSource(Network network, Map<String, AsymmetricalBranchConnector> bus2Connector) {
        int busName = 1;
        VoltageLevel vl = network.getVoltageLevel(getVoltageLevelId(busName));
        Generator generator = vl.newGenerator()
                .setId("G150")
                .setBus(getBusId(busName))
                .setMinP(-100.0)
                .setMaxP(200)
                .setTargetP(0)
                .setTargetV(vl.getNominalV() * 1.05)
                .setVoltageRegulatorOn(true)
                .add();

        Complex zz = new Complex(0.0001, 0.0001); // 0.0001 , 0.001
        Complex zn = new Complex(0.0001, 0.0001); // 0.001 , 0.01

        generator.newExtension(GeneratorFortescueAdder.class)
                .withRz(zz.getReal())
                .withXz(zz.getImaginary())
                .withRn(zn.getReal())
                .withXn(zn.getImaginary())
                .add();

        // modification of connector due to generating unit
        bus2Connector.get(getBusId(busName)).setPositiveSequenceAsCurrent(false);
    }

    private static WindingConnectionType getConnectionType(String conn) {
        switch (conn) {
            case "Delta":
                return WindingConnectionType.DELTA;
            case "Wye":
                return WindingConnectionType.Y;
            default:
                throw new PowsyblException("Connection type not supported: " + conn);
        }
    }

    public static Network create(String path) {
        return create(NetworkFactory.findDefault(), path);
    }

    public static Network create(NetworkFactory networkFactory, String path) {
        Network network = networkFactory.createNetwork("EuropeanLvTestFeeder", "csv");
        network.setCaseDate(DateTime.parse("2023-04-11T23:59:00.000+01:00"));

        Map<String, AsymmetricalBranchConnector> bus2Connector = new HashMap<>();

        createBuses(network, path, bus2Connector);
        createSource(network, bus2Connector);
        createLines(network, path, bus2Connector);
        createLoads(network, path);
        return network;
    }

}
