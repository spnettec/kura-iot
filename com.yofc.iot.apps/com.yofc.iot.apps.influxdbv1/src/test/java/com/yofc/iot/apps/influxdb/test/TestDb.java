package com.yofc.iot.apps.influxdb.test;

import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBFactory;
import org.influxdb.dto.Point;
import org.junit.jupiter.api.Test;

class TestDb {

    @Test
    public void test() {
        String database = "yofc_vfdb";
        String retentionPolicy = "";

        // 1.打开客户端
        InfluxDB client = InfluxDBFactory.connect("http://10.100.60.200:8086", "admin", "admin");
        client.setDatabase(database);
        client.setRetentionPolicy(retentionPolicy);

        System.out.println("*** Write Points ***");

        // 2.创建写入api

        Point.Builder pointBuild = Point.measurement("demo");
        pointBuild.tag("sb_id", "00111");
        pointBuild.addField("sb_20_pv", 200);
        Point point = pointBuild.build();
        System.out.println(point.toString());
        // 执行写入数据操作
        client.write(point);

        System.out.println("success");
        client.close();
    }

}
