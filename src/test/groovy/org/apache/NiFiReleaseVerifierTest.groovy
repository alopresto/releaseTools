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

    private static final List<String> SIGNATURE_EXTENSIONS = ["asc", "md5", "sha1", "sha256"]

    private NiFiReleaseVerifier verifier

    @Rule
    private final ExpectedSystemExit exit = ExpectedSystemExit.none();

    @Before
    void setUp() {
        verifier = new NiFiReleaseVerifier()
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

    @Test
    void testShouldGenerateDefaultSignatureFilesFromBase() {
        // Arrange
        String baseUrl = "https://example.com/file.txt"
        logger.debug("Base URL: '${baseUrl}'")

        final List<String> EXPECTED_SIGNATURE_FILES = SIGNATURE_EXTENSIONS.collect { "${baseUrl}.${it}" }
        logger.info(" Expected: ${EXPECTED_SIGNATURE_FILES.join("\n")}")

        // Act
        List<String> signatureFiles = verifier.generateSignatureFiles(baseUrl)
        logger.info("Generated: ${signatureFiles.join("\n")}")

        // Assert
        EXPECTED_SIGNATURE_FILES == signatureFiles - baseUrl
    }

    @Test
    void testShouldGenerateSignatureFilesFromBaseAndExtensions() {
        // Arrange
        String baseUrl = "https://example.com/file.txt"
        logger.debug("Base URL: '${baseUrl}'")

        final List<String> extensions = ["ext1", "ext2"]
        final List<String> EXPECTED_SIGNATURE_FILES = extensions.collect { "${baseUrl}.${it}" }
        logger.info(" Expected: ${EXPECTED_SIGNATURE_FILES.join("\n")}")

        // Act
        List<String> signatureFiles = verifier.generateSignatureFiles(baseUrl, extensions)
        logger.info("Generated: ${signatureFiles.join("\n")}")

        // Assert
        EXPECTED_SIGNATURE_FILES == signatureFiles - baseUrl
    }
}
