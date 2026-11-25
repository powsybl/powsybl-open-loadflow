/**
 * Copyright (c) 2026, Elia Group (https://www.eliagroup.eu)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.se;

import com.powsybl.commons.PowsyblException;
import com.powsybl.iidm.network.BoundaryLine;
import com.powsybl.iidm.network.Branch;
import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.BusbarSection;
import com.powsybl.iidm.network.Connectable;
import com.powsybl.iidm.network.Ground;
import com.powsybl.iidm.network.Injection;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.ShuntCompensator;
import com.powsybl.iidm.network.Terminal;
import com.powsybl.iidm.network.ThreeSides;
import com.powsybl.iidm.network.ThreeWindingsTransformer;
import com.powsybl.iidm.network.TieLine;
import com.powsybl.iidm.network.TwoSides;
import com.powsybl.iidm.network.extensions.Measurement;
import com.powsybl.iidm.network.extensions.Measurements;
import com.powsybl.openloadflow.ac.equations.AcEquationType;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfElement;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.impl.LfLegBranch;
import com.powsybl.openloadflow.network.impl.LfStarBus;
import com.powsybl.openloadflow.network.impl.Networks;
import com.powsybl.openloadflow.util.PerUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Turns the device level readings IIDM carries into the bus and branch quantities an
 * estimator solves for.
 *
 * <p>Readings are taken in the IIDM terminal convention, positive meaning power flowing from
 * the bus into the equipment, which is what {@code Terminal.getP()} means. A bus injection is
 * generation-positive, so forming one negates the sum. A meter in a generator bay commonly
 * reports generation as positive instead, so whether a source follows the terminal convention
 * is the first thing to confirm about a new feed.</p>
 *
 * <p>Equipment is sorted by how {@code LfNetwork} carries it, which is not always how IIDM
 * classes it. Loads, generators, batteries, converter stations and static var compensators are
 * devices, and their readings sum into the bus injection. A shunt compensator is part of the
 * network admittance, so the injection equation already holds its term and its reading is left
 * out. A boundary line is an injection in IIDM and a branch in {@code LfNetwork}, so its
 * reading is a branch flow.</p>
 *
 * <p>Everything leaves per unit on the {@code LfNetwork}'s own base: power on
 * {@link PerUnit#SB}, voltage on the bus nominal voltage, current on the base current of the
 * nominal voltage at the measured side.</p>
 *
 * @author Šime Pavlić {@literal <sime.pavlic at kickstage.com>}
 */
public class MeasurementAggregator {

    private static final Logger LOG = LoggerFactory.getLogger(MeasurementAggregator.class);

    private final LfNetwork lfNetwork;

    private final Map<Skipped, Integer> skipped = new EnumMap<>(Skipped.class);

    private int summedIntoInjections;

    private int zeroInjectionsAdded;

    private final MeasurementAggregation settings;

    public MeasurementAggregator(LfNetwork lfNetwork, MeasurementAggregation settings) {
        this.lfNetwork = Objects.requireNonNull(lfNetwork);
        this.settings = Objects.requireNonNull(settings);
    }

    /**
     * Reads every valid measurement the network carries and returns the bus and branch
     * quantities they amount to.
     *
     * @param network the network, read against its working variant
     * @return the aggregated readings, in no guaranteed order
     */
    public List<LfMeasurement> aggregate(Network network) {
        Objects.requireNonNull(network);

        List<LfMeasurement> aggregated = new ArrayList<>();
        Map<InjectionKey, InjectionBucket> injections = new LinkedHashMap<>();

        skipped.clear();
        summedIntoInjections = 0;
        zeroInjectionsAdded = 0;
        int readCount = 0;
        for (Connectable<?> connectable : network.getConnectables()) {
            for (Measurement reading : measurementsOf(connectable)) {
                readCount++;
                read(connectable, reading, aggregated, injections);
            }
        }

        injections.forEach((key, bucket) -> emitBusInjection(key, bucket, aggregated));
        addZeroInjections(network, aggregated);
        List<LfMeasurement> resolved = resolveDuplicates(aggregated);
        logSummary(readCount, resolved.size(), aggregated.size() - resolved.size());
        return resolved;
    }

