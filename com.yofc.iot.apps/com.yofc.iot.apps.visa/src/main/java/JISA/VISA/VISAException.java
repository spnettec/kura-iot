package JISA.VISA;

public class VISAException extends Exception {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    public VISAException(String message, Object... params) {
        super(String.format(message, params));
    }
}
