package com.yofc.iot.component.api;

import org.eclipse.kura.KuraConnectException;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;

public interface TextSocketClientService {

    public void setHandle(Handler<String> handler);

    public void write(String buffer);

    public void write(String message, Handler<AsyncResult<Void>> handler);

    public void sendAndReply(String message, Handler<AsyncResult<String>> handler);

    public void sendAndReply(String message, long timeOut, Handler<AsyncResult<String>> handler)
            throws KuraConnectException;

}
