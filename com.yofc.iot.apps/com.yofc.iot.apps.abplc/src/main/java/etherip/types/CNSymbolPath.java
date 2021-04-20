/*******************************************************************************
 * Copyright (c) 2012 Oak Ridge National Laboratory.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package etherip.types;

import io.netty.buffer.ByteBuf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Control Net Path for element path. Support symbolic and numeric address elements in any part.
 *
 * <p>Examples (with suitable static import):
 *
 * <p><code>CNPath path = Symbol.name("my_tag")</code> <code>
 * CNPath path = Symbol.name("my_tag[1].Name")</code> <code>CNPath path = Symbol.name("123.1.Name")
 * </code>
 *
 * @author Kay Kasemir
 */
public class CNSymbolPath extends CNPath {

  private final List<PathElement> elements = new ArrayList<>();

  /**
   * Initialize
   *
   * @param symbol Name of symbol
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
  public void encode(final ByteBuf buf) {
    int lengthStartIndex = buf.writerIndex();
    buf.writeByte((byte) 0);
    int dataStartIndex = buf.writerIndex();
    // buf.writeByte((byte) (this.getRequestSize() / 2));
    for (final PathElement pi : this.elements) {
      pi.encode(buf);
    }
    int bytesWritten = buf.writerIndex() - dataStartIndex;
    int wordsWritten = bytesWritten / 2;
    buf.markWriterIndex();
    buf.writerIndex(lengthStartIndex);
    buf.writeByte(wordsWritten);
    buf.resetWriterIndex();
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
  public void decode(final ByteBuf buf) {
    // TODO Auto-generated method stub

  }

  /**
   * One element of a path
   *
   * <p>Contains a string path and an optional array index
   */
  public static class PathElement {

    private static final int MAX_BYTE_VALUE = 255;
    private static final int MAX_INT_VALUE = 65535;
    private final String path;
    private final Integer[] index;
    private final boolean firstElement;

    public PathElement(final boolean firstElement, final String path, final Integer[] index) {
      this.firstElement = firstElement;
      if (path.matches("^[0-9]+$")) {
        this.path = null;
        this.index = new Integer[] {Integer.parseInt(path)};
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
      if (index != null) {
        for (Integer idx : index) {
          buf.writeBytes(new byte[] {0x20, 0x6B}); // Logical Segment for Symbol Class ID
          if (idx > MAX_BYTE_VALUE) {
            buf.writeByte((byte) 0x25);
            buf.writeByte((byte) 0);
            buf.writeShortLE(idx.shortValue());
          } else {
            buf.writeByte((byte) 0x24);
            buf.writeByte(idx.byteValue());
          }
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
      if (index != null) {
        for (Integer idx : index) {
          if (idx != null) {
            if (idx > MAX_INT_VALUE) {
              buf.writeByte((byte) 0x2A);
              buf.writeByte((byte) 0);
              buf.writeShortLE(idx.shortValue());
            } else if (idx > MAX_BYTE_VALUE) {
              buf.writeByte((byte) 0x29);
              buf.writeByte((byte) 0);
              buf.writeShortLE(idx.shortValue());
            } else {
              buf.writeByte((byte) 0x28);
              buf.writeByte(idx.byteValue());
            }
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
}
