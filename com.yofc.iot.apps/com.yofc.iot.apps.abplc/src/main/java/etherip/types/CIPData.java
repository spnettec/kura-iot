/*******************************************************************************
 * Copyright (c) 2012 Oak Ridge National Laboratory.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package etherip.types;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public final class CIPData {

    public enum Type {
        BOOL(0x00C1, 1),
        SINT(0x00C2, 1),
        INT(0x00C3, 2),
        DINT(0x00C4, 4),
        REAL(0x00CA, 4),
        BITS(0x00D3, 4),
        // Order of enums matter: BITS is the last numeric type (not-string)
        STRUCT(0x02A0, 0),

        /**
         * Experimental: The ENET doc just shows several structures for TIMER, COUNTER, CONTROL and indicates that the
         * T_CIP_STRUCT = 0x02A0 is followed by two more bytes, shown as "?? ??". Looks like
         * for strings, those are always 0x0FCE, followed by DINT length, characters and more zeroes
         */
        STRUCT_STRING(0x0FCE, 0);

        private final short code;

        public short getCode() {
            return code;
        }

        private final int elementSize;

        public int getElementSize() {
            return elementSize;
        }

        private static final Map<Short, Type> reverse;

        static {
            reverse = new HashMap<>();
            for (final Type t : EnumSet.allOf(Type.class)) {
                reverse.put(t.code, t);
            }
        }

        public static Type forCode(final short code) {
            if (reverse == null) {
                throw new RuntimeException("Unknown CIP type code 0x" + Integer.toHexString(code));
            }
            return reverse.get(code);

        }

        private Type(final int code, final int elementSize) {
            this.code = (short) code;
            this.elementSize = elementSize;
        }

        @Override
        public final String toString() {
            return this.name() + String.format(" (0x%04X)", this.code);
        }
    }

    /** Data type */
    private final Type type;

    /** Number of elements (i.e. number of array elements, not bytes) */
    private final short elements;

    /** Raw data, not including type code or element count */
    private final ByteBuf data;

    public ByteBuf getData() {
        return data;
    }

    /**
     * Initialize empty CIP data
     *
     * @param type
     *            Data {@link Type}
     * @param elements
     *            Number of elements
     * @throws Exception
     *             when type is not handled
     */
    public CIPData(final Type type, final int elements) throws Exception {
        switch (type) {
        case BOOL:
        case SINT:
        case INT:
        case DINT:
        case BITS:
        case REAL:
            this.data = Unpooled.buffer(type.elementSize * elements);
            this.type = type;
            this.elements = (short) elements;
            break;
        default:
            throw new Exception("Type " + type + " not handled");
        }
    }

    public CIPData(final Type type) throws Exception {
        switch (type) {
        case BOOL:
        case SINT:
        case INT:
        case DINT:
        case BITS:
        case REAL:
            this.data = Unpooled.buffer(type.elementSize * 1);
            this.type = type;
            this.elements = (short) 1;
            break;
        case STRUCT:
            this.data = Unpooled.buffer(92);
            this.type = type;
            this.elements = (short) 1;
            break;
        default:
            throw new Exception("Type " + type + " not handled");
        }
    }

    public CIPData(String text) throws Exception {
        this.data = Unpooled.buffer(92);
        this.type = Type.STRUCT;
        this.elements = (short) 1;
        setString(text);

    }

    /**
     * Initialize
     *
     * @param type
     *            Data type
     * @param data
     *            Bytes that contain the raw CIP data
     * @throws Exception
     *             when data is invalid
     */
    public CIPData(final Type type, final byte[] data) {
        this.type = type;
        this.data = Unpooled.wrappedBuffer(data);
        this.elements = this.determineElementCount();
    }

    public CIPData(final Type type, final ByteBuf data) {
        this.type = type;
        this.data = data.unwrap().copy(data.readerIndex(), data.readableBytes());
        this.elements = this.determineElementCount();
    }

    /** @return Number of elements */
    final private short determineElementCount() {
        switch (this.type) {
        case BOOL:
        case SINT:
        case INT:
        case DINT:
        case BITS:
        case REAL:
            return (short) (this.data.capacity() / this.type.elementSize);
        case STRUCT: {
            return 1; // We do not know without byte packing details but at least 1.
        }
        default:
            throw new RuntimeException("Type " + this.type + " not handled");
        }
    }

    /** @return CIP data type */
    public final Type getType() {
        return this.type;
    }

    /**
     * @return Number of elements (numbers in array). Always 1 for String
     */
    public final int getElementCount() {
        return this.elements;
    }

    /** @return <code>true</code> if data type is numeric, <code>false</code> for string */
    public final boolean isNumeric() {
        return this.type.ordinal() <= Type.BITS.ordinal();
    }

    /**
     * Read CIP data as number
     *
     * @param index
     *            Element index 0, 1, ...
     * @return Numeric value of requested element
     * @throws Exception
     *             on error, if data is not numeric
     * @throws IndexOutOfBoundsException
     *             if index is invalid
     */
    public final synchronized Number getNumber(final int index) throws Exception, IndexOutOfBoundsException {
        switch (this.type) {
        case BOOL:
        case SINT:
            return Byte.valueOf(this.data.getByte(this.type.elementSize * index));
        case INT:
            return Short.valueOf(this.data.getShortLE(this.type.elementSize * index));
        case DINT:
        case BITS:
            return Integer.valueOf(this.data.getIntLE(this.type.elementSize * index));
        case REAL:
            return Float.valueOf(this.data.getFloatLE(this.type.elementSize * index));
        default:
            throw new Exception("Cannot retrieve Number from " + this.type);
        }
    }

    public final synchronized Object get() throws Exception {
        switch (this.type) {
        case BOOL:
        case SINT:
            return Byte.valueOf(this.data.getByte(0));
        case INT:
            return Short.valueOf(this.data.getShortLE(0));
        case DINT:
        case BITS:
            return Integer.valueOf(this.data.getIntLE(0));
        case REAL:
            return Float.valueOf(this.data.getFloatLE(0));
        case STRUCT: {
            final int len = this.data.getIntLE(2);

            final short code = this.data.getShortLE(0);
            final Type elType = Type.forCode(code);
            if (len > 0) {
                final byte[] chars = new byte[len];
                for (int i = 0; i < len; ++i) {
                    chars[i] = this.data.getByte(6 + i);
                }
                if (elType == Type.STRUCT_STRING)
                    return new String(chars);
                else
                    return chars;
            } else {
                if (elType == Type.STRUCT_STRING)
                    return "";
                else {
                    final byte[] chars = new byte[1];
                    chars[0] = '\0';
                    return chars;
                }
            }

        }
        default:
            throw new Exception("Cannot retrieve value from " + this.type);
        }
    }

    /**
     * Read CIP data as string
     *
     * @return {@link String}
     * @throws Exception
     *             if data does not contain a string
     */
    public final synchronized String getString() throws Exception {
        if (this.type != Type.STRUCT) {
            throw new Exception("Type " + this.type + " does not contain string");
        }

        final short code = this.data.getShortLE(0);
        final Type el_type = Type.forCode(code);
        if (el_type != Type.STRUCT_STRING) {
            throw new Exception("No string, structure element is of type " + this.type);
        }
        int len = this.data.getIntLE(2);
        if (len > this.data.writerIndex() - 6) {
            len = this.data.writerIndex() - 6;
        }
        final byte[] chars = new byte[len];
        for (int i = 0; i < len; ++i) {
            chars[i] = this.data.getByte(6 + i);
        }
        return new String(chars);
    }

    /**
     * Set CIP data
     *
     * @param index
     *            Element index 0, 1, ...
     * @param value
     *            Numeric value to write to that element
     * @throws Exception
     *             on invalid data type
     * @throws IndexOutOfBoundsException
     *             if index is invalid
     */
    public final synchronized void set(final int index, final Number value)
            throws Exception, IndexOutOfBoundsException {
        switch (this.type) {
        case BOOL:
        case SINT:
            this.data.setByte(this.type.elementSize * index, value.byteValue());
            break;
        case INT:
            this.data.setShortLE(this.type.elementSize * index, value.shortValue());
            break;
        case DINT:
        case BITS:
            this.data.setIntLE(this.type.elementSize * index, value.intValue());
            break;
        case REAL:
            this.data.setFloatLE(this.type.elementSize * index, value.floatValue());
            break;
        default:
            throw new Exception("Cannot set type " + this.type + " to a number");
        }
    }

    /**
     * Write CIP data as string
     *
     * @param text
     *            {@link String}
     * @throws Exception
     *             if data does not contain a string
     */
    public final synchronized void setString(final String text) throws Exception {
        if (this.type != Type.STRUCT) {
            throw new Exception("Type " + this.type + " does not contain string");
        }
        this.data.clear();
        this.data.writeShortLE(Type.STRUCT_STRING.code);

        // Try to fit the text,
        // but limit it to size of buffer,
        // starting at the offset for the text
        // (2 byte STRUCT_STRING, 4 byte length)
        // and allow for the final '\0' byte
        final int len = Math.min(text.length(), 84);
        this.data.writeIntLE(len);

        final byte[] chars = text.getBytes();
        for (int i = 0; i < len; ++i) {
            this.data.writeByte(chars[i]);
        }
        // this.data.writeByte((byte) 0);
        this.data.setIndex(0, this.data.capacity());
    }

    /** @return String representation for debugging */
    @Override
    public final synchronized String toString() {
        final StringBuilder result = new StringBuilder();
        result.append("CIP_").append(this.type).append(": ");
        final ByteBuf buf = this.data.asReadOnly();
        buf.readerIndex(0);
        switch (this.type) {
        case BOOL:
        case SINT: {
            final byte[] values = new byte[this.elements];
            buf.readBytes(values);
            result.append(Arrays.toString(values));
            break;
        }
        case INT: {
            final short[] values = new short[this.elements];
            for (int i = 0; i < this.elements; ++i) {
                values[i] = buf.readShortLE();
            }
            result.append(Arrays.toString(values));
            break;
        }
        case DINT:
        case BITS: {
            final int[] values = new int[this.elements];
            for (int i = 0; i < this.elements; ++i) {
                values[i] = buf.readIntLE();
            }
            result.append(Arrays.toString(values));
            break;
        }
        case REAL: {
            final float[] values = new float[this.elements];
            for (int i = 0; i < this.elements; ++i) {
                values[i] = buf.readFloatLE();
            }
            result.append(Arrays.toString(values));
            break;
        }
        case STRUCT: {
            final short code = buf.readShortLE();
            final Type el_type;
            try {
                el_type = Type.forCode(code);
            } catch (final Exception ex) {
                result.append("Structure element with type code 0x" + Integer.toHexString(code));
                break;
            }
            if (el_type == Type.STRUCT_STRING) {
                result.append(Type.STRUCT_STRING).append(" ");
                int len = buf.readIntLE();
                if (len > buf.readableBytes()) {
                    len = buf.readableBytes();
                }
                final byte[] chars = new byte[len];
                buf.readBytes(chars);
                final String value = new String(chars);
                result.append("'").append(value).append("', len " + len);
            } else {
                result.append("Structure element of type " + this.type);
            }
            break;
        }
        default:
            result.append("Unknown Type " + this.type);
        }
        return result.toString();
    }
}
