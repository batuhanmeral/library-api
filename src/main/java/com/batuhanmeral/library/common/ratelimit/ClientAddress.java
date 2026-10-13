package com.batuhanmeral.library.common.ratelimit;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.regex.Pattern;

/**
 * Who a rate limit counts: the address the request came from. An IPv4 address is one client. An
 * IPv6 client usually gets a whole /64 network (2^64 addresses) from its provider and could use a
 * new address for every request, so IPv6 is counted per /64.
 *
 * <p>The address is the TCP peer (ServletRequest.getRemoteAddr). Behind a reverse proxy that is the
 * proxy, and every client would share one counter: such a deployment sets
 * server.forward-headers-strategy so that the servlet container takes the client from the proxy's
 * X-Forwarded-For header. Without a proxy that setting must stay off, or a client could write any
 * address into the header.
 */
final class ClientAddress {

    private static final Pattern IPV4 = Pattern.compile("\\d{1,3}(\\.\\d{1,3}){3}");

    /** Hex digits and dots with at least one colon, and a zone after "%". */
    private static final Pattern IPV6 =
            Pattern.compile("[0-9A-Fa-f.]*:[0-9A-Fa-f:.]{1,44}(%[\\w.-]{1,32})?");

    private ClientAddress() {}

    static String keyOf(String remoteAddress) {
        if (remoteAddress == null) {
            return "unknown";
        }
        if (IPV4.matcher(remoteAddress).matches()) {
            return remoteAddress;
        }
        if (!IPV6.matcher(remoteAddress).matches()) {
            // Not an address: a servlet container always gives one, so this is kept as it is.
            return remoteAddress;
        }
        InetAddress address;
        try {
            // A host name never contains a colon, so getByName only parses here and never asks
            // DNS. (InetAddress.ofLiteral, which never looks anything up, is Java 22.)
            address = InetAddress.getByName(stripZone(remoteAddress));
        } catch (UnknownHostException e) {
            return remoteAddress;
        }
        if (address instanceof Inet4Address) {
            // An IPv4-mapped IPv6 address ("::ffff:198.51.100.7") is that IPv4 client.
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
