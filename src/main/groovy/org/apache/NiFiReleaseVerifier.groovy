package org.apache

import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter
import org.apache.log4j.Logger

class NiFiReleaseVerifier {

    private static final Logger logger = Logger.getLogger(NiFiReleaseVerifier.class)

    @Parameter(names = ["--release", "-r"], description = "Version to verify (e.g. 0.6.0)", required = true, validateWith = NiFiVersionValidator.class)
    private String version

    @Parameter(names = ["--verbose", "-v"], description = "Enables verbose output (default false)", required = false, arity = 0)
    private boolean verbose = false

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

    public static void main(String[] args) {
        if (!verifier) {
            verifier = new NiFiReleaseVerifier()
        }

        try {
            verifier.parseCommandLineArgs(args)
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
