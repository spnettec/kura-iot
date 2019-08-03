package com.yofc.netty.fsm.tcp;

import io.netty.buffer.ByteBuf;

public class Command {

    private final ByteBuf payload;
    private long senderContext = 0l;

    public long getSenderContext() {
        return senderContext;
    }

    public void setSenderContext(long senderContext) {
        this.senderContext = senderContext;
    }

    protected Command(ByteBuf payload) {
        this.payload = payload;
    }

    public ByteBuf getPayload() {
        return payload;
    }
}