    /**
     * The readings attached to this equipment, empty when it carries none. Looked up by name
     * because {@code Measurements} is generic in its own equipment type, which a wildcard
     * {@code Connectable} cannot satisfy.
     */
    private static Collection<Measurement> measurementsOf(Connectable<?> connectable) {
        Measurements<?> extension = (Measurements<?>) connectable.getExtensionByName(Measurements.NAME);
        return extension == null ? List.of() : extension.getMeasurements();
    }

    private void read(Connectable<?> connectable,
                      Measurement reading,
                      List<LfMeasurement> aggregated,
                      Map<InjectionKey, InjectionBucket> injections) {
        if (!reading.isValid()) {
            skip(Skipped.INVALID);
            return;
        }
        if (Double.isNaN(reading.getValue())) {
            LOG.warn("Ignored measurement {} on {}: value is not a number",
                    reading.getId(), connectable.getId());
            skip(Skipped.NOT_A_NUMBER);
            return;
        }
        double sigma = reading.getStandardDeviation();
        if (Double.isNaN(sigma) || sigma <= 0) {
            LOG.warn("Ignored measurement {} on {}: standard deviation is {}",
                    reading.getId(), connectable.getId(), sigma);
            skip(Skipped.BAD_STANDARD_DEVIATION);
            return;
        }

        switch (reading.getType()) {
            case VOLTAGE -> readVoltage(connectable, reading, aggregated);
            case ACTIVE_POWER -> readPower(connectable, reading, true, aggregated, injections);
            case REACTIVE_POWER -> readPower(connectable, reading, false, aggregated, injections);
            case CURRENT -> readCurrent(connectable, reading, aggregated);
            default -> {
                LOG.debug("Ignored measurement {} on {}: type {} is not estimated",
                        reading.getId(), connectable.getId(), reading.getType());
                skip(Skipped.UNSUPPORTED_TYPE);
            }
        }
    }

    private void readVoltage(Connectable<?> connectable,
                             Measurement reading,
                             List<LfMeasurement> aggregated) {
        Terminal terminal = terminalOf(connectable, reading.getSide());
        if (terminal == null) {
            LOG.warn("Ignored measurement {} on branch {}: no side given",
                    reading.getId(), connectable.getId());
            skip(Skipped.NO_SIDE);
            return;
        }
        Optional<LfBus> lfBus = findBus(terminal);
        if (lfBus.isEmpty()) {
            skip(Skipped.OUTSIDE_NETWORK);
            return;
        }
        double nominalV = terminal.getVoltageLevel().getNominalV();
        aggregated.add(new LfMeasurement(AcEquationType.BUS_TARGET_V, lfBus.get(),
                reading.getValue() / nominalV,
                floored(reading.getStandardDeviation() / nominalV, reading.getValue() / nominalV),
                idsOf(reading)));
    }

    private void readPower(Connectable<?> connectable,
                           Measurement reading,
                           boolean active,
                           List<LfMeasurement> aggregated,
                           Map<InjectionKey, InjectionBucket> injections) {
        if (isCarriedAsBranch(connectable)) {
            readBranchFlow(connectable, reading,
                    active ? AcEquationType::branchTargetP : AcEquationType::branchTargetQ, aggregated);
            return;
        }
        collectInjection(connectable, reading, active, injections);
    }

    private void readCurrent(Connectable<?> connectable,
                             Measurement reading,
                             List<LfMeasurement> aggregated) {
        if (!isCarriedAsBranch(connectable)) {
            LOG.debug("Ignored current measurement {} on injection {}: not a branch quantity",
                    reading.getId(), connectable.getId());
            skip(Skipped.UNSUPPORTED_TYPE);
            return;
        }
        readBranchFlow(connectable, reading, AcEquationType::branchTargetI, aggregated);
    }

