package com.batuhanmeral.library.common.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ClientAddressTest {

    @ParameterizedTest
    @CsvSource({
        "198.51.100.7, 198.51.100.7",
        // An IPv4 client seen through an IPv6 socket is still that IPv4 client.
        "::ffff:198.51.100.7, 198.51.100.7",
        "2001:db8:1:2::1, 2001:db8:1:2::/64",
        "2001:0DB8:0001:0002:ffff:ffff:ffff:ffff, 2001:db8:1:2::/64",
        "fe80::1%eth0, fe80:0:0:0::/64",
        "::1, 0:0:0:0::/64",
        // Not an address (a servlet container always gives one): kept as it is, never looked up
        // in DNS.
        "example.com, example.com"
    })
    void aClientIsCountedByItsAddressOrItsIpv6Network(String remoteAddress, String key) {
        assertThat(ClientAddress.keyOf(remoteAddress)).isEqualTo(key);
    }
}
