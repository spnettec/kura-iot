package com.yofc.iot.apps.enip.cip.services;

import com.yofc.iot.apps.enip.cip.CipResponseException;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.ReferenceCountUtil;

public interface CipService<T> {

    void encodeRequest(ByteBuf buffer);

    T decodeResponse(ByteBuf buffer) throws CipResponseException, PartialResponseException;

    T getResponseData();

    public default int getRequestSize() {
        ByteBuf buf = Unpooled.buffer(600);
        try {
            this.encodeRequest(buf);
            return buf.writerIndex();
        } finally {
            ReferenceCountUtil.release(buf);
        }
    }

    public static final class PartialResponseException extends Exception {

        /**
         * 
         */
        private static final long serialVersionUID = 1L;
        public static final PartialResponseException INSTANCE = new PartialResponseException();
    }

}
