package org.apache.plc4x.java.opcuaserver;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.yofc.opcuaserver.configuration.Configuration;
import com.yofc.opcuaserver.configuration.DeviceConfiguration;
import com.yofc.opcuaserver.configuration.Tag;

import io.vertx.mysqlclient.MySQLBuilder;
import io.vertx.mysqlclient.MySQLConnectOptions;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.SqlClient;

public class TestConfigFile {

    String configFile = "src/test/resources/config.yml";

    @Test
    void saveConfigFile() {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        Configuration config;
        mapper.findAndRegisterModules();
        try {
            config = mapper.readValue(new File(configFile), Configuration.class);
            config.setConfigFile(configFile);
        } catch (IOException e) {
            throw new RuntimeException("Error parsing config file ", e);
        }
        DeviceConfiguration device = config.getDevices().get(0);
        List<Tag> tags = device.getTags();
        tags.clear();

        MySQLConnectOptions connectOptions = new MySQLConnectOptions().setPort(3307).setHost("127.0.0.1")
                .setDatabase("lcsp53").setUser("root").setPassword("root");

        // Pool options
        PoolOptions poolOptions = new PoolOptions().setMaxSize(1);

        // Create the client pool
        SqlClient client = MySQLBuilder.client().with(poolOptions).connectingTo(connectOptions).build();
        final CompletableFuture<RowSet<Row>> completableFuture = new CompletableFuture<>();
        // A simple query
        client.query(
                "select DISTINCT concat( READ_CONNECTION , '.' , DB2MODEL_NAME,'.',DBW2ADDRESS) from device where READ_CONNECTION is not NULL")
                .execute().onComplete(ar -> {
                    if (ar.succeeded()) {
                        RowSet<Row> result = ar.result();
                        completableFuture.complete(result);
                    } else {
                        System.out.println("Failure: " + ar.cause().getMessage());
                    }
                });

        try {
            RowSet<Row> result = completableFuture.get();
            result.forEach(r -> {
                String addr = r.getString(0) + ",b,64";
                Tag tag = new Tag();
                tag.setName(addr);
                tag.setAlias(addr);
                tag.setIden(addr);
                String addr1 = addr.replaceAll(",", "_");
                addr1 = addr1.replaceAll("-", "_");
                tag.setAddress("FILE/" + addr1 + ":BYTE[64]");
                tags.add(tag);
            });
        } catch (InterruptedException | ExecutionException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        final CompletableFuture<RowSet<Row>> completableFuture1 = new CompletableFuture<>();
        // A simple query
        client.query(
                "select DISTINCT concat( WRITE_CONNECTION, '.', DB1MODEL_NAME, '.', DBW1ADDRESS ) from device where READ_CONNECTION is not NULL")
                .execute().onComplete(ar -> {
                    if (ar.succeeded()) {
                        RowSet<Row> result = ar.result();
                        completableFuture1.complete(result);
                    } else {
                        System.out.println("Failure: " + ar.cause().getMessage());
                    }
                });

        try {
            RowSet<Row> result = completableFuture1.get();
            result.forEach(r -> {
                String addr = r.getString(0) + ",b,45";
                Tag tag = new Tag();
                tag.setName(addr);
                tag.setAlias(addr);
                tag.setIden(addr);
                String addr1 = addr.replaceAll(",", "_");
                addr1 = addr1.replaceAll("-", "_");
                tag.setAddress("FILE/" + addr1 + ":BYTE[45]");
                tags.add(tag);
            });

        } catch (InterruptedException | ExecutionException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        final CompletableFuture<RowSet<Row>> completableFuture2 = new CompletableFuture<>();
        // A simple query
        client.query(
                "select DISTINCT concat( READ_CONNECTION, '.', DB2MODEL_NAME, '.', DBW2ADDRESS ) from device where READ_CONNECTION is not NULL")
                .execute().onComplete(ar -> {
                    if (ar.succeeded()) {
                        RowSet<Row> result = ar.result();
                        completableFuture2.complete(result);
                    } else {
                        System.out.println("Failure: " + ar.cause().getMessage());
                    }
                });

        try {
            RowSet<Row> result = completableFuture2.get();
            result.forEach(r -> {
                String addr = r.getString(0) + ",b,33";
                Tag tag = new Tag();
                tag.setName(addr);
                tag.setAlias(addr);
                tag.setIden(addr);
                String addr1 = addr.replaceAll(",", "_");
                addr1 = addr1.replaceAll("-", "_");
                tag.setAddress("FILE/" + addr1 + ":BYTE[33]");
                tags.add(tag);
            });

        } catch (InterruptedException | ExecutionException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        final CompletableFuture<RowSet<Row>> completableFuture3 = new CompletableFuture<>();
        // A simple query
        client.query(
                "SELECT DISTINCT concat( READ_CONNECTION, '.', DB2MODEL_NAME, '.', DBW2ADDRESS ) FROM device WHERE READ_CONNECTION IS NOT NULL and DEVICE_KIND_INDEX=42")
                .execute().onComplete(ar -> {
                    if (ar.succeeded()) {
                        RowSet<Row> result = ar.result();
                        completableFuture3.complete(result);
                    } else {
                        System.out.println("Failure: " + ar.cause().getMessage());
                    }
                });

        try {
            RowSet<Row> result = completableFuture3.get();
            result.forEach(r -> {
                String addr = r.getString(0) + ",b,14";
                Tag tag = new Tag();
                tag.setName(addr);
                tag.setAlias(addr);
                tag.setIden(addr);
                String addr1 = addr.replaceAll(",", "_");
                addr1 = addr1.replaceAll("-", "_");
                tag.setAddress("FILE/" + addr1 + ":BYTE[14]");
                tags.add(tag);
            });

        } catch (InterruptedException | ExecutionException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        final CompletableFuture<RowSet<Row>> completableFuture4 = new CompletableFuture<>();
        // A simple query
        client.query(
                "SELECT DISTINCT concat( WRITE_CONNECTION, '.', DB1MODEL_NAME, '.', DBW1ADDRESS ) FROM device WHERE READ_CONNECTION IS NOT NULL and DEVICE_KIND_INDEX=42")
                .execute().onComplete(ar -> {
                    if (ar.succeeded()) {
                        RowSet<Row> result = ar.result();
                        completableFuture4.complete(result);
                    } else {
                        System.out.println("Failure: " + ar.cause().getMessage());
                    }
                });

        try {
            RowSet<Row> result = completableFuture4.get();
            result.forEach(r -> {
                String addr = r.getString(0) + ",b,26";
                Tag tag = new Tag();
                tag.setName(addr);
                tag.setAlias(addr);
                tag.setIden(addr);
                String addr1 = addr.replaceAll(",", "_");
                addr1 = addr1.replaceAll("-", "_");
                tag.setAddress("FILE/" + addr1 + ":BYTE[26]");
                tags.add(tag);
            });

        } catch (InterruptedException | ExecutionException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        Map<String, String> others = new HashMap<>() {

            private static final long serialVersionUID = 1L;

            {
                put("S7_Connection_6.db47.0", "16");
                put("S7_Connection_6.db31.0", "32");
                put("S7_Connection_13.db2.0", "14");
                put("S7_Connection_13.db1.0", "26");
            }
        };
        others.entrySet().stream().forEach(entry -> {
            String addr = entry.getKey() + ",b," + entry.getValue();
            Tag tag = new Tag();
            tag.setName(addr);
            tag.setAlias(addr);
            tag.setIden(addr);
            String addr1 = addr.replaceAll(",", "_");
            addr1 = addr1.replaceAll("-", "_");
            tag.setAddress("FILE/" + addr1 + ":BYTE[" + entry.getValue() + "]");
            tags.add(tag);
        });
        try {
            mapper.writeValue(new File(configFile), config);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        client.close();

    }

}