    /** The per unit base follows the quantity: power on {@link PerUnit#SB}, current on the side's. */
    private void readBranchFlow(Connectable<?> connectable,
                                Measurement reading,
                                Function<TwoSides, AcEquationType> typeAtSide,
                                List<LfMeasurement> aggregated) {
        Terminal terminal = terminalOf(connectable, reading.getSide());
        if (terminal == null) {
            LOG.warn("Ignored measurement {} on branch {}: no side given",
                    reading.getId(), connectable.getId());
            skip(Skipped.NO_SIDE);
            return;
        }
        branchOf(connectable, terminal).ifPresent(resolved -> {
            AcEquationType type = typeAtSide.apply(resolved.side());
            double base = reading.getType() == Measurement.Type.CURRENT
                    ? PerUnit.ib(terminal.getVoltageLevel().getNominalV())
                    : PerUnit.SB;
            double valuePu = reading.getValue() / base;
            aggregated.add(new LfMeasurement(type, resolved.branch(),
                    valuePu, floored(reading.getStandardDeviation() / base, valuePu), idsOf(reading)));
        });
    }

    private void collectInjection(Connectable<?> connectable,
                                  Measurement reading,
                                  boolean active,
                                  Map<InjectionKey, InjectionBucket> injections) {
        if (connectable instanceof ShuntCompensator) {
            LOG.debug("Ignored measurement {} on {}: shunts are carried in the network admittance",
                    reading.getId(), connectable.getId());
            skip(Skipped.MODELLED_AS_SHUNT);
            return;
        }
        if (!isDevice(connectable)) {
            LOG.debug("Ignored measurement {} on {}: the equipment carries no power",
                    reading.getId(), connectable.getId());
            skip(Skipped.UNSUPPORTED_TYPE);
            return;
        }
        Terminal terminal = ((Injection<?>) connectable).getTerminal();
        Optional<LfBus> lfBus = findBus(terminal);
        if (lfBus.isEmpty()) {
            skip(Skipped.OUTSIDE_NETWORK);
            return;
        }
        injections.computeIfAbsent(new InjectionKey(lfBus.get().getId(), active),
                        key -> new InjectionBucket(lfBus.get(), busOf(terminal)))
                .add(connectable.getId(), reading.getId(), reading.getValue(), reading.getStandardDeviation());
    }

    private void emitBusInjection(InjectionKey key, InjectionBucket bucket, List<LfMeasurement> aggregated) {
        if (settings.getBusInjectionPolicy() == MeasurementAggregation.BusInjectionPolicy.REQUIRE_ALL_METERED
                && !bucket.coversEveryDevice()) {
            LOG.debug("No {} injection formed at bus {}: {} of {} devices are metered",
                    key.active() ? "active" : "reactive", key.busId(),
                    bucket.meteredDeviceCount(), bucket.deviceCount());
            skip(Skipped.INCOMPLETE_INJECTION, bucket.readingCount());
            return;
        }
        summedIntoInjections += bucket.readingCount() - 1;
        // the terminal convention points into the equipment, a bus injection points out of it
        double valuePu = -bucket.sum() / PerUnit.SB;
        double sigmaPu = bucket.standardDeviation() / PerUnit.SB;
        aggregated.add(new LfMeasurement(injectionType(key.active()), bucket.lfBus(), valuePu,
                floored(sigmaPu, valuePu), bucket.sourceMeasurementIds()));
    }

    /**
     * A bus with no device injects nothing; that enters as a zero-valued pseudo-measurement. The
     * star bus of a three winding transformer is one by construction and has no IIDM bus to ask.
     */
    private void addZeroInjections(Network network, List<LfMeasurement> aggregated) {
        if (settings.getZeroInjectionMode() == MeasurementAggregation.ZeroInjectionMode.IGNORED) {
            return;
        }
        for (LfBus lfBus : lfNetwork.getBuses()) {
            if (lfBus instanceof LfStarBus || hasNoDevice(network, lfBus)) {
                addZeroInjection(lfBus, true, aggregated);
                addZeroInjection(lfBus, false, aggregated);
            }
        }
    }

