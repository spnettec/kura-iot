package com.yofc.iot.component.impl;

import com.yofc.iot.component.api.ServieTool;
import com.yofc.iot.component.api.TextSocketClientService;
import com.yofc.iot.component.impl.TextSocketClientServiceImpl.Config;
import com.yofc.vertx.osgi.api.AssetHelp;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import io.vertx.core.Future;
import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetSocket;
import io.vertx.ext.web.client.WebClient;
import org.eclipse.kura.KuraConnectException;
import org.eclipse.kura.configuration.ConfigurableComponent;
import org.osgi.service.component.annotations.*;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.BufferOverflowException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;

@Designate(ocd = Config.class, factory = true)
@Component(configurationPolicy = ConfigurationPolicy.REQUIRE, immediate = true)
public class TextSocketClientServiceImpl implements TextSocketClientService, ConfigurableComponent {

  private static final Logger logger = LoggerFactory.getLogger(TextSocketClientServiceImpl.class);
  private final ServieTool serviceTool = new ServieTool();
  private final BlockingQueue<TimePromise> messageList = new ArrayBlockingQueue<>(16);
  private final ConcurrentHashMap<String, TimePromise> messageMap =
      new ConcurrentHashMap<>(
          new LinkedHashMap<>() {

            private static final long serialVersionUID = 1L;

            @Override
            protected boolean removeEldestEntry(Map.Entry<String, TimePromise> eldest) {
              return size() > 16;
            }
          });
  @Reference private Vertx vertx;
  private NetClient netClient;
  private Optional<NetSocket> socket = Optional.empty();
  private Optional<Future<NetSocket>> connectTask = Optional.empty();
  private Handler<String> handler;
  private Config preConfig = null;
  private Map<String, Object> mapProperties;
  private String writeScriptSource = "";
  private String readScriptSource = "";
  private Script writeScript;
  private Script readScript;
  private WebClient webClient;
  @Reference private AssetHelp assetHelp;

  @Activate
  public void start(Config properties, final Map<String, Object> mapproperties)
      throws KuraConnectException {
    this.mapProperties = mapproperties;
    this.preConfig = properties;
    updateConfig(properties);
  }

  @Modified
  public void update(Config properties, final Map<String, Object> mapproperties)
      throws KuraConnectException {
    if (this.preConfig != null && properties.toString().equals(this.preConfig.toString())) {
      return;
    }
    closeSocketClient();
    updateConfig(properties);
    this.preConfig = properties;
    this.mapProperties = mapproperties;
  }

  @Deactivate
  public void stop() {
    closeSocketClient();
  }

  private synchronized void closeSocketClient() {
    serviceTool.ungetServices();
    if (socket.isPresent()) {
      socket.get().close();
      socket = Optional.empty();
    }
    if (this.connectTask.isPresent()) {
      this.connectTask = Optional.empty();
    }
    if (netClient != null) {
      netClient.close();
    }
    if (webClient != null) {
      webClient.close();
      webClient = null;
    }
    messageList.clear();
    messageMap.clear();
  }

  private void updateConfig(Config properties) {
    if (!properties.activate()) {
      closeSocketClient();
      return;
    }
    if (properties.enableKey()) {
      writeScriptSource = properties.preWriteProcessCode();
      readScriptSource = properties.preReadProcessCode();
      if (!"".equals(writeScriptSource) || !"".equals(readScriptSource)) {
        if (webClient != null) {
          webClient.close();
          webClient = WebClient.create(vertx);
        } else {
          webClient = WebClient.create(vertx);
        }
      }
      if (!"".equals(writeScriptSource)) {
        Binding binding = new Binding();
        GroovyShell shell = new GroovyShell(binding);
        binding.setVariable("logger", logger);
        binding.setVariable("webClient", webClient);
        binding.setVariable("assetHelp", assetHelp);
        binding.setVariable("serviceTool", serviceTool);
        binding.setVariable("vertx", vertx);
        try {
          writeScript = shell.parse(writeScriptSource);
        } catch (Exception e) {
          logger.error("Write Script parse error", e);
          writeScriptSource = "";
        }
        logger.info("Updating Write Script Filter... Done");
      }
      if (!"".equals(readScriptSource)) {
        Binding binding = new Binding();
        GroovyShell shell = new GroovyShell(binding);
        binding.setVariable("logger", logger);
        binding.setVariable("webClient", webClient);
        binding.setVariable("assetHelp", assetHelp);
        binding.setVariable("serviceTool", serviceTool);
        binding.setVariable("handler", handler);
        binding.setVariable("vertx", vertx);
        try {
          readScript = shell.parse(readScriptSource);
        } catch (Exception e) {
          logger.error("read Script parse error", e);
          readScriptSource = "";
        }
        logger.info("Updating read Script Filter... Done");
      }
    }

    logger.info(
        "正在启动服务器....:{},端口:{},服务:{}",
        properties.host(),
        properties.port(),
        this.mapProperties.get(ServieTool.KURA_SERVICE_PID));
    connectTask = Optional.of(doConnect(properties));
  }

