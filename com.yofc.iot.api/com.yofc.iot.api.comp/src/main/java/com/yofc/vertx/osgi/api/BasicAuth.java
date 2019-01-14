package com.yofc.vertx.osgi.api;

import org.apache.commons.lang3.StringUtils;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.AuthProvider;
import io.vertx.ext.web.handler.AuthHandler;
import io.vertx.ext.web.handler.impl.BasicAuthHandlerImpl;

public class BasicAuth extends BasicAuthHandlerImpl {

    public BasicAuth(AuthProvider authProvider, String realm) {
        super(authProvider, realm);
    }

    public static AuthHandler create(JsonObject config) {
        return new BasicAuth(authenticateBasic(config), config.getString("realm", "yofc-iot-gateway"));
    }

    public static AuthHandler create(JsonArray config) {
        return new BasicAuth(authenticateBasic(config), "yofc-iot-gateway");
    }

    private static AuthProvider authenticateBasic(JsonObject config) {
        return (authInfo, resultHandler) -> {
            String storedUsername = config.getString("username");
            String storedPassword = config.getString("password");

            if (storedUsername == null || storedPassword == null) {
                resultHandler.handle(Future.failedFuture("Credentials not set in configuration."));
                return;
            }

            String username = authInfo.getString("username");
            String password = StringUtils.chomp(authInfo.getString("password"));

            if (storedUsername.equals(username) && storedPassword.equals(password)) {
                resultHandler.handle(Future.succeededFuture());
            } else {
                resultHandler.handle(Future.failedFuture("No such user, or password incorrect."));
            }
        };
    }

    private static AuthProvider authenticateBasic(JsonArray config) {
        return (authInfo, resultHandler) -> {
            if (config == null || config.size() == 0) {
                resultHandler.handle(Future.failedFuture("Credentials not set in configuration."));
                return;
            }
            String username = authInfo.getString("username");
            String password = StringUtils.chomp(authInfo.getString("password"));
            boolean isMatch = config.stream().anyMatch(p -> {
                JsonObject storedConfig = (JsonObject) p;
                String storedUsername = storedConfig.getString("username");
                String storedPassword = storedConfig.getString("password");
                if (storedUsername.equals(username) && storedPassword.equals(password))
                    return true;
                else
                    return false;
            });
            if (isMatch) {
                resultHandler.handle(Future.succeededFuture());
            } else {
                resultHandler.handle(Future.failedFuture("No such user, or password incorrect."));
            }
        };
    }

}