    private boolean hasNoDevice(Network network, LfBus lfBus) {
        Bus bus = settings.isBreakers()
                ? network.getBusBreakerView().getBus(lfBus.getId())
                : network.getBusView().getBus(lfBus.getId());
        return bus != null && devicesAt(bus) == 0;
    }

    private void addZeroInjection(LfBus lfBus, boolean active, List<LfMeasurement> aggregated) {
        aggregated.add(LfMeasurement.of(injectionType(active), lfBus, 0,
                settings.getZeroInjectionStandardDeviationPu()));
        zeroInjectionsAdded++;
    }

    /**
     * Applies the duplicate policy to readings that describe the same quantity at the same
     * place. Bus injections cannot collide here, since one is formed per bus and quantity.
     */
    private List<LfMeasurement> resolveDuplicates(List<LfMeasurement> measurements) {
        Map<DuplicateKey, List<LfMeasurement>> byKey = new LinkedHashMap<>();
        for (LfMeasurement measurement : measurements) {
            byKey.computeIfAbsent(DuplicateKey.of(measurement), key -> new ArrayList<>()).add(measurement);
        }
        List<LfMeasurement> resolved = new ArrayList<>(measurements.size());
        for (Map.Entry<DuplicateKey, List<LfMeasurement>> entry : byKey.entrySet()) {
            List<LfMeasurement> group = entry.getValue();
            if (group.size() == 1) {
                resolved.add(group.get(0));
                continue;
            }
            switch (settings.getDuplicateMeasurementPolicy()) {
                case KEEP_ALL -> resolved.addAll(group);
                case MERGE_INVERSE_VARIANCE -> resolved.add(merged(group));
                case THROW -> throw new PowsyblException(group.size() + " measurements of "
                        + entry.getKey().type() + " at " + entry.getKey().element().getId()
                        + ": duplicate measurement policy is " + settings.getDuplicateMeasurementPolicy());
            }
        }
        return resolved;
    }

    private static LfMeasurement merged(List<LfMeasurement> group) {
        DeviceReading combined = group.stream()
                .map(m -> new DeviceReading(m.weight() * m.valuePu(), m.weight()))
                .reduce(DeviceReading::plus)
                .orElseThrow();
        LfMeasurement first = group.get(0);
        List<String> sourceMeasurementIds = group.stream()
                .flatMap(m -> m.sourceMeasurementIds().stream())
                .toList();
        return new LfMeasurement(first.type(), first.element(), combined.value(), Math.sqrt(combined.variance()), sourceMeasurementIds);
    }

    /** IIDM allows a measurement without an id. */
    private static List<String> idsOf(Measurement reading) {
        return reading.getId() == null ? List.of() : List.of(reading.getId());
    }

    private static AcEquationType injectionType(boolean active) {
        return active ? AcEquationType.BUS_TARGET_P : AcEquationType.BUS_TARGET_Q;
    }

    /** The devices at a bus that a bus injection is expected to account for. */
    private static long devicesAt(Bus bus) {
        return bus.getConnectedTerminalStream()
                .map(Terminal::getConnectable)
                .filter(MeasurementAggregator::isDevice)
                .count();
    }

    /**
     * Equipment whose power a bus injection accounts for. A busbar section and a ground carry
     * none, and {@code LfNetwork} holds a shunt compensator and a boundary line in the network
     * itself.
     */
    private static boolean isDevice(Connectable<?> connectable) {
        return connectable instanceof Injection<?>
                && !(connectable instanceof ShuntCompensator)
                && !(connectable instanceof BoundaryLine)
                && !(connectable instanceof BusbarSection)
                && !(connectable instanceof Ground);
    }

    /** A boundary line is an injection in IIDM and a branch in {@code LfNetwork}. */
    private static boolean isCarriedAsBranch(Connectable<?> connectable) {
        return !(connectable instanceof Injection<?>) || connectable instanceof BoundaryLine;
    }

    private double floored(double sigma, double value) {
        double floor = settings.getRelativeStandardDeviationFloor() * Math.abs(value);
        return Math.max(sigma, floor);
    }

