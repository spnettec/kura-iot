package com.yofc.iot.apps.enip.cpf;

import io.netty.buffer.ByteBuf;

import java.util.Arrays;

public final class SockAddr {

  private final int sinFamily;
  private final int sinPort;
  private final byte[] sinAddr;
  private final long sinZero;
  public SockAddr(int sinFamily, int sinPort, byte[] sinAddr, long sinZero) {
    this.sinFamily = sinFamily;
    this.sinPort = sinPort;
    this.sinAddr = sinAddr;
    this.sinZero = sinZero;
  }

  public static ByteBuf encode(SockAddr sockAddr, ByteBuf buffer) {
    buffer.writeShortLE(sockAddr.getSinFamily());
    buffer.writeShortLE(sockAddr.getSinPort());
    buffer.writeBytes(sockAddr.getSinAddr());
    buffer.writeLongLE(sockAddr.getSinZero());

    return buffer;
  }

  public static SockAddr decode(ByteBuf buffer) {
    int sinFamily = buffer.readUnsignedShortLE();
    int sinPort = buffer.readUnsignedShortLE();
    byte[] sinAddr = new byte[4];
    buffer.readBytes(sinAddr);
    long sinZero = buffer.readLongLE();

    return new SockAddr(sinFamily, sinPort, sinAddr, sinZero);
  }

  private static final int[] unsignedByteToInt(byte[] b) {
    int len = b.length;
    int[] result = new int[len];
    for (int i = 0; i < len; i++) {
      result[i] = b[i] & 0xFF;
    }
    return result;
  }

  @Override
  public String toString() {
    return "SockAddr [sinFamily="
        + sinFamily
        + ", sinPort="
        + sinPort
        + ", sinAddr="
        + Arrays.toString(unsignedByteToInt(sinAddr))
        + ", sinZero="
        + sinZero
        + "]";
  }

  public int getSinFamily() {
    return sinFamily;
  }

  public int getSinPort() {
    return sinPort;
  }

  public byte[] getSinAddr() {
    return sinAddr;
  }

  public long getSinZero() {
    return sinZero;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    SockAddr sockAddr = (SockAddr) o;

    return sinFamily == sockAddr.sinFamily
        && sinPort == sockAddr.sinPort
        && sinZero == sockAddr.sinZero
        && Arrays.equals(sinAddr, sockAddr.sinAddr);
  }

  @Override
  public int hashCode() {
    int result = sinFamily;
    result = 31 * result + sinPort;
    result = 31 * result + Arrays.hashCode(sinAddr);
    result = 31 * result + (int) (sinZero ^ (sinZero >>> 32));
    return result;
  }
}
