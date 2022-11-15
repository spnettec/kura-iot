package com.yofc.vertx.osgi;

import static org.eclipse.kura.channel.ChannelType.READ_WRITE;
import static org.eclipse.kura.channel.ChannelType.WRITE;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.kura.KuraException;
import org.eclipse.kura.asset.Asset;
import org.eclipse.kura.asset.AssetConfiguration;
import org.eclipse.kura.channel.Channel;
import org.eclipse.kura.channel.ChannelFlag;
import org.eclipse.kura.channel.ChannelRecord;
import org.eclipse.kura.channel.ChannelType;
import org.eclipse.kura.type.DataType;
import org.eclipse.kura.type.TypedValue;
import org.eclipse.kura.type.TypedValues;
import org.eclipse.kura.wire.WireRecord;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.util.tracker.ServiceTracker;

import com.yofc.vertx.osgi.api.AssetHelp;
import com.yofc.vertx.osgi.api.AssetTrackerCustomizer;

import io.vertx.core.json.JsonObject;

@Component(immediate = true)
public class AssetHelpImpl implements AssetHelp {

    private AssetTrackerCustomizer assetTrackerCustomizer;
    private ServiceTracker<Asset, Asset> assetServiceTracker;
    private Map<String, Asset> assets;

    public static List<ChannelRecord> determineWritingChannels(Asset asset, List<WireRecord> records) {
        final AssetConfiguration assetConfiguration = asset.getAssetConfiguration();

        final List<ChannelRecord> channelRecordsToWrite = new ArrayList<>();
        final Map<String, Channel> channels = assetConfiguration.getAssetChannels();
        for (final Entry<String, Channel> channelEntry : channels.entrySet()) {
            final Channel channel = channelEntry.getValue();
            final String channelName = channel.getName();
            final ChannelType channelType = channel.getType();

            if (!channel.isEnabled() || (channelType != WRITE && channelType != READ_WRITE)) {
                continue;
            }

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

    @Activate
    public void active(BundleContext context) {
        this.assetTrackerCustomizer = new AssetTrackerCustomizer(context);
        this.assetServiceTracker = new ServiceTracker<>(context, Asset.class.getName(), this.assetTrackerCustomizer);
        this.assetServiceTracker.open();
    }

    @Deactivate
    protected synchronized void deactivate() {
        this.assetServiceTracker.close();
    }

    private void findAssets() {
        this.assets = this.assetTrackerCustomizer.getRegisteredAssets();
    }

    public Asset getAsset(String assetPid) throws KuraException {
        findAssets();
        Asset asset = this.assets.get(assetPid);
        if (asset == null) {
            throw KuraException.internalError("can not find asset name:" + assetPid);
        }
        return asset;
    }

    @Override
    public JsonObject readTags(String assetPid, String... tagNames) throws KuraException {
        JsonObject json = new JsonObject();
        Asset asset = getAsset(assetPid);
        List<ChannelRecord> records;
        records = asset.read(Arrays.stream(tagNames).collect(Collectors.toSet()));
        for (ChannelRecord record : records) {
            json.put(record.getChannelName(), record.getValue().getValue());
        }
        return json;
    }

    @Override
    public Object readTag(String assetPid, String tagName) throws KuraException {
        JsonObject json = readTags(assetPid, tagName);
        return json.getValue(tagName);
    }

    @Override
    public JsonObject readTags(String assetPid, List<String> tagNames) throws KuraException {
        JsonObject json = new JsonObject();
        Asset asset = getAsset(assetPid);

        List<ChannelRecord> records;
        records = asset.read(new HashSet<>(tagNames));

        for (ChannelRecord record : records) {
            Object value;
            if (record.getChannelStatus().getChannelFlag() == ChannelFlag.FAILURE) {
                value = "read failured";
            } else {
                value = record.getValue() == null ? null : record.getValue().getValue();
            }
            json.put(record.getChannelName(), value);
        }
        return json;
    }

    @Override
    public void writeTags(String assetPid, JsonObject json) throws KuraException {
        List<ChannelRecord> channelRecords = new ArrayList<>();
        Asset asset = getAsset(assetPid);
        for (String tagName : json.fieldNames()) {
            Map<String, Channel> chennels = asset.getAssetConfiguration().getAssetChannels();
            Channel channel = chennels.get(tagName);
            if (channel == null) {
                throw KuraException.internalError("tag: " + tagName + " not attatched channel");
            }
            DataType dataType = channel.getValueType();
            String value = String.valueOf(json.getValue(tagName));
            TypedValue<?> tValue = TypedValues.parseTypedValue(dataType, value);
            ChannelRecord writeRecord = ChannelRecord.createWriteRecord(tagName, tValue);
            channelRecords.add(writeRecord);
        }
        asset.write(channelRecords);
        StringBuilder errorMsg = new StringBuilder();
        for (ChannelRecord channelRecord : channelRecords) {
            if (channelRecord.getChannelStatus().getChannelFlag() == ChannelFlag.FAILURE) {
                if (!"".equals(errorMsg.toString())) {
                    errorMsg.append(";");
                }
                errorMsg.append("tag:");
                errorMsg.append(channelRecord.getChannelName());
                errorMsg.append(" write failer,Error Info:");
                errorMsg.append(channelRecord.getChannelStatus().getExceptionMessage());
            }
        }
        if (!"".equals(errorMsg.toString())) {
            throw KuraException.internalError(errorMsg.toString());
        }
    }

    @Override
    public void writeTags(String assetPid, String jsonString) throws KuraException {
        JsonObject jsonObject = new JsonObject(jsonString);
        writeTags(assetPid, jsonObject);
    }

    @Override
    public void writeTags(String assetPid, Map<String, Object> map) throws KuraException {
        JsonObject jsonObject = new JsonObject(map);
        writeTags(assetPid, jsonObject);
    }

    @Override
    public void writeTags(String assetPid, Object bean) throws KuraException {
        JsonObject jsonObject = JsonObject.mapFrom(bean);
        writeTags(assetPid, jsonObject);
    }

    @Override
    public void writeTag(String assetPid, String tagName, Object value) throws KuraException {
        List<ChannelRecord> channelRecords = new ArrayList<>();
        Asset asset = getAsset(assetPid);
        Map<String, Channel> chennels = asset.getAssetConfiguration().getAssetChannels();
        Channel channel = chennels.get(tagName);
        if (channel == null) {
            throw KuraException.internalError("tag: " + tagName + " not attatched channel");
        }
        DataType dataType = channel.getValueType();
        TypedValue<?> tValue = TypedValues.parseTypedValue(dataType, String.valueOf(value));
        ChannelRecord writeRecord = ChannelRecord.createWriteRecord(tagName, tValue);
        channelRecords.add(writeRecord);
        asset.write(channelRecords);
    }
}
