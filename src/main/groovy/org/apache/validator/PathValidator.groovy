package org.apache.validator

import com.beust.jcommander.IParameterValidator
import com.beust.jcommander.ParameterException

class PathValidator implements IParameterValidator {

    public void validate(String name, String value) throws ParameterException {
        try {
            new File(value)
        } catch (NullPointerException e) {
            throw new ParameterException("Parameter " + name + " must be a valid file path (found " + value + ")")
        }
    }
}