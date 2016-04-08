package org.apache

import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter
import groovy.io.GroovyPrintStream
import org.apache.commons.io.FileUtils
import org.apache.log4j.Logger
import org.apache.maven.shared.invoker.*
import org.apache.validator.NiFiVersionValidator
import org.apache.validator.PathValidator
import org.bouncycastle.util.encoders.Hex

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

class NiFiReleaseVerifier {

    private static final Logger logger = Logger.getLogger(NiFiReleaseVerifier.class)

    @Parameter(names = ["--release", "-r"], description = "Version to verify (e.g. 0.6.0)", required = true, validateWith = NiFiVersionValidator.class)
    private String version

    @Parameter(names = ["--verbose", "-v"], description = "Enables verbose output (default false)", required = false, arity = 0)
    private boolean verbose = false

    @Parameter(names = ["--path", "-p"], description = "Base path to work (e.g. ~/scratch, will be created if it does not exist)", validateWith = PathValidator.class)
    private String workBasePath = "~/Workspace/scratch/release_verification"

    private String releaseArtifact
    private String releaseSignature
    private List<String> releaseChecksums = []

    private static final String BASE_URL = "https://dist.apache.org/repos/dist/dev/nifi/nifi-"
    private static final List<String> DEFAULT_SIGNATURE_EXTENSIONS = ["asc", "md5", "sha1", "sha256"]

    private static final String KEYS_URL = "https://dist.apache.org/repos/dist/dev/nifi/KEYS"
    private static final String KEYS_PATH = "KEYS"

    // This is to allow tests to intercept the instance
    private static NiFiReleaseVerifier verifier

    protected boolean verifyReleaseGPGSignatures() {
        // TODO: Redundant check?
        // Ensure signature file present
        boolean signatureFilePresent = formFileFromPath(releaseSignature).exists()

        // Verify signature
        final boolean signatureVerified = verifyGPGSignature(releaseArtifact, releaseSignature)
        if (!signatureFilePresent || !signatureVerified) {
            logger.error("Signature file present: ${releaseSignature} ${signatureFilePresent}")
            logger.error("Signature verified: ${signatureVerified}")
            throw new Exception("Could not verify GPG signature for release artifact")
        }
    }

    private boolean verifyGPGSignature(String targetFilePath, String signatureFilePath = "") {
        if (!targetFilePath) {
            throw new IllegalArgumentException("Target file must be specified")
        }

        File targetFile = formFileFromPath(targetFilePath)

        if (!targetFile.exists()) {
            throw new IllegalArgumentException("Target file [${targetFile.path}] does not exist")
        }

        if (!signatureFilePath) {
            signatureFilePath = targetFile.path + ".asc"
        }

        File signatureFile = formFileFromPath(signatureFilePath)

        if (!signatureFile.exists()) {
            throw new IllegalArgumentException("Signature file [${signatureFile.path}] does not exist")
        }

        // TODO: Replace with BC GPG code?
        // TODO: Sanitize paths before using in system call
        final String GPG_VERIFY_CMD = "gpg --verify ${signatureFile.path} ${targetFile.path}"
        logger.debug("GPG verify command: ${GPG_VERIFY_CMD}")

        try {
            int exitValue = runSystemCommand(GPG_VERIFY_CMD, "Error verifying GPG signature")
            return exitValue == 0
        } catch (Exception e) {
            return false
        }
    }

    private static boolean containsTrustWarning(List<String> strings) {
        strings.any { it =~ "WARNING: This key is not certified with a trusted signature" }
    }

    public File formFileFromPath(String targetPath) {
        new File(targetPath).exists() || targetPath.startsWith(workBasePath) ? new File(targetPath) : new File(workBasePath, targetPath)
    }

    private
    static boolean verifyChecksum(byte[] content, String checksum, String algorithm = MessageDigestAlgorithm.SHA1) {
        MessageDigest md = MessageDigest.getInstance(algorithm)
        byte[] calculatedChecksum = md.digest(content)
        logger.debug("Calculated checksum (${algorithm}): ${Hex.toHexString(calculatedChecksum)}")
        MessageDigest.isEqual(calculatedChecksum, Hex.decode(checksum))
    }

    protected void parseCommandLineArgs(String[] args) {
        // JCommander
        logger.debug("Using JCommander to parse ${args.length} args: [${(args as List).join(", ")}]")
        new JCommander(this, args)
        logger.debug("Parsed ${args.length} args")

        logger.debug("Release version: ${verifier.version}")
    }

    private boolean createPath(String path) {
        File work = new File(path)
        if (!work.exists()) {
            return work.mkdirs()
        } else {
            return true
        }
    }

    private boolean cleanPath(String path) {
        File work = new File(path)
        if (!work.exists() || !work.isDirectory()) {
            return false
        } else {
            try {
                FileUtils.cleanDirectory(work)
                return true
            } catch (IOException e) {
                logger.error("Could not clean path ${path}", e)
                return false
            }
        }
    }

