/**
 * Copyright (c) 2018 Eurotech and/or its affiliates and others
 *
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 */

package com.yofc.iot.component.api;

import static org.eclipse.kura.channel.ChannelFlag.FAILURE;

import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import org.eclipse.kura.channel.ChannelFlag;
import org.eclipse.kura.channel.ChannelRecord;
import org.eclipse.kura.channel.ChannelStatus;
import org.eclipse.kura.type.DataType;
import org.eclipse.kura.type.TypedValue;
import org.eclipse.kura.type.TypedValues;

public final class Utils {

    private Utils() {
    }

    public static <T> T tryExtract(final Map<String, Object> properties, final Function<Map<String, Object>, T> func,
            final String errorMessage) {
        try {
            return func.apply(properties);
        } catch (Exception e) {
            throw new IllegalArgumentException(errorMessage);
        }
    }

    public static <U> U runSafe(final Future<U> future, final long timeout, final Consumer<Throwable> onFailure)
            throws Exception {
        try {
            return future.get(timeout, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            if (onFailure != null) {
                onFailure.accept(e);
            }
            throw e;
        }
    }

    public static void fillValue(final Object variant, final ChannelRecord record) {
        try {
            record.setValue(map(variant, record.getValueType()));
            record.setChannelStatus(new ChannelStatus(ChannelFlag.SUCCESS));
        } catch (Exception e) {
            record.setChannelStatus(new ChannelStatus(FAILURE,
                    "Error while converting the retrieved value to the defined typed", null));
        }
    }

    public static void fillRecord(final Object value, final ChannelRecord record) {
        record.setTimestamp(System.currentTimeMillis());

        if (value == null) {
            record.setChannelStatus(new ChannelStatus(FAILURE, "Operation Result cannot be null", null));
            return;
        }

        fillValue(value, record);

        record.setTimestamp(System.currentTimeMillis());
    }

    public static void splitInMultipleRequests(final int itemsPerRequest, int totalItemCount,
            final BiConsumer<Integer, Integer> consumer) {
        final int requestCount = totalItemCount / itemsPerRequest + (totalItemCount % itemsPerRequest == 0 ? 0 : 1);

        for (int i = 0; i < requestCount; i++) {
            final int start = i * itemsPerRequest;
            final int end = Math.min(start + itemsPerRequest, totalItemCount);
            consumer.accept(start, end);
        }
    }

    private static TypedValue<?> map(final Object value, final DataType targetType) throws Exception {
        switch (targetType) {
        case LONG:
            return TypedValues.newLongValue(((Number) value).longValue());
        case FLOAT:
            return TypedValues.newFloatValue(((Number) value).floatValue());
        case DOUBLE:
            return TypedValues.newDoubleValue(((Number) value).doubleValue());
        case INTEGER:
            return TypedValues.newIntegerValue(((Number) value).intValue());
        case BOOLEAN:
            return TypedValues.newBooleanValue(((Number) value).intValue() > 0);
        case STRING:
            return TypedValues.newStringValue(value == null ? "" : (String) value);
        case BYTE_ARRAY:
            return TypedValues.newByteArrayValue((byte[]) value);
        default:
            throw new IllegalArgumentException();
        }
    }
}
