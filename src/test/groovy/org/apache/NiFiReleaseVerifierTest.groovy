package org.apache

import org.apache.commons.io.FileUtils
import org.apache.log4j.Logger
import org.junit.*
import org.junit.contrib.java.lang.system.ExpectedSystemExit

class NiFiReleaseVerifierTest extends GroovyTestCase {
    private static final Logger logger = Logger.getLogger(NiFiReleaseVerifierTest.class)

    private static final String RELEASE_LONG_OPT = "--release"

    private static final List<String> SIGNATURE_EXTENSIONS = ["asc", "md5", "sha1", "sha256"]

    private static final String DOWNLOAD_PARENT_DIR_PATH = "src/test/resources/downloads"
    private static final File DOWNLOAD_PARENT_DIR = new File(DOWNLOAD_PARENT_DIR_PATH)

    private NiFiReleaseVerifier verifier

    @Rule
    private final ExpectedSystemExit exit = ExpectedSystemExit.none();

    @BeforeClass
    static void setUpOnce() {
        DOWNLOAD_PARENT_DIR.deleteOnExit()
    }

    @Before
    void setUp() {
        verifier = new NiFiReleaseVerifier()
    }

    @After
    void tearDown() {
        FileUtils.cleanDirectory(DOWNLOAD_PARENT_DIR)
    }

    @AfterClass
    static void tearDownOnce() {
        if (DOWNLOAD_PARENT_DIR?.exists()) {
            FileUtils.cleanDirectory(DOWNLOAD_PARENT_DIR)
            DOWNLOAD_PARENT_DIR.delete()
        }
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

    @Test
    void testShouldDownloadFile() {
        // Arrange
        String target = "https://nifi.apache.org/faq.html"
        String targetFilename = "faq.html"
        logger.debug("Target URL: '${target}' | ${targetFilename}")


        logger.debug("Target path: ${parent}")
        File parentDir = new File(parent)
        if (!parentDir.exists()) {
            parentDir.mkdirs()
        }
        parentDir.deleteOnExit()

        // Act
        verifier.downloadFile(target, parent)

        // Assert
        assert parentDir.exists()
        assert parentDir.isDirectory()

        File targetFile = new File(parent, targetFilename)
        assert targetFile.exists()
        assert parentDir.listFiles().contains(targetFile)

        logger.debug("Read file: ${targetFile.text[0..<50]}...")
    }

    @Test
    void testShouldDownloadFileToDefaultLocation() {
        // Arrange
        String target = "https://nifi.apache.org/faq.html"
        String targetFilename = "faq.html"
        logger.debug("Target URL: '${target}' | ${targetFilename}")

        String parent = "src/test/resources/downloads"
        logger.debug("Target path: ${parent}")
        File parentDir = new File(parent)
        if (!parentDir.exists()) {
            parentDir.mkdirs()
        }
        parentDir.deleteOnExit()

        verifier.workBasePath = parent

        // Act
        verifier.downloadFile(target)

        // Assert
        assert parentDir.exists()
        assert parentDir.isDirectory()

        File targetFile = new File(parent, targetFilename)
        assert targetFile.exists()
        assert parentDir.listFiles().contains(targetFile)

        logger.debug("Read file: ${targetFile.text[0..<50]}...")
    }
}
