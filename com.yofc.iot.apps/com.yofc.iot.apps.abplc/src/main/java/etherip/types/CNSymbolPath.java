/*******************************************************************************
 * Copyright (c) 2012 Oak Ridge National Laboratory.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package etherip.types;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Control Net Path for element path. Support symbolic and numeric address elements in any part.
 * <p>
 * Examples (with suitable static import):
 * </p>
 * <p>
 * <code>CNPath path = Symbol.name("my_tag")</code>
 * <code>CNPath path = Symbol.name("my_tag[1].Name")</code>
 * <code>CNPath path = Symbol.name("123.1.Name")</code>
 * </p>
 * 
 * @author Kay Kasemir
 */
public class CNSymbolPath extends CNPath {

    /**
     * One element of a path
     * <p>
     * Contains a string path and an optional array index
     */
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

        public int getEncodedSize() {
            int size = 0;
            if (firstElement && path == null) {
                size += 2; // Add space for Symbol Class ID
            }

            if (path != null) {
                size += path.length() + 2;
                if (needPad()) {
                    size += 1;
                }
            }
            if (index != null && index.length > 0) {
                for (Integer idx : index) {
                    size += 2;

                    if (idx > MAX_BYTE_VALUE) {
                        size += 2;
                    }
                }
            }
            return size;
        }

        public void encode(final ByteBuffer buf) {
            if (firstElement && path == null) {
                encodeInstanceId(buf);
            } else {
                encodeElementName(buf);
                encodeElementId(buf);
            }
        }

        private void encodeInstanceId(final ByteBuffer buf) {
            if (index != null)
                for (Integer idx : index) {
                    buf.put(new byte[] { 0x20, 0x6B }); // Logical Segment for Symbol Class ID
                    if (idx > MAX_BYTE_VALUE) {
                        buf.put((byte) 0x25);
                        buf.put((byte) 0);
                        buf.putShort(idx.shortValue());
                    } else {
                        buf.put((byte) 0x24);
                        buf.put(idx.byteValue());
                    }
                }
        }

        private void encodeElementName(final ByteBuffer buf) {
            if (path != null) {
                // spec 4 p.21: "ANSI extended symbol segment"
                buf.put((byte) 0x91);
                buf.put((byte) path.length());
                buf.put(path.getBytes());
                if (this.needPad()) {
                    buf.put((byte) 0);
                }
            }
        }

        private void encodeElementId(final ByteBuffer buf) {
            if (index != null)
                for (Integer idx : index) {
                    if (idx != null) {
                        if (idx > MAX_INT_VALUE) {
                            buf.put((byte) 0x2A);
                            buf.put((byte) 0);
                            buf.putShort(idx.shortValue());
                        } else if (idx > MAX_BYTE_VALUE) {
                            buf.put((byte) 0x29);
                            buf.put((byte) 0);
                            buf.putShort(idx.shortValue());
                        } else {
                            buf.put((byte) 0x28);
                            buf.put(idx.byteValue());
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

    private final List<PathElement> elements = new ArrayList<>();

    /**
     * Initialize
     *
     * @param symbol
     *                   Name of symbol
     */
    protected CNSymbolPath(final String symbol) {
        boolean firstElement = true;
        for (final String s : symbol.split("\\.")) {
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

    public List<PathElement> getElements() {
        return Collections.unmodifiableList(elements);
    }

    /** {@inheritDoc} */
    @Override
    public int getRequestSize() { // End of string is padded if length is odd
        int count = 0;
        for (final PathElement s : this.elements) {
            count += s.getEncodedSize();
        }
        return count;
    }

    /** {@inheritDoc} */
    @Override
    public void encode(final ByteBuffer buf, final StringBuilder log) {
        buf.put((byte) (this.getRequestSize() / 2));
        for (final PathElement pi : this.elements) {
            pi.encode(buf);
        }
    }

    @Override
    public String toString() {
        final StringBuilder buf = new StringBuilder();
        buf.append("Path Symbol(0x91) ");
        for (final PathElement pi : this.elements) {
            if (buf.length() > 18) {
                buf.append(", ");
            }
            buf.append('\'').append(pi).append('\'');
            if (pi.needPad()) {
                buf.append(", 0x00");
            }
        }
        return buf.toString();
    }

    @Override
    public int getResponseSize(final ByteBuffer buf) throws Exception {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public void decode(final ByteBuffer buf, final int available, final StringBuilder log) throws Exception {
        // TODO Auto-generated method stub

    }
}