  private synchronized Future<NetSocket> doConnect(Config properties) {
    if (this.connectTask.isPresent() && !this.connectTask.get().isComplete()) {
      return this.connectTask.get();
    }
    Promise<NetSocket> connectPromise = Promise.promise();
    if (socket.isPresent()) {
      connectPromise.complete(socket.get());
      return connectPromise.future();
    }
    if (netClient != null) {
      netClient.close();
      netClient = null;
    }
    NetClientOptions options = new NetClientOptions();
    options.setTcpKeepAlive(true);
    options.setConnectTimeout(properties.connectTimeout());
    options.setReconnectAttempts(properties.reconnectAttempts()); // 尝试次数
    options.setReconnectInterval(properties.reconnectInterval()); // 每一次尝试间隔
    options.setLogActivity(true);
    options.setReuseAddress(true);
    netClient = vertx.createNetClient(options);
    netClient.connect(
        properties.port(), properties.host(), handleConnection(properties, connectPromise));
    this.connectTask = Optional.of(connectPromise.future());
    return connectPromise.future();
  }

  private Handler<AsyncResult<NetSocket>> handleConnection(
      Config properties, Promise<NetSocket> connectPromise) {
    return result -> {
      if (result.failed()) {
        connectPromise.fail(result.cause());
        TextSocketClientServiceImpl.this.closeSocketClient();
        if (properties.reconnectAttempts() > 0) {
          logger.info(
              "连接到服务器:{},端口:{},服务:{} 失败次数超过:{}次，服务停止，请重新启动",
              properties.host(),
              properties.port(),
              this.mapProperties.get(ServieTool.KURA_SERVICE_PID),
              properties.reconnectAttempts());
          return;
        }
      } else {
        NetSocket netSocket = result.result();
        connectPromise.complete(netSocket);
        logger.info(
            "连接到服务器:{},端口:{},服务:{} 成功",
            properties.host(),
            properties.port(),
            this.mapProperties.get(ServieTool.KURA_SERVICE_PID));
        socket = Optional.of(netSocket);
        netSocket.closeHandler(
            h -> {
              logger.info(
                  "连接到服务器:{},端口:{},服务:{} 连接丢失",
                  properties.host(),
                  properties.port(),
                  this.mapProperties.get(ServieTool.KURA_SERVICE_PID));
              TextSocketClientServiceImpl.this.closeSocketClient();
            });
        netSocket.exceptionHandler(
            e -> {
              logger.info(
                  "连接到服务器:{},端口:{},服务:{} 连接错误",
                  properties.host(),
                  properties.port(),
                  this.mapProperties.get(ServieTool.KURA_SERVICE_PID),
                  e);
              TextSocketClientServiceImpl.this.closeSocketClient();
            });

        netSocket.handler(this::handlerBuffer);
      }
    };
  }

