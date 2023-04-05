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

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

public class Configuration {

    @JsonIgnore
    private String configFile;

    @JsonProperty
    private String version;

    @JsonProperty(required = true)
    private String dir;

    @JsonProperty
    private String name;

    @JsonProperty
    private String path;

    @JsonProperty
    private String alias;

    @JsonProperty
    private Boolean resetPassword = true;

    @JsonProperty
    private String securityPassword;

    @JsonProperty
    private String username;

    @JsonProperty
    private String password;

    @JsonProperty
    private String group;

    @JsonProperty
    private Boolean disableInsecureEndpoint = true;

    @JsonProperty
    private List<DeviceConfiguration> devices;

    @JsonProperty
    private Integer tcpPort = 12686;

    public Configuration() {
    }

    public String getVersion() {
        return version;
    }

    public void setConfigFile(String value) {
        configFile = value;
    }

    public String getName() {
        return name;
    }

    public boolean getDisableInsecureEndpoint() {
        return disableInsecureEndpoint;
    }

    public String getDir() {
        return dir;
    }

    public Integer getTcpPort() {
        return tcpPort;
    }

    public List<DeviceConfiguration> getDevices() {
        return devices;
    }

    public String getSecurityPassword() {
        return securityPassword;
    }

    public void setSecurityPassword(String securityPassword) {
        this.securityPassword = securityPassword;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public Boolean getResetPassword() {
        return resetPassword;
    }

    public String getAlias() {
        return alias;
    }

    public void setAlias(String alias) {
        this.alias = alias;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

}
