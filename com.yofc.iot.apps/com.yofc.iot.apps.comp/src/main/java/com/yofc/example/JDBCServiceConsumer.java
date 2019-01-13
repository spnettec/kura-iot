package com.yofc.example;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import com.yofc.iot.component.api.DataService;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.SQLConnection;

@Component(immediate = true)
public class JDBCServiceConsumer implements DataService {

    @Reference(target = "(type=h2)")
    private JDBCClient client;

    @Activate
    public void start() {
        System.out.println("start");
    }

    @Override
    public void retrieve(Handler<AsyncResult<List<String>>> resultHandler) {
        client.getConnection(conn -> {
            if (conn.failed()) {
                resultHandler.handle(Future.failedFuture(conn.cause()));
                return;
            }

            final SQLConnection connection = conn.result();
            connection.execute("create table test(id int primary key, name varchar(255))", res -> {
                if (res.failed()) {
                    resultHandler.handle(Future.failedFuture(res.cause()));
                    return;
                }
                // insert some test data
                List<String> results = new ArrayList<>();
                connection.execute("insert into test values(1, 'Hello')", insert -> {
                    // query some data
                    connection.query("select * from test", rs -> {
                        results.addAll(
                                rs.result().getResults().stream().map(JsonArray::encode).collect(Collectors.toList()));

                        // and close the connection
                        connection.close(done -> {
                            if (done.failed()) {
                                resultHandler.handle(Future.failedFuture(done.cause()));
                            } else {
                                resultHandler.handle(Future.succeededFuture(results));
                            }
                        });
                    });
                });
            });
        });
    }
}
