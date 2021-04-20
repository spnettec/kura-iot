/**
 * Copyright (c) 2018 Eurotech and/or its affiliates and others
 *
 * <p>All rights reserved. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package etherip.driver.request;

import etherip.types.CIPData;
import org.eclipse.kura.channel.ChannelFlag;
import org.eclipse.kura.channel.ChannelRecord;
import org.eclipse.kura.channel.ChannelStatus;
import org.eclipse.kura.type.DataType;
import org.eclipse.kura.type.TypedValue;
import org.eclipse.kura.type.TypedValues;

import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.eclipse.kura.channel.ChannelFlag.FAILURE;

public final class Utils {

  private Utils() {}

  public static <T> T tryExtract(
      final Map<String, Object> properties,
      final Function<Map<String, Object>, T> func,
      final String errorMessage) {
    try {
      return func.apply(properties);
    } catch (Exception e) {
      throw new IllegalArgumentException(errorMessage);
    }
  }

  public static <U> U runSafe(
      final Future<U> future, final long timeout, final Consumer<Throwable> onFailure)
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

  public static void fillValue(final CIPData variant, final ChannelRecord record) {
    try {
      record.setValue(map(variant, record.getValueType()));
      record.setChannelStatus(new ChannelStatus(ChannelFlag.SUCCESS));
    } catch (Exception e) {
      record.setChannelStatus(
          new ChannelStatus(
              FAILURE, "Error while converting the retrieved value to the defined typed", null));
    }
  }

  public static void fillRecord(final CIPData value, final ChannelRecord record) {
    record.setTimestamp(System.currentTimeMillis());

    if (value == null) {
      record.setChannelStatus(new ChannelStatus(FAILURE, "Operation Result cannot be null", null));
      return;
    }

    fillValue(value, record);

    record.setTimestamp(System.currentTimeMillis());
  }

  public static void splitInMultipleRequests(
      final int itemsPerRequest, int totalItemCount, final BiConsumer<Integer, Integer> consumer) {
    final int requestCount =
        totalItemCount / itemsPerRequest + (totalItemCount % itemsPerRequest == 0 ? 0 : 1);

    for (int i = 0; i < requestCount; i++) {
      final int start = i * itemsPerRequest;
      final int end = Math.min(start + itemsPerRequest, totalItemCount);
      consumer.accept(start, end);
    }
  }

  private static TypedValue<?> map(final CIPData value, final DataType targetType)
      throws Exception {
    Object valueObj = value.get();
    switch (targetType) {
      case LONG:
        return TypedValues.newLongValue(((Number) valueObj).longValue());
      case FLOAT:
        return TypedValues.newFloatValue(((Number) valueObj).floatValue());
      case DOUBLE:
        return TypedValues.newDoubleValue(((Number) valueObj).doubleValue());
      case INTEGER:
        return TypedValues.newIntegerValue(((Number) valueObj).intValue());
      case BOOLEAN:
        return TypedValues.newBooleanValue(((Number) valueObj).intValue() > 0);
      case STRING:
        return TypedValues.newStringValue(valueObj == null ? "" : (String) valueObj);
      case BYTE_ARRAY:
        return TypedValues.newByteArrayValue((byte[]) valueObj);
      default:
        throw new IllegalArgumentException();
    }
  }
}
