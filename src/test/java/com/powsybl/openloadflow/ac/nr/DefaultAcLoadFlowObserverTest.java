package com.powsybl.openloadflow.ac.nr;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.test.SvcTestCaseFactory;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.math.matrix.SparseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.ac.equations.AcEquationSystem;
import com.powsybl.openloadflow.ac.equations.AcEquationSystemCreationParameters;
import com.powsybl.openloadflow.ac.outerloop.AcLoadFlowParameters;
import com.powsybl.openloadflow.ac.outerloop.AcloadFlowEngine;
import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.equations.JacobianMatrix;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.LfNetwork;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DefaultAcLoadFlowObserverTest {
    private DefaultAcLoadFlowObserver defaultAcLoadFlowObserver;
    private Network network;
    private MatrixFactory matrixFactory;
    private LfNetwork lfNetwork;
    private EquationSystem equationSystem;
    private JacobianMatrix jacobianMatrix;
    private Logger logger;
    private ListAppender<ILoggingEvent> listAppender;

    @BeforeEach
    void setUp() {
        defaultAcLoadFlowObserver = new DefaultAcLoadFlowObserver();
        network = SvcTestCaseFactory.create();
        LoadFlowParameters parameters = LoadFlowParameters.load();
        matrixFactory = new SparseMatrixFactory();
        AcLoadFlowParameters acParameters = OpenLoadFlowProvider.createAcParameters(network, matrixFactory, parameters, OpenLoadFlowProvider.getParametersExt(parameters), false);
        lfNetwork = AcloadFlowEngine.createNetworks(network, acParameters).get(0);
        AcEquationSystemCreationParameters acEquationSystemCreationParameters = new AcEquationSystemCreationParameters(true, false, false, true);
        equationSystem = AcEquationSystem.create(lfNetwork, new VariableSet(), acEquationSystemCreationParameters);
        jacobianMatrix = JacobianMatrix.create(equationSystem, matrixFactory);

        logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(DefaultAcLoadFlowObserver.class);
        logger.setLevel(Level.ALL);
        listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(listAppender);
    }

    @Test
    void testAfterEquationVectorCreation() {
        defaultAcLoadFlowObserver.afterEquationVectorCreation(null, equationSystem, 0);
        assertEquals(23, listAppender.list.size());
    }

    @Test
    void testAfterJacobianBuild() {
        defaultAcLoadFlowObserver.afterJacobianBuild(jacobianMatrix.getMatrix(), null, 0);
        assertEquals(1, listAppender.list.size());
    }

    @Test
    void testBeforeLoadFlow() {
        defaultAcLoadFlowObserver.beforeLoadFlow(lfNetwork);
        assertEquals(8, listAppender.list.size());
    }
}