    private boolean setupWorkPath() {
        boolean created = createPath(workBasePath)
        boolean cleaned = cleanPath(workBasePath)
        logger.debug("Created path: ${workBasePath} ${created}")
        logger.debug("Cleaned path: ${workBasePath} ${cleaned}")
        created && cleaned
    }

    private File downloadFile(String url, String targetPath = workBasePath) {
        // TODO: Enhance validation?
        String filename = url.split("/").last()

        File file = new File(targetPath, filename)
        OutputStream destinationStream = file.newOutputStream()
        destinationStream << new URL(url).openStream()
        destinationStream.close()

        file
    }

    protected static List<String> populateReleaseFiles(String version) {
        final String fileEnding = "source-release.zip"
        final String sourceFileUrl = "${BASE_URL}${version}/nifi-${version}-${fileEnding}"
        generateSignatureFiles(sourceFileUrl) + sourceFileUrl
    }

    protected
    static List<String> generateSignatureFiles(String baseUrl, List<String> signatureExtensions = DEFAULT_SIGNATURE_EXTENSIONS) {
        signatureExtensions?.collect { String extension ->
            "${baseUrl}.${extension}"
        }
    }

    public List<String> downloadReleaseFiles(List<String> releaseFiles) {
        releaseFiles.each { String file ->
            downloadFile(file)
        }

        // Translate the URL into the downloaded file name
        releaseFiles.collect { it.tokenize("/").last() }
    }

    public void verifyRelease() {
        if (!setupWorkPath()) {
            throw new Exception("Unable to setup the work base path: ${workBasePath}")
        }

        downloadSigningKeys()
        importSigningKeys()

        List<String> releaseUrls = populateReleaseFiles(version)
        List<String> releaseFiles = downloadReleaseFiles(releaseUrls)
        assignReleaseFiles(releaseFiles)

        verifyReleaseGPGSignatures()
        verifyChecksums()
        unzipSource()
        verifyContribCheck()
        verifyApacheArtifacts()
    }

    protected boolean verifyApacheArtifacts(String sourceDirPath = getSourceDirPath(), Map<String, List<String>> files = generateRepresentativeStrings()) {
        // Ensure the required files are present and contain representative language
        def eachFile = files.collectEntries { String filePath, List<String> representativeStrings ->
            File file = new File(sourceDirPath, filePath)
            if (!file.exists()) {
                logger.error("Required file missing: ${file.path}")
                return [filePath, [false]]
            }

            final String text = file.text
            logger.debug("Content (${file.name}): ${text}")
            def containsLines = representativeStrings.collect { String line ->
                boolean contains = text.contains(line)
                logger.debug("Testing (${file.name}) for \"${line}\": ${contains}")
                contains
            }
            logger.debug("Contains lines: ${containsLines}")

            [filePath, containsLines]
        }
        logger.debug("Each file: ${eachFile}")

        eachFile.every { fp, contains -> contains.every() }
    }

    private LinkedHashMap<String, ArrayList<String>> generateRepresentativeStrings() {
        Map<String, List<String>> files = [
                "README.md": ["Apache NiFi is an easy to use, powerful, and reliable system to process and distribute data.",
                              "It supports highly configurable directed graphs of data routing, transformation, and system mediation logic.",
                              "You may obtain a copy of the License at\n" +
                                      "\n" +
                                      "  http://www.apache.org/licenses/LICENSE-2.0",
                              "This distribution includes cryptographic software. The country in which you \n" +
                                      "currently reside may have restrictions on the import, possession, use, and/or\n" +
                                      "re-export to another country, of encryption software."],
                "LICENSE"  : ["Apache License\n" +
                                      "                           Version 2.0, January 2004\n" +
                                      "                        http://www.apache.org/licenses/",
                              "Grant of Patent License. Subject to the terms and conditions of\n" +
                                      "      this License, each Contributor hereby grants to You a perpetual,\n" +
                                      "      worldwide, non-exclusive, no-charge, royalty-free, irrevocable\n" +
                                      "      (except as stated in this section) patent license"],
                "NOTICE"   : ["Apache NiFi\n" +
                                      "Copyright 2014-2016 The Apache Software Foundation"]
        ]
        files
    }

    protected boolean verifyContribCheck() {
        runMavenCommand() == 0
    }

    private int runMavenCommand(String pomPath = getSourcePomFile().path, Map<String, List<String>> options = [:]) {
        InvocationRequest request = new DefaultInvocationRequest()
        request.setPomFile(formFileFromPath(pomPath))

        // TODO: Read from options map

        // Equivalent to "mvn clean install -Pcontrib-check
        request.setGoals(["clean", "install"])
        request.setProfiles(["contrib-check"])
//        request.setThreads("2.0C")

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream()
        GroovyPrintStream printStream = new GroovyPrintStream(outputStream, true)
        InvocationOutputHandler outputHandler = new PrintStreamHandler(printStream, true)

        Invoker invoker = new DefaultInvoker()
        invoker.setOutputHandler(outputHandler)
        invoker.setMavenHome(getMavenHome())
        InvocationResult result = invoker.execute(request)

        logger.debug("\n" + new String(outputStream.toByteArray(), StandardCharsets.UTF_8))

        int exitStatus = result.getExitCode()
        if (exitStatus != 0) {
            logger.error("Build exited with status ${exitStatus}")
            logger.error("Exception: ${result.executionException}")
            // TODO: Parse the results of the rat file & checkstyle
            throw new IllegalStateException("Build with contrib-check failed")
        }

        exitStatus
    }

