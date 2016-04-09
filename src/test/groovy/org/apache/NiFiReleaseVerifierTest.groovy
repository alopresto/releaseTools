package org.apache

import org.apache.commons.io.FileUtils
import org.apache.log4j.Logger
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.*
import org.junit.contrib.java.lang.system.ExpectedSystemExit
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

import java.nio.file.Files
import java.security.Security

@RunWith(JUnit4.class)
class NiFiReleaseVerifierTest extends GroovyTestCase {
    private static final Logger logger = Logger.getLogger(NiFiReleaseVerifierTest.class)

    private static final String RELEASE_LONG_OPT = "--release"
    private static final String PATH_LONG_OPT = "--path"

    private static final List<String> SIGNATURE_EXTENSIONS = ["asc", "md5", "sha1", "sha256"]

    private static final String RESOURCES_PATH = "src/test/resources"
    private static final String DOWNLOAD_PARENT_DIR_PATH = "${RESOURCES_PATH}/downloads"
    private static final File DOWNLOAD_PARENT_DIR = new File(DOWNLOAD_PARENT_DIR_PATH)

    private static final String WORK_DIR_PATH = "${RESOURCES_PATH}/work"
    private static final File WORK_DIR = new File(WORK_DIR_PATH)

    private NiFiReleaseVerifier verifier

    @Rule
    public final ExpectedSystemExit exit = ExpectedSystemExit.none()
    static private final String DELETE_KEY_FINGERPRINT = "56FFB72B7C1E5A9DCFEFCFA6AD7DD2BAAC34201F"

    @BeforeClass
    static void setUpOnce() {
        DOWNLOAD_PARENT_DIR.deleteOnExit()

        logger.metaClass.methodMissing = { String name, args ->
            logger.info("[${name?.toUpperCase()}] ${(args as List).join(" ")}")
        }

        Security.addProvider(new BouncyCastleProvider())
    }

    @Before
    void setUp() {
        verifier = new NiFiReleaseVerifier()
        makeDownloadDir(DOWNLOAD_PARENT_DIR_PATH)
    }

    @After
    void tearDown() {
        if (DOWNLOAD_PARENT_DIR.exists()) {
            FileUtils.cleanDirectory(DOWNLOAD_PARENT_DIR)
        }
    }

    @AfterClass
    static void tearDownOnce() {
        if (DOWNLOAD_PARENT_DIR?.exists()) {
            FileUtils.cleanDirectory(DOWNLOAD_PARENT_DIR)
            DOWNLOAD_PARENT_DIR.delete()
        }

        if (WORK_DIR?.exists()) {
            FileUtils.cleanDirectory(WORK_DIR)
            WORK_DIR.delete()
        }
    }

