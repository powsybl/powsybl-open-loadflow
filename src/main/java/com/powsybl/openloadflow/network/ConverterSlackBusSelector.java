package com.powsybl.openloadflow.network;

import com.powsybl.iidm.network.Country;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class ConverterSlackBusSelector extends AbstractSlackBusSelector {

    public ConverterSlackBusSelector(Set<Country> countries) {
        super(countries);
    }

    private static double getMaxPac(LfBus bus) {
        return bus.getConverters().stream().mapToDouble(LfAcDcConverter::getTargetP).sum();
    }

    @Override
    public SelectedSlackBus select(List<LfBus> buses, int limit) {
        List<LfBus> slackBuses = buses.stream()
                .filter(bus -> !bus.isFictitious())
                .filter(this::filterByCountry)
                .filter(bus -> !bus.getConverters().isEmpty())
                .filter(bus -> bus.getConverters()
                        .stream()
                        .anyMatch(LfVoltageSourceConverter::isVoltageRegulatorOn))
                .sorted(Comparator.comparingDouble(ConverterSlackBusSelector::getMaxPac).reversed())
                .limit(limit)
                .collect(Collectors.toList());

        //if there is no converter controlling AC Voltage, take generators instead
        if (slackBuses.isEmpty()) {
            slackBuses = buses.stream()
                    .filter(bus -> !bus.isFictitious())
                    .filter(this::filterByCountry)
                    .filter(bus -> !bus.getGenerators().isEmpty())
                    .limit(limit)
                    .collect(Collectors.toList());
        }
        return new SelectedSlackBus(slackBuses, "Largest converter bus");
    }
}
