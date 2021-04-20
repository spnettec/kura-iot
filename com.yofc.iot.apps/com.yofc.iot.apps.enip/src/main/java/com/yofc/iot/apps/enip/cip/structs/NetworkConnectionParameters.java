package com.yofc.iot.apps.enip.cip.structs;

public class NetworkConnectionParameters {

  private final int connectionSize;
  private final SizeType sizeType;
  private final Priority priority;
  private final ConnectionType connectionType;
  private final boolean redundantOwner;

  public NetworkConnectionParameters(
      int connectionSize,
      SizeType sizeType,
      Priority priority,
      ConnectionType connectionType,
      boolean redundantOwner) {

    this.connectionSize = connectionSize;
    this.sizeType = sizeType;
    this.priority = priority;
    this.connectionType = connectionType;
    this.redundantOwner = redundantOwner;
  }

  public int getConnectionSize() {
    return connectionSize;
  }

  public SizeType getSizeType() {
    return sizeType;
  }

  public Priority getPriority() {
    return priority;
  }

  public ConnectionType getConnectionType() {
    return connectionType;
  }

  public boolean isRedundantOwner() {
    return redundantOwner;
  }

  public enum SizeType {
    Fixed,
    Variable
  }

  public enum Priority {
    Low,
    High,
    Scheduled,
    Urgent
  }

  public enum ConnectionType {
    Null,
    Multicast,
    PointToPoint,
    Reserved
  }
}
