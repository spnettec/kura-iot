package com.yofc.netty.fsm.tcp;

public abstract class Command {

    private final int commandCode;
    private long senderContext = 0l;

    public long getSenderContext() {
        return senderContext;
    }

    public void setSenderContext(long senderContext) {
        this.senderContext = senderContext;
    }

    protected Command(int commandCode) {
        this.commandCode = commandCode;
    }

    public int getCommandCode() {
        return commandCode;
    }

}
