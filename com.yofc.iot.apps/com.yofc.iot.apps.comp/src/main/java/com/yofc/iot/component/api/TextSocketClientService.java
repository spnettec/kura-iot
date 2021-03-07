package com.yofc.iot.component.api;

import org.eclipse.kura.KuraConnectException;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;

public interface TextSocketClientService {

    public void setHandle(Handler<String> handler);

    public void write(String buffer) throws KuraConnectException;

    public void sendAndReply(String message, Handler<AsyncResult<String>> handler) throws KuraConnectException;

    public void sendAndReply(String message, long timeOut, Handler<AsyncResult<String>> handler)
            throws KuraConnectException;

}