    private static File getMavenHome() {
        new File(System.getenv("M3_HOME"))
    }

    private File getSourcePomFile() {
        new File(getSourceDirPath(), "pom.xml")
    }

    private String getSourceDirPath() {
        "${workBasePath}/nifi-${version}"
    }

    protected void unzipSource() {
        final String UNZIP_CMD = "unzip -q ${releaseArtifact} -d ${workBasePath}"
        logger.debug("Unzip command: ${UNZIP_CMD}")

        runSystemCommand(UNZIP_CMD, "Error unzipping source file ${releaseArtifact}")
    }

    private
    static int runSystemCommand(String command, String errorMessage = "Exception executing command: ${command}", StringBuffer output = new StringBuffer(), StringBuffer error = new StringBuffer()) {
        logger.debug("Executing system command: ${command}")

        // TODO: Sanitize/whitelist commands
        def proc = command.execute()
        proc.waitForProcessOutput(output, error)

        def errorLines = error.readLines()

        int exitValue = proc.exitValue()
        if (exitValue != 0) {
            logger.error(errorMessage)
            logger.error(errorLines.join("\n"))
            throw new Exception(errorMessage)
        }

        exitValue
    }

    protected void verifyChecksums() {
        byte[] artifactBytes = new File(releaseArtifact).bytes
        releaseChecksums.each { String checksumPath ->
            File checksumFile = formFileFromPath(checksumPath)
            String checksum = checksumFile.text
            MessageDigestAlgorithm checksumAlgorithm = MessageDigestAlgorithm.getInstance(checksumPath.split(".").last())
            verifyChecksum(artifactBytes, checksum, checksumAlgorithm.name())
        }
    }

    private void assignReleaseFiles(List<String> releaseFiles) {
// Split the files into the correct holders
        releaseArtifact = releaseFiles.find { it.endsWith(".zip") }
        releaseSignature = releaseFiles.find { it.endsWith(".asc") }
        releaseFiles.removeAll([releaseArtifact, releaseSignature])
        releaseChecksums = releaseFiles
    }

    protected int importSigningKeys() {
        // TODO: Replace with BC GPG code?
        // TODO: Sanitize paths before using in system call
        final String GPG_IMPORT_CMD = "gpg --import ${generatePath(KEYS_PATH)}"
        logger.debug("GPG import command: ${GPG_IMPORT_CMD}")

        StringBuffer outputStream = new StringBuffer()
        StringBuffer errorStream = new StringBuffer()

        runSystemCommand(GPG_IMPORT_CMD, "Error importing signing keys", outputStream, errorStream)

        def keyCounts = parseProcessedKeysCount(errorStream.readLines())
        logger.info("Key counts: ${keyCounts}")

        keyCounts["imported"] ?: 0
    }

    protected static Map<String, Integer> parseProcessedKeysCount(List<String> output) {
        def patterns = [processed: /gpg: Total number processed: (\d+)/, imported: /gpg:\s+imported: (\d+).*/, unchanged: /gpg:\s+unchanged: (\d+)/]
        def matcher
        Map<String, Integer> keyCounts = [:]

        output = output.findAll { it != ~/^gpg: key/ }

        patterns.each { String name, String pattern ->
            output.find { String line ->
                matcher = line =~ pattern
                if (matcher.matches()) {
                    keyCounts += [(name): Integer.parseInt(matcher.group(1))]
                    return true
                }
            }
        }
        keyCounts
    }

    protected File downloadSigningKeys() {
        downloadFile(KEYS_URL)
    }

    String generatePath(String s) {
        if (s?.startsWith(workBasePath)) {
            return s
        } else {
            return new File(workBasePath, s).getPath()
        }
    }

    public static void main(String[] args) {
        if (!verifier) {
            verifier = new NiFiReleaseVerifier()
        }

        try {
            verifier.parseCommandLineArgs(args)

            verifier.verifyRelease()

//            logger.info("Returned to main")
            System.exit(0)
        } catch (Exception e) {
            logger.error("Encountered an exception: ${e.getMessage()}")
            logger.debug(e.getStackTrace().join("\n"))
            System.exit(1)
        }
    }
// TODO: Implement
    def methodMissing(String name, args) {
        if (name == "debug") {
            if (verbose) {
                logger.debug(args)
            }
        } else {
            throw new MissingMethodException(name, this.class, args)
        }
    }

}
