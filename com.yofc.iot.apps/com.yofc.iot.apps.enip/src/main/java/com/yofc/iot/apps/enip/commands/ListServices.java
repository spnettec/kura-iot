package com.yofc.iot.apps.enip.commands;

import io.netty.buffer.ByteBuf;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

/**
 * The optional List Interfaces command shall be used by a connection originator to identify non-CIP
 * communication interfaces associated with the target. A session need not be established to send
 * this command.
 */
public final class ListServices extends Command {

  private final ServiceInformation[] services;

  public ListServices(ServiceInformation... services) {
    super(CommandCode.ListServices);

    this.services = services;
  }

  public static ByteBuf encode(ListServices command, ByteBuf buffer) {
    if (command.getServices().length != 0) {
      buffer.writeShortLE(command.getServices().length);

      for (ServiceInformation serviceInformation : command.getServices()) {
        ServiceInformation.encode(serviceInformation, buffer);
      }
    }

    return buffer;
  }

  public static ListServices decode(ByteBuf buffer) {
    int itemCount = buffer.readableBytes() >= 2 ? buffer.readUnsignedShortLE() : 0;

    ServiceInformation[] services = new ServiceInformation[itemCount];

    for (int i = 0; i < itemCount; i++) {
      services[i] = ServiceInformation.decode(buffer);
    }

    return new ListServices(services);
  }

  public ServiceInformation[] getServices() {
    return services;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    ListServices that = (ListServices) o;

    return Arrays.equals(services, that.services);
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(services);
  }

  public static class ServiceInformation {

    private final int typeCode;
    private final int version;
    private final int capabilityFlags;
    private final String name;

    public ServiceInformation(int typeCode, int version, int capabilityFlags, String name) {
      this.typeCode = typeCode;
      this.version = version;
      this.capabilityFlags = capabilityFlags;
      this.name = name;
    }

    public static ByteBuf encode(ServiceInformation serviceInformation, ByteBuf buffer) {
      buffer.writeShortLE(serviceInformation.getTypeCode());

      // The 16 bytes of the name plus two shorts.
      buffer.writeShortLE(20);

      // Encode the item...
      buffer.writeShortLE(serviceInformation.getVersion());
      buffer.writeShortLE(serviceInformation.getCapabilityFlags());
      writeString(serviceInformation.getName(), buffer);

      return buffer;
    }

    public static ServiceInformation decode(ByteBuf buffer) {
      int typeCode = buffer.readUnsignedShortLE();
      int itemLength = buffer.readShortLE();
      int version = buffer.readShortLE();
      int capabilityFlags = buffer.readShortLE();
      String name = readString(buffer, itemLength - 4).trim();
      return new ServiceInformation(typeCode, version, capabilityFlags, name);
    }

    private static String readString(ByteBuf buffer, int length) {
      length = Math.min(Math.min(length, 255), buffer.readableBytes());
      byte[] bs = new byte[length];
      buffer.readBytes(bs);
      return new String(bs, StandardCharsets.US_ASCII);
    }

    private static void writeString(String s, ByteBuf buffer) {
      byte[] fullBytes = new byte[16];
      byte[] bytes = s.getBytes(StandardCharsets.US_ASCII);
      System.arraycopy(bytes, 0, fullBytes, 0, Math.min(bytes.length, 16));
      buffer.writeBytes(fullBytes);
    }

    public int getTypeCode() {
      return typeCode;
    }

    public int getVersion() {
      return version;
    }

    public int getCapabilityFlags() {
      return capabilityFlags;
    }

    public String getName() {
      return name;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      ServiceInformation that = (ServiceInformation) o;

      return (typeCode == that.typeCode)
          && (version == that.version)
          && (capabilityFlags == that.capabilityFlags)
          && name.equals(that.name);
    }

    @Override
    public int hashCode() {
      return Objects.hash(getTypeCode(), getVersion(), getCapabilityFlags(), getName());
    }
  }
}
