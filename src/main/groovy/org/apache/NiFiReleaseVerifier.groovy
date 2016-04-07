package org.apache

import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter
import org.apache.commons.io.FileUtils
import org.apache.log4j.Logger
import org.apache.validator.NiFiVersionValidator
import org.apache.validator.PathValidator

class NiFiReleaseVerifier {

    private static final Logger logger = Logger.getLogger(NiFiReleaseVerifier.class)

    @Parameter(names = ["--release", "-r"], description = "Version to verify (e.g. 0.6.0)", required = true, validateWith = NiFiVersionValidator.class)
    private String version

    @Parameter(names = ["--verbose", "-v"], description = "Enables verbose output (default false)", required = false, arity = 0)
    private boolean verbose = false

    @Parameter(names = ["--path", "-p"], description = "Base path to work (e.g. ~/scratch, will be created if it does not exist)", validateWith = PathValidator.class)
    private String workBasePath = "~/Workspace/scratch/release_verification"

    private static final String BASE_URL = "https://dist.apache.org/repos/dist/dev/nifi/nifi-"
    private static final List<String> DEFAULT_SIGNATURE_EXTENSIONS = ["asc", "md5", "sha1", "sha256"]

    private static final String KEYS_URL = "https://dist.apache.org/repos/dist/dev/nifi/KEYS"
    private static final String KEYS_PATH = "KEYS"

    // This is to allow tests to intercept the instance
    private static NiFiReleaseVerifier verifier

    protected boolean verifyReleaseGPGSignatures() {

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

        def proc = GPG_VERIFY_CMD.execute()
        def outputStream = new StringBuffer();
        def errorStream = new StringBuffer();
        proc.waitForProcessOutput(outputStream, errorStream)

        def errorLines = errorStream.readLines()
        logger.debug(errorLines.join("\n"))

        if (containsTrustWarning(errorLines)) {
            logger.warn(errorLines.join("\n"))
        }

        return proc.exitValue() == 0
    }

    private static boolean containsTrustWarning(List<String> strings) {
        strings.any { it =~ "WARNING: This key is not certified with a trusted signature" }
    }

    public File formFileFromPath(String targetPath) {
        new File(targetPath).exists() || targetPath.startsWith(workBasePath) ? new File(targetPath) : new File(workBasePath, targetPath)
    }

    private boolean verifyChecksum(String artifact, String checksum, String algorithm = MessageDigestAlgorithm.SHA1) {

    }

    private boolean verifyContribCheck() {

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

    protected List<String> populateReleaseFiles(String version) {
//        RELEASE_FILES.collect { String fileEnding ->
//            SIGNATURE_EXTENSIONS.collect { String extension ->
//                "${BASE_URL}${version}/nifi-${version}${fileEnding}${extension ? ".${extension}" : ""}"
//            }
//        }.flatten()
    }

    protected
    static List<String> generateSignatureFiles(String baseUrl, List<String> signatureExtensions = DEFAULT_SIGNATURE_EXTENSIONS) {
        signatureExtensions?.collect { String extension ->
            "${baseUrl}.${extension}"
        }
    }

    public void downloadReleaseFiles() {
        List<String> releaseFiles = populateReleaseFiles(version)
        releaseFiles.each { String file ->
            downloadFile(String)
        }
    }

    public void verifyRelease() {
        if (!setupWorkPath()) {
            throw new Exception("Unable to setup the work base path: ${workBasePath}")
        }

        downloadSigningKeys()
        importSigningKeys()

        downloadReleaseFiles()
        verifyReleaseGPGSignatures()
        verifySignatures()
        unzipSource()
        runContribCheck()
        verifyApacheArtifacts()
    }

    protected int importSigningKeys() {
        // TODO: Replace with BC GPG code?
        // TODO: Sanitize paths before using in system call
        final String GPG_IMPORT_CMD = "gpg --import ${generatePath(KEYS_PATH)}"
        logger.debug("GPG import command: ${GPG_IMPORT_CMD}")

        def proc = GPG_IMPORT_CMD.execute()
        def outputStream = new StringBuffer();
        def errorStream = new StringBuffer();
        proc.waitForProcessOutput(outputStream, errorStream)

        def errorLines = errorStream.readLines()
        logger.debug(errorLines.join("\n"))

        def keyCounts = parseProcessedKeysCount(errorLines)
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
