package org.fiolino.searcher;

/**
 * Created by kuli on 29.02.16.
 */
public class NoSuchFieldException extends Exception {
    public NoSuchFieldException() {
    }

    public NoSuchFieldException(String message) {
        super(message);
    }

    public NoSuchFieldException(String message, Throwable cause) {
        super(message, cause);
    }

    public NoSuchFieldException(Throwable cause) {
        super(cause);
    }
}
