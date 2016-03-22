package org.apache

import org.apache.log4j.Logger
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.contrib.java.lang.system.ExpectedSystemExit

class NiFiReleaseVerifierTest extends GroovyTestCase {
    private static final Logger logger = Logger.getLogger(NiFiReleaseVerifierTest.class)

    private static final String RELEASE_LONG_OPT = "--release"

    @Rule
    public final ExpectedSystemExit exit = ExpectedSystemExit.none();

    @Before
    void setUp() {
    }

    @After
    void tearDown() {
    }

    @Test
    void testShouldExitWithNormalStatus() {
        // Arrange
        exit.expectSystemExitWithStatus(0)

        String release = "0.6.0"
        logger.debug("Release version: '${release}'")
        def args = [RELEASE_LONG_OPT, release] as String[]

        // Act
        NiFiReleaseVerifier.main(args)

        // Assert
    }

    @Test
    void testShouldExitWithBadStatusIfReleaseNotSpecified() {
        // Arrange
        exit.expectSystemExitWithStatus(1)

        String release = ""
        logger.debug("Release version: '${release}'")
        def args = [RELEASE_LONG_OPT, release] as String[]

        // Act
        NiFiReleaseVerifier.main(args)

        // Assert
    }
}
