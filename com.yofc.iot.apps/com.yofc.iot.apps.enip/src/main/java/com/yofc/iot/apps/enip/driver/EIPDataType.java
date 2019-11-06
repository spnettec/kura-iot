
package com.yofc.iot.apps.enip.driver;

public enum EIPDataType {

    BOOL,
    SINT,
    INT,
    DINT,
    REAL,
    BITS,
    STRUCT,
    STRUCT_STRING;

    public static EIPDataType getDataType(String stringDataType) {
        if (BOOL.name().equalsIgnoreCase(stringDataType)) {
            return BOOL;
        }
        if (SINT.name().equalsIgnoreCase(stringDataType)) {
            return SINT;
        }
        if (INT.name().equalsIgnoreCase(stringDataType)) {
            return INT;
        }
        if (DINT.name().equalsIgnoreCase(stringDataType)) {
            return DINT;
        }
        if (REAL.name().equalsIgnoreCase(stringDataType)) {
            return REAL;
        }
        if (BITS.name().equalsIgnoreCase(stringDataType)) {
            return BITS;
        }
        if (STRUCT.name().equalsIgnoreCase(stringDataType)) {
            return STRUCT;
        }
        if (STRUCT_STRING.name().equalsIgnoreCase(stringDataType)) {
            return STRUCT_STRING;
        }

        throw new IllegalArgumentException("Cannot convert to DataType");
    }
}
