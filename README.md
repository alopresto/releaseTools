# releaseTools
NiFi release verification tools

These tools assist with automatic the verification of releases of Apache projects, specifically [Apache NiFi](https://nifi.apache.org). The current feature set is specific to NiFi but the framework allows extensibility to be applied to a variety of projects. 

# Steps

These steps are adapted from the release verification helper emails accompanying NiFi releases. 

1. Download latest KEYS file:
 `wget https://dist.apache.org/repos/dist/dev/nifi/KEYS`

1. Import keys file:
 `gpg --import KEYS`

1. _[optional] Clear out local maven artifact repository_

1. Pull down nifi-0.6.1 source release artifacts for review:

* `wget https://dist.apache.org/repos/dist/dev/nifi/nifi-0.6.1/nifi-0.6.1-source-release.zip`
* `wget https://dist.apache.org/repos/dist/dev/nifi/nifi-0.6.1/nifi-0.6.1-source-release.zip.asc`
* `wget https://dist.apache.org/repos/dist/dev/nifi/nifi-0.6.1/nifi-0.6.1-source-release.zip.md5`
* `wget https://dist.apache.org/repos/dist/dev/nifi/nifi-0.6.1/nifi-0.6.1-source-release.zip.sha1`
* `wget https://dist.apache.org/repos/dist/dev/nifi/nifi-0.6.1/nifi-0.6.1-source-release.zip.sha256`

1. Verify the signature
  `gpg --verify nifi-0.6.1-source-release.zip.asc`

1. Verify the hashes (`md5`, `sha1`, `sha256`) match the source and what was provided
in the vote email thread
* `md5sum nifi-0.6.1-source-release.zip`
* `sha1sum nifi-0.6.1-source-release.zip`
* `openssl sha256 nifi-0.6.1-source-release.zip`

1. Unzip nifi-0.6.1-source-release.zip
  `unzip -q nifi-0.6.1-source-release.zip`

1. Verify the build works including release audit tool (RAT) checks
  `cd nifi-0.6.1`
  `mvn clean install -Pcontrib-check`

1. Verify the contents contain a good `README.md`, `NOTICE`, and `LICENSE`.
  _Here the code checks the existence of each file and looks for hard-coded representative strings (long enough that the random occurrence is highly unlikely). This does not compare to a reference implementation of each file because the files change dynamically with each release._

1. Verify the git commit ID is correct
  _Not yet implemented_
  
1. Verify the RC was branched off the correct git commit ID
  _Not yet implemented_

1. Look at the resulting convenience binary as found in nifi-assembly/target
  _Not yet implemented_
  
1. Make sure the README, NOTICE, and LICENSE are present and correct
  _Not yet implemented_
  
1. Run the resulting convenience binary and make sure it works as expected
  _Not yet implemented_