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

    @Parameter(names = ["--path", "-p"], description = "Base path to work (e.g. ~/scratch, will be created if it does not exist)", required = true, validateWith = PathValidator.class)
    private String workBasePath = "~/Workspace/scratch/release_verification"

    private static final String BASE_URL = "https://dist.apache.org/repos/dist/dev/nifi/nifi-"
    private static final List<String> DEFAULT_SIGNATURE_EXTENSIONS = ["asc", "md5", "sha1", "sha256"]

    // This is to allow tests to intercept the instance
    private static NiFiReleaseVerifier verifier

    private boolean verifyGPGSignature() {

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

    private void setupWorkPath() {
        createPath(workBasePath) && cleanPath(workBasePath)
    }

    private void downloadFile(String url) {
        // TODO: Enhance validation?
        String filename = url.split("/", 2).last()
        def destinationStream = new File(workBasePath, filename).newOutputStream()
        destinationStream << new URL(url).openStream()
        destinationStream.close()
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

    public static void main(String[] args) {
        if (!verifier) {
            verifier = new NiFiReleaseVerifier()
        }

        try {
            verifier.parseCommandLineArgs(args)

            if (!verifier.setupWorkPath()) {
                logger.error("Unable to setup the work base path: ${verifier.workBasePath}")
                System.exit(3)
            }

            verifier.downloadReleaseFiles()

            logger.info("Returned to main")
            System.exit(0)
        } catch (Exception e) {
            logger.error("Encountered an exception: ${e.getMessage()}")
            System.exit(2)
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
