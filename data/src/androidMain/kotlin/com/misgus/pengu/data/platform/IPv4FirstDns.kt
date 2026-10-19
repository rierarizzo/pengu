package com.misgus.pengu.data.platform

import okhttp3.Dns
import java.net.Inet4Address
import java.net.InetAddress

/**
 * DNS resolver that answers IPv4 addresses before IPv6 ones.
 *
 * A host with a broken or unroutable AAAA record makes the socket connect wait
 * for the IPv6 attempt to time out before trying IPv4, which shows up as a
 * multi-second stall on the first request to an addon. Reordering the answers
 * keeps IPv6 hosts working (they are still tried, just later) and removes the
 * stall on the ones that are not.
 */
internal class IPv4FirstDns(
    private val delegate: Dns = Dns.SYSTEM,
) : Dns {
    override fun lookup(hostname: String): List<InetAddress> = delegate.lookup(hostname).sortedBy { if (it is Inet4Address) 0 else 1 }
}
