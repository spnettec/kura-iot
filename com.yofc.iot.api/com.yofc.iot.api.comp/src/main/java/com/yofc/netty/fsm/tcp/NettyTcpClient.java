package com.yofc.netty.fsm.tcp;

import static com.yofc.netty.fsm.util.FutureUtils.complete;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.yofc.netty.fsm.ChannelFsm;
import com.yofc.netty.fsm.ChannelFsmConfig;
import com.yofc.netty.fsm.ChannelFsmFactory;
import com.yofc.vertx.osgi.api.Utils;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.Timeout;

public class NettyTcpClient {

    private static final Logger logger = LoggerFactory.getLogger(NettyTcpClient.class);
    private final TcpNetIpClientConfig config;
    private final ChannelFsm channelFsm;
    private final Map<Long, PendingRequest<? extends Command>> pendingRequests = new ConcurrentHashMap<>();

    public NettyTcpClient(TcpNetIpClientConfig config) {
        this.config = config;
        ChannelFsmConfig fsmConfig = ChannelFsmConfig.newBuilder().setLazy(config.isLazy())
                .setPersistent(config.isPersistent())
                .setMaxIdleSeconds(Utils.saturatedCast(config.getMaxIdle().getSeconds()))
                .setMaxReconnectDelaySeconds(config.getMaxReconnectDelaySeconds())
                .setChannelActions(new TcpChannelActions(this)).setExecutor(config.getExecutor())
                .setScheduler(config.getScheduledExecutor()).setLoggerName(NettyTcpClient.class.getName()).build();

        channelFsm = ChannelFsmFactory.newChannelFsm(fsmConfig);
    }

    public TcpNetIpClientConfig getConfig() {
        return config;
    }

    public CompletableFuture<NettyTcpClient> connect() {
        return complete(new CompletableFuture<NettyTcpClient>())
                .with(channelFsm.connect().thenApply(c -> NettyTcpClient.this));
    }

    public CompletableFuture<NettyTcpClient> disconnect() {
        return complete(new CompletableFuture<NettyTcpClient>())
                .with(channelFsm.disconnect().thenApply(c -> NettyTcpClient.this));
    }

    public String getState() {
        return channelFsm.getState().toString();
    }

    public void onChannelRead(ByteBuf packet) {

    }

    public void onConnected(CompletableFuture<Void> future) {
        future.complete(null);
    }

    public void onChannelInactive(ChannelHandlerContext ctx) {
        logger.debug("onChannelInactive() {} <-> {}", ctx.channel().localAddress(), ctx.channel().remoteAddress());
    }

    public void onExceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.debug("onExceptionCaught() {} <-> {}", ctx.channel().localAddress(), ctx.channel().remoteAddress(),
                cause);

        ctx.channel().close();
    }

    public <T extends Command> CompletableFuture<T> sendCommand(Command command) {
        CompletableFuture<T> future = new CompletableFuture<>();

        channelFsm.getChannel().whenComplete((ch, ex) -> {
            if (ch != null)
                writeCommand(ch, command, future);
            else
                future.completeExceptionally(ex);
        });

        return future;
    }

    public <T extends Command> void writeCommand(Channel channel, Command command, CompletableFuture<T> future) {
        Timeout timeout = config.getWheelTimer().newTimeout(tt -> {
            if (tt.isCancelled())
                return;
            PendingRequest<? extends Command> p = pendingRequests.remove(command.getSenderContext());
            if (p != null) {
                String message = String.format("senderContext=%s timed out waiting %sms for response",
                        command.getSenderContext(), 10000);
                p.promise.completeExceptionally(new Exception(message));
            }
        }, config.getTimeout().toMillis(), TimeUnit.MILLISECONDS);

        pendingRequests.put(command.getSenderContext(), new PendingRequest<>(future, timeout));

        channel.writeAndFlush(command).addListener(f -> {
            if (!f.isSuccess()) {
                PendingRequest<? extends Command> pending = pendingRequests.remove(command.getSenderContext());
                if (pending != null) {
                    pending.timeout.cancel();
                    pending.promise.completeExceptionally(f.cause());
                }
            }
        });
    }

    private static final class PendingRequest<T> {

        private final CompletableFuture<Command> promise = new CompletableFuture<>();

        private final Timeout timeout;

        @SuppressWarnings("unchecked")
        private PendingRequest(CompletableFuture<T> future, Timeout timeout) {
            this.timeout = timeout;

            promise.whenComplete((r, ex) -> {
                if (r != null) {
                    try {
                        future.complete((T) r);
                    } catch (ClassCastException e) {
                        future.completeExceptionally(e);
                    }
                } else {
                    future.completeExceptionally(ex);
                }
            });
        }

    }
}
