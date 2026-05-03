/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.yofc.opcuaserver.configuration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.yaml.YAMLFactory;

public class PasswordConfiguration {

    private static final String OBFUSCATE = "OBF:";

    @JsonIgnore
    private String passwordConfigFile;

    @JsonProperty
    private String version;

    @JsonProperty
    private String securityPassword;

    @JsonProperty
    private Map<String, User> users = new HashMap<>();

    public PasswordConfiguration() {
    }

    public boolean checkPassword(String username, String password) {
        if (users.containsKey(username)) {
            return users.get(username).checkPassword(password);
        }
        return false;
    }

    public void createUser(String username, String password, String security) throws IOException {
        User user = new User(username, password, security);
        users.put(username, user);
        ObjectMapper om = new ObjectMapper(new YAMLFactory());
        om.writeValue(new File(passwordConfigFile), this);
    }

    @JsonIgnore
    public String getSecurityPassword() {
        return deobfuscate("OBF:" + securityPassword);
    }

    @JsonIgnore
    public void setSecurityPassword(String value) throws IOException {
        securityPassword = obfuscate(value).substring(4);
        ObjectMapper om = new ObjectMapper(new YAMLFactory());
        om.writeValue(new File(passwordConfigFile), this);
    }

    public void setPasswordConfigFile(Path value) {
        passwordConfigFile = value.toString();
    }

    public void setVersion(String value) {
        version = value;
    }

    private static String deobfuscate(String s) {
        if (s.startsWith(OBFUSCATE))
            s = s.substring(4);

        byte[] b = new byte[s.length() / 2];
        int l = 0;
        for (int i = 0; i < s.length(); i += 4) {
            if (s.charAt(i) == 'U') {
                i++;
                String x = s.substring(i, i + 4);
                int i0 = Integer.parseInt(x, 36);
                byte bx = (byte) (i0 >> 8);
                b[l++] = bx;
            } else {
                String x = s.substring(i, i + 4);
                int i0 = Integer.parseInt(x, 36);
                int i1 = (i0 / 256);
                int i2 = (i0 % 256);
                byte bx = (byte) ((i1 + i2 - 254) / 2);
                b[l++] = bx;
            }
        }

        return new String(b, 0, l, StandardCharsets.UTF_8);
    }

    private static String obfuscate(String s) {
        StringBuilder buf = new StringBuilder();
        byte[] b = s.getBytes(StandardCharsets.UTF_8);

        buf.append(OBFUSCATE);
        for (int i = 0; i < b.length; i++) {
            byte b1 = b[i];
            byte b2 = b[b.length - (i + 1)];
            if (b1 < 0 || b2 < 0) {
                int i0 = (0xff & b1) * 256 + (0xff & b2);
                String x = Integer.toString(i0, 36).toLowerCase(Locale.ENGLISH);
                buf.append("U0000", 0, 5 - x.length());
                buf.append(x);
            } else {
                int i1 = 127 + b1 + b2;
                int i2 = 127 + b1 - b2;
                int i0 = i1 * 256 + i2;
                String x = Integer.toString(i0, 36).toLowerCase(Locale.ENGLISH);

                buf.append("000", 0, 4 - x.length());
                buf.append(x);
            }
        }
        return buf.toString();
    }

}
