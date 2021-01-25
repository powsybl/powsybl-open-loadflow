package com.powsybl.openloadflow.ac.util;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.powsybl.openloadflow.util.LoadFlowTestTools;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NetworkEquationSystemAcLoadFlowObserverTest {
    private LoadFlowTestTools loadFlowTestTools;
    private LfNetworkAndEquationSystemCreationAcLoadFlowObserver lfNetworkAndEquationSystemCreationAcLoadFlowObserver;
    private Logger logger;
    private ListAppender<ILoggingEvent> listAppender;

    public NetworkEquationSystemAcLoadFlowObserverTest() {
        loadFlowTestTools = new LoadFlowTestTools(new NetworkBuilder().addNetworkBus1GenBus2Svc().setBus2SvcVoltageAndSlope().addBus2Load().addBus2Gen().addBus2Sc().addBus1OpenLine().addBus2OpenLine().build());
    }

    @BeforeEach
    void setUp() {
        lfNetworkAndEquationSystemCreationAcLoadFlowObserver = new LfNetworkAndEquationSystemCreationAcLoadFlowObserver();

        logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(LfNetworkAndEquationSystemCreationAcLoadFlowObserver.class);
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
        lfNetworkAndEquationSystemCreationAcLoadFlowObserver.afterEquationVectorCreation(null, loadFlowTestTools.getEquationSystem(), 0);
        assertEquals(32, listAppender.list.size());
    }

    @Test
    void testAfterJacobianBuild() {
        lfNetworkAndEquationSystemCreationAcLoadFlowObserver.afterJacobianBuild(loadFlowTestTools.getJacobianMatrix().getMatrix(), null, 0);
        assertEquals(1, listAppender.list.size());
    }

    @Test
    void testBeforeLoadFlow() {
        lfNetworkAndEquationSystemCreationAcLoadFlowObserver.beforeLoadFlow(loadFlowTestTools.getLfNetwork());
        assertEquals(12, listAppender.list.size());
    }
}
