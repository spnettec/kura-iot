package com.yofc.iot.apps.enip.cip.services;

import com.yofc.iot.apps.enip.cip.CipResponseException;

import io.netty.buffer.ByteBuf;

public interface CipService<T> {

    void encodeRequest(ByteBuf buffer);

    T decodeResponse(ByteBuf buffer) throws CipResponseException, PartialResponseException;

    public static final class PartialResponseException extends Exception {

        /**
         * 
         */
        private static final long serialVersionUID = 1L;
        public static final PartialResponseException INSTANCE = new PartialResponseException();
    }

}
