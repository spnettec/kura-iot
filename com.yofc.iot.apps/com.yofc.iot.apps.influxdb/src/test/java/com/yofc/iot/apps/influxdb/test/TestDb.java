package com.yofc.iot.apps.influxdb.test;

import org.junit.jupiter.api.Test;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import com.influxdb.client.WriteApi;
import com.influxdb.client.write.Point;

class TestDb {

    @Test
    public void test() {
        String database = "yofc_vfdb";
        String retentionPolicy = "";

        // 1.打开客户端
        InfluxDBClient client = InfluxDBClientFactory.createV1("http://10.100.60.200:8086", "admin",
                "admin".toCharArray(), database, retentionPolicy);

        System.out.println("*** Write Points ***");

        // 2.创建写入api
        try (WriteApi writeApi = client.makeWriteApi()) {

            Point point = Point.measurement("demo").addTag("sb_id", "00111").addField("sb_10_pv", 200);
            System.out.println(point.toLineProtocol());
            // 执行写入数据操作
            writeApi.writePoint(point);

        }
        System.out.println("success");
        client.close();
    }

}
