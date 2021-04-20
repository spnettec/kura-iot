package com.yofc.vertx.osgi.api;

/** Created by beders on 7/6/15. */
public interface MessageHandler<T> extends DefaultHandler<T> {

  void handle(EventBusBridge.EventBusMessage<T> message);

  @Override
  default void invoke(EventBusBridge.EventBusMessage<T> message, EventBusBridge eb) {
    handle(message);
  }
}
