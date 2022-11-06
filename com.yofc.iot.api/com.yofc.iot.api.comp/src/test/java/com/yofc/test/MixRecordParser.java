package com.yofc.test;

import io.netty.buffer.Unpooled;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.impl.Arguments;

import java.util.Objects;

public class MixRecordParser implements Handler<Buffer> {

    // Empty and unmodifiable
    private static final Buffer EMPTY_BUFFER = Buffer.buffer(Unpooled.EMPTY_BUFFER);

    private Buffer buff = EMPTY_BUFFER;
    private int pos;            // Current position in buffer
    private int start;          // Position of beginning of current record
    private int delimPos;       // Position of current match in delimiter array

    private boolean delimited;
    private byte[] delim;
    private int recordSize;
    private int maxRecordSize;
    private long demand = Long.MAX_VALUE;
    private Handler<Buffer> eventHandler;
    private boolean parsing;

    public void setOutput(Handler<Buffer> output) {
        Objects.requireNonNull(output, "output");
        eventHandler = output;
    }

    public static Buffer latin1StringToBytes(String str) {
        byte[] bytes = new byte[str.length()];
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            bytes[i] = (byte) (c & 0xFF);
        }
        return Buffer.buffer(bytes);
    }

    public static MixRecordParser newMixed(String delim, int size) {
        MixRecordParser ls = newDelimited(delim, null);
        ls.fixedSizeMode(size);
        ls.switchDelimitedMode();
        return ls;
    }

    public static MixRecordParser newDelimited(String delim, Handler<Buffer> output) {
        return newDelimited(latin1StringToBytes(delim), output);
    }

    public static MixRecordParser newDelimited(Buffer delim, Handler<Buffer> output) {
        MixRecordParser ls = new MixRecordParser();
        ls.setOutPutHandler(output);
        ls.delimitedMode(delim);
        return ls;
    }

    public void delimitedMode(Buffer delim) {
        Objects.requireNonNull(delim, "delim");
        delimited = true;
        this.delim = delim.getBytes();
        delimPos = 0;
    }

    public void switchDelimitedMode() {
        Objects.requireNonNull(delim, "delim not inited");
        delimited = true;
    }

    public boolean isDelimitedMode() {
        return delimited;
    }

    public void fixedSizeMode(int size) {
        Arguments.require(size > 0, "Size must be > 0");
        delimited = false;
        recordSize = size;
    }

    public int getSize() {
        return recordSize;
    }

    public MixRecordParser maxRecordSize(int size) {
        Arguments.require(size > 0, "Size must be > 0");
        maxRecordSize = size;
        return this;
    }

    private void handleParsing() {
        if (parsing) {
            return;
        }
        parsing = true;
        try {
            do {
                if (demand > 0L) {
                    int next;
                    if (delimited) {
                        next = parseDelimited();
                    } else {
                        next = parseFixed();
                    }
                    if (next == -1) {
                        break;
                    }
                    if (demand != Long.MAX_VALUE) {
                        demand--;
                    }
                    Buffer event = buff.getBuffer(start, next);
                    start = pos;
                    Handler<Buffer> handler = eventHandler;
                    if (handler != null) {
                        handler.handle(event);
                    }
                } else {
                    break;
                }
            } while (true);
            int len = buff.length();
            if (start == len) {
                buff = EMPTY_BUFFER;
            } else if (start > 0) {
                buff = buff.getBuffer(start, len);
            }
            pos -= start;
            start = 0;
        } finally {
            parsing = false;
        }
    }

    private int parseDelimited() {
        int len = buff.length();
        for (; pos < len; pos++) {
            if (buff.getByte(pos) == delim[delimPos]) {
                delimPos++;
                if (delimPos == delim.length) {
                    pos++;
                    delimPos = 0;
                    return pos - delim.length;
                }
            } else {
                if (delimPos > 0) {
                    pos -= delimPos;
                    delimPos = 0;
                }
            }
        }
        return -1;
    }

    private int parseFixed() {
        int len = buff.length();
        if (len - start >= recordSize) {
            int end = start + recordSize;
            pos = end;
            return end;
        }
        return -1;
    }

    @Override
    public void handle(Buffer buffer) {
        if (buff.length() == 0) {
            buff = buffer;
        } else {
            buff.appendBuffer(buffer);
        }
        handleParsing();
        if (buff != null && maxRecordSize > 0 && buff.length() > maxRecordSize) {
            throw new IllegalStateException("The current record is too long");

        }
    }

    public void setOutPutHandler(Handler<Buffer> handler) {
        eventHandler = handler;
    }

}
