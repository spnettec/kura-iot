package com.yofc.iot.apps.enip.codec;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.yofc.iot.apps.enip.EnipPacket;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageCodec;

public class EnipCodec extends ByteToMessageCodec<EnipPacket> {

    Logger logger = LoggerFactory.getLogger(EnipCodec.class);

    private static final int HEADER_SIZE = 24;
    private static final int LENGTH_OFFSET = 2;

    @Override
    protected void encode(ChannelHandlerContext ctx, EnipPacket packet, ByteBuf out) throws Exception {
        EnipPacket.encode(packet, out);
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        int startIndex = in.readerIndex();
        if (in.readableBytes() < HEADER_SIZE)
            return;
        int length = getLength(in, startIndex);
        if (in.readableBytes() < HEADER_SIZE + length)
            return;
        while (in.readableBytes() >= HEADER_SIZE + length) {
            ByteBuf buf = in.readSlice(HEADER_SIZE + length);
            try {
                out.add(EnipPacket.decode(buf));
            } catch (Exception e) {
                logger.error("EnipPacket decode Error", e);
            }
            startIndex = in.readerIndex();
            if (in.readableBytes() < HEADER_SIZE)
                return;
            length = getLength(in, startIndex);
            if (in.readableBytes() < HEADER_SIZE + length)
                return;
        }

        // while (in.readableBytes() >= HEADER_SIZE && in.readableBytes() >= HEADER_SIZE + getLength(in, startIndex)) {

        // out.add(EnipPacket.decode(in));

        // startIndex = in.readerIndex();
        // }
    }

    private int getLength(ByteBuf buffer, int startIndex) {
        return buffer.getUnsignedShortLE(startIndex + LENGTH_OFFSET);
    }

}
