package com.yofc.vertx.osgi.api;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.kura.KuraException;
import org.eclipse.kura.asset.Asset;
import org.eclipse.kura.asset.AssetService;
import org.eclipse.kura.channel.Channel;
import org.eclipse.kura.channel.ChannelRecord;
import org.eclipse.kura.type.DataType;
import org.eclipse.kura.type.TypedValue;
import org.eclipse.kura.type.TypedValues;

import io.vertx.core.json.JsonObject;

public class AssetHelp {

    private final AssetService assetService;
    private Map<String, Asset> assets = new HashMap<>();

    public AssetHelp(AssetService assetService) {
        this.assetService = assetService;
    }

    public Asset getAsset(String assetPid) {
        if (assets.containsKey(assetPid))
            return assets.get(assetPid);
        Asset asset = this.assetService.getAsset(assetPid);
        assets.put(assetPid, asset);
        return asset;

    }

    public JsonObject readTags(String assetPid, String... tagNames) throws KuraException {
        JsonObject json = new JsonObject();
        Asset asset = getAsset(assetPid);
        List<ChannelRecord> records = asset.read(Arrays.asList(tagNames).stream().collect(Collectors.toSet()));
        for (ChannelRecord record : records) {
            json.put(record.getChannelName(), record.getValue().getValue().toString());
        }
        return json;
    }

    public JsonObject readTags(String assetPid, List<String> tagNames) throws KuraException {
        JsonObject json = new JsonObject();
        Asset asset = getAsset(assetPid);
        List<ChannelRecord> records = asset.read(tagNames.stream().collect(Collectors.toSet()));
        for (ChannelRecord record : records) {
            json.put(record.getChannelName(), record.getValue().getValue().toString());
        }
        return json;
    }

    public void writeTags(String assetPid, JsonObject json) throws KuraException {
        List<ChannelRecord> channelRecords = Collections.emptyList();
        Asset asset = getAsset(assetPid);
        for (String tagName : json.fieldNames()) {
            Map<String, Channel> chennels = asset.getAssetConfiguration().getAssetChannels();
            Channel channel = chennels.get(tagName);
            DataType dataType = channel.getValueType();
            String value = String.valueOf(json.getValue(tagName));
            TypedValue<?> tValue = TypedValues.parseTypedValue(dataType, value);
            ChannelRecord writeRecord = ChannelRecord.createWriteRecord(tagName, tValue);
            channelRecords.add(writeRecord);
        }
        asset.write(channelRecords);
    }
}
