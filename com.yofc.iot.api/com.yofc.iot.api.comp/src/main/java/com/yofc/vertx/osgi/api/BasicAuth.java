package com.yofc.vertx.osgi.api;

import org.apache.commons.lang3.StringUtils;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.authentication.AuthenticationProvider;
import io.vertx.ext.web.handler.AuthenticationHandler;
import io.vertx.ext.web.handler.impl.BasicAuthHandlerImpl;

public class BasicAuth extends BasicAuthHandlerImpl {

    public BasicAuth(AuthenticationProvider authProvider, String realm) {
        super(authProvider, realm);
    }

    public static AuthenticationHandler create(JsonObject config) {
        return new BasicAuth(authenticateBasic(config), config.getString("realm", "yofc-iot-gateway"));
    }

    public static AuthenticationHandler create(JsonArray config) {
        return new BasicAuth(authenticateBasic(config), "yofc-iot-gateway");
    }

    private static AuthenticationProvider authenticateBasic(JsonObject config) {
        return authInfo -> {
            String storedUsername = config.getString("username");
            String storedPassword = config.getString("password");

            if (storedUsername == null || storedPassword == null) {
                return Future.failedFuture("Credentials not set in configuration.");
            }

            String username = authInfo.toJson().getString("username");
            String password = StringUtils.chomp(authInfo.toJson().getString("password"));

            if (storedUsername.equals(username) && storedPassword.equals(password)) {
                return Future.succeededFuture(User.fromName(username));
            } else {
                return Future.failedFuture("No such user, or password incorrect.");
            }
        };
    }

    private static AuthenticationProvider authenticateBasic(JsonArray config) {
        return (authInfo) -> {
            if (config == null || config.size() == 0) {
                return Future.failedFuture("Credentials not set in configuration.");
            }
            String username = authInfo.toJson().getString("username");
            String password = StringUtils.chomp(authInfo.toJson().getString("password"));
            boolean isMatch = config.stream().anyMatch(p -> {
                JsonObject storedConfig = (JsonObject) p;
                String storedUsername = storedConfig.getString("username");
                String storedPassword = storedConfig.getString("password");
                return storedUsername.equals(username) && storedPassword.equals(password);
            });
            if (isMatch) {
                return Future.succeededFuture(User.fromName(username));
            } else {
                return Future.failedFuture("No such user, or password incorrect.");
            }
        };
    }
}
