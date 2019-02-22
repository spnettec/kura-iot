package com.yofc.vertx.osgi.api;

import static org.eclipse.kura.channel.ChannelType.READ_WRITE;
import static org.eclipse.kura.channel.ChannelType.WRITE;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.kura.KuraException;
import org.eclipse.kura.asset.Asset;
import org.eclipse.kura.asset.AssetConfiguration;
import org.eclipse.kura.asset.AssetService;
import org.eclipse.kura.channel.Channel;
import org.eclipse.kura.channel.ChannelFlag;
import org.eclipse.kura.channel.ChannelRecord;
import org.eclipse.kura.channel.ChannelType;
import org.eclipse.kura.type.DataType;
import org.eclipse.kura.type.TypedValue;
import org.eclipse.kura.type.TypedValues;
import org.eclipse.kura.wire.WireRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.json.JsonObject;

public class AssetHelp {

    private final AssetService assetService;
    private static final Logger logger = LoggerFactory.getLogger(AssetHelp.class);

    public AssetHelp(AssetService assetService) {
        this.assetService = assetService;
    }

    public Asset getAsset(String assetPid) {
        Asset asset = this.assetService.getAsset(assetPid);
        if (asset == null) {
            logger.error("找不到设备:{}", assetPid);
        }
        return asset;

    }

    public static List<ChannelRecord> determineWritingChannels(Asset asset, List<WireRecord> records) {
        final AssetConfiguration assetConfiguration = asset.getAssetConfiguration();

        final List<ChannelRecord> channelRecordsToWrite = new ArrayList<>();
        final Map<String, Channel> channels = assetConfiguration.getAssetChannels();
        for (final Entry<String, Channel> channelEntry : channels.entrySet()) {
            final Channel channel = channelEntry.getValue();
            final String channelName = channel.getName();
            final ChannelType channelType = channel.getType();

            if (channelType != WRITE && channelType != READ_WRITE) {
                continue;
            }
            if (!channel.isEnabled())
                continue;
            Map<String, TypedValue<?>> wireRecordProperties = records.stream().map(WireRecord::getProperties)
                    .map(Map::entrySet).flatMap(Set::stream)
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

            if (wireRecordProperties.containsKey(channelName)) {
                final TypedValue<?> value = wireRecordProperties.get(channelName);
                if (channel.getValueType() == value.getType()) {
                    channelRecordsToWrite.add(channel.createWriteRecord(value));
                }
            }
        }
        return channelRecordsToWrite;

    }

    public JsonObject readTags(String assetPid, String... tagNames) {
        JsonObject json = new JsonObject();
        Asset asset = getAsset(assetPid);
        if (asset == null) {
            logger.error("读取失败");
            return null;
        }
        List<ChannelRecord> records;
        try {
            records = asset.read(Arrays.asList(tagNames).stream().collect(Collectors.toSet()));
        } catch (KuraException e) {
            logger.error("读取失败:{}", e);
            return null;
        }
        for (ChannelRecord record : records) {
            json.put(record.getChannelName(), record.getValue().getValue());
        }
        return json;
    }

    public JsonObject readTags(String assetPid, List<String> tagNames) {
        JsonObject json = new JsonObject();
        Asset asset = getAsset(assetPid);
        if (asset == null) {
            logger.error("读取失败");
            return null;
        }
        List<ChannelRecord> records;
        try {
            records = asset.read(tagNames.stream().collect(Collectors.toSet()));
        } catch (KuraException e) {
            logger.error("读取失败:{}", e);
            return null;
        }
        for (ChannelRecord record : records) {
            Object value = null;
            if (record.getChannelStatus().getChannelFlag() == ChannelFlag.FAILURE)
                value = "读取失败";
            else
                value = record.getValue() == null ? null : record.getValue().getValue();
            json.put(record.getChannelName(), value);
        }
        return json;
    }

    public boolean writeTags(String assetPid, JsonObject json) {
        List<ChannelRecord> channelRecords = new ArrayList<>();
        Asset asset = getAsset(assetPid);
        for (String tagName : json.fieldNames()) {
            Map<String, Channel> chennels = asset.getAssetConfiguration().getAssetChannels();
            Channel channel = chennels.get(tagName);
            if (channel == null) {
                logger.error("标签:{} 错误", tagName);
                return false;
            }
            DataType dataType = channel.getValueType();
            String value = String.valueOf(json.getValue(tagName));
            TypedValue<?> tValue = TypedValues.parseTypedValue(dataType, value);
            ChannelRecord writeRecord = ChannelRecord.createWriteRecord(tagName, tValue);
            channelRecords.add(writeRecord);
        }
        try {
            asset.write(channelRecords);
        } catch (KuraException e) {
            logger.error("写入通道错误");
        }
        return true;
    }

    public boolean writeTags(String assetPid, String jsonString) {
        JsonObject jsonObject = new JsonObject(jsonString);
        return writeTags(assetPid, jsonObject);
    }

    public boolean writeTags(String assetPid, Map<String, Object> map) {
        JsonObject jsonObject = new JsonObject(map);
        return writeTags(assetPid, jsonObject);
    }

    public void writeTags(String assetPid, Object bean) throws KuraException {
        JsonObject jsonObject = JsonObject.mapFrom(bean);
        writeTags(assetPid, jsonObject);
    }

    public void writeTag(String assetPid, String tagName, Object value) throws KuraException {
        List<ChannelRecord> channelRecords = new ArrayList<>();
        Asset asset = getAsset(assetPid);
        Map<String, Channel> chennels = asset.getAssetConfiguration().getAssetChannels();
        Channel channel = chennels.get(tagName);
        if (channel == null) {
            logger.error("标签:{} 错误", tagName);
            return;
        }
        DataType dataType = channel.getValueType();
        TypedValue<?> tValue = TypedValues.parseTypedValue(dataType, String.valueOf(value));
        ChannelRecord writeRecord = ChannelRecord.createWriteRecord(tagName, tValue);
        channelRecords.add(writeRecord);
        asset.write(channelRecords);
    }
}
