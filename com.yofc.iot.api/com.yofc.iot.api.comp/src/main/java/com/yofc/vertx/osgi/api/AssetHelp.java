package com.yofc.vertx.osgi.api;

import io.vertx.core.json.JsonObject;
import org.eclipse.kura.KuraException;

import java.util.List;
import java.util.Map;

public interface AssetHelp {

  Object readTag(String assetPid, String tagName) throws KuraException;

  JsonObject readTags(String assetPid, String... tagNames) throws KuraException;

  JsonObject readTags(String assetPid, List<String> tagNames) throws KuraException;

  void writeTags(String assetPid, JsonObject json) throws KuraException;

  void writeTags(String assetPid, String jsonString) throws KuraException;

  void writeTags(String assetPid, Map<String, Object> map) throws KuraException;

  void writeTags(String assetPid, Object bean) throws KuraException;

  void writeTag(String assetPid, String tagName, Object value) throws KuraException;
}