    private Optional<LfBus> findBus(Terminal terminal) {
        Bus bus = busOf(terminal);
        if (bus == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(lfNetwork.getBusById(bus.getId()));
    }

    private Bus busOf(Terminal terminal) {
        return Networks.getBus(terminal, settings.isBreakers());
    }

    private Optional<ResolvedSide> branchOf(Connectable<?> connectable, Terminal terminal) {
        Optional<LfBus> lfBus = findBus(terminal);
        if (lfBus.isEmpty()) {
            skip(Skipped.OUTSIDE_NETWORK);
            return Optional.empty();
        }
        // the bus is in this network, so a branch that is not must be one the model does not carry
        LfBranch branch = lfNetwork.getBranchById(branchIdOf(connectable, terminal));
        if (branch == null) {
            LOG.warn("Ignored measurement on {}: no branch of that identifier in network {}",
                    connectable.getId(), lfNetwork);
            skip(Skipped.UNMODELLED_ELEMENT);
            return Optional.empty();
        }
        return Optional.of(new ResolvedSide(branch, sideOf(branch, lfBus.get())));
    }

    /** The branch orientation in {@code LfNetwork} need not match IIDM's, so the side is resolved by bus. */
    private static TwoSides sideOf(LfBranch branch, LfBus bus) {
        LfBus bus1 = branch.getBus1();
        if (bus1 != null && bus1.getNum() == bus.getNum()) {
            return TwoSides.ONE;
        }
        LfBus bus2 = branch.getBus2();
        if (bus2 != null && bus2.getNum() == bus.getNum()) {
            return TwoSides.TWO;
        }
        throw new IllegalArgumentException("Bus " + bus.getId() + " is not an end of branch " + branch.getId());
    }

    /**
     * A three winding transformer is one connectable in IIDM and three branches in an
     * {@code LfNetwork}, one per leg, so a reading on it resolves through the leg its
     * terminal belongs to. The two boundary lines of a tie line are one branch.
     */
    private static String branchIdOf(Connectable<?> connectable, Terminal terminal) {
        if (connectable instanceof ThreeWindingsTransformer transformer) {
            return LfLegBranch.getId(transformer.getSide(terminal), transformer.getId());
        }
        if (connectable instanceof BoundaryLine boundaryLine) {
            return boundaryLine.getTieLine().map(TieLine::getId).orElse(boundaryLine.getId());
        }
        return connectable.getId();
    }

    private static Terminal terminalOf(Connectable<?> connectable, ThreeSides side) {
        if (connectable instanceof Injection<?> injection) {
            return injection.getTerminal();
        }
        if (side == null) {
            return null;
        }
        if (connectable instanceof Branch<?> branch) {
            return branch.getTerminal(side.toTwoSides());
        }
        return connectable.getTerminals().get(side.getNum() - 1);
    }

    /** Why a reading did not become a measurement, counted so one line can account for a whole feed. */
    private enum Skipped {
        INVALID("flagged invalid"),
        NOT_A_NUMBER("value is not a number"),
        BAD_STANDARD_DEVIATION("standard deviation missing or not positive"),
        UNSUPPORTED_TYPE("type not estimated"),
        NO_SIDE("no side given"),
        OUTSIDE_NETWORK("outside this network"),
        UNMODELLED_ELEMENT("element not in the network model"),
        MODELLED_AS_SHUNT("carried in the network admittance"),
        INCOMPLETE_INJECTION("in a bus injection the policy did not allow to form");

        private final String text;

        Skipped(String text) {
            this.text = text;
        }

        @Override
        public String toString() {
            return text;
        }
    }

    private void skip(Skipped reason) {
        skip(reason, 1);
    }

    private void skip(Skipped reason, int count) {
        skipped.merge(reason, count, Integer::sum);
    }

    /**
     * Accounts for every reading the network carried. A feed that silently loses a tenth of its
     * measurements looks the same as one that does not, so the counts say where they went.
     * Readings outside this network are normal, since one network per connected component is
     * aggregated separately.
     */
    private void logSummary(int readCount, int produced, int merged) {
        int skippedCount = skipped.values().stream().mapToInt(Integer::intValue).sum();
        if (skippedCount == 0 && merged == 0 && summedIntoInjections == 0 && zeroInjectionsAdded == 0) {
            LOG.info("Network {}: aggregated {} measurements from {} readings", lfNetwork, produced, readCount);
            return;
        }
        StringBuilder detail = new StringBuilder();
        if (skippedCount > 0) {
            detail.append(", ").append(skippedCount).append(" skipped: ")
                    .append(skipped.entrySet().stream()
                            .filter(e -> e.getValue() > 0)
                            .map(e -> e.getValue() + " " + e.getKey())
                            .collect(Collectors.joining(", ")));
        }
        if (merged > 0) {
            detail.append(", ").append(merged).append(" merged as duplicates");
        }
        if (summedIntoInjections > 0) {
            detail.append(", ").append(summedIntoInjections).append(" summed into bus injections");
        }
        if (zeroInjectionsAdded > 0) {
            detail.append(", ").append(zeroInjectionsAdded).append(" added at zero injection buses");
        }
        LOG.info("Network {}: aggregated {} measurements from {} readings{}",
                lfNetwork, produced, readCount, detail);
    }

    /** One device's reading, held as a weighted sum so that several meters combine by inverse variance. */
    private record DeviceReading(double weightedValue, double weight) {

        private static DeviceReading of(double value, double standardDeviation) {
            double weight = 1 / (standardDeviation * standardDeviation);
            return new DeviceReading(weight * value, weight);
        }

        private DeviceReading plus(DeviceReading other) {
            return new DeviceReading(weightedValue + other.weightedValue, weight + other.weight);
        }

        private double value() {
            return weightedValue / weight;
        }

        private double variance() {
            return 1 / weight;
        }
    }

    /** Two readings are duplicates when they share a type and an element. */
    private record DuplicateKey(AcEquationType type, LfElement element) {

        private static DuplicateKey of(LfMeasurement measurement) {
            return new DuplicateKey(measurement.type(), measurement.element());
        }
    }

    private record InjectionKey(String busId, boolean active) {
    }

    private record ResolvedSide(LfBranch branch, TwoSides side) {
    }

    /**
     * The device readings seen at one bus for one quantity, and the bus they sit on, so the
     * policy can ask how many of its devices carry a meter.
     */
    private static final class InjectionBucket {

        private final LfBus lfBus;

        private final Bus bus;

        private final Map<String, DeviceReading> readingsByDevice = new LinkedHashMap<>();

        private final List<String> sourceMeasurementIds = new ArrayList<>();

        private int readingCount;

        private InjectionBucket(LfBus lfBus, Bus bus) {
            this.lfBus = lfBus;
            this.bus = bus;
        }

        /**
         * Several meters on one device describe that device's contribution, not the bus
         * injection, so they combine here rather than being summed into it twice.
         */
        private void add(String deviceId, String measurementId, double value, double standardDeviation) {
            readingCount++;
            if (measurementId != null) {
                sourceMeasurementIds.add(measurementId);
            }
            readingsByDevice.merge(deviceId, DeviceReading.of(value, standardDeviation), DeviceReading::plus);
        }

        private List<String> sourceMeasurementIds() {
            return sourceMeasurementIds;
        }

        private int readingCount() {
            return readingCount;
        }

        private LfBus lfBus() {
            return lfBus;
        }

        private double sum() {
            return readingsByDevice.values().stream().mapToDouble(DeviceReading::value).sum();
        }

        private double standardDeviation() {
            return Math.sqrt(readingsByDevice.values().stream().mapToDouble(DeviceReading::variance).sum());
        }

        private int meteredDeviceCount() {
            return readingsByDevice.size();
        }

        private long deviceCount() {
            return bus == null ? meteredDeviceCount() : devicesAt(bus);
        }

        private boolean coversEveryDevice() {
            return meteredDeviceCount() >= deviceCount();
        }
    }
}
