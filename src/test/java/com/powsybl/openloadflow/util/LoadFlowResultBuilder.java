package com.powsybl.openloadflow.util;

import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.loadflow.LoadFlowResultImpl;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LoadFlowResultBuilder {
    private Map<String, String> metrics = new HashMap<>();
    private Boolean ok;
    private String logs;
    private List<LoadFlowResult.ComponentResult> componentResults = new ArrayList<>();

    public LoadFlowResultBuilder(Boolean ok) {
        this.ok = ok;
    }

    public LoadFlowResultBuilder addMetrics(String networkIterations, String networkStatus) {
        int networkNumber = this.metrics.size() / 2;
        this.metrics.put("network_" + networkNumber + "_iterations", networkIterations);
        this.metrics.put("network_" + networkNumber + "_status", networkStatus);
        return this;
    }

    public LoadFlowResultBuilder setLogs(String logs) {
        this.logs = logs;
        return this;
    }

    public LoadFlowResultBuilder addComponentResult(int componentNum, LoadFlowResult.ComponentResult.Status status, int iterationCount, String slackBusId, double slackBusActivePowerMismatch) {
        this.componentResults.add(new LoadFlowResultImpl.ComponentResultImpl(componentNum, status, iterationCount, slackBusId, slackBusActivePowerMismatch));
        return this;
    }

    public LoadFlowResult build() throws InvalidParameterException {
        if (metrics.isEmpty() || ok == null || componentResults.isEmpty()) {
            throw new InvalidParameterException("cannot build with given information");
        }
        return new LoadFlowResultImpl(ok, metrics, logs, componentResults);
    }
}
