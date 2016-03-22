package org.apache

public enum MessageDigestAlgorithm {
    SHA1("SHA-1"),
    SHA256("SHA-256"),
    SHA512("SHA-512"),
    MD5("MD5")

    private String algorithm

    public MessageDigestAlgorithm(String algorithm) {
        this.algorithm = algorithm
    }
}