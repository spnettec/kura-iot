package com.yofc.iot.component.impl;

import java.nio.BufferOverflowException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.kura.KuraConnectException;
import org.eclipse.kura.configuration.ConfigurableComponent;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.yofc.iot.component.api.ServieTool;
import com.yofc.iot.component.api.TextSocketClientService;
import com.yofc.iot.component.impl.TextSocketClientServiceImpl.Config;
import com.yofc.vertx.osgi.api.AssetHelp;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetSocket;
import io.vertx.ext.web.client.WebClient;

@Designate(ocd = Config.class, factory = true)
@Component(configurationPolicy = ConfigurationPolicy.REQUIRE, immediate = true)
public class TextSocketClientServiceImpl implements TextSocketClientService, ConfigurableComponent {

    private static final Logger logger = LoggerFactory.getLogger(MqttClientServiceImpl.class);
    @Reference
    private Vertx vertx;
    private NetClient netClient;
    private NetSocket socket;
    NetClientOptions options;
    private Handler<String> handler;
    private Config preConfig = null;
    private Map<String, Object> mapProperties;
    private volatile boolean closed = true;
    private AtomicInteger times = new AtomicInteger(0);
    private String writeScriptSource = "";
    private String readScriptSource = "";
    private Script writeScript;
    private Script readScript;
    private WebClient webClient;
    @Reference
    private AssetHelp assetHelp;
    private ServieTool serviceTool = new ServieTool();
    private BlockingQueue<TimePromise> messageList = new ArrayBlockingQueue<>(16);
    private ConcurrentHashMap<String, TimePromise> messageMap = new ConcurrentHashMap<>(new LinkedHashMap<>() {

        private static final long serialVersionUID = 1L;

        @Override
        protected boolean removeEldestEntry(Map.Entry<String, TimePromise> eldest) {
            return size() > 16;
        }
    });

    @Activate
    public void start(Config properties, final Map<String, Object> mapproperties) throws KuraConnectException {
        this.mapProperties = mapproperties;
        this.preConfig = properties;
        updateConfig(properties);
    }

    @Modified
    public void update(Config properties, final Map<String, Object> mapproperties) throws KuraConnectException {
        if (this.preConfig != null && properties.toString().equals(this.preConfig.toString()))
            return;
        closeSocketClient();
        updateConfig(properties);
        this.preConfig = properties;
        this.mapProperties = mapproperties;
    }

    @Deactivate
    public void stop() {
        closeSocketClient();
    }

