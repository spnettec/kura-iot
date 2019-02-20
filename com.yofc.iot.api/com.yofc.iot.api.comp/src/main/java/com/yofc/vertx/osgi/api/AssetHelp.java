package com.yofc.vertx.osgi.api;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.kura.KuraException;
import org.eclipse.kura.asset.Asset;
import org.eclipse.kura.asset.AssetService;
import org.eclipse.kura.channel.Channel;
import org.eclipse.kura.channel.ChannelFlag;
import org.eclipse.kura.channel.ChannelRecord;
import org.eclipse.kura.type.DataType;
import org.eclipse.kura.type.TypedValue;
import org.eclipse.kura.type.TypedValues;

import io.vertx.core.json.JsonObject;

public class AssetHelp {

    private final AssetService assetService;

    public AssetHelp(AssetService assetService) {
        this.assetService = assetService;
    }

    public Asset getAsset(String assetPid) throws KuraException {
        Asset asset = this.assetService.getAsset(assetPid);
        if (asset == null)
            throw KuraException.internalError("找不到设备:" + assetPid);
        return asset;

    }

    public JsonObject readTags(String assetPid, String... tagNames) throws KuraException {
        JsonObject json = new JsonObject();
        Asset asset = getAsset(assetPid);
        List<ChannelRecord> records = asset.read(Arrays.asList(tagNames).stream().collect(Collectors.toSet()));
        for (ChannelRecord record : records) {
            json.put(record.getChannelName(), record.getValue().getValue());
        }
        return json;
    }

    public JsonObject readTags(String assetPid, List<String> tagNames) throws KuraException {
        JsonObject json = new JsonObject();
        Asset asset = getAsset(assetPid);
        List<ChannelRecord> records = asset.read(tagNames.stream().collect(Collectors.toSet()));
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

    public void writeTags(String assetPid, JsonObject json) throws KuraException {
        List<ChannelRecord> channelRecords = new ArrayList<>();
        Asset asset = getAsset(assetPid);
        for (String tagName : json.fieldNames()) {
            Map<String, Channel> chennels = asset.getAssetConfiguration().getAssetChannels();
            Channel channel = chennels.get(tagName);
            if (channel == null)
                throw KuraException.internalError("标签:" + tagName + "错误");
            DataType dataType = channel.getValueType();
            String value = String.valueOf(json.getValue(tagName));
            TypedValue<?> tValue = TypedValues.parseTypedValue(dataType, value);
            ChannelRecord writeRecord = ChannelRecord.createWriteRecord(tagName, tValue);
            channelRecords.add(writeRecord);
        }
        asset.write(channelRecords);
    }

    public void writeTags(String assetPid, String jsonString) throws KuraException {
        JsonObject jsonObject = new JsonObject(jsonString);
        writeTags(assetPid, jsonObject);
    }

    public void writeTags(String assetPid, Map<String, Object> map) throws KuraException {
        JsonObject jsonObject = new JsonObject(map);
        writeTags(assetPid, jsonObject);
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
        if (channel == null)
            throw KuraException.internalError("标签:" + tagName + "错误");
        DataType dataType = channel.getValueType();
        TypedValue<?> tValue = TypedValues.parseTypedValue(dataType, String.valueOf(value));
        ChannelRecord writeRecord = ChannelRecord.createWriteRecord(tagName, tValue);
        channelRecords.add(writeRecord);
        asset.write(channelRecords);
    }
}
