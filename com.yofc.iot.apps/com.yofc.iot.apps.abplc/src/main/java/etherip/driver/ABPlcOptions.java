/**
 * Copyright (c) 2017, 2018 Eurotech and/or its affiliates
 *
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Amit Kumar Mondal (admin@amitinside.com)
 *   Eurotech
 */

package etherip.driver;

import java.util.Map;

final class ABPlcOptions {

    private static final Property<String> IP_PROP = new Property<>("hostIp", "");
    private static final Property<Boolean> AUTENTICATE_PROP = new Property<>("authenticate", false);
    private static final Property<String> PASSWORD_PROP = new Property<>("password", "");
    private static final Property<Integer> PORT_PROP = new Property<>("port", 44818);
    private static final Property<Integer> SLOT_PROP = new Property<>("slot", 0);
    private static final Property<Integer> MAX_REQUEST_ITEMS = new Property<>("maxRequestItems", 100);

    private final String ip;
    private final boolean authenticate;
    private final String password;
    private final int port;
    private final int slot;
    private final int maxRequestItems;

    ABPlcOptions(final Map<String, Object> properties) {
        this.ip = IP_PROP.get(properties);
        this.authenticate = AUTENTICATE_PROP.get(properties);
        this.password = PASSWORD_PROP.get(properties);
        this.port = PORT_PROP.get(properties);
        this.slot = SLOT_PROP.get(properties);
        this.maxRequestItems = MAX_REQUEST_ITEMS.get(properties);
    }

    String getIp() {
        return this.ip;
    }

    boolean shouldAuthenticate() {
        return this.authenticate;
    }

    String getPassword() {
        return this.password;
    }

    int getPort() {
        return this.port;
    }

    int getSlot() {
        return this.slot;
    }

    int getMaxItemCountPerRequest() {
        return this.maxRequestItems;
    }

    private static class Property<T> {

        private final String key;
        private final T defaultValue;

        public Property(String key, T defaultValue) {
            this.key = key;
            this.defaultValue = defaultValue;
        }

        @SuppressWarnings("unchecked")
        public T get(Map<String, Object> properties) {
            final Object value = properties.get(this.key);
            if (this.defaultValue.getClass().isInstance(value)) {
                return (T) value;
            }
            return this.defaultValue;
        }
    }

}
