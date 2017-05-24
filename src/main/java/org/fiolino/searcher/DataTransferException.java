package org.fiolino.searcher;

/**
 * Created by kuli on 24.05.17.
 */
public class DataTransferException extends Exception {
    public DataTransferException() {
    }

    public DataTransferException(String message) {
        super(message);
    }

    public DataTransferException(String message, Throwable cause) {
        super(message, cause);
    }

    public DataTransferException(Throwable cause) {
        super(cause);
    }
}