  private void handlerBuffer(Buffer buffer) {
    String message = buffer.toString();
    ReadMsg readMsg = convertReadMessage(message);
    String key = readMsg.getKey();
    message = readMsg.getMsg();
    if (handler != null && readMsg.isHandled()) {
      return;
    }
    if (key == null || "".equals(key)) {
      TimePromise tp = messageList.poll();
      if (tp != null) {
        tp.getPromise().complete(message);
        vertx.cancelTimer(tp.getTimer());
      }
    } else {
      if (messageMap != null) {
        TimePromise tp = messageMap.get(key);
        if (tp != null) {
          tp.getPromise().complete(message);
          vertx.cancelTimer(tp.getTimer());
        }
        messageMap.remove(key);
      }
    }
  }

  @Override
  public void setHandle(Handler<String> handler) {
    this.handler = handler;
  }

  @Override
  public void write(String message) {
    this.doConnect(preConfig).onSuccess(v -> v.write(message));
  }

  @Override
  public void write(String message, Handler<AsyncResult<Void>> handler) {
    this.doConnect(preConfig)
        .onComplete(
            v -> {
              Promise<Void> promise = Promise.promise();
              promise.future().onComplete(handler);
              if (v.failed()) {
                promise.fail(v.cause());
                return;
              }
              promise.complete();
              v.result().write(message);
            });
  }

  @Override
  public void sendAndReply(String message, Handler<AsyncResult<String>> handler) {

    sendAndReply(message, 10000, handler);
  }

  private String convertWriteMessage(String message) {
    if (this.preConfig.enableKey()) {
      if (writeScriptSource == null) {
        logger.error("Script parse error.Please input correct groovy script");
        return ":" + message;
      }
      if (!"".equals(writeScriptSource)) {

        try {
          writeScript.setProperty("message", message);
          writeScript.run();
          String writeMessage = (String) writeScript.getProperty("message");
          return writeMessage;
        } catch (Exception e) {
          logger.warn("Failed to execute script", e);
        }
      }
    }
    return ":" + message;
  }

  private ReadMsg convertReadMessage(String message) {
    ReadMsg readMsg = new ReadMsg("", message);
    if (this.preConfig.enableKey()) {

      if (readScriptSource == null) {
        logger.error("Script parse error.Please input correct groovy script");
        return readMsg;
      }
      if (!"".equals(readScriptSource)) {

        try {
          readScript.setProperty("handler", handler);
          readScript.setProperty("readMsg", readMsg);
          readScript.run();
          ReadMsg readMessage = (ReadMsg) readScript.getProperty("readMsg");
          return readMessage;
        } catch (Exception e) {
          logger.warn("Failed to execute script", e);
        }
      }
    }
    return readMsg;
  }

  @Override
  public void sendAndReply(String message, long timeOut, Handler<AsyncResult<String>> handler) {
    this.doConnect(preConfig)
        .onComplete(
            v -> {
              Promise<String> promise = Promise.promise();
              promise.future().onComplete(handler);
              if (v.failed()) {
                promise.fail(v.cause());
                return;
              }
              String covertMessage = convertWriteMessage(message);
              String key = null;
              String[] msgs = covertMessage.split(":", 2);
              if (msgs.length > 1) {
                key = msgs[0];
                covertMessage = msgs[1];
              }
              if (key == null || "".equals(key)) {
                TimePromise tp = new TimePromise(promise, 0L);
                Long timer =
                    vertx.setTimer(timeOut, new TimeOutTask(key, tp, messageList, messageMap));
                tp.setTimer(timer);

                if (messageList.offer(tp)) {
                  v.result().write(covertMessage);
                } else {
                  vertx.cancelTimer(timer);
                  promise.fail(new BufferOverflowException());
                }
              } else {
                if (messageMap == null) {
                  promise.fail(new IllegalStateException());
                } else {
                  TimePromise tp = new TimePromise(promise, 0L);
                  Long timer =
                      vertx.setTimer(timeOut, new TimeOutTask(key, tp, messageList, messageMap));
                  tp.setTimer(timer);
                  messageMap.put(key, tp);
                  v.result().write(key + ":" + covertMessage);
                }
              }
            });
  }

  @ObjectClassDefinition(
      id = "com.yofc.iot.component.impl.TextSocketClientServiceImpl",
      name = "%name",
      description = "%description",
      localization = "OSGI-INF/l10n/com.yofc.iot.component.impl.TextSocketClientServiceCfg")
  @interface Config {

