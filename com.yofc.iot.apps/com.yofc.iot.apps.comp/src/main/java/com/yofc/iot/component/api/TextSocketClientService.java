package com.yofc.iot.component.api;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import org.eclipse.kura.KuraConnectException;

public interface TextSocketClientService {

  void setHandle(Handler<String> handler);

  void write(String buffer);

  void write(String message, Handler<AsyncResult<Void>> handler);

  void sendAndReply(String message, Handler<AsyncResult<String>> handler);

  void sendAndReply(String message, long timeOut, Handler<AsyncResult<String>> handler)
      throws KuraConnectException;
}
