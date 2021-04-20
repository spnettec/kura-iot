package com.yofc.iot.apps.plc.protocols.s7.netty;

/** when exceptions within netty-implementations are thrown this class will be used */
public class Plc4XNettyException extends RuntimeException {

  /** */
  private static final long serialVersionUID = -1406690626482851913L;

  /**
   * Constructs a new runtime exception with the specified detail message. The cause is not
   * initialized, and may subsequently be initialized by a call to {@link #initCause}.
   *
   * @param message the detail message. The detail message is saved for later retrieval by the
   *     {@link #getMessage()} method.
   */
  public Plc4XNettyException(String message) {
    super(message);
  }
}
