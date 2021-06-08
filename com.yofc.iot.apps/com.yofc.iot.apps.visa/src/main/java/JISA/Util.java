package JISA;

import java.io.IOException;
import java.util.HashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import JISA.Devices.DeviceException;
import JISA.VISA.VISAException;

public class Util {

    private static final Logger logger = LoggerFactory.getLogger(Util.class);

    public static void sleep(long msec) {
        try {
            Thread.sleep(msec);
        } catch (Exception e) {
        }
    }

    public static boolean isBetween(double value, double min, double max) {
        return value >= min && value <= max;
    }

    public static boolean isBetween(int value, int min, int max) {
        return value >= min && value <= max;
    }

    public static void exceptionHandler(Exception e) {

        ExType exceptionType = ExType.fromClass(e.getClass());

        switch (exceptionType) {
        case IO_EXCEPTION:
            logger.error("Communication error:{}", e.getMessage());
            break;
        case DEVICE_EXCEPTION:
            logger.error("Device error: {}", e.getMessage());
            break;
        case INTERRUPTED_EXCEPTION:
            logger.error("Waiting error: {}", e.getMessage());
            break;
        case VISA_EXCEPTION:
            logger.error("VISA error: {}", e.getMessage());
            break;
        default:
            logger.error("Unknown error: {}", e.getMessage());
            break;
        }

        logger.error("Stack Trace:", e);
    }

    public static double[] makeLinearArray(double min, double max, int numSteps) {

        double[] values = new double[numSteps];
        double step = (max - min) / (numSteps - 1D);

        values[0] = min;

        for (int i = 1; i < values.length; i++) {
            values[i] = values[i - 1] + step;
        }

        return values;
    }

    public static double[] symArray(double[] array) {

        double[] results = new double[2 * array.length - 1];

        System.arraycopy(array, 0, results, 0, array.length);

        for (int i = 0; i < array.length - 1; i++) {

            int j = array.length + i;
            int k = array.length - 2 - i;
            results[j] = array[k];
        }

        return results;
    }

    public static double[] makeLogarithmicArray(double min, double max, int numSteps) {

        double[] values = new double[numSteps];
        double step = Math.pow(max / min, 1D / numSteps);

        values[0] = min;

        for (int i = 1; i < values.length; i++) {
            values[i] = values[i - 1] * step;
        }

        return values;
    }

    enum ExType {

        IO_EXCEPTION(IOException.class),
        DEVICE_EXCEPTION(DeviceException.class),
        INTERRUPTED_EXCEPTION(InterruptedException.class),
        VISA_EXCEPTION(VISAException.class),
        UNKNOWN_EXCEPTION(Exception.class);

        private static HashMap<Class<?>, ExType> lookup = new HashMap<>();

        static {
            for (ExType e : ExType.values()) {
                lookup.put(e.getClazz(), e);
            }
        }

        private Class<?> clazz;

        ExType(Class<?> c) {
            clazz = c;
        }

        static ExType fromClass(Class<? extends Exception> c) {
            return lookup.getOrDefault(c, UNKNOWN_EXCEPTION);
        }

        Class<?> getClazz() {
            return clazz;
        }
    }
}
