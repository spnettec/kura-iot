package etherip.util;

public class CodedcException extends RuntimeException {

    public CodedcException() {
        super();
    }

    public CodedcException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

    public CodedcException(String message, Throwable cause) {
        super(message, cause);
    }

    public CodedcException(String message) {
        super(message);
    }

    public CodedcException(Throwable cause) {
        super(cause);
    }

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

}
