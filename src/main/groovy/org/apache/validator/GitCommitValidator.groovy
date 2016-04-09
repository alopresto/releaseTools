package org.apache.validator

import com.beust.jcommander.IParameterValidator
import com.beust.jcommander.ParameterException

class GitCommitValidator implements IParameterValidator {

    private static final String COMMIT_PATTERN = /^(?i)[\da-f]{40}$/

    public void validate(String name, String value) throws ParameterException {
        if (value?.isEmpty() || !(value =~ COMMIT_PATTERN)) {
            throw new ParameterException("Parameter " + name + " should be a complete git commit ID (found " + value + ")")
        }
    }
}