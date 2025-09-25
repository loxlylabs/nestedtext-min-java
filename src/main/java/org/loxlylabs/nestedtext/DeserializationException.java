package org.loxlylabs.nestedtext;

/**
 * An exception that may be thrown when binding to a Java type.
 */
public class DeserializationException extends RuntimeException {

    DeserializationException() {
    }

    DeserializationException(String message) {
        super(message);
    }

    DeserializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
