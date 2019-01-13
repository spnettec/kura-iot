package com.yofc.iot.component.api;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;

import java.util.List;

/**
 * An example of asynchronous OSGi service.
 */
public interface DataService {

  void retrieve(Handler<AsyncResult<List<String>>> resultHandler);

}
