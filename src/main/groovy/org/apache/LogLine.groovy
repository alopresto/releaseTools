package org.apache

class LogLine {

    private String contents

    protected LogLine(Object param) {
        contents = param.toString()
    }

    public static LogLine m(Object param) {
        new LogLine("message=${param?.toString()}")
    }

    public LogLine kv(Object key, Object value) {
        if (value && isCollectionOrArray(value)) {
            value = (value as Collection).join(", ")
        }
        contents += ", ${key?.toString()?.replaceAll(/\s/, '_')}=${value?.toString()}"
        this
    }

    private static boolean isCollectionOrArray(object) {
        [Collection, Object[]].any { it.isAssignableFrom(object?.getClass()) }
    }

    public String toString() {
        contents
    }

    public String s() {
        toString()
    }
}