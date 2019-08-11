package com.yofc.iot.apps.enip.cip.epath;

import java.util.ArrayList;
import java.util.List;

import io.netty.buffer.ByteBuf;

public abstract class DataSegment<T> extends EPathSegment {

    public static final int SEGMENT_TYPE = 0x80;

    protected abstract ByteBuf encode(ByteBuf buffer);

    public static ByteBuf encode(DataSegment<?> segment, boolean padded, ByteBuf buffer) {
        return segment.encode(buffer);
    }

    public static final class AnsiDataSegment extends DataSegment<String> {

        public static final int SUBTYPE = 0x11;

        private final List<PathElement> elements = new ArrayList<>();

        public AnsiDataSegment(String data) {
            boolean firstElement = true;
            for (final String s : data.split("\\.")) {
                Integer[] arrIndices = new Integer[0];
                String path = s;
                if (s.contains("[")) {
                    String[] parts = s.split("\\[");
                    path = parts[0];

                    String[] idx = parts[1].replace("]", "").split(",");
                    arrIndices = new Integer[idx.length];
                    for (int i = 0; i < idx.length; i++) {
                        arrIndices[i] = Integer.parseInt(idx[i].trim());
                    }
                }
                this.elements.add(new PathElement(firstElement, path, arrIndices));
                firstElement = false;
            }
        }

        @Override
        protected ByteBuf encode(ByteBuf buffer) {
            for (final PathElement pi : this.elements) {
                pi.encode(buffer);
            }
            return buffer;
        }

    }

    public static class PathElement {

        private static final int MAX_BYTE_VALUE = 255;
        private static final int MAX_INT_VALUE = 65535;
        private final String path;
        private final Integer index[];
        private boolean firstElement;

        public PathElement(final boolean firstElement, final String path, final Integer[] index) {
            this.firstElement = firstElement;
            if (path.matches("^[0-9]+$")) {
                this.path = null;
                this.index = new Integer[] { Integer.parseInt(path) };
            } else {
                this.path = path;
                this.index = index;
            }
        }

        public String getPath() {
            return this.path;
        }

        public Integer[] getIndex() {
            return this.index;
        }

        public void encode(final ByteBuf buf) {
            if (firstElement && path == null) {
                encodeInstanceId(buf);
            } else {
                encodeElementName(buf);
                encodeElementId(buf);
            }
        }

        private void encodeInstanceId(final ByteBuf buf) {
            if (index != null)
                for (Integer idx : index) {
                    buf.writeBytes(new byte[] { 0x20, 0x6B }); // Logical Segment for Symbol Class ID
                    if (idx > MAX_BYTE_VALUE) {
                        buf.writeByte((byte) 0x25);
                        buf.writeByte((byte) 0);
                        buf.writeShort(idx.shortValue());
                    } else {
                        buf.writeByte((byte) 0x24);
                        buf.writeByte(idx.byteValue());
                    }
                }
        }

        private void encodeElementName(final ByteBuf buf) {
            if (path != null) {
                // spec 4 p.21: "ANSI extended symbol segment"
                buf.writeByte((byte) 0x91);
                buf.writeByte((byte) path.length());
                buf.writeBytes(path.getBytes());
                if (this.needPad()) {
                    buf.writeByte((byte) 0);
                }
            }
        }

        private void encodeElementId(final ByteBuf buf) {
            if (index != null)
                for (Integer idx : index) {
                    if (idx != null) {
                        if (idx > MAX_INT_VALUE) {
                            buf.writeByte((byte) 0x2A);
                            buf.writeByte((byte) 0);
                            buf.writeShort(idx.shortValue());
                        } else if (idx > MAX_BYTE_VALUE) {
                            buf.writeByte((byte) 0x29);
                            buf.writeByte((byte) 0);
                            buf.writeShort(idx.shortValue());
                        } else {
                            buf.writeByte((byte) 0x28);
                            buf.writeByte(idx.byteValue());
                        }
                    }
                }
        }

        /** @return Is path of odd length, requiring a pad byte? */
        private boolean needPad() {
            return (path.length() % 2) != 0;
        }

        @Override
        public String toString() {
            if (this.index == null) {
                return this.path;
            }
            return this.path + "[" + this.index + "]";
        }
    }

    public static final class SimpleDataSegment extends DataSegment<short[]> {

        private final short[] data;

        public SimpleDataSegment(short[] data) {
            this.data = data;
        }

        @Override
        protected ByteBuf encode(ByteBuf buffer) {
            buffer.writeByte(SEGMENT_TYPE);
            buffer.writeByte(data.length);

            for (short d : data) {
                buffer.writeShort(d);
            }

            return buffer;
        }

    }

}