    @AttributeDefinition(name = "%activate", description = "%activateDesc")
    boolean activate() default false;

    @AttributeDefinition(
        name = "%host",
        cardinality = 0,
        description = "%hostDesc",
        required = true)
    String host() default "127.0.0.1";

    @AttributeDefinition(
        name = "%port",
        cardinality = 0,
        description = "%portDesc",
        required = true)
    int port() default 8080;

    @AttributeDefinition(
        name = "%connectTimeout",
        cardinality = 0,
        description = "%connectTimeoutDesc",
        required = true)
    int connectTimeout() default 1000;

    @AttributeDefinition(
        name = "%reconnectAttempts",
        cardinality = 0,
        description = "%reconnectAttemptsDesc",
        required = true)
    int reconnectAttempts() default 10;

    @AttributeDefinition(
        name = "%reconnectInterval",
        cardinality = 0,
        description = "%reconnectIntervalDesc",
        required = true)
    int reconnectInterval() default 10000;

    @AttributeDefinition(name = "%enableKey", description = "%enableKeyDesc")
    boolean enableKey() default true;

    @AttributeDefinition(
        name = "%preWriteProcessCode",
        cardinality = 0,
        description = "%preWriteProcessCodeDesc",
        required = false)
    String preWriteProcessCode() default
        "double random = Math.random();" + "message = random + \":\" + message + \"\\r\\n\"; ";

    @AttributeDefinition(
        name = "%preReadProcessCode",
        cardinality = 0,
        description = "%preReadProcessCodeDesc",
        required = false)
    String preReadProcessCode() default
        "String[] msgs = readMsg.getMsg().split(\":\", 2);"
            + ""
            + "if (msgs.length > 1) {"
            + "     readMsg.setKey(msgs[0]);"
            + "     readMsg.setMsg(msgs[1]);"
            + "} else if (msgs.length == 1) {"
            + "      readMsg.setMsg(msgs[0]);"
            + "}"
            + "        "
            + "if (readMsg.getMsg() != null && (readMsg.getMsg().startsWith(\"FEED\"))) {"
            + "    if (handler != null) {"
            + "        handler.handle(readMsg.getMsg());"
            + "        readMsg.setHandled(true);"
            + "      }"
            + "}";
  }

  class ReadMsg {

    private String key;
    private String msg;
    private boolean handled = false;

    ReadMsg(String key, String msg) {
      this.key = key;
      this.msg = msg;
    }

    public String getKey() {
      return key;
    }

    public void setKey(String key) {
      this.key = key;
    }

    public String getMsg() {
      return msg;
    }

    public void setMsg(String msg) {
      this.msg = msg;
    }

    public boolean isHandled() {
      return handled;
    }

    public void setHandled(boolean handled) {
      this.handled = handled;
    }
  }

  class TimeOutTask implements Handler<Long> {

    private final TimePromise tp;
    private final String key;
    private final BlockingQueue<TimePromise> messageList;
    private final ConcurrentMap<String, TimePromise> messageMap;
    public TimeOutTask(
        String key,
        TimePromise tp,
        BlockingQueue<TimePromise> messageList,
        ConcurrentMap<String, TimePromise> messageMap) {
      this.key = key;
      this.tp = tp;
      this.messageList = messageList;
      this.messageMap = messageMap;
    }

    @Override
    public void handle(Long event) {
      if (!tp.getPromise().future().isComplete()) {
        tp.getPromise().fail(new TimeoutException());
        if (key == null || "".equals(key)) {
          messageList.remove(tp);
        } else {
          messageMap.remove(key);
        }
      }
    }
  }

  class TimePromise {

    private final Promise<String> promise;
    private Long timer;

    public TimePromise(Promise<String> promise, Long timer) {
      super();
      this.promise = promise;
      this.timer = timer;
    }

    public Promise<String> getPromise() {
      return promise;
    }

    public Long getTimer() {
      return timer;
    }

    public void setTimer(Long timer) {
      this.timer = timer;
    }
  }
}