    private void closeSocketClient() {
        serviceTool.ungetServices();
        closed = true;
        times.set(0);
        if (socket != null) {
            socket.close();
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
            if (!writeScriptSource.equals("") || !readScriptSource.equals("")) {
                if (webClient != null) {
                    webClient.close();
                    webClient = WebClient.create(vertx);
                } else
                    webClient = WebClient.create(vertx);
            }
            if (!writeScriptSource.equals("")) {
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
            if (!readScriptSource.equals("")) {
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
        times.set(0);
        options = new NetClientOptions();
        options.setTcpKeepAlive(true);
        options.setConnectTimeout(properties.connectTimeout());
        options.setReconnectAttempts(properties.reconnectAttempts());// 尝试次数
        options.setReconnectInterval(properties.reconnectInterval());// 每一次尝试间隔
        options.setLogActivity(true);
        options.setReuseAddress(true);
        logger.info("启动...");
        doConnect(properties);
    }

    private void doConnect(Config properties) {
        if (!closed) {
            return;
        }
        if (netClient != null) {
            netClient.close();
            netClient = null;
        }
        netClient = vertx.createNetClient(options);
        netClient.connect(properties.port(), properties.host(), handleConnection(properties));
    }

    private Handler<AsyncResult<NetSocket>> handleConnection(Config properties) {
        return result -> {
            if (result.failed()) {
                if (TextSocketClientServiceImpl.this.times.incrementAndGet() >= properties.reconnectAttempts()) {
                    logger.info("连接到服务器:{},端口:{},服务:{} 失败次数超过:{}次，服务停止，请重新启动", properties.host(), properties.port(),
                            this.mapProperties.get(ServieTool.KURA_SERVICE_PID), properties.reconnectAttempts());
                    return;
                }
                logger.info("连接到服务器:{},端口:{},服务:{} 失败，重新尝试，次数:{}", properties.host(), properties.port(),
                        this.mapProperties.get(ServieTool.KURA_SERVICE_PID),
                        TextSocketClientServiceImpl.this.times.get());
                if (socket != null) {
                    socket.handler(null);
                    socket.exceptionHandler(null);
                    socket.closeHandler(null);
                }

                vertx.setTimer(properties.reconnectInterval(),
                        l -> TextSocketClientServiceImpl.this.doConnect(properties));

            } else {
                logger.info("连接到服务器:{},端口:{},服务:{} 成功", properties.host(), properties.port(),
                        this.mapProperties.get(ServieTool.KURA_SERVICE_PID));
                socket = result.result();
                TextSocketClientServiceImpl.this.closed = false;
                socket.closeHandler(h -> {
                    logger.info("Closed");
                    socket.handler(null);
                    socket.exceptionHandler(null);
                    socket.closeHandler(null);
                    TextSocketClientServiceImpl.this.doConnect(properties);
                });
                socket.exceptionHandler(e -> {
                    logger.info("连接到服务器:{},端口:{},服务:{} 连接错误", properties.host(), properties.port(),
                            this.mapProperties.get(ServieTool.KURA_SERVICE_PID));
                });

                socket.handler(this::handlerBuffer);
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
            if (messageList != null) {
                TimePromise tp = messageList.poll();
                if (tp != null) {
                    tp.getPromise().complete(message);
                    vertx.cancelTimer(tp.getTimer());
                }
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
    public void write(String message) throws KuraConnectException {
        if (closed)
            throw new KuraConnectException("Write error,not Connected");
        socket.write(message);
    }

    @Override
    public void sendAndReply(String message, Handler<AsyncResult<String>> handler) throws KuraConnectException {
        if (closed)
            throw new KuraConnectException("read error,not Connected");
        sendAndReply(message, 5000, handler);
    }

    private String convertWriteMessage(String message) {
        if (this.preConfig.enableKey()) {
            if (writeScriptSource == null) {
                logger.error("Script parse error.Please input correct groovy script");
                return ":" + message;
            }
            if (!writeScriptSource.equals("")) {

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
            if (!readScriptSource.equals("")) {

                try {
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
    public void sendAndReply(String message, long timeOut, Handler<AsyncResult<String>> handler)
            throws KuraConnectException {
        message = convertWriteMessage(message);
        if (closed)
            throw new KuraConnectException("read error,not Connected");
        synchronized (this) {
            String key = null;
            String[] msgs = message.split(":", 2);
            if (msgs.length > 1) {
                key = msgs[0];
                message = msgs[1];
            }
            Promise<String> promise = Promise.promise();
            promise.future().onComplete(handler);
            if (key == null || "".equals(key)) {
                if (messageList == null) {
                    promise.fail(new IllegalStateException());
                    return;
                }

                TimePromise tp = new TimePromise(promise, 0L);
                Long timer = vertx.setTimer(timeOut, new TimeOutTask(key, tp, messageList, messageMap));
                tp.setTimer(timer);

                if (messageList.offer(tp)) {
                    socket.write(message);
                } else {
                    vertx.cancelTimer(timer);
                    promise.fail(new BufferOverflowException());
                }
            } else {
                if (messageMap == null) {
                    promise.fail(new IllegalStateException());
                } else {
                    TimePromise tp = new TimePromise(promise, 0L);
                    Long timer = vertx.setTimer(timeOut, new TimeOutTask(key, tp, messageList, messageMap));
                    tp.setTimer(timer);
                    messageMap.put(key, tp);
                    socket.write(key + ":" + message);
                }
            }
        }
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

        public TimeOutTask(String key, TimePromise tp, BlockingQueue<TimePromise> messageList,
                ConcurrentMap<String, TimePromise> messageMap) {
            this.key = key;
            this.tp = tp;
            this.messageList = messageList;
            this.messageMap = messageMap;

        }

        private final TimePromise tp;
        private final String key;
        private final BlockingQueue<TimePromise> messageList;
        private final ConcurrentMap<String, TimePromise> messageMap;

        @Override
        public void handle(Long event) {
            if (!tp.getPromise().future().isComplete()) {
                tp.getPromise().fail(new TimeoutException());
                if (key == null || "".equals(key))
                    messageList.remove(tp);
                else
                    messageMap.remove(key);
            }

        }

    }

    class TimePromise {

        private final Promise<String> promise;

        public Promise<String> getPromise() {
            return promise;
        }

        public Long getTimer() {
            return timer;
        }

        public void setTimer(Long timer) {
            this.timer = timer;
        }

        private Long timer;

        public TimePromise(Promise<String> promise, Long timer) {
            super();
            this.promise = promise;
            this.timer = timer;
        }
    }

    @ObjectClassDefinition(id = "com.yofc.iot.component.impl.TextSocketClientServiceImpl", name = "%name", description = "%description", localization = "OSGI-INF/l10n/com.yofc.iot.component.impl.TextSocketClientServiceCfg")
    @interface Config {

        @AttributeDefinition(name = "%activate", description = "%activateDesc")
        boolean activate() default false;

        @AttributeDefinition(name = "%host", cardinality = 0, description = "%hostDesc", required = true)
        String host() default "127.0.0.1";

        @AttributeDefinition(name = "%port", cardinality = 0, description = "%portDesc", required = true)
        int port() default 8080;

        @AttributeDefinition(name = "%connectTimeout", cardinality = 0, description = "%connectTimeoutDesc", required = true)
        int connectTimeout() default 1000;

        @AttributeDefinition(name = "%reconnectAttempts", cardinality = 0, description = "%reconnectAttemptsDesc", required = true)
        int reconnectAttempts() default 10;

        @AttributeDefinition(name = "%reconnectInterval", cardinality = 0, description = "%reconnectIntervalDesc", required = true)
        int reconnectInterval() default 10000;

        @AttributeDefinition(name = "%enableKey", description = "%enableKeyDesc")
        boolean enableKey() default true;

        @AttributeDefinition(name = "%preWriteProcessCode", cardinality = 0, description = "%preWriteProcessCodeDesc", required = false)
        String preWriteProcessCode() default "double random = Math.random();"
                + "message = random + \":\" + message + \"\\r\\n\"; ";

        @AttributeDefinition(name = "%preReadProcessCode", cardinality = 0, description = "%preReadProcessCodeDesc", required = false)
        String preReadProcessCode() default "String[] msgs = readMsg.getMsg().split(\":\", 2);" + ""
                + "if (msgs.length > 1) {" + "     readMsg.setKey(msgs[0]);" + "     readMsg.setMsg(msgs[1]);"
                + "} else if (msgs.length == 1) {" + "      readMsg.setMsg(msgs[0]);" + "}" + "        "
                + "if (readMsg.getMsg() != null && (readMsg.getMsg().startsWith(\"FEED\"))) {"
                + "    if (handler != null) {" + "        handler.handle(message);"
                + "        readMsg.setHandled(true);" + "      }" + "}";

    }

}
