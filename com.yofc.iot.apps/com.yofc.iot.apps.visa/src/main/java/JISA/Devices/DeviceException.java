package JISA.Devices;

public class DeviceException extends Exception {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    private String message;

    public DeviceException(String message, Object... args) {

        this.message = String.format(message, args);
    }

    public String getMessage() {
        return message;
    }
}
