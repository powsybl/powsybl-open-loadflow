/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow;

import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.config.PlatformConfig;
import com.powsybl.commons.extensions.AbstractExtension;
import com.powsybl.commons.parameters.Parameter;
import com.powsybl.commons.parameters.ParameterScope;
import com.powsybl.commons.parameters.ParameterType;
import com.powsybl.iidm.network.Country;
import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.openloadflow.ac.AcLoadFlowParameters;
import com.powsybl.openloadflow.ac.VoltageMagnitudeInitializer;
import com.powsybl.openloadflow.ac.equations.AcEquationSystemCreationParameters;
import com.powsybl.openloadflow.ac.nr.*;
import com.powsybl.openloadflow.ac.outerloop.AcOuterLoop;
import com.powsybl.openloadflow.ac.outerloop.IncrementalTransformerVoltageControlOuterLoop;
import com.powsybl.openloadflow.ac.outerloop.ReactiveLimitsOuterLoop;
import com.powsybl.openloadflow.ac.outerloop.SecondaryVoltageControlOuterLoop;
import com.powsybl.openloadflow.dc.DcLoadFlowParameters;
import com.powsybl.openloadflow.dc.DcValueVoltageInitializer;
import com.powsybl.openloadflow.dc.equations.DcEquationSystemCreationParameters;
import com.powsybl.openloadflow.graph.GraphConnectivityFactory;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.util.PreviousValueVoltageInitializer;
import com.powsybl.openloadflow.network.util.UniformValueVoltageInitializer;
import com.powsybl.openloadflow.network.util.VoltageInitializer;
import de.vandermeer.asciitable.AsciiTable;
import de.vandermeer.asciitable.CWC_LongestWord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class OpenLoadFlowParameters extends AbstractExtension<LoadFlowParameters> {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenLoadFlowParameters.class);

    public static final SlackBusSelectionMode SLACK_BUS_SELECTION_MODE_DEFAULT_VALUE = SlackBusSelectionMode.MOST_MESHED;

    public static final LowImpedanceBranchMode LOW_IMPEDANCE_BRANCH_MODE_DEFAULT_VALUE = LowImpedanceBranchMode.REPLACE_BY_ZERO_IMPEDANCE_LINE;

    public static final boolean THROWS_EXCEPTION_IN_CASE_OF_SLACK_DISTRIBUTION_FAILURE_DEFAULT_VALUE = false;

    public static final boolean VOLTAGE_REMOTE_CONTROL_DEFAULT_VALUE = true;

    public static final boolean REACTIVE_POWER_REMOTE_CONTROL_DEFAULT_VALUE = false;

    public static final boolean LOAD_POWER_FACTOR_CONSTANT_DEFAULT_VALUE = false;

    /**
     * Slack bus maximum active power mismatch in MW: 1 Mw => 10^-2 in p.u
     */
    public static final double SLACK_BUS_P_MAX_MISMATCH_DEFAULT_VALUE = 1.0;

    public static final NewtonRaphsonStoppingCriteriaType NEWTONRAPHSON_STOPPING_CRITERIA_TYPE_DEFAULT_VALUE = NewtonRaphsonStoppingCriteriaType.UNIFORM_CRITERIA;

    /** Default value of the maximum active power mismatch in MW **/
    public static final double MAX_ACTIVE_POWER_MISMATCH_DEFAULT_VALUE = 1e-2;

    /** Default value of the maximum reactive power mismatch in Mvar **/
    public static final double MAX_REACTIVE_POWER_MISMATCH_DEFAULT_VALUE = 1e-2;

    /** Default value of the maximum voltage mismatch in pu **/
    public static final double MAX_VOLTAGE_MISMATCH_DEFAULT_VALUE = 1e-4;

    public static final double MAX_ANGLE_MISMATCH_DEFAULT_VALUE = 1e-5;

    public static final double MAX_RATIO_MISMATCH_DEFAULT_VALUE = 1e-5;

    public static final double MAX_SUSCEPTANCE_MISMATCH_DEFAULT_VALUE = 1e-4;

    public static final boolean VOLTAGE_PER_REACTIVE_POWER_CONTROL_DEFAULT_VALUE = false;

    public static final boolean SVC_VOLTAGE_MONITORING_DEFAULT_VALUE = true;

    public static final VoltageInitModeOverride VOLTAGE_INIT_MODE_OVERRIDE_DEFAULT_VALUE = VoltageInitModeOverride.NONE;

    public static final TransformerVoltageControlMode TRANSFORMER_VOLTAGE_CONTROL_MODE_DEFAULT_VALUE = TransformerVoltageControlMode.WITH_GENERATOR_VOLTAGE_CONTROL;

    public static final ShuntVoltageControlMode SHUNT_VOLTAGE_CONTROL_MODE_DEFAULT_VALUE = ShuntVoltageControlMode.WITH_GENERATOR_VOLTAGE_CONTROL;

    public static final PhaseShifterControlMode PHASE_SHIFTER_CONTROL_MODE_DEFAULT_VALUE = PhaseShifterControlMode.CONTINUOUS_WITH_DISCRETISATION;

    public static final Set<String> ACTIONABLE_SWITCH_IDS_DEFAULT_VALUE = Collections.emptySet();

    public static final Set<ReportedFeatures> REPORTED_FEATURES_DEFAULT_VALUE = Collections.emptySet();

    private static final ReactivePowerDispatchMode REACTIVE_POWER_DISPATCH_MODE_DEFAULT_VALUE = ReactivePowerDispatchMode.Q_EQUAL_PROPORTION;

    protected static final List<String> OUTER_LOOP_NAMES_DEFAULT_VALUE = null;

    public static final String SLACK_BUS_SELECTION_MODE_PARAM_NAME = "slackBusSelectionMode";

    public static final String SLACK_BUSES_IDS_PARAM_NAME = "slackBusesIds";

    public static final String THROWS_EXCEPTION_IN_CASE_OF_SLACK_DISTRIBUTION_FAILURE_PARAM_NAME = "throwsExceptionInCaseOfSlackDistributionFailure";

    public static final String VOLTAGE_REMOTE_CONTROL_PARAM_NAME = "voltageRemoteControl";

    public static final String REACTIVE_POWER_REMOTE_CONTROL_PARAM_NAME = "reactivePowerRemoteControl";

    public static final String LOW_IMPEDANCE_BRANCH_MODE_PARAM_NAME = "lowImpedanceBranchMode";

    public static final String LOAD_POWER_FACTOR_CONSTANT_PARAM_NAME = "loadPowerFactorConstant";

    public static final String PLAUSIBLE_ACTIVE_POWER_LIMIT_PARAM_NAME = "plausibleActivePowerLimit";

    public static final String NEWTONRAPHSON_STOPPING_CRITERIA_TYPE_PARAM_NAME = "newtonRaphsonStoppingCriteriaType";

    public static final String MAX_ACTIVE_POWER_MISMATCH_PARAM_NAME = "maxActivePowerMismatch";

    public static final String MAX_REACTIVE_POWER_MISMATCH_PARAM_NAME = "maxReactivePowerMismatch";

    public static final String MAX_VOLTAGE_MISMATCH_PARAM_NAME = "maxVoltageMismatch";

    public static final String MAX_ANGLE_MISMATCH_PARAM_NAME = "maxAngleMismatch";

    public static final String MAX_RATIO_MISMATCH_PARAM_NAME = "maxRatioMismatch";

    public static final String MAX_SUSCEPTANCE_MISMATCH_PARAM_NAME = "maxSusceptanceMismatch";

    public static final String SLACK_BUS_P_MAX_MISMATCH_PARAM_NAME = "slackBusPMaxMismatch";

    public static final String VOLTAGE_PER_REACTIVE_POWER_CONTROL_PARAM_NAME = "voltagePerReactivePowerControl";

    public static final String MAX_NEWTON_RAPHSON_ITERATIONS_PARAM_NAME = "maxNewtonRaphsonIterations";

    public static final String MAX_OUTER_LOOP_ITERATIONS_PARAM_NAME = "maxOuterLoopIterations";

    public static final String NEWTON_RAPHSON_CONV_EPS_PER_EQ_PARAM_NAME = "newtonRaphsonConvEpsPerEq";

    public static final String VOLTAGE_INIT_MODE_OVERRIDE_PARAM_NAME = "voltageInitModeOverride";

    public static final String TRANSFORMER_VOLTAGE_CONTROL_MODE_PARAM_NAME = "transformerVoltageControlMode";

    public static final String SHUNT_VOLTAGE_CONTROL_MODE_PARAM_NAME = "shuntVoltageControlMode";

    public static final String MIN_PLAUSIBLE_TARGET_VOLTAGE_PARAM_NAME = "minPlausibleTargetVoltage";

    public static final String MAX_PLAUSIBLE_TARGET_VOLTAGE_PARAM_NAME = "maxPlausibleTargetVoltage";

    public static final String MIN_REALISTIC_VOLTAGE_PARAM_NAME = "minRealisticVoltage";

    public static final String MAX_REALISTIC_VOLTAGE_PARAM_NAME = "maxRealisticVoltage";

    public static final String MIN_NOMINAL_VOLTAGE_TARGET_VOLTAGE_CHECK_PARAM_NAME = "minNominalVoltageTargetVoltageCheck";

    public static final String REACTIVE_RANGE_CHECK_MODE_PARAM_NAME = "reactiveRangeCheckMode";

    public static final String LOW_IMPEDANCE_THRESHOLD_PARAM_NAME = "lowImpedanceThreshold";

    public static final String NETWORK_CACHE_ENABLED_PARAM_NAME = "networkCacheEnabled";

    public static final String SVC_VOLTAGE_MONITORING_PARAM_NAME = "svcVoltageMonitoring";

    public static final String STATE_VECTOR_SCALING_MODE_PARAM_NAME = "stateVectorScalingMode";

    public static final String MAX_SLACK_BUS_COUNT_PARAM_NAME = "maxSlackBusCount";

    public static final String DEBUG_DIR_PARAM_NAME = "debugDir";

    public static final String INCREMENTAL_TRANSFORMER_VOLTAGE_CONTROL_OUTER_LOOP_MAX_TAP_SHIFT_PARAM_NAME = "incrementalTransformerVoltageControlOuterLoopMaxTapShift";

    public static final String SECONDARY_VOLTAGE_CONTROL_PARAM_NAME = "secondaryVoltageControl";

    public static final String CONTROLLER_TO_PILOT_POINT_VOLTAGE_SENSI_EPSILON_PARAM_NAME = "controllerToPilotPointVoltageSensiEpsilon";

    public static final String REACTIVE_LIMITS_MAX_SWITCH_PQ_PV_PARAM_NAME = "reactiveLimitsMaxPqPvSwitch";

    public static final String PHASE_SHIFTER_CONTROL_MODE_PARAM_NAME = "phaseShifterControlMode";

    private static final String ALWAYS_UPDATE_NETWORK_PARAM_NAME = "alwaysUpdateNetwork";

    private static final String MOST_MESHED_SLACK_BUS_SELECTOR_MAX_NOMINAL_VOLTAGE_PERCENTILE_PARAM_NAME = "mostMeshedSlackBusSelectorMaxNominalVoltagePercentile";

    public static final String REPORTED_FEATURES_PARAM_NAME = "reportedFeatures";

    public static final String SLACK_BUS_COUNTRY_FILTER_PARAM_NAME = "slackBusCountryFilter";

    private static final String ACTIONABLE_SWITCHES_IDS_PARAM_NAME = "actionableSwitchesIds";

    private static final String ASYMMETRICAL_PARAM_NAME = "asymmetrical";

    private static final String REACTIVE_POWER_DISPATCH_MODE_PARAM_NAME = "reactivePowerDispatchMode";

    static final String OUTER_LOOP_NAMES_PARAM_NAME = "outerLoopNames";

    private static <E extends Enum<E>> List<Object> getEnumPossibleValues(Class<E> enumClass) {
        return EnumSet.allOf(enumClass).stream().map(Enum::name).collect(Collectors.toList());
    }

    public static final List<Parameter> SPECIFIC_PARAMETERS = List.of(
        new Parameter(SLACK_BUS_SELECTION_MODE_PARAM_NAME, ParameterType.STRING, "Slack bus selection mode", SLACK_BUS_SELECTION_MODE_DEFAULT_VALUE.name(), getEnumPossibleValues(SlackBusSelectionMode.class)),
        new Parameter(SLACK_BUSES_IDS_PARAM_NAME, ParameterType.STRING_LIST, "Slack bus IDs", null),
        new Parameter(LOW_IMPEDANCE_BRANCH_MODE_PARAM_NAME, ParameterType.STRING, "Low impedance branch mode", LOW_IMPEDANCE_BRANCH_MODE_DEFAULT_VALUE.name(), getEnumPossibleValues(LowImpedanceBranchMode.class)),
        new Parameter(VOLTAGE_REMOTE_CONTROL_PARAM_NAME, ParameterType.BOOLEAN, "Generator voltage remote control", VOLTAGE_REMOTE_CONTROL_DEFAULT_VALUE),
        new Parameter(THROWS_EXCEPTION_IN_CASE_OF_SLACK_DISTRIBUTION_FAILURE_PARAM_NAME, ParameterType.BOOLEAN, "Throws an exception in case of slack distribution failure", THROWS_EXCEPTION_IN_CASE_OF_SLACK_DISTRIBUTION_FAILURE_DEFAULT_VALUE),
        new Parameter(LOAD_POWER_FACTOR_CONSTANT_PARAM_NAME, ParameterType.BOOLEAN, "Load power factor is constant", LOAD_POWER_FACTOR_CONSTANT_DEFAULT_VALUE),
        new Parameter(PLAUSIBLE_ACTIVE_POWER_LIMIT_PARAM_NAME, ParameterType.DOUBLE, "Plausible active power limit", LfNetworkParameters.PLAUSIBLE_ACTIVE_POWER_LIMIT_DEFAULT_VALUE),
        new Parameter(SLACK_BUS_P_MAX_MISMATCH_PARAM_NAME, ParameterType.DOUBLE, "Slack bus max active power mismatch", SLACK_BUS_P_MAX_MISMATCH_DEFAULT_VALUE),
        new Parameter(VOLTAGE_PER_REACTIVE_POWER_CONTROL_PARAM_NAME, ParameterType.BOOLEAN, "Voltage per reactive power slope", VOLTAGE_PER_REACTIVE_POWER_CONTROL_DEFAULT_VALUE),
        new Parameter(REACTIVE_POWER_REMOTE_CONTROL_PARAM_NAME, ParameterType.BOOLEAN, "SVC remote reactive power control", REACTIVE_POWER_REMOTE_CONTROL_DEFAULT_VALUE),
        new Parameter(MAX_NEWTON_RAPHSON_ITERATIONS_PARAM_NAME, ParameterType.INTEGER, "Max iterations per Newton-Raphson", NewtonRaphsonParameters.DEFAULT_MAX_ITERATIONS),
        new Parameter(MAX_OUTER_LOOP_ITERATIONS_PARAM_NAME, ParameterType.INTEGER, "Max outer loop iterations", AcLoadFlowParameters.DEFAULT_MAX_OUTER_LOOP_ITERATIONS),
        new Parameter(NEWTON_RAPHSON_CONV_EPS_PER_EQ_PARAM_NAME, ParameterType.DOUBLE, "Newton-Raphson convergence epsilon per equation", DefaultNewtonRaphsonStoppingCriteria.DEFAULT_CONV_EPS_PER_EQ),
        new Parameter(VOLTAGE_INIT_MODE_OVERRIDE_PARAM_NAME, ParameterType.STRING, "Voltage init mode override", VOLTAGE_INIT_MODE_OVERRIDE_DEFAULT_VALUE.name(), getEnumPossibleValues(VoltageInitModeOverride.class)),
        new Parameter(TRANSFORMER_VOLTAGE_CONTROL_MODE_PARAM_NAME, ParameterType.STRING, "Transformer voltage control mode", TRANSFORMER_VOLTAGE_CONTROL_MODE_DEFAULT_VALUE.name(), getEnumPossibleValues(TransformerVoltageControlMode.class)),
        new Parameter(SHUNT_VOLTAGE_CONTROL_MODE_PARAM_NAME, ParameterType.STRING, "Shunt voltage control mode", SHUNT_VOLTAGE_CONTROL_MODE_DEFAULT_VALUE.name(), getEnumPossibleValues(ShuntVoltageControlMode.class)),
        new Parameter(MIN_PLAUSIBLE_TARGET_VOLTAGE_PARAM_NAME, ParameterType.DOUBLE, "Min plausible target voltage", LfNetworkParameters.MIN_PLAUSIBLE_TARGET_VOLTAGE_DEFAULT_VALUE),
        new Parameter(MAX_PLAUSIBLE_TARGET_VOLTAGE_PARAM_NAME, ParameterType.DOUBLE, "Max plausible target voltage", LfNetworkParameters.MAX_PLAUSIBLE_TARGET_VOLTAGE_DEFAULT_VALUE),
        new Parameter(MIN_REALISTIC_VOLTAGE_PARAM_NAME, ParameterType.DOUBLE, "Min realistic voltage", NewtonRaphsonParameters.DEFAULT_MIN_REALISTIC_VOLTAGE),
        new Parameter(MAX_REALISTIC_VOLTAGE_PARAM_NAME, ParameterType.DOUBLE, "Max realistic voltage", NewtonRaphsonParameters.DEFAULT_MAX_REALISTIC_VOLTAGE),
        new Parameter(REACTIVE_RANGE_CHECK_MODE_PARAM_NAME, ParameterType.STRING, "Reactive range check mode", LfNetworkParameters.REACTIVE_RANGE_CHECK_MODE_DEFAULT_VALUE.name(), getEnumPossibleValues(ReactiveRangeCheckMode.class)),
        new Parameter(LOW_IMPEDANCE_THRESHOLD_PARAM_NAME, ParameterType.DOUBLE, "Low impedance threshold in per unit", LfNetworkParameters.LOW_IMPEDANCE_THRESHOLD_DEFAULT_VALUE),
        new Parameter(NETWORK_CACHE_ENABLED_PARAM_NAME, ParameterType.BOOLEAN, "Network cache enabled", LfNetworkParameters.CACHE_ENABLED_DEFAULT_VALUE),
        new Parameter(SVC_VOLTAGE_MONITORING_PARAM_NAME, ParameterType.BOOLEAN, "SVC voltage monitoring", SVC_VOLTAGE_MONITORING_DEFAULT_VALUE),
        new Parameter(STATE_VECTOR_SCALING_MODE_PARAM_NAME, ParameterType.STRING, "State vector scaling mode", NewtonRaphsonParameters.DEFAULT_STATE_VECTOR_SCALING_MODE.name(), getEnumPossibleValues(StateVectorScalingMode.class)),
        new Parameter(MAX_SLACK_BUS_COUNT_PARAM_NAME, ParameterType.INTEGER, "Maximum slack buses count", LfNetworkParameters.DEFAULT_MAX_SLACK_BUS_COUNT),
        new Parameter(DEBUG_DIR_PARAM_NAME, ParameterType.STRING, "Directory to dump debug files", LfNetworkParameters.DEBUG_DIR_DEFAULT_VALUE, Collections.emptyList(), ParameterScope.TECHNICAL),
        new Parameter(INCREMENTAL_TRANSFORMER_VOLTAGE_CONTROL_OUTER_LOOP_MAX_TAP_SHIFT_PARAM_NAME, ParameterType.INTEGER, "Incremental transformer voltage control maximum tap shift per outer loop", IncrementalTransformerVoltageControlOuterLoop.DEFAULT_MAX_TAP_SHIFT),
        new Parameter(SECONDARY_VOLTAGE_CONTROL_PARAM_NAME, ParameterType.BOOLEAN, "Secondary voltage control simulation", LfNetworkParameters.SECONDARY_VOLTAGE_CONTROL_DEFAULT_VALUE),
        new Parameter(CONTROLLER_TO_PILOT_POINT_VOLTAGE_SENSI_EPSILON_PARAM_NAME, ParameterType.DOUBLE, "Secondary voltage control, controller unit to pilot point voltage minimal sensitivity", SecondaryVoltageControlOuterLoop.SENSI_V_V_EPS_DEFAULT_VALUE),
        new Parameter(REACTIVE_LIMITS_MAX_SWITCH_PQ_PV_PARAM_NAME, ParameterType.INTEGER, "Reactive limits maximum Pq Pv switch", ReactiveLimitsOuterLoop.MAX_SWITCH_PQ_PV_DEFAULT_VALUE),
        new Parameter(NEWTONRAPHSON_STOPPING_CRITERIA_TYPE_PARAM_NAME, ParameterType.STRING, "Newton raphson stopping criteria type", NEWTONRAPHSON_STOPPING_CRITERIA_TYPE_DEFAULT_VALUE.name(), getEnumPossibleValues(NewtonRaphsonStoppingCriteriaType.class)),
        new Parameter(MAX_ACTIVE_POWER_MISMATCH_PARAM_NAME, ParameterType.DOUBLE, "Maximum active power for per equation stopping criteria", MAX_ACTIVE_POWER_MISMATCH_DEFAULT_VALUE),
        new Parameter(MAX_REACTIVE_POWER_MISMATCH_PARAM_NAME, ParameterType.DOUBLE, "Maximum reactive power for per equation stopping criteria", MAX_REACTIVE_POWER_MISMATCH_DEFAULT_VALUE),
        new Parameter(MAX_VOLTAGE_MISMATCH_PARAM_NAME, ParameterType.DOUBLE, "Maximum voltage for per equation stopping criteria", MAX_VOLTAGE_MISMATCH_DEFAULT_VALUE),
        new Parameter(MAX_ANGLE_MISMATCH_PARAM_NAME, ParameterType.DOUBLE, "Maximum angle for per equation stopping criteria", MAX_ANGLE_MISMATCH_DEFAULT_VALUE),
        new Parameter(MAX_RATIO_MISMATCH_PARAM_NAME, ParameterType.DOUBLE, "Maximum ratio for per equation stopping criteria", MAX_RATIO_MISMATCH_DEFAULT_VALUE),
        new Parameter(MAX_SUSCEPTANCE_MISMATCH_PARAM_NAME, ParameterType.DOUBLE, "Maximum susceptance for per equation stopping criteria", MAX_SUSCEPTANCE_MISMATCH_DEFAULT_VALUE),
        new Parameter(PHASE_SHIFTER_CONTROL_MODE_PARAM_NAME, ParameterType.STRING, "Phase shifter control mode", PHASE_SHIFTER_CONTROL_MODE_DEFAULT_VALUE.name(), getEnumPossibleValues(PhaseShifterControlMode.class)),
        new Parameter(ALWAYS_UPDATE_NETWORK_PARAM_NAME, ParameterType.BOOLEAN, "Update network even if Newton-Raphson algorithm has diverged", NewtonRaphsonParameters.ALWAYS_UPDATE_NETWORK_DEFAULT_VALUE),
        new Parameter(MOST_MESHED_SLACK_BUS_SELECTOR_MAX_NOMINAL_VOLTAGE_PERCENTILE_PARAM_NAME, ParameterType.DOUBLE, "In case of most meshed slack bus selection, the max nominal voltage percentile", MostMeshedSlackBusSelector.MAX_NOMINAL_VOLTAGE_PERCENTILE_DEFAULT_VALUE), new Parameter(REPORTED_FEATURES_PARAM_NAME, ParameterType.STRING_LIST, "List of extra reported features to be added to report", null, getEnumPossibleValues(ReportedFeatures.class)),
        new Parameter(SLACK_BUS_COUNTRY_FILTER_PARAM_NAME, ParameterType.STRING_LIST, "Slac bus selection country filter (no filtering if empty)", new ArrayList<>(LfNetworkParameters.SLACK_BUS_COUNTRY_FILTER_DEFAULT_VALUE)),
        new Parameter(ACTIONABLE_SWITCHES_IDS_PARAM_NAME, ParameterType.STRING_LIST, "List of actionable switches IDs (used with fast restart)", new ArrayList<>(ACTIONABLE_SWITCH_IDS_DEFAULT_VALUE)),
        new Parameter(ASYMMETRICAL_PARAM_NAME, ParameterType.BOOLEAN, "Asymmetrical calculation", LfNetworkParameters.ASYMMETRICAL_DEFAULT_VALUE),
        new Parameter(MIN_NOMINAL_VOLTAGE_TARGET_VOLTAGE_CHECK_PARAM_NAME, ParameterType.DOUBLE, "Min nominal voltage for target voltage check", LfNetworkParameters.MIN_NOMINAL_VOLTAGE_TARGET_VOLTAGE_CHECK_DEFAULT_VALUE),
        new Parameter(REACTIVE_POWER_DISPATCH_MODE_PARAM_NAME, ParameterType.STRING, "Generators reactive power from bus dispatch mode", REACTIVE_POWER_DISPATCH_MODE_DEFAULT_VALUE.name(), getEnumPossibleValues(ReactivePowerDispatchMode.class)),
        new Parameter(OUTER_LOOP_NAMES_PARAM_NAME, ParameterType.STRING_LIST, "Ordered explicit list of outer loop names to run among " + String.join(", ", ExplicitAcOuterLoopConfig.NAMES), OUTER_LOOP_NAMES_DEFAULT_VALUE)
    );

    public enum VoltageInitModeOverride {
        NONE,
        VOLTAGE_MAGNITUDE,
        FULL_VOLTAGE
    }

    public enum TransformerVoltageControlMode {
        WITH_GENERATOR_VOLTAGE_CONTROL,
        AFTER_GENERATOR_VOLTAGE_CONTROL,
        INCREMENTAL_VOLTAGE_CONTROL
    }

    public enum ShuntVoltageControlMode {
        WITH_GENERATOR_VOLTAGE_CONTROL,
        INCREMENTAL_VOLTAGE_CONTROL
    }

    public enum PhaseShifterControlMode {
        CONTINUOUS_WITH_DISCRETISATION,
        INCREMENTAL
    }

    public enum ReportedFeatures {
        NEWTON_RAPHSON_LOAD_FLOW,
        NEWTON_RAPHSON_SECURITY_ANALYSIS,
        NEWTON_RAPHSON_SENSITIVITY_ANALYSIS,
    }

    private SlackBusSelectionMode slackBusSelectionMode = SLACK_BUS_SELECTION_MODE_DEFAULT_VALUE;

    private List<String> slackBusesIds = Collections.emptyList();

    private boolean throwsExceptionInCaseOfSlackDistributionFailure = THROWS_EXCEPTION_IN_CASE_OF_SLACK_DISTRIBUTION_FAILURE_DEFAULT_VALUE;

    private boolean voltageRemoteControl = VOLTAGE_REMOTE_CONTROL_DEFAULT_VALUE;

    private LowImpedanceBranchMode lowImpedanceBranchMode = LOW_IMPEDANCE_BRANCH_MODE_DEFAULT_VALUE;

    public enum LowImpedanceBranchMode {
        REPLACE_BY_ZERO_IMPEDANCE_LINE,
        REPLACE_BY_MIN_IMPEDANCE_LINE
    }

    private boolean loadPowerFactorConstant = LOAD_POWER_FACTOR_CONSTANT_DEFAULT_VALUE;

    private double plausibleActivePowerLimit = LfNetworkParameters.PLAUSIBLE_ACTIVE_POWER_LIMIT_DEFAULT_VALUE;

    private NewtonRaphsonStoppingCriteriaType newtonRaphsonStoppingCriteriaType = NEWTONRAPHSON_STOPPING_CRITERIA_TYPE_DEFAULT_VALUE;

    private double maxActivePowerMismatch = MAX_ACTIVE_POWER_MISMATCH_DEFAULT_VALUE;

    private double maxReactivePowerMismatch = MAX_REACTIVE_POWER_MISMATCH_DEFAULT_VALUE;

    private double maxVoltageMismatch = MAX_VOLTAGE_MISMATCH_DEFAULT_VALUE;

    private double maxAngleMismatch = MAX_ANGLE_MISMATCH_DEFAULT_VALUE;

    private double maxRatioMismatch = MAX_RATIO_MISMATCH_DEFAULT_VALUE;

    private double maxSusceptanceMismatch = MAX_SUSCEPTANCE_MISMATCH_DEFAULT_VALUE;

    private double slackBusPMaxMismatch = SLACK_BUS_P_MAX_MISMATCH_DEFAULT_VALUE;

    private boolean voltagePerReactivePowerControl = VOLTAGE_PER_REACTIVE_POWER_CONTROL_DEFAULT_VALUE;

    private boolean reactivePowerRemoteControl = REACTIVE_POWER_REMOTE_CONTROL_DEFAULT_VALUE;

    private int maxNewtonRaphsonIterations = NewtonRaphsonParameters.DEFAULT_MAX_ITERATIONS;

    private int maxOuterLoopIterations = AcLoadFlowParameters.DEFAULT_MAX_OUTER_LOOP_ITERATIONS;

    private double newtonRaphsonConvEpsPerEq = DefaultNewtonRaphsonStoppingCriteria.DEFAULT_CONV_EPS_PER_EQ;

    private VoltageInitModeOverride voltageInitModeOverride = VOLTAGE_INIT_MODE_OVERRIDE_DEFAULT_VALUE;

    private TransformerVoltageControlMode transformerVoltageControlMode = TRANSFORMER_VOLTAGE_CONTROL_MODE_DEFAULT_VALUE;

    private ShuntVoltageControlMode shuntVoltageControlMode = SHUNT_VOLTAGE_CONTROL_MODE_DEFAULT_VALUE;

    private double minPlausibleTargetVoltage = LfNetworkParameters.MIN_PLAUSIBLE_TARGET_VOLTAGE_DEFAULT_VALUE;

    private double maxPlausibleTargetVoltage = LfNetworkParameters.MAX_PLAUSIBLE_TARGET_VOLTAGE_DEFAULT_VALUE;

    private double minNominalVoltageTargetVoltageCheck = LfNetworkParameters.MIN_NOMINAL_VOLTAGE_TARGET_VOLTAGE_CHECK_DEFAULT_VALUE;

    private double minRealisticVoltage = NewtonRaphsonParameters.DEFAULT_MIN_REALISTIC_VOLTAGE;

    private double maxRealisticVoltage = NewtonRaphsonParameters.DEFAULT_MAX_REALISTIC_VOLTAGE;

    private double lowImpedanceThreshold = LfNetworkParameters.LOW_IMPEDANCE_THRESHOLD_DEFAULT_VALUE;

    public enum ReactiveRangeCheckMode {
        MIN_MAX,
        MAX,
        TARGET_P
    }

    private ReactiveRangeCheckMode reactiveRangeCheckMode = LfNetworkParameters.REACTIVE_RANGE_CHECK_MODE_DEFAULT_VALUE;

    private boolean networkCacheEnabled = LfNetworkParameters.CACHE_ENABLED_DEFAULT_VALUE;

    private boolean svcVoltageMonitoring = SVC_VOLTAGE_MONITORING_DEFAULT_VALUE;

    private StateVectorScalingMode stateVectorScalingMode = NewtonRaphsonParameters.DEFAULT_STATE_VECTOR_SCALING_MODE;

    private int maxSlackBusCount = LfNetworkParameters.DEFAULT_MAX_SLACK_BUS_COUNT;

    private String debugDir = LfNetworkParameters.DEBUG_DIR_DEFAULT_VALUE;

    private int incrementalTransformerVoltageControlOuterLoopMaxTapShift = IncrementalTransformerVoltageControlOuterLoop.DEFAULT_MAX_TAP_SHIFT;

    private boolean secondaryVoltageControl = LfNetworkParameters.SECONDARY_VOLTAGE_CONTROL_DEFAULT_VALUE;

    private double controllerToPilotPointVoltageSensiEpsilon = SecondaryVoltageControlOuterLoop.SENSI_V_V_EPS_DEFAULT_VALUE;

    private int reactiveLimitsMaxPqPvSwitch = ReactiveLimitsOuterLoop.MAX_SWITCH_PQ_PV_DEFAULT_VALUE;

    private PhaseShifterControlMode phaseShifterControlMode = PHASE_SHIFTER_CONTROL_MODE_DEFAULT_VALUE;

    private boolean alwaysUpdateNetwork = NewtonRaphsonParameters.ALWAYS_UPDATE_NETWORK_DEFAULT_VALUE;

    private double mostMeshedSlackBusSelectorMaxNominalVoltagePercentile = MostMeshedSlackBusSelector.MAX_NOMINAL_VOLTAGE_PERCENTILE_DEFAULT_VALUE;

    private Set<ReportedFeatures> reportedFeatures = REPORTED_FEATURES_DEFAULT_VALUE;

    private Set<Country> slackBusCountryFilter = LfNetworkParameters.SLACK_BUS_COUNTRY_FILTER_DEFAULT_VALUE;

    private Set<String> actionableSwitchesIds = ACTIONABLE_SWITCH_IDS_DEFAULT_VALUE;

    private boolean asymmetrical = LfNetworkParameters.ASYMMETRICAL_DEFAULT_VALUE;

    private ReactivePowerDispatchMode reactivePowerDispatchMode = REACTIVE_POWER_DISPATCH_MODE_DEFAULT_VALUE;

    private List<String> outerLoopNames = OUTER_LOOP_NAMES_DEFAULT_VALUE;

    @Override
    public String getName() {
        return "open-load-flow-parameters";
    }

    public SlackBusSelectionMode getSlackBusSelectionMode() {
        return slackBusSelectionMode;
    }

    public OpenLoadFlowParameters setSlackBusSelectionMode(SlackBusSelectionMode slackBusSelectionMode) {
        this.slackBusSelectionMode = Objects.requireNonNull(slackBusSelectionMode);
        return this;
    }

    public List<String> getSlackBusesIds() {
        return slackBusesIds;
    }

    public OpenLoadFlowParameters setSlackBusesIds(List<String> slackBusesIds) {
        this.slackBusesIds = Objects.requireNonNull(slackBusesIds);
        return this;
    }

    public OpenLoadFlowParameters setSlackBusId(String slackBusId) {
        this.slackBusesIds = List.of(Objects.requireNonNull(slackBusId));
        return this;
    }

    public boolean isThrowsExceptionInCaseOfSlackDistributionFailure() {
        return throwsExceptionInCaseOfSlackDistributionFailure;
    }

    public OpenLoadFlowParameters setThrowsExceptionInCaseOfSlackDistributionFailure(boolean throwsExceptionInCaseOfSlackDistributionFailure) {
        this.throwsExceptionInCaseOfSlackDistributionFailure = throwsExceptionInCaseOfSlackDistributionFailure;
        return this;
    }

    public boolean hasVoltageRemoteControl() {
        return voltageRemoteControl;
    }

    public OpenLoadFlowParameters setVoltageRemoteControl(boolean voltageRemoteControl) {
        this.voltageRemoteControl = voltageRemoteControl;
        return this;
    }

    public LowImpedanceBranchMode getLowImpedanceBranchMode() {
        return lowImpedanceBranchMode;
    }

    public OpenLoadFlowParameters setLowImpedanceBranchMode(LowImpedanceBranchMode lowImpedanceBranchMode) {
        this.lowImpedanceBranchMode = Objects.requireNonNull(lowImpedanceBranchMode);
        return this;
    }

    public boolean isLoadPowerFactorConstant() {
        return loadPowerFactorConstant;
    }

    public OpenLoadFlowParameters setLoadPowerFactorConstant(boolean loadPowerFactorConstant) {
        this.loadPowerFactorConstant = loadPowerFactorConstant;
        return this;
    }

    public double getPlausibleActivePowerLimit() {
        return plausibleActivePowerLimit;
    }

    public OpenLoadFlowParameters setPlausibleActivePowerLimit(double plausibleActivePowerLimit) {
        if (plausibleActivePowerLimit <= 0) {
            throw new IllegalArgumentException("Invalid plausible active power limit: " + plausibleActivePowerLimit);
        }
        this.plausibleActivePowerLimit = plausibleActivePowerLimit;
        return this;
    }

    public double getSlackBusPMaxMismatch() {
        return slackBusPMaxMismatch;
    }

    public OpenLoadFlowParameters setSlackBusPMaxMismatch(double pSlackBusPMaxMismatch) {
        this.slackBusPMaxMismatch = pSlackBusPMaxMismatch;
        return this;
    }

    public boolean isVoltagePerReactivePowerControl() {
        return voltagePerReactivePowerControl;
    }

    public OpenLoadFlowParameters setVoltagePerReactivePowerControl(boolean voltagePerReactivePowerControl) {
        this.voltagePerReactivePowerControl = voltagePerReactivePowerControl;
        return this;
    }

    public boolean hasReactivePowerRemoteControl() {
        return reactivePowerRemoteControl;
    }

    public OpenLoadFlowParameters setReactivePowerRemoteControl(boolean reactivePowerRemoteControl) {
        this.reactivePowerRemoteControl = reactivePowerRemoteControl;
        return this;
    }

    public int getMaxNewtonRaphsonIterations() {
        return maxNewtonRaphsonIterations;
    }

    public OpenLoadFlowParameters setMaxNewtonRaphsonIterations(int maxNewtonRaphsonIterations) {
        this.maxNewtonRaphsonIterations = NewtonRaphsonParameters.checkMaxIteration(maxNewtonRaphsonIterations);
        return this;
    }

    public int getMaxOuterLoopIterations() {
        return maxOuterLoopIterations;
    }

    public OpenLoadFlowParameters setMaxOuterLoopIterations(int maxOuterLoopIterations) {
        this.maxOuterLoopIterations = NewtonRaphsonParameters.checkMaxIteration(maxOuterLoopIterations);
        return this;
    }

    public double getNewtonRaphsonConvEpsPerEq() {
        return newtonRaphsonConvEpsPerEq;
    }

    public OpenLoadFlowParameters setNewtonRaphsonConvEpsPerEq(double newtonRaphsonConvEpsPerEq) {
        this.newtonRaphsonConvEpsPerEq = newtonRaphsonConvEpsPerEq;
        return this;
    }

    public NewtonRaphsonStoppingCriteriaType getNewtonRaphsonStoppingCriteriaType() {
        return newtonRaphsonStoppingCriteriaType;
    }

    public OpenLoadFlowParameters setNewtonRaphsonStoppingCriteriaType(NewtonRaphsonStoppingCriteriaType newtonRaphsonStoppingCriteriaType) {
        this.newtonRaphsonStoppingCriteriaType = Objects.requireNonNull(newtonRaphsonStoppingCriteriaType);
        return this;
    }

    public double getMaxActivePowerMismatch() {
        return maxActivePowerMismatch;
    }

    public OpenLoadFlowParameters setMaxActivePowerMismatch(double maxActivePowerMismatch) {
        if (maxActivePowerMismatch <= 0) {
            throw new PowsyblException("maxActivePowerMismatch must be greater to 0");
        }
        this.maxActivePowerMismatch = maxActivePowerMismatch;
        return this;
    }

    public double getMaxReactivePowerMismatch() {
        return maxReactivePowerMismatch;
    }

    public OpenLoadFlowParameters setMaxReactivePowerMismatch(double maxReactivePowerMismatch) {
        if (maxReactivePowerMismatch <= 0) {
            throw new PowsyblException("maxReactivePowerMismatch must be greater to 0");
        }
        this.maxReactivePowerMismatch = maxReactivePowerMismatch;
        return this;
    }

    public double getMaxVoltageMismatch() {
        return maxVoltageMismatch;
    }

    public OpenLoadFlowParameters setMaxVoltageMismatch(double maxVoltageMismatch) {
        if (maxVoltageMismatch <= 0) {
            throw new PowsyblException("maxVoltageMismatch must be greater to 0");
        }
        this.maxVoltageMismatch = maxVoltageMismatch;
        return this;
    }

    public double getMaxAngleMismatch() {
        return maxAngleMismatch;
    }

    public OpenLoadFlowParameters setMaxAngleMismatch(double maxAngleMismatch) {
        if (maxAngleMismatch <= 0) {
            throw new PowsyblException("maxAngleMismatch must be greater to 0");
        }
        this.maxAngleMismatch = maxAngleMismatch;
        return this;
    }

    public double getMaxRatioMismatch() {
        return maxRatioMismatch;
    }

    public OpenLoadFlowParameters setMaxRatioMismatch(double maxRatioMismatch) {
        if (maxRatioMismatch <= 0) {
            throw new PowsyblException("maxRatioMismatch must be greater to 0");
        }
        this.maxRatioMismatch = maxRatioMismatch;
        return this;
    }

    public double getMaxSusceptanceMismatch() {
        return maxSusceptanceMismatch;
    }

    public OpenLoadFlowParameters setMaxSusceptanceMismatch(double maxSusceptanceMismatch) {
        if (maxSusceptanceMismatch <= 0) {
            throw new PowsyblException("maxSusceptanceMismatch must be greater to 0");
        }
        this.maxSusceptanceMismatch = maxSusceptanceMismatch;
        return this;
    }

    public VoltageInitModeOverride getVoltageInitModeOverride() {
        return voltageInitModeOverride;
    }

    public OpenLoadFlowParameters setVoltageInitModeOverride(VoltageInitModeOverride voltageInitModeOverride) {
        this.voltageInitModeOverride = Objects.requireNonNull(voltageInitModeOverride);
        return this;
    }

    public TransformerVoltageControlMode getTransformerVoltageControlMode() {
        return transformerVoltageControlMode;
    }

    public OpenLoadFlowParameters setTransformerVoltageControlMode(TransformerVoltageControlMode transformerVoltageControlMode) {
        this.transformerVoltageControlMode = Objects.requireNonNull(transformerVoltageControlMode);
        return this;
    }

    public ShuntVoltageControlMode getShuntVoltageControlMode() {
        return shuntVoltageControlMode;
    }

    public OpenLoadFlowParameters setShuntVoltageControlMode(ShuntVoltageControlMode shuntVoltageControlMode) {
        this.shuntVoltageControlMode = Objects.requireNonNull(shuntVoltageControlMode);
        return this;
    }

    public double getMinPlausibleTargetVoltage() {
        return minPlausibleTargetVoltage;
    }

    public OpenLoadFlowParameters setMinPlausibleTargetVoltage(double minPlausibleTargetVoltage) {
        this.minPlausibleTargetVoltage = minPlausibleTargetVoltage;
        return this;
    }

    public double getMaxPlausibleTargetVoltage() {
        return maxPlausibleTargetVoltage;
    }

    public OpenLoadFlowParameters setMaxPlausibleTargetVoltage(double maxPlausibleTargetVoltage) {
        this.maxPlausibleTargetVoltage = maxPlausibleTargetVoltage;
        return this;
    }

    public double getMinNominalVoltageTargetVoltageCheck() {
        return minNominalVoltageTargetVoltageCheck;
    }

    public OpenLoadFlowParameters setMinNominalVoltageTargetVoltageCheck(double minNominalVoltageTargetVoltageCheck) {
        this.minNominalVoltageTargetVoltageCheck = minNominalVoltageTargetVoltageCheck;
        return this;
    }

    public double getMinRealisticVoltage() {
        return minRealisticVoltage;
    }

    public OpenLoadFlowParameters setMinRealisticVoltage(double minRealisticVoltage) {
        this.minRealisticVoltage = minRealisticVoltage;
        return this;
    }

    public double getMaxRealisticVoltage() {
        return maxRealisticVoltage;
    }

    public OpenLoadFlowParameters setMaxRealisticVoltage(double maxRealisticVoltage) {
        this.maxRealisticVoltage = maxRealisticVoltage;
        return this;
    }

    public ReactiveRangeCheckMode getReactiveRangeCheckMode() {
        return reactiveRangeCheckMode;
    }

    public OpenLoadFlowParameters setReactiveRangeCheckMode(ReactiveRangeCheckMode reactiveRangeCheckMode) {
        this.reactiveRangeCheckMode = reactiveRangeCheckMode;
        return this;
    }

    public double getLowImpedanceThreshold() {
        return lowImpedanceThreshold;
    }

    public OpenLoadFlowParameters setLowImpedanceThreshold(double lowImpedanceThreshold) {
        if (lowImpedanceThreshold <= 0) {
            throw new PowsyblException("lowImpedanceThreshold must be greater than 0");
        }
        this.lowImpedanceThreshold = lowImpedanceThreshold;
        return this;
    }

    public boolean isNetworkCacheEnabled() {
        return networkCacheEnabled;
    }

    public OpenLoadFlowParameters setNetworkCacheEnabled(boolean networkCacheEnabled) {
        this.networkCacheEnabled = networkCacheEnabled;
        return this;
    }

    public boolean isSvcVoltageMonitoring() {
        return svcVoltageMonitoring;
    }

    public OpenLoadFlowParameters setSvcVoltageMonitoring(boolean svcVoltageMonitoring) {
        this.svcVoltageMonitoring = svcVoltageMonitoring;
        return this;
    }

    public StateVectorScalingMode getStateVectorScalingMode() {
        return stateVectorScalingMode;
    }

    public OpenLoadFlowParameters setStateVectorScalingMode(StateVectorScalingMode stateVectorScalingMode) {
        this.stateVectorScalingMode = Objects.requireNonNull(stateVectorScalingMode);
        return this;
    }

    public int getMaxSlackBusCount() {
        return maxSlackBusCount;
    }

    public OpenLoadFlowParameters setMaxSlackBusCount(int maxSlackBusCount) {
        this.maxSlackBusCount = LfNetworkParameters.checkMaxSlackBusCount(maxSlackBusCount);
        return this;
    }

    public boolean isSecondaryVoltageControl() {
        return secondaryVoltageControl;
    }

    public OpenLoadFlowParameters setSecondaryVoltageControl(boolean secondaryVoltageControl) {
        this.secondaryVoltageControl = secondaryVoltageControl;
        return this;
    }

    public double getControllerToPilotPointVoltageSensiEpsilon() {
        return controllerToPilotPointVoltageSensiEpsilon;
    }

    public OpenLoadFlowParameters setControllerToPilotPointVoltageSensiEpsilon(double controllerToPilotPointVoltageSensiEpsilon) {
        this.controllerToPilotPointVoltageSensiEpsilon = controllerToPilotPointVoltageSensiEpsilon;
        return this;
    }

    public String getDebugDir() {
        return debugDir;
    }

    public OpenLoadFlowParameters setDebugDir(String debugDir) {
        this.debugDir = debugDir;
        return this;
    }

    public int getIncrementalTransformerVoltageControlOuterLoopMaxTapShift() {
        return incrementalTransformerVoltageControlOuterLoopMaxTapShift;
    }

    public OpenLoadFlowParameters setIncrementalTransformerVoltageControlOuterLoopMaxTapShift(int incrementalTransformerVoltageControlOuterLoopMaxTapShift) {
        if (incrementalTransformerVoltageControlOuterLoopMaxTapShift < 1) {
            throw new IllegalArgumentException("Invalid max tap shift value: " + incrementalTransformerVoltageControlOuterLoopMaxTapShift);
        }
        this.incrementalTransformerVoltageControlOuterLoopMaxTapShift = incrementalTransformerVoltageControlOuterLoopMaxTapShift;
        return this;
    }

    public int getReactiveLimitsMaxPqPvSwitch() {
        return reactiveLimitsMaxPqPvSwitch;
    }

    public OpenLoadFlowParameters setReactiveLimitsMaxPqPvSwitch(int reactiveLimitsMaxPqPvSwitch) {
        if (reactiveLimitsMaxPqPvSwitch < 0) {
            throw new IllegalArgumentException("Invalid max Pq Pv switch value: " + reactiveLimitsMaxPqPvSwitch);
        }
        this.reactiveLimitsMaxPqPvSwitch = reactiveLimitsMaxPqPvSwitch;
        return this;
    }

    public PhaseShifterControlMode getPhaseShifterControlMode() {
        return phaseShifterControlMode;
    }

    public OpenLoadFlowParameters setPhaseShifterControlMode(PhaseShifterControlMode phaseShifterControlMode) {
        this.phaseShifterControlMode = Objects.requireNonNull(phaseShifterControlMode);
        return this;
    }

    public boolean isAlwaysUpdateNetwork() {
        return alwaysUpdateNetwork;
    }

    public OpenLoadFlowParameters setAlwaysUpdateNetwork(boolean alwaysUpdateNetwork) {
        this.alwaysUpdateNetwork = alwaysUpdateNetwork;
        return this;
    }

    public double getMostMeshedSlackBusSelectorMaxNominalVoltagePercentile() {
        return mostMeshedSlackBusSelectorMaxNominalVoltagePercentile;
    }

    private static void checkPercent(double percent) {
        if (percent < 0 || percent > 100) {
            throw new IllegalArgumentException("Invalid percent value: " + percent);
        }
    }

    public OpenLoadFlowParameters setMostMeshedSlackBusSelectorMaxNominalVoltagePercentile(double mostMeshedSlackBusSelectorMaxNominalVoltagePercentile) {
        checkPercent(mostMeshedSlackBusSelectorMaxNominalVoltagePercentile);
        this.mostMeshedSlackBusSelectorMaxNominalVoltagePercentile = mostMeshedSlackBusSelectorMaxNominalVoltagePercentile;
        return this;
    }

    public Set<ReportedFeatures> getReportedFeatures() {
        return reportedFeatures;
    }

    public OpenLoadFlowParameters setReportedFeatures(Set<ReportedFeatures> reportedFeatures) {
        this.reportedFeatures = Objects.requireNonNull(reportedFeatures);
        return this;
    }

    public Set<Country> getSlackBusCountryFilter() {
        return slackBusCountryFilter;
    }

    public OpenLoadFlowParameters setSlackBusCountryFilter(Set<Country> slackBusCountryFilter) {
        this.slackBusCountryFilter = Objects.requireNonNull(slackBusCountryFilter);
        return this;
    }

    public Set<String> getActionableSwitchesIds() {
        return actionableSwitchesIds;
    }

    public OpenLoadFlowParameters setActionableSwitchesIds(Set<String> actionableSwitchesIds) {
        this.actionableSwitchesIds = Objects.requireNonNull(actionableSwitchesIds);
        return this;
    }

    public boolean isAsymmetrical() {
        return asymmetrical;
    }

    public OpenLoadFlowParameters setAsymmetrical(boolean asymmetrical) {
        this.asymmetrical = asymmetrical;
        return this;
    }

    public ReactivePowerDispatchMode getReactivePowerDispatchMode() {
        return reactivePowerDispatchMode;
    }

    public OpenLoadFlowParameters setReactivePowerDispatchMode(ReactivePowerDispatchMode reactivePowerDispatchMode) {
        this.reactivePowerDispatchMode = Objects.requireNonNull(reactivePowerDispatchMode);
        return this;
    }

    public List<String> getOuterLoopNames() {
        return outerLoopNames;
    }

    public OpenLoadFlowParameters setOuterLoopNames(List<String> outerLoopNames) {
        this.outerLoopNames = outerLoopNames;
        return this;
    }

    public static OpenLoadFlowParameters load() {
        return load(PlatformConfig.defaultConfig());
    }

    public static OpenLoadFlowParameters load(PlatformConfig platformConfig) {
        OpenLoadFlowParameters parameters = new OpenLoadFlowParameters();
        platformConfig.getOptionalModuleConfig("open-loadflow-default-parameters")
            .ifPresent(config -> parameters
                .setSlackBusSelectionMode(config.getEnumProperty(SLACK_BUS_SELECTION_MODE_PARAM_NAME, SlackBusSelectionMode.class, SLACK_BUS_SELECTION_MODE_DEFAULT_VALUE))
                .setSlackBusesIds(config.getStringListProperty(SLACK_BUSES_IDS_PARAM_NAME, Collections.emptyList()))
                .setLowImpedanceBranchMode(config.getEnumProperty(LOW_IMPEDANCE_BRANCH_MODE_PARAM_NAME, LowImpedanceBranchMode.class, LOW_IMPEDANCE_BRANCH_MODE_DEFAULT_VALUE))
                .setVoltageRemoteControl(config.getBooleanProperty(VOLTAGE_REMOTE_CONTROL_PARAM_NAME, VOLTAGE_REMOTE_CONTROL_DEFAULT_VALUE))
                .setThrowsExceptionInCaseOfSlackDistributionFailure(
                        config.getBooleanProperty(THROWS_EXCEPTION_IN_CASE_OF_SLACK_DISTRIBUTION_FAILURE_PARAM_NAME, THROWS_EXCEPTION_IN_CASE_OF_SLACK_DISTRIBUTION_FAILURE_DEFAULT_VALUE)
                )
                .setLoadPowerFactorConstant(config.getBooleanProperty(LOAD_POWER_FACTOR_CONSTANT_PARAM_NAME, LOAD_POWER_FACTOR_CONSTANT_DEFAULT_VALUE))
                .setPlausibleActivePowerLimit(config.getDoubleProperty(PLAUSIBLE_ACTIVE_POWER_LIMIT_PARAM_NAME, LfNetworkParameters.PLAUSIBLE_ACTIVE_POWER_LIMIT_DEFAULT_VALUE))
                .setNewtonRaphsonStoppingCriteriaType(config.getEnumProperty(NEWTONRAPHSON_STOPPING_CRITERIA_TYPE_PARAM_NAME, NewtonRaphsonStoppingCriteriaType.class, NEWTONRAPHSON_STOPPING_CRITERIA_TYPE_DEFAULT_VALUE))
                .setMaxActivePowerMismatch(config.getDoubleProperty(MAX_ACTIVE_POWER_MISMATCH_PARAM_NAME, MAX_ACTIVE_POWER_MISMATCH_DEFAULT_VALUE))
                .setMaxReactivePowerMismatch(config.getDoubleProperty(MAX_REACTIVE_POWER_MISMATCH_PARAM_NAME, MAX_REACTIVE_POWER_MISMATCH_DEFAULT_VALUE))
                .setMaxVoltageMismatch(config.getDoubleProperty(MAX_VOLTAGE_MISMATCH_PARAM_NAME, MAX_VOLTAGE_MISMATCH_DEFAULT_VALUE))
                .setMaxAngleMismatch(config.getDoubleProperty(MAX_ANGLE_MISMATCH_PARAM_NAME, MAX_ANGLE_MISMATCH_DEFAULT_VALUE))
                .setMaxRatioMismatch(config.getDoubleProperty(MAX_RATIO_MISMATCH_PARAM_NAME, MAX_RATIO_MISMATCH_DEFAULT_VALUE))
                .setMaxSusceptanceMismatch(config.getDoubleProperty(MAX_SUSCEPTANCE_MISMATCH_PARAM_NAME, MAX_SUSCEPTANCE_MISMATCH_DEFAULT_VALUE))
                .setSlackBusPMaxMismatch(config.getDoubleProperty(SLACK_BUS_P_MAX_MISMATCH_PARAM_NAME, SLACK_BUS_P_MAX_MISMATCH_DEFAULT_VALUE))
                .setVoltagePerReactivePowerControl(config.getBooleanProperty(VOLTAGE_PER_REACTIVE_POWER_CONTROL_PARAM_NAME, VOLTAGE_PER_REACTIVE_POWER_CONTROL_DEFAULT_VALUE))
                .setReactivePowerRemoteControl(config.getBooleanProperty(REACTIVE_POWER_REMOTE_CONTROL_PARAM_NAME, REACTIVE_POWER_REMOTE_CONTROL_DEFAULT_VALUE))
                .setMaxNewtonRaphsonIterations(config.getIntProperty(MAX_NEWTON_RAPHSON_ITERATIONS_PARAM_NAME, NewtonRaphsonParameters.DEFAULT_MAX_ITERATIONS))
                .setMaxOuterLoopIterations(config.getIntProperty(MAX_OUTER_LOOP_ITERATIONS_PARAM_NAME, AcLoadFlowParameters.DEFAULT_MAX_OUTER_LOOP_ITERATIONS))
                .setNewtonRaphsonConvEpsPerEq(config.getDoubleProperty(NEWTON_RAPHSON_CONV_EPS_PER_EQ_PARAM_NAME, DefaultNewtonRaphsonStoppingCriteria.DEFAULT_CONV_EPS_PER_EQ))
                .setVoltageInitModeOverride(config.getEnumProperty(VOLTAGE_INIT_MODE_OVERRIDE_PARAM_NAME, VoltageInitModeOverride.class, VOLTAGE_INIT_MODE_OVERRIDE_DEFAULT_VALUE))
                .setTransformerVoltageControlMode(config.getEnumProperty(TRANSFORMER_VOLTAGE_CONTROL_MODE_PARAM_NAME, TransformerVoltageControlMode.class, TRANSFORMER_VOLTAGE_CONTROL_MODE_DEFAULT_VALUE))
                .setShuntVoltageControlMode(config.getEnumProperty(SHUNT_VOLTAGE_CONTROL_MODE_PARAM_NAME, ShuntVoltageControlMode.class, SHUNT_VOLTAGE_CONTROL_MODE_DEFAULT_VALUE))
                .setMinPlausibleTargetVoltage(config.getDoubleProperty(MIN_PLAUSIBLE_TARGET_VOLTAGE_PARAM_NAME, LfNetworkParameters.MIN_PLAUSIBLE_TARGET_VOLTAGE_DEFAULT_VALUE))
                .setMaxPlausibleTargetVoltage(config.getDoubleProperty(MAX_PLAUSIBLE_TARGET_VOLTAGE_PARAM_NAME, LfNetworkParameters.MAX_PLAUSIBLE_TARGET_VOLTAGE_DEFAULT_VALUE))
                .setMinRealisticVoltage(config.getDoubleProperty(MIN_REALISTIC_VOLTAGE_PARAM_NAME, NewtonRaphsonParameters.DEFAULT_MIN_REALISTIC_VOLTAGE))
                .setMaxRealisticVoltage(config.getDoubleProperty(MAX_REALISTIC_VOLTAGE_PARAM_NAME, NewtonRaphsonParameters.DEFAULT_MAX_REALISTIC_VOLTAGE))
                .setReactiveRangeCheckMode(config.getEnumProperty(REACTIVE_RANGE_CHECK_MODE_PARAM_NAME, ReactiveRangeCheckMode.class, LfNetworkParameters.REACTIVE_RANGE_CHECK_MODE_DEFAULT_VALUE))
                .setLowImpedanceThreshold(config.getDoubleProperty(LOW_IMPEDANCE_THRESHOLD_PARAM_NAME, LfNetworkParameters.LOW_IMPEDANCE_THRESHOLD_DEFAULT_VALUE))
                .setNetworkCacheEnabled(config.getBooleanProperty(NETWORK_CACHE_ENABLED_PARAM_NAME, LfNetworkParameters.CACHE_ENABLED_DEFAULT_VALUE))
                .setSvcVoltageMonitoring(config.getBooleanProperty(SVC_VOLTAGE_MONITORING_PARAM_NAME, SVC_VOLTAGE_MONITORING_DEFAULT_VALUE))
                .setNetworkCacheEnabled(config.getBooleanProperty(NETWORK_CACHE_ENABLED_PARAM_NAME, LfNetworkParameters.CACHE_ENABLED_DEFAULT_VALUE))
                .setStateVectorScalingMode(config.getEnumProperty(STATE_VECTOR_SCALING_MODE_PARAM_NAME, StateVectorScalingMode.class, NewtonRaphsonParameters.DEFAULT_STATE_VECTOR_SCALING_MODE))
                .setMaxSlackBusCount(config.getIntProperty(MAX_SLACK_BUS_COUNT_PARAM_NAME, LfNetworkParameters.DEFAULT_MAX_SLACK_BUS_COUNT))
                .setDebugDir(config.getStringProperty(DEBUG_DIR_PARAM_NAME, LfNetworkParameters.DEBUG_DIR_DEFAULT_VALUE))
                .setIncrementalTransformerVoltageControlOuterLoopMaxTapShift(config.getIntProperty(INCREMENTAL_TRANSFORMER_VOLTAGE_CONTROL_OUTER_LOOP_MAX_TAP_SHIFT_PARAM_NAME, IncrementalTransformerVoltageControlOuterLoop.DEFAULT_MAX_TAP_SHIFT))
                .setSecondaryVoltageControl(config.getBooleanProperty(SECONDARY_VOLTAGE_CONTROL_PARAM_NAME, LfNetworkParameters.SECONDARY_VOLTAGE_CONTROL_DEFAULT_VALUE))
                .setControllerToPilotPointVoltageSensiEpsilon(config.getDoubleProperty(CONTROLLER_TO_PILOT_POINT_VOLTAGE_SENSI_EPSILON_PARAM_NAME, SecondaryVoltageControlOuterLoop.SENSI_V_V_EPS_DEFAULT_VALUE))
                .setReactiveLimitsMaxPqPvSwitch(config.getIntProperty(REACTIVE_LIMITS_MAX_SWITCH_PQ_PV_PARAM_NAME, ReactiveLimitsOuterLoop.MAX_SWITCH_PQ_PV_DEFAULT_VALUE))
                .setPhaseShifterControlMode(config.getEnumProperty(PHASE_SHIFTER_CONTROL_MODE_PARAM_NAME, PhaseShifterControlMode.class, PHASE_SHIFTER_CONTROL_MODE_DEFAULT_VALUE))
                .setAlwaysUpdateNetwork(config.getBooleanProperty(ALWAYS_UPDATE_NETWORK_PARAM_NAME, NewtonRaphsonParameters.ALWAYS_UPDATE_NETWORK_DEFAULT_VALUE))
                .setMostMeshedSlackBusSelectorMaxNominalVoltagePercentile(config.getDoubleProperty(MOST_MESHED_SLACK_BUS_SELECTOR_MAX_NOMINAL_VOLTAGE_PERCENTILE_PARAM_NAME, MostMeshedSlackBusSelector.MAX_NOMINAL_VOLTAGE_PERCENTILE_DEFAULT_VALUE))
                .setReportedFeatures(config.getEnumSetProperty(REPORTED_FEATURES_PARAM_NAME, ReportedFeatures.class, REPORTED_FEATURES_DEFAULT_VALUE))
                .setSlackBusCountryFilter(config.getEnumSetProperty(SLACK_BUS_COUNTRY_FILTER_PARAM_NAME, Country.class, LfNetworkParameters.SLACK_BUS_COUNTRY_FILTER_DEFAULT_VALUE))
                .setActionableSwitchesIds(new HashSet<>(config.getStringListProperty(ACTIONABLE_SWITCHES_IDS_PARAM_NAME, new ArrayList<>(ACTIONABLE_SWITCH_IDS_DEFAULT_VALUE))))
                .setAsymmetrical(config.getBooleanProperty(ASYMMETRICAL_PARAM_NAME, LfNetworkParameters.ASYMMETRICAL_DEFAULT_VALUE))
                .setMinNominalVoltageTargetVoltageCheck(config.getDoubleProperty(MIN_NOMINAL_VOLTAGE_TARGET_VOLTAGE_CHECK_PARAM_NAME, LfNetworkParameters.MIN_NOMINAL_VOLTAGE_TARGET_VOLTAGE_CHECK_DEFAULT_VALUE))
                .setReactivePowerDispatchMode(config.getEnumProperty(REACTIVE_POWER_DISPATCH_MODE_PARAM_NAME, ReactivePowerDispatchMode.class, REACTIVE_POWER_DISPATCH_MODE_DEFAULT_VALUE))
                .setOuterLoopNames(config.getStringListProperty(OUTER_LOOP_NAMES_PARAM_NAME, OUTER_LOOP_NAMES_DEFAULT_VALUE))
            );
        return parameters;
    }

    public static OpenLoadFlowParameters load(Map<String, String> properties) {
        return new OpenLoadFlowParameters().update(properties);
    }

    private static List<String> parseStringListProp(String prop) {
        return Arrays.asList(prop.split("[:,]"));
    }

    public OpenLoadFlowParameters update(Map<String, String> properties) {
        Optional.ofNullable(properties.get(SLACK_BUS_SELECTION_MODE_PARAM_NAME))
                .ifPresent(prop -> this.setSlackBusSelectionMode(SlackBusSelectionMode.valueOf(prop)));
        Optional.ofNullable(properties.get(SLACK_BUSES_IDS_PARAM_NAME))
                .ifPresent(prop -> this.setSlackBusesIds(parseStringListProp(prop)));
        Optional.ofNullable(properties.get(LOW_IMPEDANCE_BRANCH_MODE_PARAM_NAME))
                .ifPresent(prop -> this.setLowImpedanceBranchMode(LowImpedanceBranchMode.valueOf(prop)));
        Optional.ofNullable(properties.get(VOLTAGE_REMOTE_CONTROL_PARAM_NAME))
                .ifPresent(prop -> this.setVoltageRemoteControl(Boolean.parseBoolean(prop)));
        Optional.ofNullable(properties.get(THROWS_EXCEPTION_IN_CASE_OF_SLACK_DISTRIBUTION_FAILURE_PARAM_NAME))
                .ifPresent(prop -> this.setThrowsExceptionInCaseOfSlackDistributionFailure(Boolean.parseBoolean(prop)));
        Optional.ofNullable(properties.get(LOAD_POWER_FACTOR_CONSTANT_PARAM_NAME))
                .ifPresent(prop -> this.setLoadPowerFactorConstant(Boolean.parseBoolean(prop)));
        Optional.ofNullable(properties.get(PLAUSIBLE_ACTIVE_POWER_LIMIT_PARAM_NAME))
                .ifPresent(prop -> this.setPlausibleActivePowerLimit(Double.parseDouble(prop)));
        Optional.ofNullable(properties.get(NEWTONRAPHSON_STOPPING_CRITERIA_TYPE_PARAM_NAME))
                .ifPresent(prop -> this.setNewtonRaphsonStoppingCriteriaType(NewtonRaphsonStoppingCriteriaType.valueOf(prop)));
        Optional.ofNullable(properties.get(MAX_ACTIVE_POWER_MISMATCH_PARAM_NAME))
                .ifPresent(prop -> this.setMaxActivePowerMismatch(Double.parseDouble(prop)));
        Optional.ofNullable(properties.get(MAX_REACTIVE_POWER_MISMATCH_PARAM_NAME))
                .ifPresent(prop -> this.setMaxReactivePowerMismatch(Double.parseDouble(prop)));
        Optional.ofNullable(properties.get(MAX_VOLTAGE_MISMATCH_PARAM_NAME))
                .ifPresent(prop -> this.setMaxVoltageMismatch(Double.parseDouble(prop)));
        Optional.ofNullable(properties.get(MAX_ANGLE_MISMATCH_PARAM_NAME))
                .ifPresent(prop -> this.setMaxAngleMismatch(Double.parseDouble(prop)));
        Optional.ofNullable(properties.get(MAX_RATIO_MISMATCH_PARAM_NAME))
                .ifPresent(prop -> this.setMaxRatioMismatch(Double.parseDouble(prop)));
        Optional.ofNullable(properties.get(MAX_SUSCEPTANCE_MISMATCH_PARAM_NAME))
                .ifPresent(prop -> this.setMaxSusceptanceMismatch(Double.parseDouble(prop)));
        Optional.ofNullable(properties.get(SLACK_BUS_P_MAX_MISMATCH_PARAM_NAME))
                .ifPresent(prop -> this.setSlackBusPMaxMismatch(Double.parseDouble(prop)));
        Optional.ofNullable(properties.get(VOLTAGE_PER_REACTIVE_POWER_CONTROL_PARAM_NAME))
                .ifPresent(prop -> this.setVoltagePerReactivePowerControl(Boolean.parseBoolean(prop)));
        Optional.ofNullable(properties.get(REACTIVE_POWER_REMOTE_CONTROL_PARAM_NAME))
                .ifPresent(prop -> this.setReactivePowerRemoteControl(Boolean.parseBoolean(prop)));
        Optional.ofNullable(properties.get(MAX_NEWTON_RAPHSON_ITERATIONS_PARAM_NAME))
                .ifPresent(prop -> this.setMaxNewtonRaphsonIterations(Integer.parseInt(prop)));
        Optional.ofNullable(properties.get(MAX_OUTER_LOOP_ITERATIONS_PARAM_NAME))
                .ifPresent(prop -> this.setMaxOuterLoopIterations(Integer.parseInt(prop)));
        Optional.ofNullable(properties.get(NEWTON_RAPHSON_CONV_EPS_PER_EQ_PARAM_NAME))
                .ifPresent(prop -> this.setNewtonRaphsonConvEpsPerEq(Double.parseDouble(prop)));
        Optional.ofNullable(properties.get(VOLTAGE_INIT_MODE_OVERRIDE_PARAM_NAME))
                .ifPresent(prop -> this.setVoltageInitModeOverride(VoltageInitModeOverride.valueOf(prop)));
        Optional.ofNullable(properties.get(TRANSFORMER_VOLTAGE_CONTROL_MODE_PARAM_NAME))
                .ifPresent(prop -> this.setTransformerVoltageControlMode(TransformerVoltageControlMode.valueOf(prop)));
        Optional.ofNullable(properties.get(SHUNT_VOLTAGE_CONTROL_MODE_PARAM_NAME))
                .ifPresent(prop -> this.setShuntVoltageControlMode(ShuntVoltageControlMode.valueOf(prop)));
        Optional.ofNullable(properties.get(MIN_PLAUSIBLE_TARGET_VOLTAGE_PARAM_NAME))
                .ifPresent(prop -> this.setMinPlausibleTargetVoltage(Double.parseDouble(prop)));
        Optional.ofNullable(properties.get(MAX_PLAUSIBLE_TARGET_VOLTAGE_PARAM_NAME))
                .ifPresent(prop -> this.setMaxPlausibleTargetVoltage(Double.parseDouble(prop)));
        Optional.ofNullable(properties.get(MIN_REALISTIC_VOLTAGE_PARAM_NAME))
                .ifPresent(prop -> this.setMinRealisticVoltage(Double.parseDouble(prop)));
        Optional.ofNullable(properties.get(MAX_REALISTIC_VOLTAGE_PARAM_NAME))
                .ifPresent(prop -> this.setMaxRealisticVoltage(Double.parseDouble(prop)));
        Optional.ofNullable(properties.get(REACTIVE_RANGE_CHECK_MODE_PARAM_NAME))
                .ifPresent(prop -> this.setReactiveRangeCheckMode(ReactiveRangeCheckMode.valueOf(prop)));
        Optional.ofNullable(properties.get(LOW_IMPEDANCE_THRESHOLD_PARAM_NAME))
                .ifPresent(prop -> this.setLowImpedanceThreshold(Double.parseDouble(prop)));
        Optional.ofNullable(properties.get(NETWORK_CACHE_ENABLED_PARAM_NAME))
                .ifPresent(prop -> this.setNetworkCacheEnabled(Boolean.parseBoolean(prop)));
        Optional.ofNullable(properties.get(SVC_VOLTAGE_MONITORING_PARAM_NAME))
                .ifPresent(prop -> this.setSvcVoltageMonitoring(Boolean.parseBoolean(prop)));
        Optional.ofNullable(properties.get(STATE_VECTOR_SCALING_MODE_PARAM_NAME))
                .ifPresent(prop -> this.setStateVectorScalingMode(StateVectorScalingMode.valueOf(prop)));
        Optional.ofNullable(properties.get(MAX_SLACK_BUS_COUNT_PARAM_NAME))
                .ifPresent(prop -> this.setMaxSlackBusCount(Integer.parseInt(prop)));
        Optional.ofNullable(properties.get(DEBUG_DIR_PARAM_NAME))
                .ifPresent(this::setDebugDir);
        Optional.ofNullable(properties.get(INCREMENTAL_TRANSFORMER_VOLTAGE_CONTROL_OUTER_LOOP_MAX_TAP_SHIFT_PARAM_NAME))
                .ifPresent(prop -> this.setIncrementalTransformerVoltageControlOuterLoopMaxTapShift(Integer.parseInt(prop)));
        Optional.ofNullable(properties.get(SECONDARY_VOLTAGE_CONTROL_PARAM_NAME))
                .ifPresent(prop -> this.setSecondaryVoltageControl(Boolean.parseBoolean(prop)));
        Optional.ofNullable(properties.get(CONTROLLER_TO_PILOT_POINT_VOLTAGE_SENSI_EPSILON_PARAM_NAME))
                .ifPresent(prop -> this.setControllerToPilotPointVoltageSensiEpsilon(Double.parseDouble(prop)));
        Optional.ofNullable(properties.get(REACTIVE_LIMITS_MAX_SWITCH_PQ_PV_PARAM_NAME))
                .ifPresent(prop -> this.setReactiveLimitsMaxPqPvSwitch(Integer.parseInt(prop)));
        Optional.ofNullable(properties.get(PHASE_SHIFTER_CONTROL_MODE_PARAM_NAME))
                .ifPresent(prop -> this.setPhaseShifterControlMode(PhaseShifterControlMode.valueOf(prop)));
        Optional.ofNullable(properties.get(ALWAYS_UPDATE_NETWORK_PARAM_NAME))
                .ifPresent(prop -> this.setAlwaysUpdateNetwork(Boolean.parseBoolean(prop)));
        Optional.ofNullable(properties.get(MOST_MESHED_SLACK_BUS_SELECTOR_MAX_NOMINAL_VOLTAGE_PERCENTILE_PARAM_NAME))
                .ifPresent(prop -> this.setMostMeshedSlackBusSelectorMaxNominalVoltagePercentile(Double.parseDouble(prop)));
        Optional.ofNullable(properties.get(REPORTED_FEATURES_PARAM_NAME))
                .ifPresent(prop -> this.setReportedFeatures(
                        parseStringListProp(prop).stream()
                        .map(ReportedFeatures::valueOf)
                        .collect(Collectors.toSet())));
        Optional.ofNullable(properties.get(SLACK_BUS_COUNTRY_FILTER_PARAM_NAME))
                .ifPresent(prop -> this.setSlackBusCountryFilter(parseStringListProp(prop).stream().map(Country::valueOf).collect(Collectors.toSet())));
        Optional.ofNullable(properties.get(ACTIONABLE_SWITCHES_IDS_PARAM_NAME))
                .ifPresent(prop -> this.setActionableSwitchesIds(new HashSet<>(parseStringListProp(prop))));
        Optional.ofNullable(properties.get(ASYMMETRICAL_PARAM_NAME))
                .ifPresent(prop -> this.setAsymmetrical(Boolean.parseBoolean(prop)));
        Optional.ofNullable(properties.get(MIN_NOMINAL_VOLTAGE_TARGET_VOLTAGE_CHECK_PARAM_NAME))
                .ifPresent(prop -> this.setMinNominalVoltageTargetVoltageCheck(Double.parseDouble(prop)));
        Optional.ofNullable(properties.get(REACTIVE_POWER_DISPATCH_MODE_PARAM_NAME))
                .ifPresent(prop -> this.setReactivePowerDispatchMode(ReactivePowerDispatchMode.valueOf(prop)));
        Optional.ofNullable(properties.get(OUTER_LOOP_NAMES_PARAM_NAME))
                .ifPresent(prop -> this.setOuterLoopNames(parseStringListProp(prop)));
        return this;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>(47);
        map.put(SLACK_BUS_SELECTION_MODE_PARAM_NAME, slackBusSelectionMode);
        map.put(SLACK_BUSES_IDS_PARAM_NAME, slackBusesIds);
        map.put(THROWS_EXCEPTION_IN_CASE_OF_SLACK_DISTRIBUTION_FAILURE_PARAM_NAME, throwsExceptionInCaseOfSlackDistributionFailure);
        map.put(VOLTAGE_REMOTE_CONTROL_PARAM_NAME, voltageRemoteControl);
        map.put(LOW_IMPEDANCE_BRANCH_MODE_PARAM_NAME, lowImpedanceBranchMode);
        map.put(LOAD_POWER_FACTOR_CONSTANT_PARAM_NAME, loadPowerFactorConstant);
        map.put(PLAUSIBLE_ACTIVE_POWER_LIMIT_PARAM_NAME, plausibleActivePowerLimit);
        map.put(NEWTONRAPHSON_STOPPING_CRITERIA_TYPE_PARAM_NAME, newtonRaphsonStoppingCriteriaType);
        map.put(SLACK_BUS_P_MAX_MISMATCH_PARAM_NAME, slackBusPMaxMismatch);
        map.put(MAX_ACTIVE_POWER_MISMATCH_PARAM_NAME, maxActivePowerMismatch);
        map.put(MAX_REACTIVE_POWER_MISMATCH_PARAM_NAME, maxReactivePowerMismatch);
        map.put(MAX_VOLTAGE_MISMATCH_PARAM_NAME, maxVoltageMismatch);
        map.put(MAX_ANGLE_MISMATCH_PARAM_NAME, maxAngleMismatch);
        map.put(MAX_RATIO_MISMATCH_PARAM_NAME, maxRatioMismatch);
        map.put(MAX_SUSCEPTANCE_MISMATCH_PARAM_NAME, maxSusceptanceMismatch);
        map.put(VOLTAGE_PER_REACTIVE_POWER_CONTROL_PARAM_NAME, voltagePerReactivePowerControl);
        map.put(REACTIVE_POWER_REMOTE_CONTROL_PARAM_NAME, reactivePowerRemoteControl);
        map.put(MAX_NEWTON_RAPHSON_ITERATIONS_PARAM_NAME, maxNewtonRaphsonIterations);
        map.put(MAX_OUTER_LOOP_ITERATIONS_PARAM_NAME, maxOuterLoopIterations);
        map.put(NEWTON_RAPHSON_CONV_EPS_PER_EQ_PARAM_NAME, newtonRaphsonConvEpsPerEq);
        map.put(VOLTAGE_INIT_MODE_OVERRIDE_PARAM_NAME, voltageInitModeOverride);
        map.put(TRANSFORMER_VOLTAGE_CONTROL_MODE_PARAM_NAME, transformerVoltageControlMode);
        map.put(SHUNT_VOLTAGE_CONTROL_MODE_PARAM_NAME, shuntVoltageControlMode);
        map.put(MIN_PLAUSIBLE_TARGET_VOLTAGE_PARAM_NAME, minPlausibleTargetVoltage);
        map.put(MAX_PLAUSIBLE_TARGET_VOLTAGE_PARAM_NAME, maxPlausibleTargetVoltage);
        map.put(MIN_REALISTIC_VOLTAGE_PARAM_NAME, minRealisticVoltage);
        map.put(MAX_REALISTIC_VOLTAGE_PARAM_NAME, maxRealisticVoltage);
        map.put(REACTIVE_RANGE_CHECK_MODE_PARAM_NAME, reactiveRangeCheckMode);
        map.put(LOW_IMPEDANCE_THRESHOLD_PARAM_NAME, lowImpedanceThreshold);
        map.put(NETWORK_CACHE_ENABLED_PARAM_NAME, networkCacheEnabled);
        map.put(SVC_VOLTAGE_MONITORING_PARAM_NAME, svcVoltageMonitoring);
        map.put(STATE_VECTOR_SCALING_MODE_PARAM_NAME, stateVectorScalingMode);
        map.put(MAX_SLACK_BUS_COUNT_PARAM_NAME, maxSlackBusCount);
        map.put(DEBUG_DIR_PARAM_NAME, debugDir);
        map.put(INCREMENTAL_TRANSFORMER_VOLTAGE_CONTROL_OUTER_LOOP_MAX_TAP_SHIFT_PARAM_NAME, incrementalTransformerVoltageControlOuterLoopMaxTapShift);
        map.put(SECONDARY_VOLTAGE_CONTROL_PARAM_NAME, secondaryVoltageControl);
        map.put(CONTROLLER_TO_PILOT_POINT_VOLTAGE_SENSI_EPSILON_PARAM_NAME, controllerToPilotPointVoltageSensiEpsilon);
        map.put(REACTIVE_LIMITS_MAX_SWITCH_PQ_PV_PARAM_NAME, reactiveLimitsMaxPqPvSwitch);
        map.put(PHASE_SHIFTER_CONTROL_MODE_PARAM_NAME, phaseShifterControlMode);
        map.put(ALWAYS_UPDATE_NETWORK_PARAM_NAME, alwaysUpdateNetwork);
        map.put(MOST_MESHED_SLACK_BUS_SELECTOR_MAX_NOMINAL_VOLTAGE_PERCENTILE_PARAM_NAME, mostMeshedSlackBusSelectorMaxNominalVoltagePercentile);
        map.put(REPORTED_FEATURES_PARAM_NAME, reportedFeatures);
        map.put(SLACK_BUS_COUNTRY_FILTER_PARAM_NAME, slackBusCountryFilter);
        map.put(ACTIONABLE_SWITCHES_IDS_PARAM_NAME, actionableSwitchesIds);
        map.put(ASYMMETRICAL_PARAM_NAME, asymmetrical);
        map.put(MIN_NOMINAL_VOLTAGE_TARGET_VOLTAGE_CHECK_PARAM_NAME, minNominalVoltageTargetVoltageCheck);
        map.put(REACTIVE_POWER_DISPATCH_MODE_PARAM_NAME, reactivePowerDispatchMode);
        map.put(OUTER_LOOP_NAMES_PARAM_NAME, outerLoopNames);
        return map;
    }

    @Override
    public String toString() {
        return "OpenLoadFlowParameters(" + toMap().entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining(", ")) + ")";
    }

    public static OpenLoadFlowParameters get(LoadFlowParameters parameters) {
        OpenLoadFlowParameters parametersExt = parameters.getExtension(OpenLoadFlowParameters.class);
        if (parametersExt == null) {
            parametersExt = new OpenLoadFlowParameters();
        }
        return parametersExt;
    }

    private static OpenLoadFlowParameters create(LoadFlowParameters parameters, Supplier<OpenLoadFlowParameters> parametersExtSupplier) {
        Objects.requireNonNull(parameters);
        OpenLoadFlowParameters parametersExt = parametersExtSupplier.get();
        parameters.addExtension(OpenLoadFlowParameters.class, parametersExt);
        return parametersExt;
    }

    public static OpenLoadFlowParameters create(LoadFlowParameters parameters) {
        return create(parameters, OpenLoadFlowParameters::new);
    }

    public static OpenLoadFlowParameters load(LoadFlowParameters parameters) {
        return create(parameters, OpenLoadFlowParameters::load);
    }

    public static void log(LoadFlowParameters parameters, OpenLoadFlowParameters parametersExt) {
        if (LOGGER.isInfoEnabled()) {
            AsciiTable at = new AsciiTable();
            at.addRule();
            at.addRow("Name", "Value");
            at.addRule();
            for (var e : parameters.toMap().entrySet()) {
                at.addRow(e.getKey(), e.getValue());
            }
            for (var e : parametersExt.toMap().entrySet()) {
                at.addRow(e.getKey(), Objects.toString(e.getValue(), ""));
            }
            at.addRule();
            at.getRenderer().setCWC(new CWC_LongestWord());
            at.setPaddingLeftRight(1, 1);
            LOGGER.info("Parameters:\n{}", at.render());
        }
    }

    static VoltageInitializer getVoltageInitializer(LoadFlowParameters parameters, OpenLoadFlowParameters parametersExt, LfNetworkParameters networkParameters, MatrixFactory matrixFactory) {
        switch (parameters.getVoltageInitMode()) {
            case UNIFORM_VALUES:
                return new UniformValueVoltageInitializer();
            case PREVIOUS_VALUES:
                return new PreviousValueVoltageInitializer();
            case DC_VALUES:
                return new DcValueVoltageInitializer(networkParameters, parameters.isDistributedSlack(), parameters.getBalanceType(), parameters.isDcUseTransformerRatio(), matrixFactory, parametersExt.getMaxOuterLoopIterations());
            default:
                throw new UnsupportedOperationException("Unsupported voltage init mode: " + parameters.getVoltageInitMode());
        }
    }

    static VoltageInitializer getExtendedVoltageInitializer(LoadFlowParameters parameters, OpenLoadFlowParameters parametersExt,
                                                            LfNetworkParameters networkParameters, MatrixFactory matrixFactory) {
        switch (parametersExt.getVoltageInitModeOverride()) {
            case NONE:
                return getVoltageInitializer(parameters, parametersExt, networkParameters, matrixFactory);

            case VOLTAGE_MAGNITUDE:
                return new VoltageMagnitudeInitializer(parameters.isTransformerVoltageControlOn(), matrixFactory, networkParameters.getLowImpedanceThreshold());

            case FULL_VOLTAGE:
                return new FullVoltageInitializer(new VoltageMagnitudeInitializer(parameters.isTransformerVoltageControlOn(), matrixFactory, networkParameters.getLowImpedanceThreshold()),
                        new DcValueVoltageInitializer(networkParameters,
                                                      parameters.isDistributedSlack(),
                                                      parameters.getBalanceType(),
                                                      parameters.isDcUseTransformerRatio(),
                                                      matrixFactory,
                                                      parametersExt.getMaxOuterLoopIterations()));

            default:
                throw new PowsyblException("Unknown voltage init mode override: " + parametersExt.getVoltageInitModeOverride());
        }
    }

    static LfNetworkParameters getNetworkParameters(LoadFlowParameters parameters, OpenLoadFlowParameters parametersExt,
                                                    SlackBusSelector slackBusSelector, GraphConnectivityFactory<LfBus, LfBranch> connectivityFactory,
                                                    boolean breakers) {
        return new LfNetworkParameters()
                .setSlackBusSelector(slackBusSelector)
                .setConnectivityFactory(connectivityFactory)
                .setGeneratorVoltageRemoteControl(parametersExt.hasVoltageRemoteControl())
                .setMinImpedance(parametersExt.getLowImpedanceBranchMode() == OpenLoadFlowParameters.LowImpedanceBranchMode.REPLACE_BY_MIN_IMPEDANCE_LINE)
                .setTwtSplitShuntAdmittance(parameters.isTwtSplitShuntAdmittance())
                .setBreakers(breakers)
                .setPlausibleActivePowerLimit(parametersExt.getPlausibleActivePowerLimit())
                .setComputeMainConnectedComponentOnly(parameters.getConnectedComponentMode() == LoadFlowParameters.ConnectedComponentMode.MAIN)
                .setCountriesToBalance(parameters.getCountriesToBalance())
                .setDistributedOnConformLoad(parameters.isDistributedSlack() && parameters.getBalanceType() == LoadFlowParameters.BalanceType.PROPORTIONAL_TO_CONFORM_LOAD)
                .setPhaseControl(parameters.isPhaseShifterRegulationOn())
                .setTransformerVoltageControl(parameters.isTransformerVoltageControlOn())
                .setVoltagePerReactivePowerControl(parametersExt.isVoltagePerReactivePowerControl())
                .setReactivePowerRemoteControl(parametersExt.hasReactivePowerRemoteControl())
                .setLoadFlowModel(parameters.isDc() ? LoadFlowModel.DC : LoadFlowModel.AC)
                .setShuntVoltageControl(parameters.isShuntCompensatorVoltageControlOn())
                .setReactiveLimits(parameters.isUseReactiveLimits())
                .setHvdcAcEmulation(parameters.isHvdcAcEmulation())
                .setMinPlausibleTargetVoltage(parametersExt.getMinPlausibleTargetVoltage())
                .setMaxPlausibleTargetVoltage(parametersExt.getMaxPlausibleTargetVoltage())
                .setReactiveRangeCheckMode(parametersExt.getReactiveRangeCheckMode())
                .setLowImpedanceThreshold(parametersExt.getLowImpedanceThreshold())
                .setSvcVoltageMonitoring(parametersExt.isSvcVoltageMonitoring())
                .setMaxSlackBusCount(parametersExt.getMaxSlackBusCount())
                .setDebugDir(parametersExt.getDebugDir())
                .setSecondaryVoltageControl(parametersExt.isSecondaryVoltageControl())
                .setCacheEnabled(parametersExt.isNetworkCacheEnabled())
                .setAsymmetrical(parametersExt.isAsymmetrical())
                .setMinNominalVoltageTargetVoltageCheck(parametersExt.getMinNominalVoltageTargetVoltageCheck());
    }

    public static AcLoadFlowParameters createAcParameters(Network network, LoadFlowParameters parameters, OpenLoadFlowParameters parametersExt,
                                                          MatrixFactory matrixFactory, GraphConnectivityFactory<LfBus, LfBranch> connectivityFactory) {
        return createAcParameters(network, parameters, parametersExt, matrixFactory, connectivityFactory, false, false);
    }

    public static AcLoadFlowParameters createAcParameters(Network network, LoadFlowParameters parameters, OpenLoadFlowParameters parametersExt,
                                                          MatrixFactory matrixFactory, GraphConnectivityFactory<LfBus, LfBranch> connectivityFactory,
                                                          boolean breakers, boolean forceA1Var) {
        AcLoadFlowParameters acParameters = createAcParameters(parameters, parametersExt, matrixFactory, connectivityFactory, breakers, forceA1Var);
        if (parameters.isReadSlackBus()) {
            acParameters.getNetworkParameters().setSlackBusSelector(new NetworkSlackBusSelector(network, parametersExt.getSlackBusCountryFilter(),
                    acParameters.getNetworkParameters().getSlackBusSelector()));
        }
        return acParameters;
    }

    private static NewtonRaphsonStoppingCriteria createNewtonRaphsonStoppingCriteria(OpenLoadFlowParameters parametersExt) {
        switch (parametersExt.getNewtonRaphsonStoppingCriteriaType()) {
            case UNIFORM_CRITERIA:
                return new DefaultNewtonRaphsonStoppingCriteria(parametersExt.getNewtonRaphsonConvEpsPerEq());
            case PER_EQUATION_TYPE_CRITERIA:
                return new PerEquationTypeStoppingCriteria(parametersExt.getMaxActivePowerMismatch(),
                        parametersExt.getMaxReactivePowerMismatch(), parametersExt.getMaxVoltageMismatch(),
                        parametersExt.getMaxAngleMismatch(), parametersExt.getMaxRatioMismatch(),
                        parametersExt.getMaxSusceptanceMismatch());
            default:
                throw new PowsyblException("Unknown Newton Raphson stopping criteria type: " + parametersExt.getNewtonRaphsonStoppingCriteriaType());
        }
    }

    static List<AcOuterLoop> createOuterLoops(LoadFlowParameters parameters, OpenLoadFlowParameters parametersExt) {
        AcOuterLoopConfig outerLoopConfig = AcOuterLoopConfig.findOuterLoopConfig()
                .orElseGet(() -> parametersExt.getOuterLoopNames() != null ? new ExplicitAcOuterLoopConfig()
                                                                           : new DefaultAcOuterLoopConfig());
        return outerLoopConfig.configure(parameters, parametersExt);
    }

    public static AcLoadFlowParameters createAcParameters(LoadFlowParameters parameters, OpenLoadFlowParameters parametersExt,
                                                          MatrixFactory matrixFactory, GraphConnectivityFactory<LfBus, LfBranch> connectivityFactory,
                                                          boolean breakers, boolean forceA1Var) {
        SlackBusSelector slackBusSelector = SlackBusSelector.fromMode(parametersExt.getSlackBusSelectionMode(), parametersExt.getSlackBusesIds(),
                parametersExt.getPlausibleActivePowerLimit(), parametersExt.getMostMeshedSlackBusSelectorMaxNominalVoltagePercentile(), parametersExt.getSlackBusCountryFilter());

        var networkParameters = getNetworkParameters(parameters, parametersExt, slackBusSelector, connectivityFactory, breakers);

        var equationSystemCreationParameters = new AcEquationSystemCreationParameters(forceA1Var);

        VoltageInitializer voltageInitializer = getExtendedVoltageInitializer(parameters, parametersExt, networkParameters, matrixFactory);

        var newtonRaphsonParameters = new NewtonRaphsonParameters()
                .setStoppingCriteria(createNewtonRaphsonStoppingCriteria(parametersExt))
                .setMaxIterations(parametersExt.getMaxNewtonRaphsonIterations())
                .setMinRealisticVoltage(parametersExt.getMinRealisticVoltage())
                .setMaxRealisticVoltage(parametersExt.getMaxRealisticVoltage())
                .setStateVectorScalingMode(parametersExt.getStateVectorScalingMode())
                .setAlwaysUpdateNetwork(parametersExt.isAlwaysUpdateNetwork());

        List<AcOuterLoop> outerLoops = createOuterLoops(parameters, parametersExt);

        return new AcLoadFlowParameters(networkParameters,
                                        equationSystemCreationParameters,
                                        newtonRaphsonParameters,
                                        outerLoops,
                                        parametersExt.getMaxOuterLoopIterations(),
                                        matrixFactory,
                                        voltageInitializer,
                                        parametersExt.isAsymmetrical());
    }

    public static DcLoadFlowParameters createDcParameters(Network network, LoadFlowParameters parameters, OpenLoadFlowParameters parametersExt,
                                                          MatrixFactory matrixFactory, GraphConnectivityFactory<LfBus, LfBranch> connectivityFactory,
                                                          boolean forcePhaseControlOffAndAddAngle1Var) {
        var dcParameters = createDcParameters(parameters, parametersExt, matrixFactory, connectivityFactory, forcePhaseControlOffAndAddAngle1Var);
        if (parameters.isReadSlackBus()) {
            dcParameters.getNetworkParameters().setSlackBusSelector(new NetworkSlackBusSelector(network, parametersExt.getSlackBusCountryFilter(),
                    dcParameters.getNetworkParameters().getSlackBusSelector()));
        }
        return dcParameters;
    }

    public static DcLoadFlowParameters createDcParameters(LoadFlowParameters parameters, OpenLoadFlowParameters parametersExt,
                                                          MatrixFactory matrixFactory, GraphConnectivityFactory<LfBus, LfBranch> connectivityFactory,
                                                          boolean forcePhaseControlOffAndAddAngle1Var) {
        SlackBusSelector slackBusSelector = SlackBusSelector.fromMode(parametersExt.getSlackBusSelectionMode(), parametersExt.getSlackBusesIds(),
                parametersExt.getPlausibleActivePowerLimit(), parametersExt.getMostMeshedSlackBusSelectorMaxNominalVoltagePercentile(), parametersExt.getSlackBusCountryFilter());

        var networkParameters = new LfNetworkParameters()
                .setSlackBusSelector(slackBusSelector)
                .setConnectivityFactory(connectivityFactory)
                .setGeneratorVoltageRemoteControl(false)
                .setMinImpedance(parametersExt.getLowImpedanceBranchMode() == OpenLoadFlowParameters.LowImpedanceBranchMode.REPLACE_BY_MIN_IMPEDANCE_LINE)
                .setTwtSplitShuntAdmittance(false)
                .setBreakers(false)
                .setPlausibleActivePowerLimit(parametersExt.getPlausibleActivePowerLimit())
                .setComputeMainConnectedComponentOnly(parameters.getConnectedComponentMode() == LoadFlowParameters.ConnectedComponentMode.MAIN)
                .setCountriesToBalance(parameters.getCountriesToBalance())
                .setDistributedOnConformLoad(parameters.isDistributedSlack() && parameters.getBalanceType() == LoadFlowParameters.BalanceType.PROPORTIONAL_TO_CONFORM_LOAD)
                .setPhaseControl(parameters.isPhaseShifterRegulationOn())
                .setTransformerVoltageControl(false)
                .setVoltagePerReactivePowerControl(false)
                .setReactivePowerRemoteControl(false)
                .setLoadFlowModel(LoadFlowModel.DC)
                .setShuntVoltageControl(false)
                .setReactiveLimits(false)
                .setHvdcAcEmulation(false) // FIXME
                .setLowImpedanceThreshold(parametersExt.getLowImpedanceThreshold())
                .setSvcVoltageMonitoring(false)
                .setMaxSlackBusCount(1);

        var equationSystemCreationParameters = new DcEquationSystemCreationParameters(true,
                                                                                      forcePhaseControlOffAndAddAngle1Var,
                                                                                      parameters.isDcUseTransformerRatio());

        return new DcLoadFlowParameters(networkParameters,
                                        equationSystemCreationParameters,
                                        matrixFactory,
                                        parameters.isDistributedSlack(),
                                        parameters.getBalanceType(),
                                        true,
                                        parametersExt.getMaxOuterLoopIterations());
    }

    public static boolean equals(LoadFlowParameters parameters1, LoadFlowParameters parameters2) {
        Objects.requireNonNull(parameters1);
        Objects.requireNonNull(parameters2);
        boolean equals = parameters1.getVoltageInitMode() == parameters2.getVoltageInitMode() &&
                parameters1.isTransformerVoltageControlOn() == parameters2.isTransformerVoltageControlOn() &&
                parameters1.isUseReactiveLimits() == parameters2.isUseReactiveLimits() &&
                parameters1.isPhaseShifterRegulationOn() == parameters2.isPhaseShifterRegulationOn() &&
                parameters1.isTwtSplitShuntAdmittance() == parameters2.isTwtSplitShuntAdmittance() &&
                parameters1.isShuntCompensatorVoltageControlOn() == parameters2.isShuntCompensatorVoltageControlOn() &&
                parameters1.isReadSlackBus() == parameters2.isReadSlackBus() &&
                parameters1.isWriteSlackBus() == parameters2.isWriteSlackBus() &&
                parameters1.isDc() == parameters2.isDc() &&
                parameters1.isDistributedSlack() == parameters2.isDistributedSlack() &&
                parameters1.getBalanceType() == parameters2.getBalanceType() &&
                parameters1.isDcUseTransformerRatio() == parameters2.isDcUseTransformerRatio() &&
                parameters1.getCountriesToBalance().equals(parameters2.getCountriesToBalance()) &&
                parameters1.getConnectedComponentMode() == parameters2.getConnectedComponentMode() &&
                parameters1.isHvdcAcEmulation() == parameters2.isHvdcAcEmulation() &&
                parameters1.getDcPowerFactor() == parameters2.getDcPowerFactor();
        if (!equals) {
            return false;
        }

        OpenLoadFlowParameters extension1 = parameters1.getExtension(OpenLoadFlowParameters.class);
        OpenLoadFlowParameters extension2 = parameters2.getExtension(OpenLoadFlowParameters.class);
        if (extension1 == null && extension2 == null) {
            return true;
        }
        if (extension1 == null) {
            return false;
        }
        if (extension2 == null) {
            return false;
        }

        return extension1.getSlackBusSelectionMode() == extension2.getSlackBusSelectionMode() &&
                extension1.getSlackBusesIds().equals(extension2.getSlackBusesIds()) &&
                extension1.isThrowsExceptionInCaseOfSlackDistributionFailure() == extension2.isThrowsExceptionInCaseOfSlackDistributionFailure() &&
                extension1.hasVoltageRemoteControl() == extension2.hasVoltageRemoteControl() &&
                extension1.getLowImpedanceBranchMode() == extension2.getLowImpedanceBranchMode() &&
                extension1.isLoadPowerFactorConstant() == extension2.isLoadPowerFactorConstant() &&
                extension1.getPlausibleActivePowerLimit() == extension2.getPlausibleActivePowerLimit() &&
                extension1.getSlackBusPMaxMismatch() == extension2.getSlackBusPMaxMismatch() &&
                extension1.isVoltagePerReactivePowerControl() == extension2.isVoltagePerReactivePowerControl() &&
                extension1.hasReactivePowerRemoteControl() == extension2.hasReactivePowerRemoteControl() &&
                extension1.getMaxNewtonRaphsonIterations() == extension2.getMaxNewtonRaphsonIterations() &&
                extension1.getMaxOuterLoopIterations() == extension2.getMaxOuterLoopIterations() &&
                extension1.getNewtonRaphsonConvEpsPerEq() == extension2.getNewtonRaphsonConvEpsPerEq() &&
                extension1.getVoltageInitModeOverride() == extension2.getVoltageInitModeOverride() &&
                extension1.getTransformerVoltageControlMode() == extension2.getTransformerVoltageControlMode() &&
                extension1.getShuntVoltageControlMode() == extension2.getShuntVoltageControlMode() &&
                extension1.getMinPlausibleTargetVoltage() == extension2.getMinPlausibleTargetVoltage() &&
                extension1.getMaxPlausibleTargetVoltage() == extension2.getMaxPlausibleTargetVoltage() &&
                extension1.getMinRealisticVoltage() == extension2.getMinRealisticVoltage() &&
                extension1.getMaxRealisticVoltage() == extension2.getMaxRealisticVoltage() &&
                extension1.getReactiveRangeCheckMode() == extension2.getReactiveRangeCheckMode() &&
                extension1.getLowImpedanceThreshold() == extension2.getLowImpedanceThreshold() &&
                extension1.isNetworkCacheEnabled() == extension2.isNetworkCacheEnabled() &&
                extension1.isSvcVoltageMonitoring() == extension2.isSvcVoltageMonitoring() &&
                extension1.getStateVectorScalingMode() == extension2.getStateVectorScalingMode() &&
                extension1.getMaxSlackBusCount() == extension2.getMaxSlackBusCount() &&
                Objects.equals(extension1.getDebugDir(), extension2.getDebugDir()) &&
                extension1.getIncrementalTransformerVoltageControlOuterLoopMaxTapShift() == extension2.getIncrementalTransformerVoltageControlOuterLoopMaxTapShift() &&
                extension1.isSecondaryVoltageControl() == extension2.isSecondaryVoltageControl() &&
                extension1.getControllerToPilotPointVoltageSensiEpsilon() == extension2.getControllerToPilotPointVoltageSensiEpsilon() &&
                extension1.getReactiveLimitsMaxPqPvSwitch() == extension2.getReactiveLimitsMaxPqPvSwitch() &&
                extension1.getPhaseShifterControlMode() == extension2.getPhaseShifterControlMode() &&
                extension1.isAlwaysUpdateNetwork() == extension2.isAlwaysUpdateNetwork() &&
                extension1.getMostMeshedSlackBusSelectorMaxNominalVoltagePercentile() == extension2.getMostMeshedSlackBusSelectorMaxNominalVoltagePercentile() &&
                extension1.getReportedFeatures().equals(extension2.getReportedFeatures()) &&
                extension1.getSlackBusCountryFilter().equals(extension2.getSlackBusCountryFilter()) &&
                extension1.getActionableSwitchesIds().equals(extension2.getActionableSwitchesIds()) &&
                extension1.isAsymmetrical() == extension2.isAsymmetrical() &&
                extension1.getMinNominalVoltageTargetVoltageCheck() == extension2.getMinNominalVoltageTargetVoltageCheck() &&
                extension1.getReactivePowerDispatchMode() == extension2.getReactivePowerDispatchMode() &&
                Objects.equals(extension1.getOuterLoopNames(), extension2.getOuterLoopNames());
    }

    public static LoadFlowParameters clone(LoadFlowParameters parameters) {
        Objects.requireNonNull(parameters);
        LoadFlowParameters parameters2 = new LoadFlowParameters()
                .setVoltageInitMode(parameters.getVoltageInitMode())
                .setTransformerVoltageControlOn(parameters.isTransformerVoltageControlOn())
                .setUseReactiveLimits(parameters.isUseReactiveLimits())
                .setPhaseShifterRegulationOn(parameters.isPhaseShifterRegulationOn())
                .setTwtSplitShuntAdmittance(parameters.isTwtSplitShuntAdmittance())
                .setShuntCompensatorVoltageControlOn(parameters.isShuntCompensatorVoltageControlOn())
                .setReadSlackBus(parameters.isReadSlackBus())
                .setWriteSlackBus(parameters.isWriteSlackBus())
                .setDc(parameters.isDc())
                .setDistributedSlack(parameters.isDistributedSlack())
                .setBalanceType(parameters.getBalanceType())
                .setDcUseTransformerRatio(parameters.isDcUseTransformerRatio())
                .setCountriesToBalance(new HashSet<>(parameters.getCountriesToBalance()))
                .setConnectedComponentMode(parameters.getConnectedComponentMode())
                .setHvdcAcEmulation(parameters.isHvdcAcEmulation())
                .setDcPowerFactor(parameters.getDcPowerFactor());

        OpenLoadFlowParameters extension = parameters.getExtension(OpenLoadFlowParameters.class);
        if (extension != null) {
            OpenLoadFlowParameters extension2 = new OpenLoadFlowParameters()
                    .setSlackBusSelectionMode(extension.getSlackBusSelectionMode())
                    .setSlackBusesIds(new ArrayList<>(extension.getSlackBusesIds()))
                    .setThrowsExceptionInCaseOfSlackDistributionFailure(extension.isThrowsExceptionInCaseOfSlackDistributionFailure())
                    .setVoltageRemoteControl(extension.hasVoltageRemoteControl())
                    .setLowImpedanceBranchMode(extension.getLowImpedanceBranchMode())
                    .setLoadPowerFactorConstant(extension.isLoadPowerFactorConstant())
                    .setPlausibleActivePowerLimit(extension.getPlausibleActivePowerLimit())
                    .setSlackBusPMaxMismatch(extension.getSlackBusPMaxMismatch())
                    .setVoltagePerReactivePowerControl(extension.isVoltagePerReactivePowerControl())
                    .setReactivePowerRemoteControl(extension.hasReactivePowerRemoteControl())
                    .setMaxNewtonRaphsonIterations(extension.getMaxNewtonRaphsonIterations())
                    .setMaxOuterLoopIterations(extension.getMaxOuterLoopIterations())
                    .setNewtonRaphsonConvEpsPerEq(extension.getNewtonRaphsonConvEpsPerEq())
                    .setVoltageInitModeOverride(extension.getVoltageInitModeOverride())
                    .setTransformerVoltageControlMode(extension.getTransformerVoltageControlMode())
                    .setShuntVoltageControlMode(extension.getShuntVoltageControlMode())
                    .setMinPlausibleTargetVoltage(extension.getMinPlausibleTargetVoltage())
                    .setMaxPlausibleTargetVoltage(extension.getMaxPlausibleTargetVoltage())
                    .setMinRealisticVoltage(extension.getMinRealisticVoltage())
                    .setMaxRealisticVoltage(extension.getMaxRealisticVoltage())
                    .setReactiveRangeCheckMode(extension.getReactiveRangeCheckMode())
                    .setLowImpedanceThreshold(extension.getLowImpedanceThreshold())
                    .setNetworkCacheEnabled(extension.isNetworkCacheEnabled())
                    .setSvcVoltageMonitoring(extension.isSvcVoltageMonitoring())
                    .setStateVectorScalingMode(extension.getStateVectorScalingMode())
                    .setMaxSlackBusCount(extension.getMaxSlackBusCount())
                    .setDebugDir(extension.getDebugDir())
                    .setIncrementalTransformerVoltageControlOuterLoopMaxTapShift(extension.getIncrementalTransformerVoltageControlOuterLoopMaxTapShift())
                    .setSecondaryVoltageControl(extension.isSecondaryVoltageControl())
                    .setControllerToPilotPointVoltageSensiEpsilon(extension.getControllerToPilotPointVoltageSensiEpsilon())
                    .setReactiveLimitsMaxPqPvSwitch(extension.getReactiveLimitsMaxPqPvSwitch())
                    .setPhaseShifterControlMode(extension.getPhaseShifterControlMode())
                    .setAlwaysUpdateNetwork(extension.isAlwaysUpdateNetwork())
                    .setMostMeshedSlackBusSelectorMaxNominalVoltagePercentile(extension.getMostMeshedSlackBusSelectorMaxNominalVoltagePercentile())
                    .setReportedFeatures(extension.getReportedFeatures())
                    .setSlackBusCountryFilter(new HashSet<>(extension.getSlackBusCountryFilter()))
                    .setActionableSwitchesIds(new HashSet<>(extension.getActionableSwitchesIds()))
                    .setAsymmetrical(extension.isAsymmetrical())
                    .setMinNominalVoltageTargetVoltageCheck(extension.getMinNominalVoltageTargetVoltageCheck())
                    .setReactivePowerDispatchMode(extension.getReactivePowerDispatchMode())
                    .setOuterLoopNames(extension.getOuterLoopNames());
            if (extension2 != null) {
                parameters2.addExtension(OpenLoadFlowParameters.class, extension2);
            }
        }

        return parameters2;
    }
}

