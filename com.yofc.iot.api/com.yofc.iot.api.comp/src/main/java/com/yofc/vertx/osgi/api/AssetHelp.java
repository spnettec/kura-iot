package com.yofc.vertx.osgi.api;

import java.util.List;
import java.util.Map;

import org.eclipse.kura.KuraException;

import io.vertx.core.json.JsonObject;

public interface AssetHelp {

    public JsonObject readTags(String assetPid, String... tagNames) throws KuraException;

    public JsonObject readTags(String assetPid, List<String> tagNames) throws KuraException;

    public void writeTags(String assetPid, JsonObject json) throws KuraException;

    public void writeTags(String assetPid, String jsonString) throws KuraException;

    public void writeTags(String assetPid, Map<String, Object> map) throws KuraException;

    public void writeTags(String assetPid, Object bean) throws KuraException;

    public void writeTag(String assetPid, String tagName, Object value) throws KuraException;

}
