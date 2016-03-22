package org.apache

import org.apache.log4j.Logger
import org.junit.After
import org.junit.Before
import org.junit.Test

class LogLineTest extends GroovyTestCase {

    private static final Logger logger = Logger.getLogger(LogLineTest.class)

    @Before
    void setUp() {
    }

    @After
    void tearDown() {
    }

    @Test
    void testStaticMessageShouldPopulateContents() {
        // Arrange
        String message = "This is a message"
        logger.debug("Set up message '${message}'")

        // Act
        def ll = LogLine.m(message)
        logger.debug("LogLine contents: '${ll.contents}'")

        // Assert
        assert ll.toString() == "message=${message}".toString()
    }

    @Test
    void testStaticMessageWithInterpolationShouldPopulateContents() {
        // Arrange
        String some = "a little bit of"
        String message = "This is a message with ${some} var embedding"
        logger.debug("Set up message '${message}'")

        // Act
        def ll = LogLine.m(message)
        logger.debug("LogLine contents: '${ll.contents}'")

        // Assert
        assert ll.toString() == "message=${message}".toString()
    }

    @Test
    void testStaticMessageWithEmptyMessageShouldPopulateContents() {
        // Arrange
        String message = ""
        logger.debug("Set up message '${message}'")

        // Act
        def ll = LogLine.m(message)
        logger.debug("LogLine contents: '${ll.contents}'")

        // Assert
        assert ll.toString() == "message=${message}".toString()
    }

    @Test
    void testKvShouldPopulateKeyAndSingleValue() {
        // Arrange
        String message = "This is a message"
        logger.debug("Set up message '${message}'")
        def ll = LogLine.m(message)

        String key = "a String key"
        String value = "a String value"

        String safeKey = key.replaceAll(/\s/, '_')

        // Act
        ll = ll.kv(key, value)
        logger.debug("LogLine contents: '${ll.contents}'")

        // Assert
        assert ll.toString() == "message=${message}, ${safeKey}=${value}".toString()
    }

    @Test
    void testKvShouldPopulateKeyAndCollectionValue() {
        // Arrange
        String message = "This is a message"
        logger.debug("Set up message '${message}'")
        def ll = LogLine.m(message)

        String key = "a String key"
        def value = ["multiple", "String", "values"]

        String safeKey = key.replaceAll(/\s/, '_')

        // Act
        ll = ll.kv(key, value)
        logger.debug("LogLine contents: '${ll.contents}'")

        // Assert
        assert ll.toString() == "message=${message}, ${safeKey}=${value.join(", ")}".toString()
    }

    @Test
    void testKvShouldPopulateKeyAndEmptyCollectionValue() {
        // Arrange
        String message = "This is a message"
        logger.debug("Set up message '${message}'")
        def ll = LogLine.m(message)

        String key = "a String key"
        def value = []

        String safeKey = key.replaceAll(/\s/, '_')

        // Act
        ll = ll.kv(key, value)
        logger.debug("LogLine contents: '${ll.contents}'")

        // Assert
        assert ll.toString() == "message=${message}, ${safeKey}=[]".toString()
    }

    @Test
    void testKvShouldPopulateKeyAndNullValue() {
        // Arrange
        String message = "This is a message"
        logger.debug("Set up message '${message}'")
        def ll = LogLine.m(message)

        String key = "a String key"
        def value = null

        String safeKey = key.replaceAll(/\s/, '_')

        // Act
        ll = ll.kv(key, value)
        logger.debug("LogLine contents: '${ll.contents}'")

        // Assert
        assert ll.toString() == "message=${message}, ${safeKey}=null".toString()
    }
}