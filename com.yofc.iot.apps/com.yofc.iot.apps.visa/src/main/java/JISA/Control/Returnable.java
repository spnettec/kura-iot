package JISA.Control;

import JISA.Devices.DeviceException;

import java.io.IOException;

public interface Returnable<T> {

  T get() throws IOException, DeviceException;
}