    @Ignore("Not yet implemented")
    @Test
    void testShouldExitWithNormalStatus() {
        // Arrange
        exit.expectSystemExitWithStatus(0)

        String release = "0.6.0"
        logger.debug("Release version: '${release}'")

        String path = WORK_DIR_PATH
        logger.debug("Work path: '${path}'")

        def args = [RELEASE_LONG_OPT, release, PATH_LONG_OPT, path] as String[]

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
    void testShouldPopulateReleaseFiles() {
        // Arrange
        String version = "0.6.1"
        logger.info("Version: ${version}")

        String mainFileName = "nifi-${version}-source-release.zip"
        logger.info("Source file: ${mainFileName}")

        final String EXPECTED_SOURCE_URL = "https://dist.apache.org/repos/dist/dev/nifi/nifi-${version}/nifi-${version}-source-release.zip"

        final List<String> EXPECTED_URLS = ["", ".asc", ".md5", ".sha1", ".sha256"].collect { EXPECTED_SOURCE_URL + it }

        // Act
        def releaseUrls = verifier.populateReleaseFiles(version)
        logger.info("Release URLs: ${releaseUrls.join("\n")}")

        // Assert
        assert releaseUrls.sort() == EXPECTED_URLS.sort()
    }

    @Test
    void testShouldDownloadFile() {
        // Arrange
        String target = "https://nifi.apache.org/faq.html"
        String targetFilename = "faq.html"
        logger.debug("Target URL: '${target}' | ${targetFilename}")

        String parent = DOWNLOAD_PARENT_DIR_PATH
        logger.debug("Target path: ${parent}")
        File parentDir = makeDownloadDir(parent)

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

        String parent = DOWNLOAD_PARENT_DIR_PATH
        logger.debug("Target path: ${parent}")
        File parentDir = makeDownloadDir(parent)

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

    @Test
    void testShouldGeneratePathInWorkBasePath() {
        // Arrange
        String targetFilename = "faq.html"
        logger.debug("File name: ${targetFilename}")

        String parent = DOWNLOAD_PARENT_DIR_PATH
        logger.debug("Target path: ${parent}")
        File parentDir = makeDownloadDir(parent)

        verifier.workBasePath = parent

        // Act
        String generatedPath = verifier.generatePath(targetFilename)
        logger.debug("Generated path: ${generatedPath}")

        // Assert
        assert generatedPath == [DOWNLOAD_PARENT_DIR_PATH, targetFilename].join("/")
    }

    @Test
    void testShouldGeneratePathIfWorkBasePathAlreadyPresent() {
        // Arrange
        String targetFilename = "faq.html"
        logger.debug("File name: ${targetFilename}")
        String completePath = "${DOWNLOAD_PARENT_DIR_PATH}/${targetFilename}"
        logger.debug("Complete path: ${completePath}")

        String parent = DOWNLOAD_PARENT_DIR_PATH
        logger.debug("Target path: ${parent}")
        File parentDir = makeDownloadDir(parent)

        verifier.workBasePath = parent

        // Act
        String generatedPath = verifier.generatePath(completePath)
        logger.debug("Generated path: ${generatedPath}")

        // Assert
        assert generatedPath == [DOWNLOAD_PARENT_DIR_PATH, targetFilename].join("/")
    }

    @Test
    void testShouldDownloadReleaseFiles() {
        // Arrange
        String target = "https://nifi.apache.org/faq.html"
        String targetFilename = "faq.html"
        logger.debug("Target URL: '${target}' | ${targetFilename}")

        String parent = DOWNLOAD_PARENT_DIR_PATH
        logger.debug("Target path: ${parent}")
        File parentDir = makeDownloadDir(parent)

        verifier.workBasePath = parentDir.path

        // Act
        verifier.downloadReleaseFiles([target])

        // Assert
        assert parentDir.exists()
        assert parentDir.isDirectory()

        File targetFile = new File(parent, targetFilename)
        assert targetFile.exists()
        assert parentDir.listFiles().contains(targetFile)

        logger.debug("Read file: ${targetFile.text[0..<50]}...")
    }

    @Test
    void testImportGPGKeysShouldHandleNewKey() {
        // Arrange
        String parent = DOWNLOAD_PARENT_DIR_PATH
        logger.debug("Target path: ${parent}")
        File parentDir = makeDownloadDir(parent)
        File keys = new File(RESOURCES_PATH, "KEYS")
        Files.copy(keys.toPath(), new File(parentDir, "KEYS").toPath())

        verifier.workBasePath = parent

        // Delete the "delete" key to ensure it is not present in the keyring
        removeTestKey()

        // Act
        int modifiedKeysCount = verifier.importSigningKeys()
        logger.debug("Modified keys count: ${modifiedKeysCount}")

        // Assert
        assert modifiedKeysCount == 1
    }

    @Test
    void testImportGPGKeysShouldHandleNoNewKeys() {
        // Arrange
        String parent = DOWNLOAD_PARENT_DIR_PATH
        logger.debug("Target path: ${parent}")
        File parentDir = makeDownloadDir(parent)
        File keys = new File(RESOURCES_PATH, "KEYS")
        Files.copy(keys.toPath(), new File(parentDir, "KEYS").toPath())

        verifier.workBasePath = parent

        // Import all keys to ensure keyring contains both
        verifier.importSigningKeys()

        // Act
        int modifiedKeysCount = verifier.importSigningKeys()
        logger.debug("Modified keys count: ${modifiedKeysCount}")

        // Assert
        assert modifiedKeysCount == 0
    }

    @Test
    void testShouldFormFileFromPath() {
        // Arrange
        String targetPath = "plain.txt"
        logger.info("Target path: ${targetPath}")
        File targetFile = new File(RESOURCES_PATH, targetPath)
        logger.info("Expected path: ${targetFile.path}")

        verifier.workBasePath = RESOURCES_PATH
        logger.info("Work base path: ${verifier.workBasePath}")

        // Act
        File formedFile = verifier.formFileFromPath(targetPath)
        logger.info("Formed file: ${formedFile.path}")

        // Assert
        assert formedFile.path == targetFile.path
    }

    @Test
    void testFormFileFromPathShouldDetectExistingFile() {
        // Arrange
        String targetPath = "plain.txt"
        logger.info("Target path: ${targetPath}")
        File targetFile = new File(RESOURCES_PATH, targetPath)
        logger.info("Expected path: ${targetFile.path}")

        logger.info("Work base path: ${verifier.workBasePath}")

        // Act
        File formedFile = verifier.formFileFromPath(targetFile.path)
        logger.info("Formed file: ${formedFile.path}")

        // Assert
        assert formedFile.path == targetFile.path
    }

    @Test
    void testVerifyGPGSignatureShouldHandleSuccessfulSignatureWithDefaultSignatureFile() {
        // Arrange
        String targetPath = "plain.txt"
        File targetFile = new File(RESOURCES_PATH, targetPath)

        // Act
        boolean signatureVerified = verifier.verifyGPGSignature(targetFile.path)
        logger.debug("Signature verified: ${signatureVerified}")

        // Assert
        assert signatureVerified
    }

    @Test
    void testVerifyGPGSignatureShouldHandleSuccessfulSignatureWithTrustWarning() {
        // Arrange
        String targetPath = "plain.txt"
        File targetFile = new File(RESOURCES_PATH, targetPath)

        String signaturePath = "good_untrusted_sig.asc"
        File signatureFile = new File(RESOURCES_PATH, signaturePath)

        // Act
        boolean signatureVerified = verifier.verifyGPGSignature(targetFile.path, signatureFile.path)
        logger.debug("Signature verified: ${signatureVerified}")

        // Assert
        assert signatureVerified
    }

    @Test
    void testVerifyGPGSignatureShouldHandleUnsuccessfulSignature() {
        // Arrange
        String targetPath = "plain.txt"
        File targetFile = new File(RESOURCES_PATH, targetPath)

        String signaturePath = "bad_sig.asc"
        File signatureFile = new File(RESOURCES_PATH, signaturePath)

        // Act
        boolean signatureVerified = verifier.verifyGPGSignature(targetFile.path, signatureFile.path)
        logger.debug("Signature verified: ${signatureVerified}")

        // Assert
        assert !signatureVerified
    }

    @Test
    void testVerifyGPGSignatureShouldRequireTargetPath() {
        // Arrange
        String targetPath = "plain.txt"
        File targetFile = new File(RESOURCES_PATH, targetPath)

        // Act
        def msg = shouldFail(IllegalArgumentException) {
            boolean signatureVerified = verifier.verifyGPGSignature(null)
        }
        logger.expected(msg)

        // Assert
        assert msg =~ "Target file must be specified"
    }

    @Test
    void testVerifyGPGSignatureShouldFormTargetPathFromWorkPath() {
        // Arrange
        String parent = DOWNLOAD_PARENT_DIR_PATH
        logger.debug("Target path: ${parent}")
        File parentDir = makeDownloadDir(parent)

        String targetPath = "plain.txt"
        File targetFile = new File(RESOURCES_PATH, targetPath)
        File downloadedTargetFile = new File(parentDir, targetPath)
        Files.copy(targetFile.toPath(), downloadedTargetFile.toPath())

        String signaturePath = "plain.txt.asc"
        File signatureFile = new File(RESOURCES_PATH, signaturePath)
        File downloadedSignatureFile = new File(parentDir, signaturePath)
        Files.copy(signatureFile.toPath(), downloadedSignatureFile.toPath())

        verifier.workBasePath = parent

        // Act
        boolean signatureVerified = verifier.verifyGPGSignature(targetPath)
        logger.debug("Signature verified: ${signatureVerified}")

        // Assert
        assert signatureVerified
    }

    @Test
    void testVerifyGPGSignatureShouldRequireTargetFile() {
        // Arrange
        String targetPath = "missing.txt"
        logger.info("Target path: ${targetPath}")
        File targetFile = new File(RESOURCES_PATH, targetPath)
        logger.info("Expected path: ${targetFile.path}")

        verifier.workBasePath = RESOURCES_PATH
        logger.info("Work base path: ${verifier.workBasePath}")

        // Act
        def msg = shouldFail(IllegalArgumentException) {
            boolean signatureVerified = verifier.verifyGPGSignature(targetFile.path)
        }
        logger.expected(msg)

        // Assert
        assert msg == "Target file [${targetFile.path}] does not exist" as String
    }

    @Test
    void testVerifyGPGSignatureShouldRequireSignatureFile() {
        // Arrange
        String targetPath = "plain.txt"
        logger.info("Target path: ${targetPath}")
        File targetFile = new File(RESOURCES_PATH, targetPath)
        logger.info("Expected path: ${targetFile.path}")

        String signaturePath = "missing.txt.asc"
        File signatureFile = new File(RESOURCES_PATH, signaturePath)

        verifier.workBasePath = RESOURCES_PATH
        logger.info("Work base path: ${verifier.workBasePath}")

        // Act
        def msg = shouldFail(IllegalArgumentException) {
            boolean signatureVerified = verifier.verifyGPGSignature(targetFile.path, signaturePath)
        }
        logger.expected(msg)

        // Assert
        assert msg == "Signature file [${signatureFile.path}] does not exist" as String
    }

    @Test
    void testVerifyChecksumShouldHandleSuccessfulChecksum() {
        // Arrange
        String targetPath = "plain.txt"
        File targetFile = new File(RESOURCES_PATH, targetPath)

        ["md5", "sha1", "sha256"].each { String algorithm ->
            String checksumPath = "${targetPath}.${algorithm}"
            File checksumFile = new File(RESOURCES_PATH, checksumPath)
            String checksum = checksumFile.text
            MessageDigestAlgorithm mda = MessageDigestAlgorithm.getInstance(algorithm)
            logger.info("Expected checksum (${mda.name()}): ${checksum}")

            // Act
            boolean checksumVerified = verifier.verifyChecksum(targetFile.bytes, checksum, mda.name())
            logger.info("Checksum verified: ${checksumVerified}")

            // Assert
            assert checksumVerified
        }
    }

    @Test
    void testVerifyChecksumShouldHandleUnsuccessfulChecksum() {
        // Arrange
        String targetPath = "plain.txt"
        File targetFile = new File(RESOURCES_PATH, targetPath)

        ["md5", "sha1", "sha256"].each { String algorithm ->
            String checksumPath = "${targetPath}.${algorithm}"
            File checksumFile = new File(RESOURCES_PATH, checksumPath)
            String checksum = checksumFile.text
            MessageDigestAlgorithm mda = MessageDigestAlgorithm.getInstance(algorithm)
            logger.info("Expected checksum (${mda.name()}): ${checksum}")

            // Act
            byte[] incorrectArtifactBytes = (targetFile.bytes as List<Byte>).reverse()
            boolean checksumVerified = verifier.verifyChecksum(incorrectArtifactBytes, checksum, mda.name())
            logger.info("Checksum verified: ${checksumVerified}")

            // Assert
            assert !checksumVerified
        }
    }

    @Test
    void testShouldUnzipFile() {
        // Arrange
        String parent = DOWNLOAD_PARENT_DIR_PATH
        logger.debug("Target path: ${parent}")
        File parentDir = makeDownloadDir(parent)
        File zipFile = new File(RESOURCES_PATH, "inside.zip")
        File workDirZipFile = new File(parentDir, "inside.zip")
        Files.copy(zipFile.toPath(), workDirZipFile.toPath())

        verifier.workBasePath = parent
        verifier.releaseArtifact = workDirZipFile.path

        // Delete the unzipped directory to ensure it is not present
        File unzippedDir = new File(parentDir, "inside")
        unzippedDir.deleteDir()
        logger.info("Unzipped directory exists (${unzippedDir.path}): ${unzippedDir.exists()}")

        // Act
        verifier.unzipSource()

        // Assert
        logger.info("Unzipped directory exists (${unzippedDir.path}): ${unzippedDir.exists()}")
        assert unzippedDir.exists()
    }

    @Test
    void testUnzipFileShouldHandleError() {
        // Arrange
        String parent = DOWNLOAD_PARENT_DIR_PATH
        logger.debug("Target path: ${parent}")
        File parentDir = makeDownloadDir(parent)
        File zipFile = new File(RESOURCES_PATH, "inside.zip")
        File workDirZipFile = new File(parentDir, "missing.zip")

        verifier.workBasePath = parent
        verifier.releaseArtifact = workDirZipFile.path

        // Delete the unzipped directory to ensure it is not present
        File unzippedDir = new File(parentDir, "inside")
        unzippedDir.deleteDir()
        logger.info("Unzipped directory exists (${unzippedDir.path}): ${unzippedDir.exists()}")

        // Delete the input zip file
        workDirZipFile.delete()
        logger.info("Zip file exists (${workDirZipFile.path}): ${workDirZipFile.exists()}")

        // Act
        def msg = shouldFail(Exception) {
            verifier.unzipSource()
        }

        // Assert
        logger.info("Unzipped directory exists (${unzippedDir.path}): ${unzippedDir.exists()}")
        assert !unzippedDir.exists()

        logger.expected(msg)
        assert msg =~ "Error unzipping source file ${workDirZipFile.path}"
    }

    @Test
    void testShouldVerifyContribCheck() {
        // Arrange
        String mvnSubProjectPath = RESOURCES_PATH + "/testContribCheckProject"
        File pomFile = new File(mvnSubProjectPath, "pom.xml")
        logger.info("Pom path: ${pomFile.path}")

        // Act
        int exitStatus = verifier.runMavenCommand(pomFile.path)

        // Assert
        logger.info("Mvn contrib check passed: ${exitStatus == 0}")
        assert exitStatus == 0
    }

    @Test
    void testVerifyContribCheckShouldHandleFailures() {
        // Arrange
        String mvnSubProjectPath = RESOURCES_PATH + "/testFailingContribCheckProject"
        File pomFile = new File(mvnSubProjectPath, "pom.xml")
        logger.info("Pom path: ${pomFile.path}")

        // Act
        def msg = shouldFail(IllegalStateException) {
            int exitStatus = verifier.runMavenCommand(pomFile.path)
        }

        // Assert
        logger.expected(msg)
        assert msg =~ "Build with contrib-check failed"
    }

    @Test
    void testShouldVerifyApacheArtifacts() {
        // Arrange
        File plainFile = new File(RESOURCES_PATH, "plain.txt")
        String plainFilePath = plainFile.path
        logger.info("Plain file path: ${plainFilePath}")
        logger.info("File contents: ${plainFile.text}")

        // Keep words of even length
        def allWords = plainFile.text.split(/\s/)
        def keywords = allWords.findAll { it.size() % 2 == 0 }

        def expectedFileAndWords = [(plainFile.name): keywords]
        logger.info("Files and expected words: ${expectedFileAndWords}")

        // Act
        boolean existsAndContainsWords = verifier.verifyApacheArtifacts(plainFile.toPath().parent.toString(), expectedFileAndWords)

        // Assert
        logger.info("Exists and contains words: ${existsAndContainsWords}")
        assert existsAndContainsWords
    }

    @Test
    void testVerifyApacheArtifactsShouldHandleActualArtifacts() {
        // Arrange
        def expectedFileAndWords = verifier.generateRepresentativeStrings()
        logger.info("Files and expected words: ${expectedFileAndWords}")

        // Act
        boolean existsAndContainsWords = verifier.verifyApacheArtifacts(RESOURCES_PATH, expectedFileAndWords)

        // Assert
        logger.info("Exists and contains words: ${existsAndContainsWords}")
        assert existsAndContainsWords
    }

    @Test
    void testVerifyApacheArtifactsShouldHandleMissingFile() {
        // Arrange
        File plainFile = new File(RESOURCES_PATH, "missing.txt")
        String plainFilePath = plainFile.path
        logger.info("Plain file path: ${plainFilePath}")
        logger.info("File contents: ${plainFile.exists() ? plainFile.text : ""}")

        def expectedFileAndWords = [(plainFile.name): ["any", "word", "here"]]
        logger.info("Files and expected words: ${expectedFileAndWords}")

        // Act
        boolean existsAndContainsWords = verifier.verifyApacheArtifacts(plainFile.toPath().parent.toString(), expectedFileAndWords)

        // Assert
        logger.info("Exists and contains words: ${existsAndContainsWords}")
        assert !existsAndContainsWords
    }

    @Test
    void testVerifyApacheArtifactsShouldHandleMissingLines() {
        // Arrange
        File plainFile = new File(RESOURCES_PATH, "plain.txt")
        String plainFilePath = plainFile.path
        logger.info("Plain file path: ${plainFilePath}")
        logger.info("File contents: ${plainFile.text}")

        // Keep words of even length
        def allWords = plainFile.text.split(/\s/)
        def keywords = allWords.findAll { it.size() % 2 == 0 }

        def expectedFileAndWords = [(plainFile.name): keywords*.reverse()]
        logger.info("Files and expected words: ${expectedFileAndWords}")

        // Act
        boolean existsAndContainsWords = verifier.verifyApacheArtifacts(plainFile.toPath().parent.toString(), expectedFileAndWords)

        // Assert
        logger.info("Exists and contains words: ${existsAndContainsWords}")
        assert !existsAndContainsWords
    }

    @Test
    void testShouldVerifyGitCommitId() {
        // Arrange
        File nifiGitHome = new File("/Users/alopresto/Workspace/nifi")
        String nifiGitPath = nifiGitHome.path
        logger.info("NiFi git path: ${nifiGitPath}")

        // Get the most recent git commit (tags may change, so use master for tests)
        final String EXPECTED_GIT_CMD = "git -C ${nifiGitPath} rev-list -n 1 master"
        logger.info("Expected git command: ${EXPECTED_GIT_CMD}")
        StringBuffer output = new StringBuffer()
        StringBuffer error = new StringBuffer()

        def proc = EXPECTED_GIT_CMD.execute()
        proc.waitForProcessOutput(output, error)

//        logger.output(output)
//        logger.errors(error)
//        logger.exit(proc.exitValue())

        final String EXPECTED_COMMIT = output.toString().trim()
        logger.info("Expected commit ID: ${EXPECTED_COMMIT}")

        verifier.commitId = EXPECTED_COMMIT
        verifier.rc = null
        verifier.version = "master"

        // Act
        boolean commitIdsMatch = verifier.verifyGitCommitId()

        // Assert
        logger.info("Commit IDs match: ${commitIdsMatch}")
        assert commitIdsMatch
    }

    @Test
    void testVerifyGitCommitIdShouldHandleFailure() {
        // Arrange
        File nifiGitHome = new File("/Users/alopresto/Workspace/nifi")
        String nifiGitPath = nifiGitHome.path
        logger.info("NiFi git path: ${nifiGitPath}")

        // Get the most recent git commit (tags may change, so use master for tests)
        final String EXPECTED_GIT_CMD = "git -C ${nifiGitPath} rev-list -n 1 master"
        logger.info("Expected git command: ${EXPECTED_GIT_CMD}")
        StringBuffer output = new StringBuffer()
        StringBuffer error = new StringBuffer()

        def proc = EXPECTED_GIT_CMD.execute()
        proc.waitForProcessOutput(output, error)

//        logger.output(output)
//        logger.errors(error)
//        logger.exit(proc.exitValue())

        final String EXPECTED_COMMIT = output.toString().trim().reverse()
        logger.reversed("Expected commit ID: ${EXPECTED_COMMIT}")

        verifier.commitId = EXPECTED_COMMIT
        verifier.rc = null
        verifier.version = "master"

        // Act
        boolean commitIdsMatch = verifier.verifyGitCommitId()

        // Assert
        logger.info("Commit IDs match: ${commitIdsMatch}")
        assert !commitIdsMatch
    }

    @Test
    void testShouldVerifyReleaseBinariesExist() {
        // Arrange
        final String VERSION = "0.6.1"

        String parent = DOWNLOAD_PARENT_DIR_PATH
        logger.debug("Target path: ${parent}")
        File parentDir = makeDownloadDir(parent)
        File targetDir = new File(parentDir.path, "/nifi-${VERSION}/nifi-assembly/target")
        targetDir.mkdirs()


        verifier.version = VERSION
        verifier.workBasePath = parentDir.path

        def binaryExtensions = ["zip", "tar.gz"]
        binaryExtensions.each { String extension ->
            File file = new File(targetDir.path, "nifi-${VERSION}-bin.${extension}")
            file.write("Test resource file")
            logger.info("Created file ${file.path}")

            assert file.exists()
        }

        // Act
        boolean releaseBinariesExist = verifier.verifyReleaseBinariesExist()

        // Assert
        logger.info("Release binaries exist: ${releaseBinariesExist}")
        assert releaseBinariesExist
    }

    @Test
    void testVerifyReleaseBinariesExistShouldHandleMissingFile() {
        // Arrange
        final String VERSION = "0.6.1"

        String parent = DOWNLOAD_PARENT_DIR_PATH
        logger.debug("Target path: ${parent}")
        File parentDir = makeDownloadDir(parent)
        File targetDir = new File(parentDir.path, "/nifi-${VERSION}/nifi-assembly/target")
        targetDir.mkdirs()


        verifier.version = VERSION
        verifier.workBasePath = parentDir.path

        def binaryExtensions = ["zip", "tar.gz"]
        binaryExtensions.each { String extension ->
            File file = new File(targetDir.path, "nifi-${VERSION}-bin.${extension}")
            file.delete()
            logger.info("Deleted file ${file.path}")

            assert !file.exists()
        }

        // Act
        boolean releaseBinariesExist = verifier.verifyReleaseBinariesExist()

        // Assert
        logger.info("Release binaries exist: ${releaseBinariesExist}")
        assert !releaseBinariesExist
    }

    private static void removeTestKey() {
        // Specifies the fingerprint of the test key from the resource file to delete
        def deleteCommandPrefixes = ["gpg --batch --delete-secret-keys ", "gpg --batch --delete-keys "]
        deleteCommandPrefixes.each { String cmd ->
            def proc = (cmd + DELETE_KEY_FINGERPRINT).execute()
            def outputStream = new StringBuffer();
            def errorStream = new StringBuffer();
            proc.waitForProcessOutput(outputStream, errorStream)
            def errorLines = errorStream.readLines()
            logger.info(errorLines.join("\n"))
        }
    }

    private static File makeDownloadDir(String parent) {
        File parentDir = new File(parent)
        if (!parentDir.exists()) {
            parentDir.mkdirs()
        }
        parentDir.deleteOnExit()
        parentDir
    }
}
