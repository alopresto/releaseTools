package org.apache

import com.beust.jcommander.IParameterValidator
import com.beust.jcommander.ParameterException

class NiFiVersionValidator implements IParameterValidator {

    private static final String VERSION_PATTERN = /^[\d]+\.[\d]+\.[\d]+(-SNAPSHOT|-RC[\d]+)?$/

    public void validate(String name, String value) throws ParameterException {
        if (value?.isEmpty() || !(value =~ VERSION_PATTERN)) {
            throw new ParameterException("Parameter " + name + " should be of the format 1.2.3 with optional -SNAPSHOT or -RC1 (found " + value + ")")
        }
    }
}