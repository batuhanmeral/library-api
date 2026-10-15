package com.batuhanmeral.library.common.ratelimit;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.regex.Pattern;

/**
 * Hız sınırının saydığı istemci: isteğin geldiği adres. IPv4 tek istemcidir, IPv6 /64 ağı olarak
 * sayılır.
 *
 * <p>Adres TCP karşı tarafıdır. Reverse proxy arkasında server.forward-headers-strategy açılmalı;
 * proxy yoksa kapalı kalmalı, yoksa istemci header'a istediği adresi yazabilir.
 */
final class ClientAddress {

    private static final Pattern IPV4 =
            Pattern.compile(
                    "((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)");

    /**
     * Hex rakam ya da iki nokta ile başlar ve iki nokta içerir: InetAddress bunu DNS'e sormadan
     * literal olarak okur.
     */
    private static final Pattern IPV6 =
            Pattern.compile("[0-9A-Fa-f:][0-9A-Fa-f.]*:[0-9A-Fa-f:.]{0,44}(%[\\w.-]{1,32})?");

    /** Adres olmayan her değer bu tek anahtarı paylaşır, her biri yeni sayaç almasın. */
    static final String INVALID = "invalid";

    private ClientAddress() {}

    static String keyOf(String remoteAddress) {
        if (remoteAddress == null) {
            return INVALID;
        }
        if (IPV4.matcher(remoteAddress).matches()) {
            return remoteAddress;
        }
        if (!IPV6.matcher(remoteAddress).matches()) {
            // Servlet container hep adres verir; bu sadece değer başka yerden gelirse olur.
            return INVALID;
        }
        InetAddress address;
        try {
            // Literal olarak okunur, DNS'e sorulmaz (InetAddress.ofLiteral Java 22'de geldi).
            address = InetAddress.getByName(stripZone(remoteAddress));
        } catch (UnknownHostException e) {
            return INVALID;
        }
        if (address instanceof Inet4Address) {
            // IPv4'e eşlenmiş IPv6 adresi ("::ffff:198.51.100.7") o IPv4 istemcisidir.
            return address.getHostAddress();
        }
        byte[] bytes = address.getAddress();
        return "%x:%x:%x:%x::/64"
                .formatted(group(bytes, 0), group(bytes, 2), group(bytes, 4), group(bytes, 6));
    }

    private static String stripZone(String address) {
        int zone = address.indexOf('%');
        return zone < 0 ? address : address.substring(0, zone);
    }

    private static int group(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xff) << 8) | (bytes[offset + 1] & 0xff);
    }
}
