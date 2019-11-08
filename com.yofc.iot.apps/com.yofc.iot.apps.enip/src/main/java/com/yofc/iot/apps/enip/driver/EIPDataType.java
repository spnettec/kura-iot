
package com.yofc.iot.apps.enip.driver;

import com.yofc.iot.apps.enip.cip.structs.CIPData.Type;

public enum EIPDataType {

    BOOL,
    SINT,
    INT,
    DINT,
    REAL,
    BITS,
    STRUCT,
    STRUCT_STRING;

    public static Type getDataType(String stringDataType) {
        if (BOOL.name().equalsIgnoreCase(stringDataType)) {
            return Type.BOOL;
        }
        if (SINT.name().equalsIgnoreCase(stringDataType)) {
            return Type.SINT;
        }
        if (INT.name().equalsIgnoreCase(stringDataType)) {
            return Type.INT;
        }
        if (DINT.name().equalsIgnoreCase(stringDataType)) {
            return Type.DINT;
        }
        if (REAL.name().equalsIgnoreCase(stringDataType)) {
            return Type.REAL;
        }
        if (BITS.name().equalsIgnoreCase(stringDataType)) {
            return Type.BITS;
        }
        if (STRUCT.name().equalsIgnoreCase(stringDataType)) {
            return Type.STRUCT;
        }
        if (STRUCT_STRING.name().equalsIgnoreCase(stringDataType)) {
            return Type.STRUCT_STRING;
        }

        throw new IllegalArgumentException("Cannot convert to DataType");
    }
}
