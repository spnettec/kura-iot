package org.pcap4j.packet.factory;

import org.pcap4j.packet.factory.statik.services.StaticPacketFactoryBinderProvider;
import org.pcap4j.packet.namednumber.NamedNumber;

public final class PacketFactories {

    private static final PacketFactoryBinder FACTORY_BINDER = new StaticPacketFactoryBinderProvider().getBinder();

    private PacketFactories() {
        throw new AssertionError();
    }

    /**
     * @param <T>
     *            target
     * @param <N>
     *            number
     * @param targetClass
     *            targetClass
     * @param numberClass
     *            numberClass
     * @return a {@link org.pcap4j.packet.factory.PacketFactory PacketFactory} object.
     */
    public static <T, N extends NamedNumber<?, ?>> PacketFactory<T, N> getFactory(Class<T> targetClass,
            Class<N> numberClass) {
        if (numberClass == null || targetClass == null) {
            StringBuilder sb = new StringBuilder();
            sb.append("numberClass: ").append(numberClass).append(" targetClass: ").append(targetClass);
            throw new NullPointerException(sb.toString());
        }

        if (FACTORY_BINDER != null) {
            return FACTORY_BINDER.getPacketFactory(targetClass, numberClass);
        } else {
            return SimplePacketFactoryBinder.getInstance().getPacketFactory(targetClass, numberClass);
        }
    }
}
